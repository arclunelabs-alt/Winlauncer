package com.arclune.winlauncher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.TextView

/** Pengaturan + warna bergaya Windows 11 (Fluent / Mica). */
object Theme {
    var dark = true
    var accent = Color.parseColor("#0067C0")
    var centered = true
    var showDesktopIcons = true
    var windowMode = false
    var wallpaper = 0
    var acrylic = true
    const val DEFAULT_SCALE = 1.15f
    var uiScale = DEFAULT_SCALE
    var showBoot = true
    var lockOnScreenOff = false
    var lockOnStart = false
    var toasts = true
    var focus = false
    var pin = ""
    var sounds = true

    val accentList = listOf("#0067C0", "#8764B8", "#038387", "#C239B3", "#E81123", "#FF8C00")

    val surface get() = if (dark) Color.argb(238, 32, 32, 32) else Color.argb(238, 243, 243, 243)
    val surfaceSolid get() = if (dark) Color.rgb(43, 43, 43) else Color.rgb(250, 250, 250)
    val taskbar get() = if (dark) Color.argb(225, 28, 28, 28) else Color.argb(225, 240, 240, 240)
    val card get() = if (dark) Color.argb(26, 255, 255, 255) else Color.argb(190, 255, 255, 255)
    val text get() = if (dark) Color.WHITE else Color.rgb(27, 27, 27)
    val subText get() = if (dark) Color.argb(175, 255, 255, 255) else Color.argb(160, 0, 0, 0)
    val stroke get() = if (dark) Color.argb(45, 255, 255, 255) else Color.argb(35, 0, 0, 0)
    val hover get() = if (dark) Color.argb(28, 255, 255, 255) else Color.argb(20, 0, 0, 0)

    fun load(c: Context) {
        val p = c.getSharedPreferences("win11", Context.MODE_PRIVATE)
        dark = p.getBoolean("dark", true)
        accent = p.getInt("accent", accent)
        centered = p.getBoolean("centered", true)
        showDesktopIcons = p.getBoolean("icons", true)
        windowMode = p.getBoolean("windowMode", false)
        wallpaper = p.getInt("wallpaper", 0)
        acrylic = p.getBoolean("acrylic", true)
        uiScale = p.getFloat("uiScale", DEFAULT_SCALE)
        showBoot = p.getBoolean("showBoot", true)
        lockOnScreenOff = p.getBoolean("lockOff", false)
        lockOnStart = p.getBoolean("lockStart", false)
        toasts = p.getBoolean("toasts", true)
        focus = p.getBoolean("focus", false)
        pin = p.getString("pin", "") ?: ""
        sounds = p.getBoolean("sounds", true)
    }

    fun save(c: Context) {
        c.getSharedPreferences("win11", Context.MODE_PRIVATE).edit()
            .putBoolean("dark", dark).putInt("accent", accent)
            .putBoolean("centered", centered).putBoolean("icons", showDesktopIcons)
            .putBoolean("windowMode", windowMode).putInt("wallpaper", wallpaper)
            .putBoolean("acrylic", acrylic).putFloat("uiScale", uiScale)
            .putBoolean("showBoot", showBoot).putBoolean("lockOff", lockOnScreenOff).putBoolean("lockStart", lockOnStart)
            .putBoolean("toasts", toasts).putBoolean("focus", focus).putString("pin", pin).putBoolean("sounds", sounds).apply()
    }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun rounded(c: Context, color: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 1): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = c.dpf(radiusDp.toFloat())
        if (strokeColor != 0) setStroke(c.dp(strokeDp).coerceAtLeast(1), strokeColor)
    }

/** Latar dengan efek hover/ripple halus seperti Windows 11. */
fun hoverBg(c: Context, radiusDp: Int, base: Int = 0): RippleDrawable {
    val content = rounded(c, base, radiusDp)
    val mask = rounded(c, Color.WHITE, radiusDp)
    return RippleDrawable(ColorStateList.valueOf(Theme.hover), content, mask)
}

fun label(c: Context, s: String, sp: Float, color: Int = Theme.text, bold: Boolean = false): TextView =
    TextView(c).apply {
        text = s
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        else typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
    }

/** Easing Fluent: cepat di awal, mendarat halus. */
object Anim {
    val ease by lazy { PathInterpolator(0.1f, 0.9f, 0.2f, 1f) }
}

private val pressScale = View.OnTouchListener { v, e ->
    when (e.actionMasked) {
        MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).start()
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
            v.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(Anim.ease).start()
    }
    false
}

fun View.pressable(radiusDp: Int = 6, base: Int = 0, scale: Boolean = false) {
    background = hoverBg(context, radiusDp, base)
    isClickable = true
    if (scale) setOnTouchListener(pressScale)
}
