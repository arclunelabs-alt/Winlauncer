package com.arclune.winlauncher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// =====================================================================================
// Quick Settings (klik ikon Wi-Fi / volume / baterai)
// =====================================================================================
class QuickPanel(private val a: MainActivity) : LinearLayout(a) {
    init { orientation = VERTICAL; elevation = a.dpf(24f) }

    fun panelWidth() = minOf(a.dp(380), (a.resources.displayMetrics.widthPixels * 0.42f).toInt())

    fun refresh() {
        removeAllViews()
        background = acrylicBg(this, 8)
        setPadding(a.dp(18), a.dp(18), a.dp(18), a.dp(10))

        val tiles = listOf(
            Triple("Wi-Fi", G.WIFI, wifiOn()),
            Triple("Bluetooth", G.BT, btOn()),
            Triple("Mode pesawat", G.PLANE, airplane()),
            Triple("Penghemat baterai", G.SAVER, saver()),
            Triple("Fokus", G.BELL, Theme.focus),
            Triple("Kunci layar", G.LOCK, false),
            Triple("Aksesibilitas", G.ACCESS, false),
            Triple("Layar cast", G.CAST, false)
        )
        tiles.chunked(3).forEach { row ->
            val r = LinearLayout(a)
            row.forEach { (name, g, on) -> r.addView(tile(name, g, on), lp(0, a.dp(64), 1f).apply { setMargins(a.dp(3), a.dp(3), a.dp(3), a.dp(3)) }) }
            addView(r, lp(MATCH, WRAP))
        }

        // kecerahan
        val bright = hBox(a).apply { setPadding(0, a.dp(14), 0, 0) }
        bright.addView(Glyph(a, G.SUN).apply { layoutParams = lp(a.dp(20), a.dp(20)) })
        val cur = currentBrightness()
        bright.addView(FluentSlider(a, cur) { setBrightness(it) }, lp(0, a.dp(28), 1f).apply { leftMargin = a.dp(10) })
        addView(bright, lp(MATCH, WRAP))

        // volume
        val am = a.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val vol = hBox(a).apply { setPadding(0, a.dp(6), 0, 0) }
        vol.addView(Glyph(a, G.VOLUME).apply { layoutParams = lp(a.dp(20), a.dp(20)) })
        vol.addView(FluentSlider(a, am.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).toInt(), 0)
        }, lp(0, a.dp(28), 1f).apply { leftMargin = a.dp(10) })
        addView(vol, lp(MATCH, WRAP))

        // footer
        val foot = hBox(a).apply { setPadding(a.dp(2), a.dp(12), a.dp(2), 0) }
        foot.addView(Glyph(a, G.BATTERY, 0, a.batteryLevel).apply { layoutParams = lp(a.dp(20), a.dp(20)) })
        foot.addView(label(a, "${(a.batteryLevel * 100).toInt()}%" + if (a.charging) " · mengisi" else "", 12f)
            .apply { setPadding(a.dp(8), 0, 0, 0) }, lp(0, WRAP, 1f))
        foot.addView(FrameLayout(a).apply {
            pressable(5)
            addView(Glyph(a, G.SETTINGS), FrameLayout.LayoutParams(a.dp(20), a.dp(20), Gravity.CENTER))
            setOnClickListener { a.openSettings(0) }
        }, lp(a.dp(36), a.dp(36)))
        addView(foot, lp(MATCH, WRAP))
    }

    private fun tile(name: String, g: G, on: Boolean): View {
        val v = vBox(a).apply {
            gravity = Gravity.CENTER
            background = rounded(a, if (on) Theme.accent else Theme.card, 6, if (on) 0 else Theme.stroke)
            isClickable = true
            setOnClickListener { tileAction(name) }
        }
        v.addView(Glyph(a, g, if (on) Color.WHITE else 0).apply { layoutParams = lp(a.dp(22), a.dp(22)) })
        v.addView(label(a, name, 10f, if (on) Color.WHITE else Theme.text).apply {
            setPadding(a.dp(2), a.dp(6), a.dp(2), 0); gravity = Gravity.CENTER
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        return v
    }

    private fun tileAction(name: String) {
        if (name == "Fokus") { Theme.focus = !Theme.focus; Theme.save(a); refresh(); return }
        if (name == "Kunci layar") { a.open(Panel.NONE); a.lock(); return }
        val i = when (name) {
            "Wi-Fi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "Bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "Mode pesawat" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            "Penghemat baterai" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "Aksesibilitas" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            else -> Intent(Settings.ACTION_CAST_SETTINGS)
        }
        a.startSafe(i)
    }

    private fun wifiOn(): Boolean = try {
        val cm = a.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    } catch (e: Exception) { false }

    private fun btOn() = try { Settings.Global.getInt(a.contentResolver, "bluetooth_on", 0) == 1 } catch (e: Exception) { false }
    private fun airplane() = try { Settings.Global.getInt(a.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 } catch (e: Exception) { false }
    private fun saver() = (a.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode

    private fun currentBrightness(): Float = try {
        Settings.System.getInt(a.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (e: Exception) { 0.5f }

    private fun setBrightness(v: Float) {
        val value = (v * 255).toInt().coerceIn(5, 255)
        if (Settings.System.canWrite(a)) {
            Settings.System.putInt(a.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(a.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
        } else {
            val lp = a.window.attributes; lp.screenBrightness = value / 255f; a.window.attributes = lp
        }
    }
}

// =====================================================================================
// Notification center + kalender (klik jam)
// =====================================================================================
class NotifPanel(private val a: MainActivity) : LinearLayout(a) {
    private var monthOffset = 0
    private val cal = FrameLayout(a)

    init { elevation = a.dpf(24f) }

    fun panelWidth() = minOf(a.dp(660), (a.resources.displayMetrics.widthPixels * 0.7f).toInt())
    fun panelHeight() = minOf(a.dp(400), a.resources.displayMetrics.heightPixels - a.dp(48 + 16))

    fun refresh() {
        removeAllViews(); monthOffset = 0
        background = acrylicBg(this, 8)
        orientation = HORIZONTAL

        // kiri: notifikasi
        val left = vBox(a, 14)
        val head = hBox(a)
        head.addView(label(a, "Notifikasi", 14f, bold = true), lp(0, WRAP, 1f))
        head.addView(label(a, "Hapus semua", 11f, Theme.accent).apply {
            setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6)); pressable(5)
            setOnClickListener { NotifService.clearAll(); postDelayed({ refresh() }, 400) }
        })
        head.addView(label(a, "Pengaturan", 11f, Theme.accent).apply {
            setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6)); pressable(5)
            setOnClickListener { a.startSafe(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
        })
        left.addView(head, lp(MATCH, a.dp(34)))
        left.addView(notifList(), lp(MATCH, 0, 1f))
        addView(left, lp(0, MATCH, 1f))

        // pemisah
        addView(View(a).apply { setBackgroundColor(Theme.stroke) }, lp(a.dp(1), MATCH).apply { setMargins(0, a.dp(14), 0, a.dp(14)) })

        // kanan: kalender
        val right = vBox(a, 14)
        right.addView(label(a, SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()), 13f, bold = true)
            .apply { setPadding(a.dp(4), 0, 0, a.dp(6)) })
        right.addView(cal, lp(MATCH, 0, 1f))
        addView(right, lp(a.dp(290), MATCH))
        drawCalendar()
    }

    private fun notifList(): View {
        val col = vBox(a)
        val items = NotifService.list().filter { it.isClearable || true }.sortedByDescending { it.postTime }
        val sv = ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(col) }
        if (NotifService.instance == null) {
            col.addView(label(a, "Aktifkan akses notifikasi agar notifikasi tampil di sini.", 12f, Theme.subText)
                .apply { setPadding(a.dp(4), a.dp(10), a.dp(4), a.dp(10)) })
            return sv
        }
        if (items.isEmpty()) {
            col.addView(label(a, "Tidak ada notifikasi baru", 12f, Theme.subText).apply { setPadding(a.dp(4), a.dp(10), 0, 0) })
        }
        items.forEach { sbn ->
            val n = sbn.notification
            val ex = n.extras
            val title = ex.getCharSequence("android.title")?.toString() ?: ""
            val text = ex.getCharSequence("android.text")?.toString() ?: ""
            if (title.isEmpty() && text.isEmpty()) return@forEach
            val row = hBox(a).apply {
                setPadding(a.dp(10), a.dp(8), a.dp(10), a.dp(8))
                background = rounded(a, Theme.card, 6, Theme.stroke)
                setOnClickListener {
                    try { n.contentIntent?.send(); a.open(Panel.NONE) } catch (e: PendingIntent.CanceledException) { }
                }
            }
            val ic = ImageView(a)
            try { ic.setImageDrawable(n.smallIcon?.loadDrawable(a)); ic.setColorFilter(Theme.text) } catch (e: Exception) { }
            row.addView(ic, lp(a.dp(18), a.dp(18)))
            val c = vBox(a)
            c.addView(label(a, title, 12f, bold = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            c.addView(label(a, text, 11f, Theme.subText).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
            row.addView(c, lp(0, WRAP, 1f).apply { leftMargin = a.dp(10) })
            col.addView(row, lp(MATCH, WRAP).apply { bottomMargin = a.dp(4) })
        }
        return sv
    }

    private fun drawCalendar() {
        cal.removeAllViews()
        val c = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, monthOffset) }
        val today = Calendar.getInstance()
        val col = vBox(a)

        val head = hBox(a)
        head.addView(label(a, SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(c.time), 13f, bold = true)
            .apply { setPadding(a.dp(4), 0, 0, 0) }, lp(0, WRAP, 1f))
        fun nav(g: G, d: Int) = FrameLayout(a).apply {
            pressable(5)
            addView(Glyph(a, g), FrameLayout.LayoutParams(a.dp(14), a.dp(14), Gravity.CENTER))
            setOnClickListener { monthOffset += d; drawCalendar() }
        }
        head.addView(nav(G.BACK, -1), lp(a.dp(32), a.dp(32)))
        head.addView(nav(G.NEXT, 1), lp(a.dp(32), a.dp(32)))
        col.addView(head, lp(MATCH, a.dp(34)))

        val names = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
        val hr = LinearLayout(a)
        names.forEach { hr.addView(label(a, it, 10f, Theme.subText).apply { gravity = Gravity.CENTER }, lp(0, a.dp(26), 1f)) }
        col.addView(hr)

        val offset = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Senin = 0
        val days = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val prevMax = (c.clone() as Calendar).apply { add(Calendar.MONTH, -1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
        var d = 1 - offset
        for (w in 0 until 6) {
            val row = LinearLayout(a)
            for (i in 0 until 7) {
                val inMonth = d in 1..days
                val num = if (d < 1) prevMax + d else if (d > days) d - days else d
                val isToday = inMonth && monthOffset == 0 && d == today.get(Calendar.DAY_OF_MONTH)
                val cell = FrameLayout(a)
                val t = label(a, num.toString(), 11f, when {
                    isToday -> Color.WHITE
                    inMonth -> Theme.text
                    else -> Theme.subText
                }).apply { gravity = Gravity.CENTER }
                if (isToday) t.background = rounded(a, Theme.accent, 16)
                cell.addView(t, FrameLayout.LayoutParams(a.dp(30), a.dp(30), Gravity.CENTER))
                row.addView(cell, lp(0, a.dp(34), 1f))
                d++
            }
            col.addView(row)
        }
        cal.addView(col)
    }
}

// =====================================================================================
// Widgets (slide dari kiri)
// =====================================================================================
class WidgetsPanel(private val a: MainActivity) : LinearLayout(a) {
    init { orientation = VERTICAL; elevation = a.dpf(24f) }

    fun panelWidth() = minOf(a.dp(430), (a.resources.displayMetrics.widthPixels * 0.5f).toInt())
    fun panelHeight() = a.resources.displayMetrics.heightPixels - a.dp(48 + 16)

    fun refresh() {
        removeAllViews()
        background = acrylicBg(this, 8)
        val col = vBox(a, 16)
        val now = Date()

        val head = hBox(a)
        head.addView(vBox(a).apply {
            addView(label(a, SimpleDateFormat("EEEE", Locale.getDefault()).format(now), 18f, bold = true))
            addView(label(a, SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(now), 12f, Theme.subText))
        }, lp(0, WRAP, 1f))
        head.addView(label(a, SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), 26f, Theme.accent, true))
        col.addView(head, lp(MATCH, WRAP).apply { bottomMargin = a.dp(12) })

        // cuaca (diisi async)
        val weatherBox = vBox(a)
        weatherBox.addView(label(a, "Memuat cuaca...", 12f, Theme.subText).apply { setPadding(a.dp(12), a.dp(12), a.dp(12), a.dp(12)) })
        weatherBox.background = rounded(a, Theme.card, 6, Theme.stroke)
        col.addView(weatherBox, lp(MATCH, WRAP).apply { bottomMargin = a.dp(8) })
        Info.weather(a) { w -> if (weatherBox.parent != null) fillWeather(weatherBox, w) }

        // baris kartu: baterai + penyimpanan
        val r1 = LinearLayout(a)
        r1.addView(card("Baterai", "${(a.batteryLevel * 100).toInt()}%", if (a.charging) "Sedang mengisi" else "Menggunakan baterai",
            a.batteryLevel), lp(0, WRAP, 1f).apply { rightMargin = a.dp(4) })
        val st = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val usedFrac = 1f - st.availableBytes.toFloat() / st.totalBytes.toFloat()
        r1.addView(card("Penyimpanan", "${(usedFrac * 100).toInt()}%", "${Apps.gb(st.availableBytes.toDouble())} GB bebas", usedFrac),
            lp(0, WRAP, 1f).apply { leftMargin = a.dp(4) })
        col.addView(r1, lp(MATCH, WRAP).apply { bottomMargin = a.dp(8) })

        // catatan cepat (tersimpan)
        val prefs = a.getSharedPreferences("win11", Context.MODE_PRIVATE)
        val note = vBox(a, 12).apply { background = rounded(a, Theme.card, 6, Theme.stroke) }
        note.addView(label(a, "Catatan cepat", 12f, bold = true).apply { setPadding(0, 0, 0, a.dp(6)) })
        val et = EditText(a).apply {
            hint = "Tulis sesuatu..."; setHintTextColor(Theme.subText); setTextColor(Theme.text); textSize = 12f
            background = null; minLines = 3; gravity = Gravity.TOP
            setText(prefs.getString("note", ""))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { prefs.edit().putString("note", s?.toString() ?: "").apply() }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
        }
        note.addView(et, lp(MATCH, WRAP))
        col.addView(note, lp(MATCH, WRAP).apply { bottomMargin = a.dp(8) })

        // berita (diisi async)
        val newsBox = vBox(a, 12).apply { background = rounded(a, Theme.card, 6, Theme.stroke) }
        val nh = hBox(a)
        nh.addView(label(a, "Berita", 12f, bold = true), lp(0, WRAP, 1f))
        nh.addView(label(a, "Segarkan", 11f, Theme.accent).apply {
            setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6)); pressable(5)
            setOnClickListener { fillNewsLoading(newsBox, nh); Info.news(a, true) { l -> if (newsBox.parent != null) fillNews(newsBox, nh, l) } }
        })
        newsBox.addView(nh, lp(MATCH, a.dp(30)))
        col.addView(newsBox, lp(MATCH, WRAP))
        fillNewsLoading(newsBox, nh)
        Info.news(a) { l -> if (newsBox.parent != null) fillNews(newsBox, nh, l) }

        addView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(col) }, lp(MATCH, MATCH))
    }

    private fun fillWeather(box: LinearLayout, w: Weather?) {
        box.removeAllViews()
        box.setPadding(a.dp(12), a.dp(12), a.dp(12), a.dp(12))
        if (w == null) {
            box.addView(label(a, "Cuaca tidak tersedia. Periksa koneksi internet.", 12f, Theme.subText))
            box.addView(label(a, "Coba lagi", 11f, Theme.accent).apply {
                setPadding(0, a.dp(8), 0, 0)
                setOnClickListener { Info.weather(a, true) { r -> if (box.parent != null) fillWeather(box, r) } }
            })
            return
        }
        val top = hBox(a)
        top.addView(label(a, Info.codeEmoji(w.code), 30f), lp(WRAP, WRAP))
        val mid = vBox(a)
        mid.addView(label(a, w.temp.toString() + "\u00B0C", 24f, Theme.text, true))
        mid.addView(label(a, Info.codeText(w.code), 12f, Theme.subText))
        top.addView(mid, lp(0, WRAP, 1f).apply { leftMargin = a.dp(12) })
        val side = vBox(a)
        side.addView(label(a, w.city, 12f, Theme.accent, true).apply {
            gravity = Gravity.END
            setOnClickListener { cityDialog(box) }
        })
        side.addView(label(a, "Lembap " + w.humidity + "%", 10f, Theme.subText).apply { gravity = Gravity.END })
        side.addView(label(a, "Angin " + w.wind + " km/j", 10f, Theme.subText).apply { gravity = Gravity.END })
        top.addView(side, lp(WRAP, WRAP))
        box.addView(top, lp(MATCH, WRAP))
        val days = LinearLayout(a)
        for (d in w.days) {
            val c = vBox(a).apply { gravity = Gravity.CENTER_HORIZONTAL }
            c.addView(label(a, d.label, 10f, Theme.subText).apply { gravity = Gravity.CENTER })
            c.addView(label(a, Info.codeEmoji(d.code), 16f).apply { gravity = Gravity.CENTER; setPadding(0, a.dp(2), 0, a.dp(2)) })
            c.addView(label(a, d.hi.toString() + "\u00B0", 11f, Theme.text).apply { gravity = Gravity.CENTER })
            c.addView(label(a, d.lo.toString() + "\u00B0", 10f, Theme.subText).apply { gravity = Gravity.CENTER })
            days.addView(c, lp(0, WRAP, 1f))
        }
        box.addView(days, lp(MATCH, WRAP).apply { topMargin = a.dp(10) })
    }

    private fun cityDialog(box: LinearLayout) {
        val et = EditText(a).apply { setText(Info.city(a)); setSingleLine(); setSelection(text.length) }
        android.app.AlertDialog.Builder(a).setTitle("Kota untuk cuaca").setView(et)
            .setPositiveButton("OK") { _, _ ->
                Info.setCity(a, et.text.toString())
                a.taskbar.refresh()
                Info.weather(a, true) { r -> if (box.parent != null) fillWeather(box, r); a.taskbar.refresh() }
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun fillNewsLoading(box: LinearLayout, head: View) {
        while (box.childCount > 1) box.removeViewAt(1)
        box.addView(label(a, "Memuat berita...", 12f, Theme.subText).apply { setPadding(a.dp(2), a.dp(8), 0, a.dp(4)) })
    }

    private fun fillNews(box: LinearLayout, head: View, list: List<NewsItem>) {
        while (box.childCount > 1) box.removeViewAt(1)
        if (list.isEmpty()) {
            box.addView(label(a, "Berita tidak tersedia. Periksa koneksi internet.", 12f, Theme.subText).apply { setPadding(a.dp(2), a.dp(8), 0, a.dp(4)) })
            return
        }
        for (n in list.take(8)) {
            val row = vBox(a).apply {
                setPadding(a.dp(6), a.dp(8), a.dp(6), a.dp(8)); pressable(5)
                setOnClickListener { a.openBrowser(n.link) }
            }
            row.addView(label(a, n.title, 12f).apply { maxLines = 3; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.TOP })
            if (n.date.isNotEmpty()) row.addView(label(a, "BBC Indonesia \u00B7 " + n.date, 10f, Theme.subText).apply { setPadding(0, a.dp(3), 0, 0) })
            box.addView(row, lp(MATCH, WRAP))
        }
    }

    private fun card(title: String, big: String, sub: String, frac: Float): View {
        val v = vBox(a, 12).apply { background = rounded(a, Theme.card, 6, Theme.stroke) }
        v.addView(label(a, title, 11f, Theme.subText))
        v.addView(label(a, big, 24f, Theme.text, true).apply { setPadding(0, a.dp(4), 0, a.dp(4)) })
        val bar = FrameLayout(a).apply { background = rounded(a, if (Theme.dark) 0x33FFFFFF else 0x22000000, 3) }
        val fill = View(a).apply { background = rounded(a, Theme.accent, 3) }
        bar.addView(fill, FrameLayout.LayoutParams(0, MATCH))
        bar.post { fill.layoutParams = FrameLayout.LayoutParams((bar.width * frac.coerceIn(0f, 1f)).toInt(), MATCH) }
        v.addView(bar, lp(MATCH, a.dp(5)).apply { bottomMargin = a.dp(6) })
        v.addView(label(a, sub, 10f, Theme.subText))
        return v
    }
}
