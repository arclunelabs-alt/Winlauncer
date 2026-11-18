package com.arclune.winlauncher

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Taskbar Windows 11: ikon di tengah, widgets di kiri, system tray + jam di kanan. */
class Taskbar(private val a: MainActivity) : FrameLayout(a) {
    private val left = LinearLayout(a)
    private val center = LinearLayout(a)
    private val tray = LinearLayout(a)
    private val time = label(a, "", 12f)
    private val date = label(a, "", 12f)
    private val batGlyph = Glyph(a, G.BATTERY)
    private val widgetsTitle = label(a, "", 12f, bold = true)
    private val widgetsSub = label(a, "", 11f, Theme.subText)
    private val tick = Handler(Looper.getMainLooper())
    private var wx: Weather? = null
    private var ticks = 0
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            ticks++
            if (ticks % 180 == 0) loadWeather(true)   // tiap 30 menit
            tick.postDelayed(this, 10_000)
        }
    }

    init {
        setPadding(a.dp(8), 0, 0, 0)
        addView(left, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.START))
        addView(center, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL))
        addView(tray, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.END))
        left.gravity = Gravity.CENTER_VERTICAL
        center.gravity = Gravity.CENTER_VERTICAL
        tray.gravity = Gravity.CENTER_VERTICAL
        isLongClickable = true
        setOnLongClickListener { a.taskbarMenu(); true }
        refresh()
        loadWeather(false)
    }

    private fun loadWeather(force: Boolean) {
        Info.weather(a, force) { w -> if (w != null) { wx = w; updateStatus() } }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); tick.post(clockRunnable) }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); tick.removeCallbacks(clockRunnable) }

    private fun btn(sizeDp: Int = 40, content: View, onClick: () -> Unit): FrameLayout =
        FrameLayout(a).apply {
            addView(content)
            pressable(5, scale = true)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(a.dp(sizeDp), a.dp(40)).apply { setMargins(a.dp(4), 0, a.dp(4), 0) }
        }

    private fun glyphBtn(g: G, tip: String = "", onClick: () -> Unit): FrameLayout {
        val gl = Glyph(a, g)
        return btn(40, gl, onClick).also {
            gl.layoutParams = FrameLayout.LayoutParams(a.dp(24), a.dp(24), Gravity.CENTER)
            if (tip.isNotEmpty()) it.tooltipText = tip
        }
    }

    private fun indicator(f: FrameLayout, active: Boolean, focused: Boolean) {
        if (!active) return
        val v = View(a)
        v.background = rounded(a, if (focused) Theme.accent else Theme.subText, 2)
        f.addView(v, FrameLayout.LayoutParams(a.dp(if (focused) 16 else 6), a.dp(3), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            .apply { bottomMargin = a.dp(1) })
    }

    private fun appBtn(app: AppInfo, running: Boolean): FrameLayout {
        val iv = ImageView(a).apply { setImageDrawable(app.icon) }
        val f = btn(40, iv) { a.launchApp(app) }
        f.tooltipText = app.label
        iv.layoutParams = FrameLayout.LayoutParams(a.dp(26), a.dp(26), Gravity.CENTER)
        f.setOnLongClickListener { a.showAppMenu(f, app, true); true }
        indicator(f, running, false)
        return f
    }

    fun refresh() {
        // warna label mengikuti tema
        time.setTextColor(Theme.text); date.setTextColor(Theme.text)
        widgetsTitle.setTextColor(Theme.text); widgetsSub.setTextColor(Theme.subText)
        background = acrylicBg(this, 0, true)

        left.removeAllViews(); center.removeAllViews(); tray.removeAllViews()
        for (v in listOf<View>(widgetsTitle, widgetsSub, time, date, batGlyph))
            (v.parent as? android.view.ViewGroup)?.removeView(v)

        // ---- Widgets (kiri bila rata tengah) ----
        val widgetsBtn = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(a.dp(8), 0, a.dp(10), 0)
            pressable(5)
            setOnClickListener { a.open(Panel.WIDGETS) }
            addView(Glyph(a, G.WIDGETS).apply { layoutParams = LinearLayout.LayoutParams(a.dp(24), a.dp(24)) })
            val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(8), 0, 0, 0) }
            col.addView(widgetsTitle); col.addView(widgetsSub)
            addView(col)
        }
        if (Theme.centered) {
            left.addView(widgetsBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, a.dp(40)))
        }

        // ---- Grup tengah ----
        if (!Theme.centered) {
            (center.layoutParams as LayoutParams).gravity = Gravity.START
        } else (center.layoutParams as LayoutParams).gravity = Gravity.CENTER_HORIZONTAL

        center.addView(glyphBtn(G.START, "Mulai") { a.open(Panel.START) })
        center.addView(glyphBtn(G.SEARCH, "Cari") { a.open(Panel.SEARCH) })
        center.addView(glyphBtn(G.TASKVIEW, "Tampilan Tugas") { a.toggleTaskView() })
        if (!Theme.centered) center.addView(glyphBtn(G.WIDGETS, "Widget") { a.open(Panel.WIDGETS) })

        val pins = AppRepo.taskbarPins()
        val running = AppRepo.running()
        for (p in pins) center.addView(appBtn(p, running.any { it.pkg == p.pkg }))
        for (r in running) if (pins.none { it.pkg == r.pkg }) center.addView(appBtn(r, true))

        // jendela internal (Settings, This PC, dst.)
        for (w in a.windows.all()) {
            val gl = Glyph(a, w.glyph)
            val f = btn(40, gl) { a.windows.toggle(w) }
            f.tooltipText = w.title
            gl.layoutParams = FrameLayout.LayoutParams(a.dp(24), a.dp(24), Gravity.CENTER)
            indicator(f, true, w.isFocusedWin && !w.minimized)
            center.addView(f)
        }

        // ---- Tray ----
        val chev = Glyph(a, G.CHEVRON)
        tray.addView(btn(28, chev) { a.showTrayMenu() }.also {
            it.tooltipText = "Ikon tersembunyi"
            chev.layoutParams = FrameLayout.LayoutParams(a.dp(16), a.dp(16), Gravity.CENTER)
        })

        val cluster = LinearLayout(a).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(a.dp(14), 0, a.dp(14), 0)
            pressable(5)
            setOnClickListener { a.open(Panel.QS) }
            for (gl in listOf(Glyph(a, G.WIFI), Glyph(a, G.VOLUME), batGlyph)) {
                (gl.parent as? android.view.ViewGroup)?.removeView(gl)
                addView(gl, LinearLayout.LayoutParams(a.dp(18), a.dp(18)).apply { setMargins(a.dp(6), 0, a.dp(6), 0) })
            }
        }
        tray.addView(cluster, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, a.dp(40)))

        val clock = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(a.dp(14), 0, a.dp(14), 0)
            pressable(5)
            setOnClickListener { a.open(Panel.NOTIF) }
            (time.parent as? android.view.ViewGroup)?.removeView(time)
            (date.parent as? android.view.ViewGroup)?.removeView(date)
            time.gravity = Gravity.END; date.gravity = Gravity.END
            addView(time); addView(date)
        }
        tray.addView(clock, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, a.dp(40)))

        // sliver "Show desktop" di ujung kanan
        val sliver = View(a).apply {
            pressable(0)
            setOnClickListener { a.showDesktop() }
        }
        tray.addView(sliver, LinearLayout.LayoutParams(a.dp(8), LinearLayout.LayoutParams.MATCH_PARENT))

        updateClock()
        updateStatus()
    }

    fun updateClock() {
        val now = Date()
        time.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        date.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now)
    }

    fun updateStatus() {
        batGlyph.level = a.batteryLevel
        batGlyph.invalidate()
        val w = wx
        if (w != null) {
            widgetsTitle.text = Info.codeEmoji(w.code) + " " + w.temp + "\u00B0C"
            widgetsSub.text = w.city + " \u00B7 " + Info.codeText(w.code)
        } else {
            val pct = (a.batteryLevel * 100).toInt()
            widgetsTitle.text = "Baterai $pct%"
            widgetsSub.text = if (a.charging) "Sedang mengisi" else "Menggunakan baterai"
        }
    }
}
