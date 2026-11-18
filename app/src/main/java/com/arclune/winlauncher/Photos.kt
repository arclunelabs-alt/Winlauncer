package com.arclune.winlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.min

/** Pemindai file media sederhana (tanpa MediaStore), dipakai Foto dan Media Player. */
object MediaScan {
    val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
    val audioExt = setOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "opus")
    val videoExt = setOf("mp4", "mkv", "mov", "webm", "3gp")

    fun scan(exts: Set<String>, dirs: List<String>, max: Int): List<File> {
        val root = Environment.getExternalStorageDirectory()
        val out = ArrayList<File>()
        fun walk(d: File, depth: Int) {
            if (out.size >= max || depth > 4) return
            val list = d.listFiles() ?: return
            for (f in list) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) walk(f, depth + 1)
                else if (f.extension.lowercase() in exts) { out.add(f); if (out.size >= max) return }
            }
        }
        for (name in dirs) walk(File(root, name), 0)
        return out.sortedByDescending { it.lastModified() }
    }
}

/** Thumbnail dengan cache memori + 2 thread decode. */
object Thumbs {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val pool = Executors.newFixedThreadPool(2)
    private val ui = Handler(Looper.getMainLooper())

    fun decode(f: File, maxSide: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= maxSide && o.outHeight / (s * 2) >= maxSide) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    fun load(f: File, size: Int, iv: ImageView) {
        val key = f.path + "@" + size
        iv.tag = key
        val hit = cache.get(key)
        if (hit != null) { iv.setImageBitmap(hit); return }
        iv.setImageDrawable(null)
        pool.execute {
            val b = try { decode(f, size) } catch (e: Throwable) { null }
            if (b != null) {
                cache.put(key, b)
                ui.post { if (iv.tag == key) iv.setImageBitmap(b) }
            }
        }
    }
}

/** ImageView dengan pinch-zoom, geser, dan ketuk-dua-kali untuk reset. */
class ZoomImageView(ctx: Context) : ImageView(ctx) {
    private val m = Matrix()
    private var zoom = 1f
    private var lx = 0f
    private var ly = 0f
    private val scaler = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val target = (zoom * d.scaleFactor).coerceIn(1f, 6f)
            val f = target / zoom
            zoom = target
            m.postScale(f, f, d.focusX, d.focusY)
            imageMatrix = m
            return true
        }
    })
    private val taps = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean { fit(); return true }
    })

    init { scaleType = ScaleType.MATRIX }

    fun setImage(b: Bitmap?) { setImageBitmap(b); post { fit() } }

    private fun fit() {
        val d = drawable ?: return
        val vw = width.toFloat(); val vh = height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        val s = min(vw / d.intrinsicWidth, vh / d.intrinsicHeight)
        zoom = 1f
        m.reset()
        m.postScale(s, s)
        m.postTranslate((vw - d.intrinsicWidth * s) / 2f, (vh - d.intrinsicHeight * s) / 2f)
        imageMatrix = m
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); fit() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaler.onTouchEvent(e)
        taps.onTouchEvent(e)
        if (!scaler.isInProgress && e.pointerCount == 1) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lx = e.x; ly = e.y }
                MotionEvent.ACTION_MOVE -> {
                    if (zoom > 1f) { m.postTranslate(e.x - lx, e.y - ly); imageMatrix = m }
                    lx = e.x; ly = e.y
                }
            }
        }
        return true
    }
}

/** Aplikasi Foto: grid thumbnail, penampil dengan zoom, dan slideshow. */
class PhotosApp(private val a: MainActivity) : FrameLayout(a) {
    private var files: List<File> = emptyList()
    private var idx = 0
    private var slideshow = false
    private val handler = Handler(Looper.getMainLooper())
    private val head = label(a, "Foto", 14f, bold = true)
    private val info = label(a, "Memindai...", 11f, Theme.subText)
    private val grid = GridView(a)
    private val viewer = FrameLayout(a)
    private val zoom = ZoomImageView(a)
    private val nameTv = label(a, "", 12f, 0xFFFFFFFF.toInt())
    private val countTv = label(a, "", 11f, 0xCCFFFFFF.toInt())
    private lateinit var playGlyph: Glyph
    private val slideRun = object : Runnable {
        override fun run() {
            if (!slideshow || files.isEmpty()) return
            showPhoto((idx + 1) % files.size)
            handler.postDelayed(this, 3000)
        }
    }

    init {
        setBackgroundColor(if (Theme.dark) 0xFF272727.toInt() else 0xFFF9F9F9.toInt())

        val page = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val bar = hBox(a).apply { setPadding(a.dp(14), a.dp(8), a.dp(14), a.dp(4)) }
        bar.addView(head); bar.addView(info, lp(0, WRAP, 1f).apply { leftMargin = a.dp(10) })
        bar.addView(FrameLayout(a).apply {
            pressable(5)
            addView(Glyph(a, G.REFRESH), LayoutParams(a.dp(16), a.dp(16), Gravity.CENTER))
            setOnClickListener { scanAsync() }
        }, lp(a.dp(32), a.dp(32)))
        page.addView(bar, lp(MATCH, WRAP))
        grid.apply {
            numColumns = GridView.AUTO_FIT; columnWidth = a.dp(92); stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = a.dp(3); verticalSpacing = a.dp(3)
            isVerticalScrollBarEnabled = false
            setPadding(a.dp(8), 0, a.dp(8), a.dp(8)); clipToPadding = false
            adapter = GridAdapter()
            setOnItemClickListener { _, _, pos, _ -> openViewer(pos) }
        }
        page.addView(grid, lp(MATCH, 0, 1f))
        addView(page, LayoutParams(-1, -1))

        // penampil
        viewer.setBackgroundColor(0xFF000000.toInt())
        viewer.visibility = View.GONE
        viewer.addView(zoom, LayoutParams(-1, -1))
        val top = hBox(a).apply { setBackgroundColor(0x88000000.toInt()); setPadding(a.dp(8), 0, a.dp(12), 0) }
        top.addView(viewerBtn(G.BACK) { closeViewer() }, lp(a.dp(36), a.dp(36)))
        top.addView(nameTv.apply { maxLines = 1; setPadding(a.dp(8), 0, 0, 0) }, lp(0, WRAP, 1f))
        top.addView(countTv)
        viewer.addView(top, LayoutParams(-1, a.dp(40), Gravity.TOP))
        val bottom = hBox(a).apply { setBackgroundColor(0x88000000.toInt()); gravity = Gravity.CENTER }
        bottom.addView(viewerBtn(G.BACK) { step(-1) }, lp(a.dp(44), a.dp(40)))
        playGlyph = Glyph(a, G.PLAY, 0xFFFFFFFF.toInt())
        bottom.addView(FrameLayout(a).apply {
            pressable(5)
            addView(playGlyph, LayoutParams(a.dp(20), a.dp(20), Gravity.CENTER))
            setOnClickListener { toggleSlideshow() }
        }, lp(a.dp(44), a.dp(40)))
        bottom.addView(viewerBtn(G.NEXT) { step(1) }, lp(a.dp(44), a.dp(40)))
        viewer.addView(bottom, LayoutParams(-1, a.dp(40), Gravity.BOTTOM))
        addView(viewer, LayoutParams(-1, -1))
    }

    private fun viewerBtn(g: G, click: () -> Unit): View = FrameLayout(a).apply {
        pressable(5)
        addView(Glyph(a, g, 0xFFFFFFFF.toInt()), LayoutParams(a.dp(18), a.dp(18), Gravity.CENTER))
        setOnClickListener { click() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); scanAsync() }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); slideshow = false; handler.removeCallbacks(slideRun) }

    private fun scanAsync() {
        if (!Perms.hasFiles(a)) {
            info.text = "Butuh izin akses file — buka File Explorer untuk memberi izin"
            Perms.requestFiles(a)
            return
        }
        info.text = "Memindai..."
        Thread {
            val list = try { MediaScan.scan(MediaScan.imageExt, listOf("DCIM", "Pictures", "Download"), 800) } catch (e: Throwable) { emptyList() }
            handler.post {
                files = list
                (grid.adapter as BaseAdapter).notifyDataSetChanged()
                info.text = "${list.size} foto"
            }
        }.start()
    }

    private fun openViewer(i: Int) { viewer.visibility = View.VISIBLE; showPhoto(i) }

    private fun closeViewer() {
        slideshow = false; handler.removeCallbacks(slideRun); playGlyph.g = G.PLAY; playGlyph.invalidate()
        viewer.visibility = View.GONE
    }

    private fun step(d: Int) { if (files.isNotEmpty()) showPhoto((idx + d + files.size) % files.size) }

    private fun showPhoto(i: Int) {
        if (i !in files.indices) return
        idx = i
        val f = files[i]
        nameTv.text = f.name
        countTv.text = "${i + 1} / ${files.size}"
        Thread {
            val b = try { Thumbs.decode(f, 1600) } catch (e: Throwable) { null }
            handler.post { if (idx == i) zoom.setImage(b) }
        }.start()
    }

    private fun toggleSlideshow() {
        slideshow = !slideshow
        playGlyph.g = if (slideshow) G.PAUSE else G.PLAY; playGlyph.invalidate()
        handler.removeCallbacks(slideRun)
        if (slideshow) handler.postDelayed(slideRun, 3000)
    }

    private inner class GridAdapter : BaseAdapter() {
        override fun getCount() = files.size
        override fun getItem(position: Int): Any = files[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val iv = (convertView as? ImageView) ?: ImageView(a).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = ColorDrawable(Theme.card)
                layoutParams = AbsListView.LayoutParams(a.dp(92), a.dp(92))
            }
            Thumbs.load(files[position], a.dp(92), iv)
            return iv
        }
    }
}
