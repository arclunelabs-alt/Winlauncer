package com.arclune.winlauncher

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

/** Kanvas gambar: pena halus, penghapus, undo, dan simpan ke galeri. */
class PaintView(ctx: Context) : View(ctx) {
    private var bmp: Bitmap? = null
    private var cv: Canvas? = null
    private val undo = ArrayList<Bitmap>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private var lx = 0f
    private var ly = 0f

    var color = Color.BLACK
    var eraser = false
    var brush = 6f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (w <= 0 || h <= 0) return
        val nb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        nb.eraseColor(Color.WHITE)
        val old = bmp
        val c = Canvas(nb)
        if (old != null) c.drawBitmap(old, 0f, 0f, null)
        bmp = nb; cv = c
    }

    override fun onDraw(c: Canvas) {
        val b = bmp ?: return
        c.drawBitmap(b, 0f, 0f, null)
    }

    private fun pushUndo() {
        val b = bmp ?: return
        undo.add(b.copy(Bitmap.Config.ARGB_8888, false))
        if (undo.size > 8) undo.removeAt(0)
    }

    fun undoLast() {
        if (undo.isEmpty()) return
        val b = undo.removeAt(undo.size - 1).copy(Bitmap.Config.ARGB_8888, true)
        bmp = b; cv = Canvas(b)
        invalidate()
    }

    fun clearAll() {
        pushUndo()
        bmp?.eraseColor(Color.WHITE)
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val c = cv ?: return false
        paint.color = if (eraser) Color.WHITE else color
        paint.strokeWidth = brush
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                pushUndo()
                lx = e.x; ly = e.y
                path.reset(); path.moveTo(lx, ly)
                paint.style = Paint.Style.FILL
                c.drawCircle(lx, ly, brush / 2f, paint)
                paint.style = Paint.Style.STROKE
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.historySize) segment(c, e.getHistoricalX(i), e.getHistoricalY(i))
                segment(c, e.x, e.y)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                path.lineTo(lx, ly)
                c.drawPath(path, paint)
                path.reset()
                invalidate()
            }
        }
        return true
    }

    private fun segment(c: Canvas, x: Float, y: Float) {
        val mx = (x + lx) / 2f
        val my = (y + ly) / 2f
        path.quadTo(lx, ly, mx, my)
        c.drawPath(path, paint)
        path.reset(); path.moveTo(mx, my)
        lx = x; ly = y
    }

    /** Simpan ke Pictures/Win11Launcher. Mengembalikan true bila berhasil. */
    fun save(a: MainActivity): Boolean {
        val b = bmp ?: return false
        val name = "Paint_" + System.currentTimeMillis() + ".png"
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Win11Launcher")
                }
                val uri = a.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
                if (uri == null) false else {
                    a.contentResolver.openOutputStream(uri)?.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    true
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Win11Launcher")
                dir.mkdirs()
                FileOutputStream(File(dir, name)).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                true
            }
        } catch (e: Exception) { false }
    }
}

/** Jendela Paint: toolbar kiri (alat, warna, ukuran, aksi) + kanvas. */
class PaintApp(private val a: MainActivity) : LinearLayout(a) {
    private val canvasView = PaintView(a)
    private val swatches = ArrayList<View>()
    private val palette = intArrayOf(
        0xFF000000.toInt(), 0xFFE81123.toInt(), 0xFFFF8C00.toInt(), 0xFFFFD800.toInt(),
        0xFF16C60C.toInt(), 0xFF0067C0.toInt(), 0xFF8764B8.toInt(), 0xFF7A7574.toInt()
    )
    private lateinit var penBtn: View
    private lateinit var eraserBtn: View

    init {
        setBackgroundColor(if (Theme.dark) 0xFF272727.toInt() else 0xFFF9F9F9.toInt())

        val tools = vBox(a, 8)
        tools.addView(label(a, "Alat", 10f, Theme.subText))
        val row1 = LinearLayout(a)
        penBtn = toolBtn(G.BRUSH) { setEraser(false) }
        eraserBtn = toolBtn(G.ERASER) { setEraser(true) }
        row1.addView(penBtn, lp(0, a.dp(34), 1f).apply { rightMargin = a.dp(3) })
        row1.addView(eraserBtn, lp(0, a.dp(34), 1f).apply { leftMargin = a.dp(3) })
        tools.addView(row1, lp(MATCH, WRAP).apply { topMargin = a.dp(4); bottomMargin = a.dp(8) })

        tools.addView(label(a, "Warna", 10f, Theme.subText))
        for (rowColors in palette.toList().chunked(4)) {
            val r = LinearLayout(a)
            for (col in rowColors) {
                val sw = View(a).apply { setOnClickListener { canvasView.color = col; setEraser(false); markSwatches() } }
                sw.tag = col
                swatches.add(sw)
                r.addView(sw, lp(0, a.dp(24), 1f).apply { setMargins(a.dp(2), a.dp(2), a.dp(2), a.dp(2)) })
            }
            tools.addView(r, lp(MATCH, WRAP))
        }

        tools.addView(label(a, "Ukuran", 10f, Theme.subText).apply { setPadding(0, a.dp(8), 0, 0) })
        tools.addView(FluentSlider(a, 0.15f) { canvasView.brush = 2f + it * 38f }, lp(MATCH, a.dp(28)))

        tools.addView(action("Urungkan") { canvasView.undoLast() }, lp(MATCH, a.dp(32)).apply { topMargin = a.dp(8) })
        tools.addView(action("Hapus semua") { canvasView.clearAll() }, lp(MATCH, a.dp(32)).apply { topMargin = a.dp(4) })
        tools.addView(action("Simpan", true) {
            val ok = canvasView.save(a)
            Toast.makeText(a, if (ok) "Tersimpan di Pictures/Win11Launcher" else "Gagal menyimpan", Toast.LENGTH_SHORT).show()
        }, lp(MATCH, a.dp(32)).apply { topMargin = a.dp(4) })

        addView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(tools) }, lp(a.dp(150), MATCH))
        addView(canvasView, lp(0, MATCH, 1f).apply { setMargins(0, a.dp(8), a.dp(8), a.dp(8)) })
        setEraser(false)
        markSwatches()
    }

    private fun toolBtn(g: G, click: () -> Unit): View = FrameLayout(a).apply {
        addView(Glyph(a, g), FrameLayout.LayoutParams(a.dp(18), a.dp(18), Gravity.CENTER))
        setOnClickListener { click() }
    }

    private fun action(t: String, primary: Boolean = false, click: () -> Unit) = label(a, t, 12f,
        if (primary) Color.WHITE else Theme.text).apply {
        gravity = Gravity.CENTER
        pressable(5, if (primary) Theme.accent else Theme.card)
        setOnClickListener { click() }
    }

    private fun setEraser(on: Boolean) {
        canvasView.eraser = on
        penBtn.background = rounded(a, if (!on) Theme.hover else 0, 5, if (!on) Theme.accent else Theme.stroke, 1)
        eraserBtn.background = rounded(a, if (on) Theme.hover else 0, 5, if (on) Theme.accent else Theme.stroke, 1)
    }

    private fun markSwatches() {
        for (v in swatches) {
            val col = v.tag as Int
            v.background = rounded(a, col, 12, if (col == canvasView.color && !canvasView.eraser) Theme.accent else Theme.stroke, 2)
        }
    }
}
