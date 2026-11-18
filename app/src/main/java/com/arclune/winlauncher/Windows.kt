package com.arclune.winlauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.abs

object Zone { const val NONE = 0; const val LEFT = 1; const val RIGHT = 2; const val MAX = 3 }

/** Pegangan kecil (titik diagonal) di pojok kanan-bawah untuk mengubah ukuran jendela. */
class GripView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        p.color = Theme.subText
        val u = context.dpf(2.2f)
        val r = u * 0.55f
        for (i in 0..2) for (j in 0..2) if (i + j >= 2) {
            c.drawCircle(width - u * 2.2f * (i + 1) , height - u * 2.2f * (j + 1), r, p)
        }
    }
}

/** Jendela mengambang bergaya Windows 11: title bar, min/max/close, geser, resize, snap. */
class WindowFrame(
    val a: MainActivity,
    val title: String,
    val glyph: G,
    val content: View,
    val wDp: Int = 560,
    val hDp: Int = 340
) : LinearLayout(a) {
    var minimized = false
    var isFocusedWin = false
    var desk = 0
    val snapped get() = saved != null
    lateinit var wm: Windows
    private var saved: FloatArray? = null
    private val titleBar = LinearLayout(a)
    private var dx = 0f
    private var dy = 0f
    private var moved = false
    private var lastTap = 0L
    private var startX = 0f
    private var startY = 0f
    private val slop = ViewConfiguration.get(a).scaledTouchSlop
    private lateinit var maxBtn: View

    init {
        orientation = VERTICAL
        elevation = a.dpf(16f)
        background = rounded(a, Theme.surfaceSolid, 8, Theme.stroke)
        clipToOutline = true

        titleBar.apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(if (Theme.dark) 0xFF202020.toInt() else 0xFFF3F3F3.toInt())
            setPadding(a.dp(12), 0, 0, 0)
        }
        titleBar.addView(Glyph(a, glyph).apply { layoutParams = LayoutParams(a.dp(16), a.dp(16)) })
        titleBar.addView(label(a, title, 12f).apply {
            setPadding(a.dp(10), 0, 0, 0)
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        })
        titleBar.addView(capBtn(G.MIN, false, null) { wm.minimize(this) })
        maxBtn = capBtn(G.MAX, false, { showSnapMenu() }) { toggleMax() }
        titleBar.addView(maxBtn)
        titleBar.addView(capBtn(G.CLOSE, true, null) { wm.close(this) })
        addView(titleBar, LayoutParams(LayoutParams.MATCH_PARENT, a.dp(34)))

        val body = FrameLayout(a)
        body.addView(content, FrameLayout.LayoutParams(-1, -1))
        val grip = GripView(a)
        grip.setOnTouchListener(resizeTouch())
        body.addView(grip, FrameLayout.LayoutParams(a.dp(26), a.dp(26), Gravity.BOTTOM or Gravity.END))
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        titleBar.setOnTouchListener { _, e -> onTitleTouch(e) }
        setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_DOWN) wm.focus(this); false }
    }

    // ------------------------------------------------------------------ geser + snap
    private fun onTitleTouch(e: MotionEvent): Boolean {
        wm.focus(this)
        val p = parent as View
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dx = x - e.rawX; dy = y - e.rawY
                startX = e.rawX; startY = e.rawY; moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && abs(e.rawX - startX) < slop && abs(e.rawY - startY) < slop) return true
                moved = true
                if (saved != null) {            // tarik jendela yang ter-snap: kembali ke ukuran semula
                    restoreUnderFinger(wm.localX(e.rawX))
                    dx = x - e.rawX; dy = y - e.rawY
                }
                x = (e.rawX + dx).coerceIn(-width * 0.7f, p.width - width * 0.3f)
                y = (e.rawY + dy).coerceIn(0f, (p.height - a.dp(34)).toFloat())
                wm.showPreview(wm.zoneFor(e.rawX, e.rawY))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val z = if (moved && e.actionMasked == MotionEvent.ACTION_UP) wm.zoneFor(e.rawX, e.rawY) else Zone.NONE
                wm.hidePreview()
                if (z != Zone.NONE) snap(z)
                else if (!moved && e.actionMasked == MotionEvent.ACTION_UP) {
                    // ketuk dua kali title bar = maksimalkan / pulihkan
                    if (e.eventTime - lastTap < 320) { toggleMax(); lastTap = 0L } else lastTap = e.eventTime
                }
            }
        }
        return true
    }

    private fun resizeTouch() = View.OnTouchListener { _, e ->
        val p = parent as View
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                wm.focus(this)
                startX = e.rawX; startY = e.rawY
                dx = width.toFloat(); dy = height.toFloat()   // dipakai sebagai ukuran awal
                p.parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                saved = null
                val lp = layoutParams
                lp.width = (dx + (e.rawX - startX)).toInt().coerceIn(a.dp(280), (p.width - x).toInt().coerceAtLeast(a.dp(280)))
                lp.height = (dy + (e.rawY - startY)).toInt().coerceIn(a.dp(180), (p.height - y).toInt().coerceAtLeast(a.dp(180)))
                layoutParams = lp
            }
        }
        true
    }

    fun snap(zone: Int) {
        val p = parent as View
        val pw = p.width; val ph = p.height
        if (saved == null) saved = floatArrayOf(x, y, width.toFloat(), height.toFloat())
        when (zone) {
            Zone.LEFT -> animateTo(0f, 0f, pw / 2, ph)
            Zone.RIGHT -> animateTo((pw / 2).toFloat(), 0f, pw - pw / 2, ph)
            else -> animateTo(0f, 0f, pw, ph)
        }
    }

    fun toggleMax() {
        val s = saved
        if (s != null) { saved = null; animateTo(s[0], s[1], s[2].toInt(), s[3].toInt()) }
        else snap(Zone.MAX)
    }

    private fun restoreUnderFinger(localX: Float) {
        val s = saved ?: return
        saved = null
        val p = parent as View
        val lp = layoutParams
        lp.width = s[2].toInt(); lp.height = s[3].toInt()
        layoutParams = lp
        x = (localX - s[2] / 2f).coerceIn(0f, (p.width - s[2]).coerceAtLeast(0f))
    }

    private fun showSnapMenu() {
        val loc = IntArray(2); maxBtn.getLocationOnScreen(loc)
        showMenu(a, loc[0] + maxBtn.width, loc[1] + maxBtn.height, listOf(
            MenuItem("Kiri ½", G.BACK) { snap(Zone.LEFT) },
            MenuItem("Kanan ½", G.NEXT) { snap(Zone.RIGHT) },
            MenuItem("Layar penuh", G.MAX) { snap(Zone.MAX) },
            MenuItem(""),
            MenuItem("Pulihkan", G.RESTORE) { val s = saved; if (s != null) { saved = null; animateTo(s[0], s[1], s[2].toInt(), s[3].toInt()) } }
        ))
    }

    private fun capBtn(g: G, close: Boolean, long: (() -> Unit)?, click: () -> Unit): View {
        val gl = Glyph(a, g)
        val f = FrameLayout(a)
        f.addView(gl, FrameLayout.LayoutParams(a.dp(14), a.dp(14), Gravity.CENTER))
        f.background = hoverBg(a, 0, 0)
        f.setOnClickListener { click() }
        if (long != null) f.setOnLongClickListener { long(); true }
        if (close) f.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { v.setBackgroundColor(0xFFC42B1C.toInt()); gl.tint = Color.WHITE; gl.invalidate() }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.background = hoverBg(a, 0, 0); gl.tint = 0; gl.invalidate()
                }
            }
            false
        }
        f.layoutParams = LayoutParams(a.dp(46), LayoutParams.MATCH_PARENT)
        return f
    }

    fun animateTo(nx: Float, ny: Float, nw: Int, nh: Int) {
        val sx = x; val sy = y; val sw = width; val sh = height
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            interpolator = Anim.ease
            addUpdateListener {
                val t = it.animatedValue as Float
                x = sx + (nx - sx) * t; y = sy + (ny - sy) * t
                val lp = layoutParams
                lp.width = (sw + (nw - sw) * t).toInt(); lp.height = (sh + (nh - sh) * t).toInt()
                layoutParams = lp
            }
            start()
        }
    }

    fun setFocusedLook(f: Boolean) {
        isFocusedWin = f
        elevation = a.dpf(if (f) 24f else 8f)
        alpha = if (f) 1f else 0.96f
    }
}

/** Mengelola daftar jendela: buka, fokus, minimize, tutup, serta pratinjau snap. */
class Windows(private val a: MainActivity, private val layer: FrameLayout) {
    private val list = mutableListOf<WindowFrame>()
    private val preview = View(a)
    private var previewZone = Zone.NONE

    var current = 0
        private set
    var count = 1
        private set

    /** Jendela pada desktop virtual yang sedang aktif. */
    fun all(): List<WindowFrame> = list.filter { it.desk == current }
    /** Semua jendela di semua desktop virtual. */
    fun everything(): List<WindowFrame> = list.toList()
    fun find(title: String): WindowFrame? = list.firstOrNull { it.title == title }
    fun countOn(d: Int) = list.count { it.desk == d }

    fun switchDesktop(i: Int) {
        if (i !in 0 until count || i == current) return
        current = i
        for (w in list) {
            if (w.desk == i && !w.minimized) {
                w.visibility = View.VISIBLE
                w.alpha = 0f
                w.animate().alpha(1f).setDuration(180).start()
            } else w.visibility = View.GONE
        }
        var top: WindowFrame? = null
        for (k in layer.childCount - 1 downTo 0) {
            val c = layer.getChildAt(k)
            if (c is WindowFrame && c.desk == i && !c.minimized) { top = c; break }
        }
        if (top != null) focus(top) else { for (o in list) o.setFocusedLook(false); a.taskbar.refresh() }
    }

    fun addDesktop() {
        if (count >= 4) return
        count++
        switchDesktop(count - 1)
    }

    fun removeDesktop(i: Int) {
        if (count <= 1 || i !in 0 until count) return
        for (w in list) {
            if (w.desk == i) w.desk = maxOf(i - 1, 0)
            else if (w.desk > i) w.desk--
        }
        count--
        current = (if (current > i) current - 1 else if (current == i) maxOf(i - 1, 0) else current).coerceIn(0, count - 1)
        for (w in list) w.visibility = if (w.desk == current && !w.minimized) View.VISIBLE else View.GONE
        a.taskbar.refresh()
    }

    fun moveToDesktop(w: WindowFrame, d: Int) {
        if (d !in 0 until count) return
        w.desk = d
        if (d != current) w.visibility = View.GONE
        a.taskbar.refresh()
    }

    /** Alt+Tab: pindah fokus ke jendela berikutnya pada desktop ini. */
    fun cycle() {
        val l = all()
        if (l.isEmpty()) return
        val idx = l.indexOfFirst { it.isFocusedWin }
        restoreWin(l[(idx + 1) % l.size])
    }

    /** Jendela yang sedang fokus pada desktop ini (atau null). */
    fun focused(): WindowFrame? = all().firstOrNull { it.isFocusedWin && !it.minimized }

    // ---- snap helper ----
    fun localX(rawX: Float): Float { val l = IntArray(2); layer.getLocationOnScreen(l); return rawX - l[0] }

    fun zoneFor(rawX: Float, rawY: Float): Int {
        val l = IntArray(2); layer.getLocationOnScreen(l)
        val px = rawX - l[0]; val py = rawY - l[1]
        val edge = a.dp(16)
        return when {
            py < a.dp(8) -> Zone.MAX
            px < edge -> Zone.LEFT
            px > layer.width - edge -> Zone.RIGHT
            else -> Zone.NONE
        }
    }

    fun showPreview(zone: Int) {
        if (zone == previewZone) return
        previewZone = zone
        if (zone == Zone.NONE) { preview.animate().alpha(0f).setDuration(100).start(); return }
        if (preview.parent == null) {
            preview.background = rounded(a, Color.argb(48, Color.red(Theme.accent), Color.green(Theme.accent), Color.blue(Theme.accent)),
                8, Theme.accent, 2)
            layer.addView(preview, 0, FrameLayout.LayoutParams(0, 0))
        }
        val pw = layer.width; val ph = layer.height; val m = a.dp(4)
        val lp = preview.layoutParams as FrameLayout.LayoutParams
        when (zone) {
            Zone.LEFT -> { lp.width = pw / 2 - m; lp.height = ph - m * 2; lp.leftMargin = m }
            Zone.RIGHT -> { lp.width = pw / 2 - m; lp.height = ph - m * 2; lp.leftMargin = pw / 2 }
            else -> { lp.width = pw - m * 2; lp.height = ph - m * 2; lp.leftMargin = m }
        }
        lp.topMargin = m
        preview.layoutParams = lp
        preview.animate().alpha(1f).setDuration(120).start()
    }

    fun hidePreview() { showPreview(Zone.NONE) }

    // ---- daftar jendela ----
    fun open(w: WindowFrame) {
        list.firstOrNull { it.title == w.title }?.let {
            if (it.desk != current) switchDesktop(it.desk)
            restoreWin(it); return
        }
        w.wm = this
        w.desk = current
        val lp = FrameLayout.LayoutParams(a.dp(w.wDp), a.dp(w.hDp))
        layer.addView(w, lp)
        w.x = (layer.width - a.dp(w.wDp)) / 2f + list.size * a.dp(28)
        w.y = a.dp(34).toFloat().coerceAtMost((layer.height - a.dp(w.hDp)).toFloat().coerceAtLeast(0f)) + list.size * a.dp(20)
        list.add(w)
        w.alpha = 0f; w.scaleX = 0.92f; w.scaleY = 0.92f
        w.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(Anim.ease).start()
        focus(w)
    }

    fun restoreWin(w: WindowFrame) {
        if (w.minimized) {
            w.minimized = false
            w.visibility = View.VISIBLE
            w.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(Anim.ease).start()
        }
        focus(w)
    }

    fun focus(w: WindowFrame) {
        w.bringToFront()
        for (o in list) o.setFocusedLook(o === w)
        a.taskbar.refresh()
    }

    fun minimize(w: WindowFrame) {
        w.minimized = true
        w.setFocusedLook(false)
        w.animate().alpha(0f).translationY(a.dp(60).toFloat()).scaleX(0.9f).scaleY(0.9f).setDuration(160)
            .withEndAction { if (w.minimized) w.visibility = View.GONE }.start()
        a.taskbar.refresh()
    }

    fun toggle(w: WindowFrame) {
        if (w.minimized) restoreWin(w)
        else if (w.isFocusedWin) minimize(w)
        else focus(w)
    }

    fun close(w: WindowFrame) {
        w.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(130).withEndAction {
            layer.removeView(w); list.remove(w); a.taskbar.refresh()
        }.start()
    }

    /** Tutup tanpa animasi; mengembalikan posisi terakhir (untuk dibuka ulang di tempat sama). */
    fun closeNow(title: String): Pair<Float, Float>? {
        val w = list.firstOrNull { it.title == title } ?: return null
        layer.removeView(w); list.remove(w)
        return Pair(w.x, w.y)
    }

    fun minimizeAll() { for (w in all()) if (!w.minimized) minimize(w) }
}
