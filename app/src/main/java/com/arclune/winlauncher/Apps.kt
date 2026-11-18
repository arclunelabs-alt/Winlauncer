package com.arclune.winlauncher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/** Isi jendela bawaan: Settings, This PC, Recycle Bin. */
object Apps {

    // ------------------------------------------------------------------ Settings
    fun settings(a: MainActivity, page: Int = 0): View {
        val root = LinearLayout(a)
        root.setBackgroundColor(if (Theme.dark) 0xFF272727.toInt() else 0xFFF9F9F9.toInt())

        val names = listOf("Sistem", "Personalisasi", "Aplikasi", "Tentang", "Kunci & suara")
        val glyphs = listOf(G.PC, G.PALETTE, G.APPS, G.SETTINGS, G.LOCK)

        // sidebar
        val side = vBox(a, 8)
        side.addView(hBox(a).apply {
            setPadding(a.dp(6), a.dp(4), a.dp(6), a.dp(12))
            addView(Glyph(a, G.USER).apply { layoutParams = lp(a.dp(34), a.dp(34)); tint = Theme.accent })
            val col = vBox(a)
            col.addView(label(a, "Pengguna", 13f, bold = true))
            col.addView(label(a, "Akun lokal", 11f, Theme.subText))
            addView(col, lp(WRAP, WRAP).apply { leftMargin = a.dp(10) })
        })
        names.forEachIndexed { i, n ->
            val item = hBox(a).apply {
                setPadding(a.dp(10), 0, a.dp(10), 0)
                background = if (i == page) rounded(a, Theme.hover, 5) else hoverBg(a, 5)
                setOnClickListener { a.openSettings(i) }
                addView(Glyph(a, glyphs[i]).apply { layoutParams = lp(a.dp(18), a.dp(18)) })
                addView(label(a, n, 13f).apply { setPadding(a.dp(12), 0, 0, 0) })
            }
            side.addView(item, lp(MATCH, a.dp(38)).apply { bottomMargin = a.dp(2) })
        }
        root.addView(side, lp(a.dp(170), MATCH))

        // konten
        val body = vBox(a, 18)
        body.addView(label(a, names[page], 20f, bold = true).apply { setPadding(0, 0, 0, a.dp(12)) })
        when (page) {
            0 -> system(a, body)
            1 -> personalize(a, body)
            2 -> appsPage(a, body)
            4 -> lockPage(a, body)
            else -> about(a, body)
        }
        root.addView(vScroll(a, body), lp(0, MATCH, 1f))
        return root
    }

    private fun system(a: MainActivity, b: LinearLayout) {
        b.addView(settingRow(a, G.SUN, "Layar", "Kecerahan dan tampilan", null) {
            a.open(Panel.QS)
        })
        b.addView(settingRow(a, G.VOLUME, "Suara", "Volume dan perangkat keluaran", null) {
            a.startSafe(Intent(Settings.ACTION_SOUND_SETTINGS))
        })
        b.addView(settingRow(a, G.BATTERY, "Daya & baterai", "${(a.batteryLevel * 100).toInt()}% " +
            if (a.charging) "· sedang mengisi" else "· menggunakan baterai", null) {
            a.startSafe(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        })
        b.addView(settingRow(a, G.PC, "Penyimpanan", storageText(), null) {
            a.startSafe(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        })
        b.addView(settingRow(a, G.BELL, "Notifikasi", "Izinkan launcher membaca notifikasi", null) {
            a.startSafe(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        })
    }

    private fun personalize(a: MainActivity, b: LinearLayout) {
        // Skala UI
        val opts = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f)
        val selScale = opts.indices.minByOrNull { kotlin.math.abs(opts[it] - Theme.uiScale) } ?: 2
        val scard = vBox(a, 12).apply { background = rounded(a, Theme.card, 5, Theme.stroke) }
        scard.addView(label(a, "Skala UI", 13f))
        scard.addView(label(a, "Besarkan atau kecilkan seluruh tampilan (launcher dimuat ulang)", 11f, Theme.subText)
            .apply { setPadding(0, a.dp(2), 0, a.dp(8)) })
        scard.addView(segmented(a, listOf("85%", "100%", "115%", "130%", "150%"), selScale) { i -> a.setUiScale(opts[i]) })
        scard.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = a.dp(4) }
        b.addView(scard)
        // Tema
        b.addView(settingRow(a, G.NIGHT, "Mode warna", "Pilih mode terang atau gelap",
            segmented(a, listOf("Gelap", "Terang"), if (Theme.dark) 0 else 1) { i ->
                Theme.dark = i == 0; a.applyTheme(); a.openSettings(1, true)
            }))
        // Warna aksen
        val sw = hBox(a)
        Theme.accentList.forEach { hex ->
            val col = Color.parseColor(hex)
            val v = View(a).apply {
                background = rounded(a, col, 14, if (col == Theme.accent) Theme.text else 0, 2)
                setOnClickListener { Theme.accent = col; a.applyTheme(); a.openSettings(1, true) }
            }
            sw.addView(v, lp(a.dp(28), a.dp(28)).apply { leftMargin = a.dp(6) })
        }
        b.addView(settingRow(a, G.PALETTE, "Warna aksen", "Dipakai pada tombol dan indikator", sw))
        // Wallpaper
        val wcard = vBox(a, 12).apply { background = rounded(a, Theme.card, 5, Theme.stroke) }
        wcard.addView(label(a, "Latar belakang", 13f))
        wcard.addView(label(a, "Pilih wallpaper bawaan atau pakai fotomu", 11f, Theme.subText).apply { setPadding(0, a.dp(2), 0, a.dp(8)) })
        val wsc = HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false }
        val wrow = hBox(a)
        WallpaperView.thumbs.forEachIndexed { i, cols ->
            val v = View(a).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, cols).apply {
                    cornerRadius = a.dpf(6f)
                    if (Theme.wallpaper == i) setStroke(a.dp(2), Theme.text)
                }
                setOnClickListener { Theme.wallpaper = i; a.applyTheme(); a.openSettings(1, true) }
            }
            wrow.addView(v, lp(a.dp(56), a.dp(36)).apply { rightMargin = a.dp(8) })
        }
        val photo = FrameLayout(a).apply {
            background = rounded(a, Theme.hover, 6, if (Theme.wallpaper == WallpaperView.CUSTOM) Theme.text else Theme.stroke, 2)
            addView(Glyph(a, G.IMAGE), FrameLayout.LayoutParams(a.dp(18), a.dp(18), Gravity.CENTER))
            setOnClickListener { a.pickWallpaper() }
        }
        wrow.addView(photo, lp(a.dp(56), a.dp(36)))
        wsc.addView(wrow)
        wcard.addView(wsc)
        wcard.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = a.dp(4) }
        b.addView(wcard)
        b.addView(settingRow(a, G.SETTINGS, "Efek kaca (blur)", "Taskbar & panel tembus pandang. Matikan jika terasa berat",
            fluentSwitch(a, Theme.acrylic) { Theme.acrylic = it; a.applyTheme() }))
        // Taskbar
        b.addView(settingRow(a, G.TASKVIEW, "Perataan taskbar", "Ikon di tengah atau di kiri",
            segmented(a, listOf("Tengah", "Kiri"), if (Theme.centered) 0 else 1) { i ->
                Theme.centered = i == 0; a.applyTheme(); a.openSettings(1, true)
            }))
        b.addView(settingRow(a, G.FOLDER, "Ikon desktop", "Tampilkan ikon di desktop",
            fluentSwitch(a, Theme.showDesktopIcons) { Theme.showDesktopIcons = it; a.applyTheme() }))
        b.addView(settingRow(a, G.MAX, "Mode jendela aplikasi", "Buka aplikasi di jendela (butuh Freeform di Opsi Pengembang)",
            fluentSwitch(a, Theme.windowMode) { Theme.windowMode = it; Theme.save(a) }))
    }

    private fun lockPage(a: MainActivity, b: LinearLayout) {
        b.addView(settingRow(a, G.LOCK, "Kunci sekarang", "Tampilkan layar kunci", null) { a.open(Panel.NONE); a.lock() })
        b.addView(settingRow(a, G.LOCK, "Layar kunci saat launcher dibuka", "Minta buka kunci setiap launcher mulai dari awal",
            fluentSwitch(a, Theme.lockOnStart) { Theme.lockOnStart = it; Theme.save(a) }))
        b.addView(settingRow(a, G.LOCK, "Layar kunci saat layar mati", "Kunci otomatis ketika layar dimatikan",
            fluentSwitch(a, Theme.lockOnScreenOff) { Theme.lockOnScreenOff = it; Theme.save(a) }))
        b.addView(settingRow(a, G.USER, "PIN layar kunci", if (Theme.pin.isEmpty()) "Belum diatur (ketuk untuk membuat)" else "Aktif (ketuk untuk mengubah atau menghapus)", null) {
            pinDialog(a)
        })
        if (Theme.pin.isNotEmpty()) b.addView(settingRow(a, G.LOCK, "Lupa PIN?", "Hapus PIN dengan memverifikasi kunci layar Android", null) { a.forgotPin() })
        b.addView(settingRow(a, G.START, "Animasi boot", "Logo dan spinner saat launcher pertama kali dibuka",
            fluentSwitch(a, Theme.showBoot) { Theme.showBoot = it; Theme.save(a) }))
        b.addView(settingRow(a, G.VOLUME, "Suara sistem", "Nada startup, buka kunci, dan notifikasi",
            fluentSwitch(a, Theme.sounds) { Theme.sounds = it; Theme.save(a); if (it) Sounds.click() }))
        b.addView(settingRow(a, G.BELL, "Banner notifikasi", "Tampilkan notifikasi baru di pojok kanan atas (butuh akses notifikasi)",
            fluentSwitch(a, Theme.toasts) { Theme.toasts = it; Theme.save(a) }))
        b.addView(settingRow(a, G.BELL, "Mode Fokus", "Sembunyikan banner dan suara notifikasi",
            fluentSwitch(a, Theme.focus) { Theme.focus = it; Theme.save(a) }))
        b.addView(settingRow(a, G.GLOBE, "Kota untuk cuaca", Info.city(a), null) {
            val et = android.widget.EditText(a).apply { setText(Info.city(a)); setSingleLine(); setSelection(text.length) }
            android.app.AlertDialog.Builder(a).setTitle("Kota untuk cuaca").setView(et)
                .setPositiveButton("OK") { _, _ -> Info.setCity(a, et.text.toString()); a.taskbar.refresh(); a.openSettings(4, true) }
                .setNegativeButton("Batal", null).show()
        })
    }

    private fun pinDialog(a: MainActivity) {
        val et = android.widget.EditText(a).apply {
            hint = "4-8 angka, kosongkan untuk menghapus"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setSingleLine()
        }
        android.app.AlertDialog.Builder(a).setTitle("PIN layar kunci").setView(et)
            .setPositiveButton("Simpan") { _, _ ->
                val t = et.text.toString()
                if (t.isEmpty() || t.length in 4..8) { Pin.set(a, t); a.openSettings(4, true) }
                else android.widget.Toast.makeText(a, "PIN harus 4-8 angka", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun appsPage(a: MainActivity, b: LinearLayout) {
        b.addView(settingRow(a, G.APPS, "Aplikasi terpasang", "${AppRepo.all.size} aplikasi", null) {
            a.startSafe(Intent(Settings.ACTION_APPLICATION_SETTINGS))
        })
        b.addView(settingRow(a, G.START, "Jadikan launcher default", "Pilih Win11 Launcher sebagai layar Home", null) {
            a.startSafe(Intent(Settings.ACTION_HOME_SETTINGS))
        })
        b.addView(settingRow(a, G.TRASH, "Reset pin", "Kembalikan pin Start & taskbar ke bawaan", null) {
            a.getSharedPreferences("win11apps", Context.MODE_PRIVATE).edit().remove("pinned").remove("tbpins").apply()
            a.applyTheme()
        })
    }

    private fun about(a: MainActivity, b: LinearLayout) {
        b.addView(settingRow(a, G.PC, Build.MODEL, "${Build.MANUFACTURER} · Android ${Build.VERSION.RELEASE}", null))
        b.addView(settingRow(a, G.SETTINGS, "Win11 Launcher 1.3", "Versi 1.3 · Launcher bertema Windows 11 untuk Android", null))
        b.addView(settingRow(a, G.PC, "Memori (RAM)", ramText(a), null))
        b.addView(settingRow(a, G.PC, "Penyimpanan", storageText(), null))
    }

    // ------------------------------------------------------------------ This PC
    fun thisPc(a: MainActivity): View {
        val root = vBox(a, 18)
        root.setBackgroundColor(if (Theme.dark) 0xFF272727.toInt() else 0xFFF9F9F9.toInt())
        root.addView(label(a, "Perangkat dan drive", 14f, bold = true).apply { setPadding(0, 0, 0, a.dp(10)) })

        val st = StatFs(Environment.getDataDirectory().path)
        val total = st.totalBytes.toDouble(); val free = st.availableBytes.toDouble()
        root.addView(drive(a, "Penyimpanan internal (C:)", "${gb(free)} GB bebas dari ${gb(total)} GB", ((total - free) / total).toFloat(), G.PC) {
            a.openFiles()
        })

        val mi = ActivityManager.MemoryInfo()
        (a.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val rt = mi.totalMem.toDouble(); val rf = mi.availMem.toDouble()
        root.addView(drive(a, "Memori (RAM)", "${gb(rf)} GB tersedia dari ${gb(rt)} GB", ((rt - rf) / rt).toFloat(), G.SETTINGS, null))

        root.addView(drive(a, "Baterai", "${(a.batteryLevel * 100).toInt()}%" + if (a.charging) " · mengisi" else "",
            1f - a.batteryLevel, G.BATTERY, null))
        return vScroll(a, root).let { sv -> FrameLayout(a).apply { addView(sv) } }
    }

    private fun drive(a: MainActivity, name: String, sub: String, used: Float, g: G, click: (() -> Unit)?): View {
        val box = hBox(a).apply {
            setPadding(a.dp(12), a.dp(12), a.dp(12), a.dp(12))
            background = rounded(a, Theme.card, 6, Theme.stroke)
            if (click != null) setOnClickListener { click() }
        }
        box.addView(Glyph(a, g).apply { layoutParams = lp(a.dp(36), a.dp(36)) })
        val col = vBox(a)
        col.addView(label(a, name, 13f))
        val bar = FrameLayout(a)
        bar.background = rounded(a, if (Theme.dark) 0x33FFFFFF else 0x22000000, 3)
        val fillColor = if (used > 0.9f) 0xFFC42B1C.toInt() else Theme.accent
        val fill = View(a).apply { background = rounded(a, fillColor, 3) }
        bar.addView(fill, FrameLayout.LayoutParams(0, MATCH))
        bar.post { fill.layoutParams = FrameLayout.LayoutParams((bar.width * used.coerceIn(0f, 1f)).toInt(), MATCH) }
        col.addView(bar, lp(MATCH, a.dp(6)).apply { topMargin = a.dp(6); bottomMargin = a.dp(6) })
        col.addView(label(a, sub, 11f, Theme.subText))
        box.addView(col, lp(0, WRAP, 1f).apply { leftMargin = a.dp(12) })
        return box.also { it.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = a.dp(8) } }
    }

    // ------------------------------------------------------------------ Recycle Bin
    fun recycle(a: MainActivity): View {
        val root = FrameLayout(a)
        root.setBackgroundColor(if (Theme.dark) 0xFF272727.toInt() else 0xFFF9F9F9.toInt())
        val col = vBox(a).apply { gravity = Gravity.CENTER }
        col.addView(Glyph(a, G.TRASH).apply { layoutParams = lp(a.dp(56), a.dp(56)) })
        col.addView(label(a, "Recycle Bin kosong", 15f, bold = true).apply { setPadding(0, a.dp(12), 0, a.dp(4)) })
        col.addView(label(a, "Item yang dihapus akan muncul di sini", 12f, Theme.subText))
        root.addView(col, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        return root
    }

    // ------------------------------------------------------------------ util
    fun gb(b: Double) = String.format("%.1f", b / (1024.0 * 1024.0 * 1024.0))
    fun storageText(): String {
        val st = StatFs(Environment.getDataDirectory().path)
        return "${gb(st.availableBytes.toDouble())} GB bebas dari ${gb(st.totalBytes.toDouble())} GB"
    }
    fun ramText(c: Context): String {
        val mi = ActivityManager.MemoryInfo()
        (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return "${gb(mi.availMem.toDouble())} GB tersedia dari ${gb(mi.totalMem.toDouble())} GB"
    }
}
