package com.arclune.winlauncher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.min

/** Mini-app bawaan: Kalkulator, Notepad, Jam. */
object MiniApps {

    private fun bg(): Int = if (Theme.dark) 0xFF272727.toInt() else 0xFFF9F9F9.toInt()

    // ------------------------------------------------------------------ Kalkulator
    fun calculator(a: MainActivity): View {
        var cur = "0"
        var acc: Double? = null
        var op: String? = null
        var fresh = false
        var expr = ""

        val root = LinearLayout(a).apply { setBackgroundColor(bg()); setPadding(a.dp(8), a.dp(8), a.dp(8), a.dp(8)) }
        val exprTv = label(a, "", 12f, Theme.subText).apply { gravity = Gravity.END or Gravity.BOTTOM }
        val resTv = label(a, "0", 30f, Theme.text, true).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; maxLines = 1 }

        fun fmt(v: Double): String {
            if (v.isNaN() || v.isInfinite()) return "Error"
            val s = String.format(Locale.US, "%.10f", v).trimEnd('0').trimEnd('.')
            return if (s.isEmpty() || s == "-") "0" else s
        }
        fun show() {
            resTv.text = cur; exprTv.text = expr
            resTv.textSize = if (cur.length > 11) 18f else if (cur.length > 8) 24f else 30f
        }
        fun value(): Double = cur.toDoubleOrNull() ?: 0.0
        fun calc(x: Double, y: Double, o: String): Double = when (o) {
            "+" -> x + y
            "−" -> x - y
            "×" -> x * y
            else -> if (y == 0.0) Double.NaN else x / y
        }
        fun digit(d: String) {
            if (fresh || cur == "Error") { cur = "0"; fresh = false }
            cur = when {
                d == "." -> if (cur.contains(".")) cur else "$cur."
                cur == "0" -> d
                cur.length >= 14 -> cur
                else -> cur + d
            }
            show()
        }
        fun applyOp(o: String) {
            val pa = acc; val po = op
            if (pa != null && po != null && !fresh) { val r = calc(pa, value(), po); cur = fmt(r); acc = if (r.isNaN()) null else r }
            else acc = value()
            op = o; fresh = true
            expr = (acc?.let { fmt(it) } ?: "") + " " + o
            show()
        }
        fun evaluate() {
            val pa = acc; val po = op
            if (pa != null && po != null) {
                val b = value(); val r = calc(pa, b, po)
                expr = fmt(pa) + " " + po + " " + fmt(b) + " ="
                cur = fmt(r); acc = null; op = null; fresh = true
            }
            show()
        }
        fun key(k: String) {
            when (k) {
                "C" -> { cur = "0"; acc = null; op = null; fresh = false; expr = ""; show() }
                "⌫" -> { if (!fresh && cur != "Error") { cur = cur.dropLast(1); if (cur.isEmpty() || cur == "-") cur = "0" }; show() }
                "%" -> { cur = fmt(value() / 100.0); fresh = true; show() }
                "±" -> { if (cur != "0" && cur != "Error") cur = if (cur.startsWith("-")) cur.substring(1) else "-$cur"; show() }
                "+", "−", "×", "÷" -> applyOp(k)
                "=" -> evaluate()
                else -> digit(k)
            }
        }

        val left = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(8), 0, a.dp(12), 0) }
        left.addView(exprTv, lp(MATCH, 0, 1f))
        left.addView(resTv, lp(MATCH, a.dp(56)))
        root.addView(left, lp(0, MATCH, 1f))

        val grid = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val rows = listOf(
            listOf("C", "⌫", "%", "÷"), listOf("7", "8", "9", "×"), listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"), listOf("±", "0", ".", "=")
        )
        rows.forEach { r ->
            val row = LinearLayout(a)
            r.forEach { k ->
                val isOp = k in listOf("÷", "×", "−", "+", "C", "⌫", "%")
                val b = TextView(a).apply {
                    text = k; gravity = Gravity.CENTER; textSize = 16f
                    setTextColor(if (k == "=") Color.WHITE else Theme.text)
                    pressable(6, if (k == "=") Theme.accent else if (isOp) Theme.hover else Theme.card, scale = true)
                    setOnClickListener { key(k) }
                }
                row.addView(b, lp(0, MATCH, 1f).apply { setMargins(a.dp(2), a.dp(2), a.dp(2), a.dp(2)) })
            }
            grid.addView(row, lp(MATCH, 0, 1f))
        }
        root.addView(grid, lp(0, MATCH, 1.25f))
        return root
    }

    // ------------------------------------------------------------------ Notepad
    fun notepad(a: MainActivity): View {
        val prefs = a.getSharedPreferences("win11", Context.MODE_PRIVATE)
        val root = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg()) }
        val status = label(a, "", 11f, Theme.subText).apply { setPadding(a.dp(12), a.dp(4), a.dp(12), a.dp(4)) }
        val et = EditText(a).apply {
            hint = "Mulai mengetik..."; setHintTextColor(Theme.subText); setTextColor(Theme.text); textSize = 14f
            background = null; gravity = Gravity.TOP or Gravity.START
            setPadding(a.dp(14), a.dp(8), a.dp(14), a.dp(8))
            setText(prefs.getString("notepad", ""))
        }
        fun count() {
            val t = et.text.toString()
            val words = t.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
            status.text = "${t.length} karakter · $words kata · tersimpan otomatis"
        }
        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { prefs.edit().putString("notepad", s?.toString() ?: "").apply(); count() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        fun action(name: String, run: () -> Unit) = label(a, name, 12f).apply {
            gravity = Gravity.CENTER; setPadding(a.dp(12), 0, a.dp(12), 0); pressable(5); setOnClickListener { run() }
        }
        val bar = LinearLayout(a).apply {
            setPadding(a.dp(6), a.dp(2), a.dp(6), a.dp(2))
            setBackgroundColor(if (Theme.dark) 0x14FFFFFF else 0x0F000000)
        }
        bar.addView(action("Baru") { et.setText("") }, lp(WRAP, a.dp(32)))
        bar.addView(action("Salin") {
            (a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("catatan", et.text.toString()))
            Toast.makeText(a, "Tersalin", Toast.LENGTH_SHORT).show()
        }, lp(WRAP, a.dp(32)))
        bar.addView(action("Bagikan") {
            a.startSafe(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, et.text.toString()), null))
        }, lp(WRAP, a.dp(32)))
        root.addView(bar, lp(MATCH, WRAP))
        root.addView(et, lp(MATCH, 0, 1f))
        root.addView(status, lp(MATCH, WRAP))
        count()
        return root
    }

    // ------------------------------------------------------------------ Jam + stopwatch
    fun clock(a: MainActivity): View = ClockApp(a)
}

private class AnalogView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = min(width, height) / 2f - context.dpf(8f)
        p.style = Paint.Style.FILL; p.color = Theme.card; c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = context.dpf(1.5f); p.color = Theme.stroke; c.drawCircle(cx, cy, r, p)
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 12) {
            c.save(); c.rotate(i * 30f, cx, cy)
            p.color = Theme.subText; p.strokeWidth = context.dpf(if (i % 3 == 0) 2.2f else 1.2f)
            c.drawLine(cx, cy - r + context.dpf(4f), cx, cy - r + context.dpf(if (i % 3 == 0) 12f else 8f), p)
            c.restore()
        }
        val now = Calendar.getInstance()
        val sec = now.get(Calendar.SECOND) + now.get(Calendar.MILLISECOND) / 1000f
        val mins = now.get(Calendar.MINUTE) + sec / 60f
        val hr = (now.get(Calendar.HOUR) % 12) + mins / 60f
        hand(c, cx, cy, hr / 12f * 360f, r * 0.5f, 4f, Theme.text)
        hand(c, cx, cy, mins / 60f * 360f, r * 0.75f, 3f, Theme.text)
        hand(c, cx, cy, sec / 60f * 360f, r * 0.85f, 1.6f, Theme.accent)
        p.style = Paint.Style.FILL; p.color = Theme.accent; c.drawCircle(cx, cy, context.dpf(4f), p)
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, wDp: Float, col: Int) {
        c.save(); c.rotate(deg, cx, cy)
        p.style = Paint.Style.STROKE; p.strokeWidth = context.dpf(wDp); p.color = col
        c.drawLine(cx, cy + len * 0.12f, cx, cy - len, p)
        c.restore()
    }
}

private class ClockApp(private val a: MainActivity) : LinearLayout(a) {
    private val analog = AnalogView(a)
    private val timeTv = label(a, "", 30f, Theme.text, true)
    private val dateTv = label(a, "", 12f, Theme.subText)
    private val swTv = label(a, "00:00.00", 24f, Theme.text, true)
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var startAt = 0L
    private var accumulated = 0L
    private lateinit var toggleBtn: TextView

    private val tick = object : Runnable {
        override fun run() {
            analog.invalidate()
            val now = Date()
            timeTv.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)
            dateTv.text = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(now)
            swTv.text = fmt(elapsed())
            handler.postDelayed(this, if (running) 40L else 250L)
        }
    }

    init {
        setBackgroundColor(if (Theme.dark) 0xFF272727.toInt() else 0xFFF9F9F9.toInt())
        setPadding(a.dp(12), a.dp(12), a.dp(12), a.dp(12))
        addView(analog, lp(0, MATCH, 1f))
        val col = LinearLayout(a).apply { orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(a.dp(14), 0, 0, 0) }
        col.addView(timeTv); col.addView(dateTv)
        col.addView(View(a).apply { setBackgroundColor(Theme.stroke) }, lp(MATCH, a.dp(1)).apply { setMargins(0, a.dp(12), 0, a.dp(10)) })
        col.addView(label(a, "Stopwatch", 11f, Theme.subText))
        col.addView(swTv, lp(WRAP, a.dp(40)))
        val btns = LinearLayout(a)
        toggleBtn = button("Mulai", true) {
            if (running) { accumulated += SystemClock.elapsedRealtime() - startAt; running = false; toggleBtn.text = "Lanjut" }
            else { startAt = SystemClock.elapsedRealtime(); running = true; toggleBtn.text = "Jeda" }
        }
        btns.addView(toggleBtn, lp(0, a.dp(34), 1f).apply { rightMargin = a.dp(4) })
        btns.addView(button("Reset", false) { running = false; accumulated = 0L; toggleBtn.text = "Mulai"; swTv.text = fmt(0L) },
            lp(0, a.dp(34), 1f).apply { leftMargin = a.dp(4) })
        col.addView(btns, lp(MATCH, WRAP))
        addView(col, lp(0, MATCH, 1f))
    }

    private fun button(t: String, primary: Boolean, click: () -> Unit) = TextView(a).apply {
        text = t; gravity = Gravity.CENTER; textSize = 13f
        setTextColor(if (primary) Color.WHITE else Theme.text)
        pressable(5, if (primary) Theme.accent else Theme.card, scale = true)
        setOnClickListener { click() }
    }

    private fun elapsed(): Long = accumulated + if (running) SystemClock.elapsedRealtime() - startAt else 0L

    private fun fmt(ms: Long): String {
        val m = ms / 60000; val s = (ms / 1000) % 60; val cs = (ms / 10) % 100
        return String.format(Locale.US, "%02d:%02d.%02d", m, s, cs)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); handler.post(tick) }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); handler.removeCallbacks(tick) }
}
