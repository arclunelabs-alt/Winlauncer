package com.arclune.winlauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** PIN disimpan sebagai hash SHA-256 (tidak pernah dalam bentuk asli). */
object Pin {
    fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { String.format("%02x", it) }

    fun set(c: Context, pin: String) {
        Theme.pin = if (pin.isEmpty()) "" else hash(pin)
        Theme.save(c)
    }

    fun check(pin: String): Boolean = Theme.pin.isNotEmpty() && hash(pin) == Theme.pin
}

/** Layar kunci: jam besar di atas wallpaper kabur, geser/ketuk untuk membuka, PIN opsional. */
class LockScreen(private val a: MainActivity, private val onUnlocked: () -> Unit) : FrameLayout(a) {
    private val time = label(a, "", 64f, Color.WHITE)
    private val date = label(a, "", 16f, Color.WHITE)
    private val hint = label(a, "Ketuk atau geser ke atas untuk membuka", 12f, 0xCCFFFFFF.toInt())
    private val pinBox = LinearLayout(a)
    private val dots = label(a, "", 22f, Color.WHITE)
    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(a).scaledTouchSlop
    private var entered = ""
    private var pinMode = false
    private var unlocking = false
    private var downY = 0f
    private var dragged = 0f

    private val tick = object : Runnable {
        override fun run() { update(); handler.postDelayed(this, 5000) }
    }

    init {
        background = acrylicBg(this, 0)
        isClickable = true
        elevation = a.dpf(40f)

        time.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        time.gravity = Gravity.CENTER
        date.gravity = Gravity.CENTER
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        col.addView(time, LinearLayout.LayoutParams(MATCH, WRAP))
        col.addView(date, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = a.dp(6) })
        addView(col, LayoutParams(MATCH, WRAP, Gravity.TOP).apply { topMargin = a.dp(60) })

        hint.gravity = Gravity.CENTER
        addView(hint, LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { bottomMargin = a.dp(32) })

        pinBox.orientation = LinearLayout.VERTICAL
        pinBox.gravity = Gravity.CENTER_HORIZONTAL
        pinBox.visibility = View.GONE
        addView(pinBox, LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { bottomMargin = a.dp(12) })
        update()
    }

    private fun update() {
        val now = Date()
        time.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        date.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); handler.post(tick) }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); handler.removeCallbacks(tick) }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (pinMode || unlocking) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY = e.rawY; dragged = 0f }
            MotionEvent.ACTION_MOVE -> {
                dragged = (e.rawY - downY).coerceAtMost(0f)
                translationY = dragged
            }
            MotionEvent.ACTION_UP -> {
                if (dragged < -a.dp(80).toFloat() || abs(e.rawY - downY) < slop) requestUnlock()
                else animate().translationY(0f).setDuration(180).setInterpolator(Anim.ease).start()
            }
            MotionEvent.ACTION_CANCEL ->
                animate().translationY(0f).setDuration(180).setInterpolator(Anim.ease).start()
        }
        return true
    }

    private fun requestUnlock() {
        if (Theme.pin.isEmpty()) unlock()
        else { translationY = 0f; showPin() }
    }

    // ------------------------------------------------------------------ PIN
    private fun showPin() {
        pinMode = true
        hint.visibility = View.GONE
        pinBox.visibility = View.VISIBLE
        pinBox.removeAllViews()
        entered = ""
        pinBox.addView(label(a, "Masukkan PIN", 13f, Color.WHITE).apply { gravity = Gravity.CENTER })
        dots.gravity = Gravity.CENTER
        (dots.parent as? ViewGroup)?.removeView(dots)
        pinBox.addView(dots, LinearLayout.LayoutParams(MATCH, a.dp(28)).apply { topMargin = a.dp(4) })
        drawDots()

        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
        for (rowKeys in keys.chunked(3)) {
            val row = LinearLayout(a).apply { gravity = Gravity.CENTER }
            for (k in rowKeys) {
                row.addView(label(a, k, 18f, Color.WHITE).apply {
                    gravity = Gravity.CENTER
                    pressable(24, 0x22FFFFFF)
                    setOnClickListener { press(k) }
                }, LinearLayout.LayoutParams(a.dp(76), a.dp(40)).apply { setMargins(a.dp(4), a.dp(2), a.dp(4), a.dp(2)) })
            }
            pinBox.addView(row)
        }
        pinBox.addView(label(a, "Lupa PIN?", 12f, 0xFF8AB4F8.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(a.dp(16), a.dp(6), a.dp(16), a.dp(6)); pressable(6)
            setOnClickListener { a.forgotPin() }
        }, LinearLayout.LayoutParams(WRAP, a.dp(30)).apply { topMargin = a.dp(2) })
        pinBox.addView(label(a, "Batal", 12f, 0xCCFFFFFF.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(a.dp(16), a.dp(6), a.dp(16), a.dp(6)); pressable(6)
            setOnClickListener { hidePin() }
        }, LinearLayout.LayoutParams(WRAP, a.dp(30)).apply { topMargin = a.dp(2) })
    }

    private fun hidePin() {
        pinMode = false
        pinBox.visibility = View.GONE
        hint.visibility = View.VISIBLE
        entered = ""
    }

    private fun drawDots() {
        dots.text = if (entered.isEmpty()) "" else "●".repeat(entered.length).chunked(1).joinToString(" ")
    }

    private fun press(k: String) {
        when (k) {
            "⌫" -> if (entered.isNotEmpty()) entered = entered.dropLast(1)
            "OK" -> {
                if (Pin.check(entered)) { unlock(); return }
                Sounds.error()
                entered = ""
                dots.animate().translationX(a.dp(12).toFloat()).setDuration(50).withEndAction {
                    dots.animate().translationX(-a.dp(12).toFloat()).setDuration(80).withEndAction {
                        dots.animate().translationX(0f).setDuration(50).start()
                    }.start()
                }.start()
            }
            else -> if (entered.length < 8) entered += k
        }
        drawDots()
    }

    fun unlock() {
        if (unlocking) return
        unlocking = true
        Sounds.unlock()
        animate().translationY(-height.toFloat()).alpha(0f).setDuration(260).setInterpolator(Anim.ease).withEndAction {
            (parent as? ViewGroup)?.removeView(this)
            onUnlocked()
        }.start()
    }
}

/** Titik-titik berputar ala layar boot Windows. */
private class Spinner(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var rot = 0f
    private var anim: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1500; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { rot = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); anim?.cancel() }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = minOf(width, height) / 2f - context.dpf(4f)
        for (i in 0 until 5) {
            val ang = Math.toRadians((rot - i * 26).toDouble())
            p.alpha = 255 - i * 40
            c.drawCircle(cx + r * cos(ang).toFloat(), cy + r * sin(ang).toFloat(), context.dpf(3.2f) * (1f - i * 0.1f), p)
        }
    }
}

/** Animasi boot: logo + spinner di atas latar hitam, lalu memudar. */
class BootView(private val a: MainActivity, private val done: () -> Unit) : FrameLayout(a) {
    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        elevation = a.dpf(60f)
        addView(Glyph(a, G.START, 0xFF4CC2FF.toInt()),
            LayoutParams(a.dp(72), a.dp(72), Gravity.CENTER).apply { bottomMargin = a.dp(60) })
        addView(Spinner(a), LayoutParams(a.dp(36), a.dp(36), Gravity.CENTER).apply { topMargin = a.dp(90) })
    }

    fun run() {
        Sounds.startup()
        postDelayed({
            animate().alpha(0f).setDuration(450).withEndAction {
                (parent as? ViewGroup)?.removeView(this)
                done()
            }.start()
        }, 2300)
    }
}
