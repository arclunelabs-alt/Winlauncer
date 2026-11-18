package com.arclune.winlauncher

import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Grafik garis kecil riwayat pemakaian (0..1). */
private class Spark(ctx: Context) : View(ctx) {
    val values = FloatArray(40)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun push(v: Float) {
        System.arraycopy(values, 1, values, 0, values.size - 1)
        values[values.size - 1] = v.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.style = Paint.Style.FILL; p.color = Theme.card; c.drawRect(0f, 0f, w, h, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = Theme.stroke
        for (i in 1..3) c.drawLine(0f, h * i / 4f, w, h * i / 4f, p)
        val step = w / (values.size - 1)
        path.reset()
        path.moveTo(0f, h - values[0] * h)
        for (i in 1 until values.size) path.lineTo(i * step, h - values[i] * h)
        p.style = Paint.Style.STROKE; p.strokeWidth = context.dpf(1.8f); p.color = Theme.accent
        c.drawPath(path, p)
        path.lineTo(w, h); path.lineTo(0f, h); path.close()
        p.style = Paint.Style.FILL; p.color = (Theme.accent and 0x00FFFFFF) or 0x33000000
        c.drawPath(path, p)
    }
}

/** Task Manager: grafik RAM live, penyimpanan, baterai, dan daftar proses yang bisa diakhiri. */
class TaskManagerApp(private val a: MainActivity) : LinearLayout(a) {
    private val spark = Spark(a)
    private val ramTv = label(a, "", 12f)
    private val ramPct = label(a, "", 22f, Theme.text, true)
    private val extraTv = label(a, "", 11f, Theme.subText)
    private val procBox = LinearLayout(a)
    private val handler = Handler(Looper.getMainLooper())
    private var ticks = 0
    private var lastSig = ""

    private val tick = object : Runnable {
        override fun run() {
            update()
            ticks++
            handler.postDelayed(this, 1000)
        }
    }

    init {
        setBackgroundColor(if (Theme.dark) 0xFF272727.toInt() else 0xFFF9F9F9.toInt())
        setPadding(a.dp(12), a.dp(12), a.dp(12), a.dp(12))

        // kiri: performa
        val left = vBox(a)
        left.addView(label(a, "Performa", 13f, bold = true).apply { setPadding(0, 0, 0, a.dp(8)) })
        left.addView(label(a, "Memori (RAM)", 11f, Theme.subText))
        left.addView(ramPct)
        left.addView(spark, lp(MATCH, a.dp(70)).apply { topMargin = a.dp(4); bottomMargin = a.dp(6) })
        left.addView(ramTv)
        left.addView(space(a, 10))
        left.addView(extraTv)
        addView(left, lp(a.dp(190), MATCH).apply { rightMargin = a.dp(12) })

        // kanan: proses
        val right = vBox(a)
        right.addView(label(a, "Proses", 13f, bold = true).apply { setPadding(0, 0, 0, a.dp(8)) })
        right.addView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(procBox) }, lp(MATCH, 0, 1f))
        procBox.orientation = VERTICAL
        addView(right, lp(0, MATCH, 1f))
        update()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); handler.post(tick) }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); handler.removeCallbacks(tick) }

    private fun update() {
        val mi = ActivityManager.MemoryInfo()
        (a.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val used = (mi.totalMem - mi.availMem).toDouble()
        val frac = (used / mi.totalMem.toDouble()).toFloat()
        spark.push(frac)
        ramPct.text = "${(frac * 100).toInt()}%"
        ramTv.text = "${Apps.gb(used)} / ${Apps.gb(mi.totalMem.toDouble())} GB terpakai"

        val st = StatFs(Environment.getDataDirectory().path)
        val sUsed = 1f - st.availableBytes.toFloat() / st.totalBytes.toFloat()
        extraTv.text = "Penyimpanan: ${(sUsed * 100).toInt()}% terpakai\nBaterai: ${(a.batteryLevel * 100).toInt()}%" +
            if (a.charging) " (mengisi)" else ""

        // daftar proses: segarkan tiap 2 detik hanya bila berubah
        if (ticks % 2 == 0) rebuildProcesses()
    }

    private fun rebuildProcesses() {
        val wins = a.windows.everything()
        val apps = AppRepo.running()
        val sig = wins.joinToString("|") { it.title } + "#" + apps.joinToString("|") { it.pkg }
        if (sig == lastSig) return
        lastSig = sig
        procBox.removeAllViews()

        procBox.addView(section("Aplikasi"))
        procBox.addView(row(null, G.START, "Win11 Launcher", "Sistem · tidak bisa diakhiri", null))
        for (w in wins) {
            procBox.addView(row(null, w.glyph, w.title, if (w.minimized) "Diminimalkan" else "Terbuka") {
                a.windows.close(w); lastSig = ""
            })
        }
        for (app in apps) {
            procBox.addView(row(app, null, app.label, "Aplikasi Android") {
                try {
                    (a.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(app.pkg)
                } catch (e: Exception) { }
                AppRepo.markRunning(app.pkg, false)
                a.taskbar.refresh()
                Toast.makeText(a, "Meminta ${app.label} berhenti", Toast.LENGTH_SHORT).show()
                lastSig = ""
            })
        }
        if (wins.isEmpty() && apps.isEmpty()) {
            procBox.addView(label(a, "Tidak ada aplikasi lain yang dibuka lewat launcher.", 11f, Theme.subText)
                .apply { setPadding(a.dp(4), a.dp(8), 0, 0) })
        }
    }

    private fun section(t: String): View = label(a, t, 11f, Theme.subText).apply { setPadding(a.dp(4), 0, 0, a.dp(4)) }

    private fun row(app: AppInfo?, g: G?, name: String, sub: String, end: (() -> Unit)?): View {
        val r = hBox(a).apply {
            setPadding(a.dp(10), a.dp(8), a.dp(8), a.dp(8))
            background = rounded(a, Theme.card, 5, Theme.stroke)
        }
        if (app != null) r.addView(ImageView(a).apply { setImageDrawable(app.icon) }, lp(a.dp(26), a.dp(26)))
        else if (g != null) r.addView(Glyph(a, g), lp(a.dp(22), a.dp(22)))
        val c = vBox(a)
        c.addView(label(a, name, 12.5f))
        c.addView(label(a, sub, 10f, Theme.subText))
        r.addView(c, lp(0, WRAP, 1f).apply { leftMargin = a.dp(12) })
        if (end != null) {
            r.addView(label(a, "Akhiri tugas", 11f, Theme.accent).apply {
                gravity = Gravity.CENTER; setPadding(a.dp(10), a.dp(6), a.dp(10), a.dp(6)); pressable(5)
                setOnClickListener { end() }
            })
        }
        r.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = a.dp(4) }
        return r
    }
}
