package com.arclune.winlauncher

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import kotlin.math.abs

/** Aplikasi bawaan launcher (muncul di Start, desktop, dan pencarian). */
class BuiltIn(val id: String, val name: String, val glyph: G, val open: (MainActivity) -> Unit)

object Builtins {
    val all = listOf(
        BuiltIn("files", "File Explorer", G.FOLDER) { it.openExplorer() },
        BuiltIn("calc", "Kalkulator", G.CALC) { it.openCalc() },
        BuiltIn("notes", "Notepad", G.NOTE) { it.openNotes() },
        BuiltIn("clock", "Jam", G.CLOCK) { it.openClock() },
        BuiltIn("browser", "Browser", G.GLOBE) { it.openBrowser() },
        BuiltIn("photos", "Foto", G.IMAGE) { it.openPhotos() },
        BuiltIn("media", "Media Player", G.MUSIC) { it.openMedia() },
        BuiltIn("paint", "Paint", G.BRUSH) { it.openPaint() },
        BuiltIn("taskmgr", "Pengelola Tugas", G.PULSE) { it.openTaskMgr() },
        BuiltIn("settings", "Pengaturan", G.SETTINGS) { it.openSettings(0) },
        BuiltIn("pc", "Perangkat ini", G.PC) { it.openThisPc() },
        BuiltIn("bin", "Recycle Bin", G.TRASH) { it.openBin() }
    )
    fun find(id: String) = all.firstOrNull { it.id == id }
}

/**
 * Desktop dengan ikon yang bisa digeser: tahan lalu seret, lepas untuk snap ke kotak grid.
 * Tahan tanpa menggeser untuk menu (Buka / Hapus dari desktop).
 */
class DesktopView(private val a: MainActivity) : FrameLayout(a) {
    private class DItem(val id: String, var col: Int, var row: Int)

    private val items = mutableListOf<DItem>()
    private val cell = a.dp(84)
    private val pad = a.dp(8)
    private var selected: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(a).scaledTouchSlop
    private val prefs = a.getSharedPreferences("win11", Context.MODE_PRIVATE)

    private fun cols(): Int = ((a.resources.displayMetrics.widthPixels - pad * 2) / cell).coerceAtLeast(1)
    private fun rows(): Int = ((a.resources.displayMetrics.heightPixels - a.dp(48) - pad * 2) / cell).coerceAtLeast(1)

    init {
        load()
        setOnLongClickListener { a.desktopMenu(); true }
        setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) { select(null); a.open(Panel.NONE) }
            false
        }
    }

    // ------------------------------------------------------------------ simpan / muat
    private fun load() {
        items.clear()
        val raw = prefs.getString("desktop", null)
        if (raw == null) { defaults(); return }
        raw.split(",").filter { it.isNotBlank() }.forEach { s ->
            val parts = s.split(":")
            // format id: "b:pc:col:row" atau "a:com.paket:col:row"
            if (parts.size == 4) items.add(DItem(parts[0] + ":" + parts[1], parts[2].toIntOrNull() ?: 0, parts[3].toIntOrNull() ?: 0))
        }
    }

    private fun defaults() {
        items.clear()
        val r = rows()
        val ids = listOf("b:pc", "b:bin", "b:settings", "b:files", "b:calc", "b:notes")
        ids.forEachIndexed { i, id -> items.add(DItem(id, i / r, i % r)) }
        save()
    }

    fun restoreDefaults() { prefs.edit().remove("desktop").apply(); defaults(); build() }

    private fun save() {
        prefs.edit().putString("desktop", items.joinToString(",") { "${it.id}:${it.col}:${it.row}" }).apply()
    }

    // ------------------------------------------------------------------ bangun tampilan
    fun build() {
        removeAllViews()
        selected = null
        if (AppRepo.loaded) items.removeAll { it.id.startsWith("a:") && AppRepo.find(it.id.substring(2)) == null }
        if (!Theme.showDesktopIcons) return
        for (it in items) {
            val v = tile(it) ?: continue
            addView(v, LayoutParams(cell - a.dp(6), cell - a.dp(6)).apply {
                leftMargin = pad + it.col * cell; topMargin = pad + it.row * cell
            })
        }
    }

    private fun tile(item: DItem): View? {
        val name: String
        val icon: View
        if (item.id.startsWith("b:")) {
            val b = Builtins.find(item.id.substring(2)) ?: return null
            name = b.name; icon = Glyph(a, b.glyph, Color.WHITE)
        } else {
            val app = AppRepo.find(item.id.substring(2)) ?: return null
            name = app.label; icon = ImageView(a).apply { setImageDrawable(app.icon) }
        }
        val v = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(a.dp(2), a.dp(6), a.dp(2), a.dp(4))
        }
        v.addView(icon, LinearLayout.LayoutParams(a.dp(38), a.dp(38)))
        v.addView(label(a, name, 11f, Color.WHITE).apply {
            gravity = Gravity.CENTER; maxLines = 2
            setShadowLayer(4f, 0f, 1f, 0xCC000000.toInt())
            setPadding(0, a.dp(4), 0, 0)
        })
        attach(v, item)
        return v
    }

    private fun select(v: View?) {
        selected?.background = null
        selected = v
        v?.background = rounded(a, 0x44FFFFFF, 5, 0x66FFFFFF, 1)
    }

    private fun run(item: DItem) {
        if (item.id.startsWith("b:")) Builtins.find(item.id.substring(2))?.open?.invoke(a)
        else AppRepo.find(item.id.substring(2))?.let { a.launchApp(it) }
    }

    // ------------------------------------------------------------------ sentuhan: klik / seret
    private fun attach(v: View, item: DItem) {
        var downX = 0f
        var downY = 0f
        var dragging = false
        var movedFar = false
        val longRun = Runnable {
            dragging = true
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            v.bringToFront()
            v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(100).start()
        }
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; dragging = false; movedFar = false
                    select(v); a.open(Panel.NONE)
                    handler.postDelayed(longRun, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dxm = e.rawX - downX; val dym = e.rawY - downY
                    if (abs(dxm) > slop || abs(dym) > slop) {
                        movedFar = true
                        if (!dragging) handler.removeCallbacks(longRun)
                    }
                    if (dragging) { v.translationX = dxm; v.translationY = dym }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longRun)
                    if (dragging) {
                        if (movedFar) drop(v, item) else {
                            v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                            itemMenu(v, item)
                        }
                    } else if (!movedFar) run(item)
                    dragging = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longRun)
                    v.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f).setDuration(120).start()
                    dragging = false
                }
            }
            true
        }
    }

    private fun drop(v: View, item: DItem) {
        val vx = v.left + v.translationX
        val vy = v.top + v.translationY
        var c = Math.round((vx - pad) / cell.toFloat()).coerceIn(0, cols() - 1)
        var r = Math.round((vy - pad) / cell.toFloat()).coerceIn(0, rows() - 1)
        if (items.any { it !== item && it.col == c && it.row == r }) {
            var best = Float.MAX_VALUE
            for (cc in 0 until cols()) for (rr in 0 until rows()) {
                if (items.any { it !== item && it.col == cc && it.row == rr }) continue
                val dx = (pad + cc * cell) - vx; val dy = (pad + rr * cell) - vy
                val d = dx * dx + dy * dy
                if (d < best) { best = d; c = cc; r = rr }
            }
        }
        item.col = c; item.row = r; save()
        val newLeft = pad + c * cell; val newTop = pad + r * cell
        val lp = v.layoutParams as LayoutParams
        lp.leftMargin = newLeft; lp.topMargin = newTop
        v.layoutParams = lp
        v.translationX = vx - newLeft; v.translationY = vy - newTop
        v.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(Anim.ease).start()
    }

    private fun itemMenu(v: View, item: DItem) {
        val loc = IntArray(2); v.getLocationOnScreen(loc)
        showMenu(a, loc[0] + v.width / 2, loc[1] + v.height / 2, listOf(
            MenuItem("Buka", G.NEXT) { run(item) },
            MenuItem(""),
            MenuItem("Hapus dari desktop", G.TRASH) { items.remove(item); save(); build() }
        ), centerOnX = true)
    }

    // ------------------------------------------------------------------ API untuk luar
    fun has(id: String) = items.any { it.id == id }

    fun addApp(pkg: String) {
        val id = "a:$pkg"
        if (has(id)) return
        val r = rows()
        outer@ for (c in 0 until cols()) for (rr in 0 until r) {
            if (items.none { it.col == c && it.row == rr }) { items.add(DItem(id, c, rr)); break@outer }
        }
        save(); build()
    }
}
