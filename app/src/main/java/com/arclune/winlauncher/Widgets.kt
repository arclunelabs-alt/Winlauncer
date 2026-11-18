package com.arclune.winlauncher

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Komponen UI Fluent yang dipakai ulang: switch, kartu baris, segmented, slider. */

fun fluentSwitch(c: Context, on: Boolean, onChange: (Boolean) -> Unit): View {
    val w = c.dp(40); val h = c.dp(20); val k = c.dp(12)
    val root = FrameLayout(c)
    val knob = View(c)
    var state = on
    fun render(anim: Boolean) {
        root.background = if (state) rounded(c, Theme.accent, 10)
        else rounded(c, 0, 10, if (Theme.dark) 0xB3FFFFFF.toInt() else 0x99000000.toInt(), 1)
        knob.background = rounded(c, if (state) Color.WHITE else (if (Theme.dark) 0xCCFFFFFF.toInt() else 0xCC000000.toInt()), 8)
        val tx = if (state) (w - k - c.dp(4)).toFloat() else 0f
        if (anim) knob.animate().translationX(tx).setDuration(120).start() else knob.translationX = tx
    }
    root.addView(knob, FrameLayout.LayoutParams(k, k, Gravity.CENTER_VERTICAL).apply { leftMargin = c.dp(4) })
    render(false)
    root.setOnClickListener { state = !state; render(true); onChange(state) }
    root.layoutParams = LinearLayout.LayoutParams(w, h)
    return root
}

/** Baris kartu: judul + subjudul di kiri, kontrol di kanan. */
fun settingRow(c: Context, glyph: G?, title: String, sub: String?, control: View?, onClick: (() -> Unit)? = null): View {
    val row = LinearLayout(c).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(c.dp(14), c.dp(10), c.dp(14), c.dp(10))
        background = rounded(c, Theme.card, 5, Theme.stroke)
        if (onClick != null) { setOnClickListener { onClick() } }
    }
    if (glyph != null) row.addView(Glyph(c, glyph).apply { layoutParams = LinearLayout.LayoutParams(c.dp(20), c.dp(20)).apply { rightMargin = c.dp(14) } })
    val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
    col.addView(label(c, title, 13f))
    if (sub != null) col.addView(label(c, sub, 11f, Theme.subText).apply { setPadding(0, c.dp(2), 0, 0) })
    row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    if (control != null) row.addView(control)
    row.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        .apply { bottomMargin = c.dp(4) }
    return row
}

/** Pilihan bersegmen, mis. Gelap | Terang. */
fun segmented(c: Context, items: List<String>, selected: Int, onPick: (Int) -> Unit): View {
    val box = LinearLayout(c).apply { background = rounded(c, Theme.hover, 6) ; setPadding(c.dp(2), c.dp(2), c.dp(2), c.dp(2)) }
    items.forEachIndexed { i, s ->
        val t = label(c, s, 12f, if (i == selected) Color.WHITE else Theme.text).apply {
            gravity = Gravity.CENTER
            setPadding(c.dp(14), c.dp(6), c.dp(14), c.dp(6))
            background = if (i == selected) rounded(c, Theme.accent, 5) else null
            setOnClickListener { onPick(i) }
        }
        box.addView(t)
    }
    return box
}

/** Slider horizontal sederhana bergaya Windows 11 (0f..1f). */
class FluentSlider(c: Context, var value: Float, val onChange: (Float) -> Unit) : View(c) {
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: android.graphics.Canvas) {
        val h = height / 2f; val pad = context.dpf(10f)
        val x = pad + (width - 2 * pad) * value
        p.style = android.graphics.Paint.Style.FILL
        p.color = if (Theme.dark) 0x66FFFFFF else 0x66000000
        p.strokeCap = android.graphics.Paint.Cap.ROUND; p.strokeWidth = context.dpf(4f)
        cv.drawLine(pad, h, width - pad, h, p)
        p.color = Theme.accent
        cv.drawLine(pad, h, x, h, p)
        p.color = if (Theme.dark) 0xFF454545.toInt() else Color.WHITE
        cv.drawCircle(x, h, context.dpf(10f), p)
        p.color = Theme.accent
        cv.drawCircle(x, h, context.dpf(6f), p)
    }
    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        val pad = context.dpf(10f)
        value = ((e.x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
        invalidate(); onChange(value)
        return true
    }
}

fun vScroll(c: Context, inner: View): ScrollView = ScrollView(c).apply {
    isVerticalScrollBarEnabled = false
    overScrollMode = View.OVER_SCROLL_NEVER
    addView(inner)
}

fun vBox(c: Context, pad: Int = 0): LinearLayout = LinearLayout(c).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(c.dp(pad), c.dp(pad), c.dp(pad), c.dp(pad))
}

fun hBox(c: Context): LinearLayout = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }

fun space(c: Context, hDp: Int) = View(c).apply { layoutParams = LinearLayout.LayoutParams(1, c.dp(hDp)) }

fun lp(w: Int, h: Int, weight: Float = 0f): LinearLayout.LayoutParams = LinearLayout.LayoutParams(w, h, weight)

val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
