package com.arclune.winlauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow

/** Item menu konteks. action == null berarti garis pemisah. */
class MenuItem(val text: String, val glyph: G? = null, val action: (() -> Unit)? = null)

/**
 * Menu konteks gaya Windows 11 (sudut membulat, akrilik).
 * Bila [above] true, menu muncul di atas titik (x,y) — dipakai untuk taskbar.
 */
fun showMenu(a: MainActivity, x: Int, y: Int, items: List<MenuItem>, above: Boolean = false, centerOnX: Boolean = false) {
    val box = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(a, Theme.surfaceSolid, 8, Theme.stroke)
        setPadding(a.dp(4), a.dp(4), a.dp(4), a.dp(4))
    }
    val pw = PopupWindow(box, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
    pw.elevation = a.dpf(16f)
    pw.setBackgroundDrawable(null)
    items.forEach { it0 ->
        if (it0.action == null) {
            box.addView(View(a).apply { setBackgroundColor(Theme.stroke) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.dp(1)).apply { setMargins(a.dp(6), a.dp(4), a.dp(6), a.dp(4)) })
        } else {
            val row = LinearLayout(a).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(a.dp(10), 0, a.dp(24), 0)
                pressable(5)
                setOnClickListener { pw.dismiss(); it0.action.invoke() }
            }
            row.addView(if (it0.glyph != null) Glyph(a, it0.glyph) else View(a),
                LinearLayout.LayoutParams(a.dp(16), a.dp(16)).apply { rightMargin = a.dp(12) })
            row.addView(label(a, it0.text, 12.5f))
            box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, a.dp(36)))
        }
    }
    box.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    val dm = a.resources.displayMetrics
    val w = box.measuredWidth; val h = box.measuredHeight
    var px = if (centerOnX) x - w / 2 else x
    var py = if (above) y - h - a.dp(8) else y
    px = px.coerceIn(a.dp(4), dm.widthPixels - w - a.dp(4))
    py = py.coerceIn(a.dp(4), dm.heightPixels - h - a.dp(4))
    pw.showAtLocation(a.window.decorView, Gravity.NO_GRAVITY, px, py)
}

/** Menu klik-kanan (tahan lama) untuk sebuah aplikasi. */
fun appMenu(a: MainActivity, anchor: View, app: AppInfo, fromTaskbar: Boolean, extra: List<MenuItem> = emptyList()) {
    val loc = IntArray(2); anchor.getLocationOnScreen(loc)
    val running = AppRepo.running().any { it.pkg == app.pkg }
    val items = mutableListOf(MenuItem("Buka", G.NEXT) { a.launchApp(app) })
    items.addAll(extra)
    if (running) items.add(MenuItem("Tutup jendela", G.CLOSE) { AppRepo.markRunning(app.pkg, false); a.taskbar.refresh() })
    items.add(MenuItem(""))
    items.add(MenuItem(if (AppRepo.isPinned(app.pkg)) "Lepas dari Start" else "Sematkan ke Start", G.START) {
        AppRepo.togglePin(app.pkg)
        if (a.currentPanel == Panel.START) { a.open(Panel.NONE); a.open(Panel.START) }
    })
    items.add(MenuItem(if (AppRepo.isTaskbarPinned(app.pkg)) "Lepas dari taskbar" else "Sematkan ke taskbar", G.TASKVIEW) {
        AppRepo.toggleTaskbarPin(app.pkg); a.taskbar.refresh()
    })
    items.add(MenuItem("Kirim ke desktop", G.FOLDER) { a.desktopView.addApp(app.pkg) })
    items.add(MenuItem(""))
    items.add(MenuItem("Info aplikasi", G.SETTINGS) { AppRepo.appInfo(a, app.pkg) })
    items.add(MenuItem("Copot pemasangan", G.TRASH) { AppRepo.uninstall(a, app.pkg) })
    if (fromTaskbar) showMenu(a, loc[0] + anchor.width / 2, loc[1], items, above = true, centerOnX = true)
    else showMenu(a, loc[0] + anchor.width / 2, loc[1] + anchor.height / 2, items)
}

/** Task View: thumbnail jendela terbuka + aplikasi terakhir. */
class TaskView(private val a: MainActivity) : FrameLayout(a) {
    init {
        setBackgroundColor(if (Theme.dark) 0xB0000000.toInt() else 0x80FFFFFF.toInt())
        isClickable = true
        setOnClickListener { a.toggleTaskView() }
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        addView(col, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        col.addView(deskStrip(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = a.dp(16) })

        val wins = a.windows.all()
        if (wins.isEmpty()) {
            col.addView(Glyph(a, G.TASKVIEW).apply { layoutParams = LinearLayout.LayoutParams(a.dp(48), a.dp(48)) })
            col.addView(label(a, "Tidak ada jendela terbuka", 15f, Theme.text, true).apply { setPadding(0, a.dp(12), 0, 0) })
        } else {
            val row = LinearLayout(a)
            wins.forEach { w -> row.addView(card(w)) }
            col.addView(HorizontalScrollView(a).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
                setPadding(a.dp(24), 0, a.dp(24), 0)
            })
        }
        // aplikasi terakhir
        val rec = AppRepo.recents()
        if (rec.isNotEmpty()) {
            col.addView(label(a, "Aplikasi terbaru", 11f, Theme.subText).apply { setPadding(0, a.dp(20), 0, a.dp(8)) })
            val r = LinearLayout(a)
            rec.forEach { app ->
                r.addView(ImageView(a).apply { setImageDrawable(app.icon); pressable(8); setPadding(a.dp(6), a.dp(6), a.dp(6), a.dp(6))
                    setOnClickListener { a.launchApp(app) } },
                    LinearLayout.LayoutParams(a.dp(48), a.dp(48)).apply { setMargins(a.dp(4), 0, a.dp(4), 0) })
            }
            col.addView(r)
        }
    }

    /** Deretan desktop virtual: ketuk untuk pindah, tahan untuk menghapus, "+" untuk menambah. */
    private fun deskStrip(): View {
        val row = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        for (i in 0 until a.windows.count) {
            val cur = i == a.windows.current
            val chip = label(a, "Desktop ${i + 1}  \u00B7  ${a.windows.countOn(i)}", 12f, if (cur) Color.WHITE else Theme.text).apply {
                gravity = Gravity.CENTER
                setPadding(a.dp(14), a.dp(8), a.dp(14), a.dp(8))
                background = rounded(a, if (cur) Theme.accent else Theme.card, 6, Theme.stroke)
                setOnClickListener { a.windows.switchDesktop(i); a.reopenTaskView() }
                if (a.windows.count > 1) setOnLongClickListener {
                    val loc = IntArray(2); getLocationOnScreen(loc)
                    showMenu(a, loc[0], loc[1] + height, listOf(MenuItem("Hapus Desktop ${i + 1}", G.CLOSE) {
                        a.windows.removeDesktop(i); a.reopenTaskView()
                    }))
                    true
                }
            }
            row.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(a.dp(4), 0, a.dp(4), 0) })
        }
        if (a.windows.count < 4) {
            row.addView(label(a, "+ Desktop baru", 12f, Theme.accent).apply {
                gravity = Gravity.CENTER
                setPadding(a.dp(14), a.dp(8), a.dp(14), a.dp(8))
                background = rounded(a, 0, 6, Theme.accent)
                setOnClickListener { a.windows.addDesktop(); a.reopenTaskView() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(a.dp(4), 0, a.dp(4), 0) })
        }
        return row
    }

    private fun windowMenu(v: View, w: WindowFrame) {
        val loc = IntArray(2); v.getLocationOnScreen(loc)
        val items = ArrayList<MenuItem>()
        for (d in 0 until a.windows.count) if (d != w.desk) items.add(MenuItem("Pindahkan ke Desktop ${d + 1}", G.NEXT) {
            a.windows.moveToDesktop(w, d); a.reopenTaskView()
        })
        if (items.isNotEmpty()) items.add(MenuItem(""))
        items.add(MenuItem("Tutup jendela", G.CLOSE) { a.windows.close(w); a.reopenTaskView() })
        showMenu(a, loc[0] + v.width / 2, loc[1] + v.height / 2, items, centerOnX = true)
    }

    private fun card(w: WindowFrame): View {
        val v = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(a.dp(8), a.dp(8), a.dp(8), a.dp(8))
            background = rounded(a, Theme.surfaceSolid, 8, if (w.isFocusedWin) Theme.accent else Theme.stroke, 2)
            setOnClickListener { a.toggleTaskView(); if (w.minimized) a.windows.toggle(w) else a.windows.focus(w) }
            setOnLongClickListener { windowMenu(this, w); true }
        }
        val head = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(Glyph(a, w.glyph), LinearLayout.LayoutParams(a.dp(14), a.dp(14)))
        head.addView(label(a, w.title, 11f).apply { setPadding(a.dp(6), 0, 0, 0) })
        v.addView(head, LinearLayout.LayoutParams(a.dp(190), a.dp(20)))
        val thumb = FrameLayout(a).apply { setBackgroundColor(if (Theme.dark) 0xFF1A1A1A.toInt() else 0xFFE8E8E8.toInt()) }
        if (w.width > 0 && w.height > 0 && !w.minimized) {
            val bmp = Bitmap.createBitmap(w.width, w.height, Bitmap.Config.ARGB_8888)
            w.draw(Canvas(bmp))
            thumb.addView(ImageView(a).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_CENTER },
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        } else thumb.addView(Glyph(a, w.glyph), LayoutParams(a.dp(40), a.dp(40), Gravity.CENTER))
        v.addView(thumb, LinearLayout.LayoutParams(a.dp(190), a.dp(115)).apply { topMargin = a.dp(6) })
        v.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(a.dp(6), 0, a.dp(6), 0) }
        return v
    }
}
