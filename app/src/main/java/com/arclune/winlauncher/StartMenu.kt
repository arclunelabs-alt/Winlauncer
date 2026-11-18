package com.arclune.winlauncher

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/** Start menu Windows 11: search, Pinned, All apps, Recommended, profil + tombol power. */
class StartMenu(private val a: MainActivity) : LinearLayout(a) {
    private enum class Mode { HOME, ALL, SEARCH }
    private var mode = Mode.HOME
    private val search = EditText(a)
    private val body = FrameLayout(a)
    private var query = ""

    init {
        orientation = VERTICAL
        elevation = a.dpf(24f)
        buildChrome()
    }

    fun panelWidth(): Int = minOf(a.dp(640), (a.resources.displayMetrics.widthPixels * 0.62f).toInt())
    fun panelHeight(): Int = minOf(a.dp(520), a.resources.displayMetrics.heightPixels - a.dp(48 + 16))

    private fun buildChrome() {
        removeAllViews()
        background = acrylicBg(this, 8)

        // kotak pencarian
        val sbox = hBox(a).apply {
            background = rounded(a, Theme.card, 18, Theme.stroke)
            setPadding(a.dp(12), 0, a.dp(12), 0)
        }
        sbox.addView(Glyph(a, G.SEARCH).apply { layoutParams = lp(a.dp(16), a.dp(16)) })
        search.apply {
            hint = "Ketik di sini untuk mencari"
            setHintTextColor(Theme.subText); setTextColor(Theme.text); textSize = 13f
            background = null; maxLines = 1; inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setPadding(a.dp(10), 0, 0, 0)
            (parent as? android.view.ViewGroup)?.removeView(this)
        }
        if (search.tag == null) {
            search.tag = true
            search.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString()?.trim() ?: ""
                    if (query.isNotEmpty()) { mode = Mode.SEARCH; render() }
                    else if (mode == Mode.SEARCH) { mode = Mode.HOME; render() }
                }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
            search.setOnEditorActionListener { _, _, _ ->
                AppRepo.all.firstOrNull { it.label.contains(query, true) }?.let { a.launchApp(it) }; true
            }
        }
        sbox.addView(search, lp(0, MATCH, 1f))
        addView(sbox, lp(MATCH, a.dp(36)).apply { setMargins(a.dp(24), a.dp(20), a.dp(24), a.dp(8)) })

        addView(body, lp(MATCH, 0, 1f))

        // footer
        val foot = hBox(a).apply {
            setBackgroundColor(if (Theme.dark) 0x33000000 else 0x14000000)
            setPadding(a.dp(28), 0, a.dp(20), 0)
        }
        val user = hBox(a).apply {
            pressable(5); setPadding(a.dp(8), 0, a.dp(12), 0)
            addView(Glyph(a, G.USER).apply { tint = Theme.accent; layoutParams = lp(a.dp(26), a.dp(26)) })
            addView(label(a, "Pengguna", 12f).apply { setPadding(a.dp(10), 0, 0, 0) })
            setOnClickListener { a.openSettings(3) ; a.open(Panel.NONE) }
        }
        foot.addView(user, lp(WRAP, a.dp(40)))
        foot.addView(View(a), lp(0, 1, 1f))
        val power = FrameLayout(a).apply {
            pressable(5)
            addView(Glyph(a, G.POWER), FrameLayout.LayoutParams(a.dp(18), a.dp(18), Gravity.CENTER))
            setOnClickListener { a.showPowerMenu(this) }
        }
        foot.addView(power, lp(a.dp(40), a.dp(40)))
        addView(foot, lp(MATCH, a.dp(56)))
    }

    /** Dipanggil setiap Start dibuka. */
    fun show(m: Int) {
        buildChrome()
        search.setText("")
        query = ""
        mode = Mode.HOME
        render()
        if (m == 1) { // fokus pencarian
            search.requestFocus()
            search.post {
                (a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(search, 0)
            }
        }
    }

    fun hideKeyboard() {
        (a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(windowToken, 0)
        search.clearFocus()
    }

    private fun render() {
        if (mode != Mode.SEARCH) Search.cancel()
        body.removeAllViews()
        when (mode) {
            Mode.HOME -> body.addView(home())
            Mode.ALL -> body.addView(allApps())
            Mode.SEARCH -> body.addView(results())
        }
    }

    // ------------------------------------------------------------------ HOME
    private fun home(): View {
        val col = vBox(a).apply { setPadding(a.dp(28), a.dp(8), a.dp(28), a.dp(12)) }

        val head = hBox(a)
        head.addView(label(a, "Disematkan", 13f, bold = true), lp(0, WRAP, 1f))
        head.addView(hBox(a).apply {
            pressable(5); setPadding(a.dp(10), a.dp(5), a.dp(8), a.dp(5))
            background = rounded(a, Theme.card, 5, Theme.stroke)
            addView(label(a, "Semua aplikasi", 11f))
            addView(Glyph(a, G.NEXT).apply { layoutParams = lp(a.dp(14), a.dp(14)).apply { leftMargin = a.dp(2) } })
            setOnClickListener { mode = Mode.ALL; render() }
        })
        col.addView(head, lp(MATCH, a.dp(30)))
        col.addView(space(a, 6))

        // grid disematkan: 6 kolom (aplikasi bawaan dulu, lalu aplikasi pilihanmu)
        val cols = 6
        val builtinTiles = listOf("files", "browser", "photos", "media", "paint", "taskmgr", "calc", "notes", "clock", "settings")
            .mapNotNull { Builtins.find(it) }.map { builtinTile(it) }
        val tiles = builtinTiles + AppRepo.pinEntries().map { if (it.folder != null) folderTile(it) else tile(it.app!!, it) }
        tiles.chunked(cols).forEach { rowViews ->
            val row = LinearLayout(a)
            rowViews.forEach { row.addView(it, lp(0, WRAP, 1f)) }
            repeat(cols - rowViews.size) { row.addView(View(a), lp(0, 1, 1f)) }
            col.addView(row)
        }
        if (!AppRepo.loaded) col.addView(label(a, "Memuat aplikasi...", 12f, Theme.subText).apply { setPadding(0, a.dp(12), 0, a.dp(12)) })

        // Recommended
        col.addView(space(a, 10))
        col.addView(label(a, "Rekomendasi", 13f, bold = true).apply { setPadding(0, 0, 0, a.dp(6)) })
        val rec = AppRepo.recents()
        if (rec.isEmpty()) {
            col.addView(label(a, "Aplikasi yang baru dibuka akan muncul di sini", 12f, Theme.subText))
        } else {
            rec.chunked(2).forEach { pair ->
                val row = LinearLayout(a)
                pair.forEach { row.addView(recRow(it), lp(0, a.dp(44), 1f)) }
                if (pair.size == 1) row.addView(View(a), lp(0, 1, 1f))
                col.addView(row)
            }
        }
        return ScrollView(a).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; addView(col) }
    }

    private fun builtinTile(b: BuiltIn): View {
        val v = vBox(a).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(a.dp(2), a.dp(8), a.dp(2), a.dp(6))
            pressable(5, scale = true)
            setOnClickListener { b.open(a) }
        }
        val box = FrameLayout(a).apply { background = rounded(a, Theme.card, 9, Theme.stroke) }
        box.addView(Glyph(a, b.glyph), FrameLayout.LayoutParams(a.dp(22), a.dp(22), Gravity.CENTER))
        v.addView(box, lp(a.dp(34), a.dp(34)))
        v.addView(label(a, b.name, 11f).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
            setPadding(a.dp(2), a.dp(6), a.dp(2), 0)
        }, lp(MATCH, WRAP))
        return v
    }

    private fun tile(app: AppInfo, entry: PinEntry? = null): View {
        val v = vBox(a).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(a.dp(2), a.dp(8), a.dp(2), a.dp(6))
            pressable(5, scale = true)
            setOnClickListener { a.launchApp(app) }
            setOnLongClickListener { a.showAppMenu(this, app, false, moveItems(entry) + folderItem(app, this)); true }
        }
        v.addView(ImageView(a).apply { setImageDrawable(app.icon) }, lp(a.dp(34), a.dp(34)))
        v.addView(label(a, app.label, 11f).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
            setPadding(a.dp(2), a.dp(6), a.dp(2), 0)
        }, lp(MATCH, WRAP))
        return v
    }

    private fun recRow(app: AppInfo): View = hBox(a).apply {
        pressable(5); setPadding(a.dp(8), 0, a.dp(8), 0)
        setOnClickListener { a.launchApp(app) }
        setOnLongClickListener { a.showAppMenu(this, app, false); true }
        addView(ImageView(a).apply { setImageDrawable(app.icon) }, lp(a.dp(28), a.dp(28)))
        val c = vBox(a)
        c.addView(label(a, app.label, 12f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        c.addView(label(a, "Baru saja dibuka", 10f, Theme.subText))
        addView(c, lp(0, WRAP, 1f).apply { leftMargin = a.dp(10) })
    }

    // ------------------------------------------------------------------ folder & urutan
    private fun moveItems(e: PinEntry?): List<MenuItem> {
        if (e == null) return emptyList()
        return listOf(
            MenuItem("Geser ke kiri", G.BACK) { AppRepo.moveEntry(e.idx, -1); render() },
            MenuItem("Geser ke kanan", G.NEXT) { AppRepo.moveEntry(e.idx, 1); render() }
        )
    }

    private fun folderItem(app: AppInfo, anchor: View): List<MenuItem> =
        listOf(MenuItem("Sematkan ke folder...", G.FOLDER) { pickFolder(app, anchor) })

    private fun pickFolder(app: AppInfo, anchor: View) {
        val loc = IntArray(2); anchor.getLocationOnScreen(loc)
        val items = ArrayList<MenuItem>()
        for (n in AppRepo.folderNames()) items.add(MenuItem(n, G.FOLDER) { AppRepo.addToFolder(app.pkg, n); render() })
        if (items.isNotEmpty()) items.add(MenuItem(""))
        items.add(MenuItem("Folder baru", G.PLUS) { AppRepo.newFolder(app.pkg); render() })
        showMenu(a, loc[0] + anchor.width / 2, loc[1] + anchor.height / 2, items)
    }

    private fun folderTile(e: PinEntry): View {
        val name = e.folder ?: ""
        val v = vBox(a).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(a.dp(2), a.dp(8), a.dp(2), a.dp(6))
            pressable(5, scale = true)
            setOnClickListener { openFolder(name) }
            setOnLongClickListener { folderMenu(this, e); true }
        }
        val box = FrameLayout(a).apply {
            background = rounded(a, Theme.card, 9, Theme.stroke)
            setPadding(a.dp(3), a.dp(3), a.dp(3), a.dp(3))
        }
        val grid = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        for (pair in e.apps.take(4).chunked(2)) {
            val row = LinearLayout(a)
            for (ap in pair) row.addView(ImageView(a).apply { setImageDrawable(ap.icon) },
                lp(0, MATCH, 1f).apply { setMargins(a.dp(1), a.dp(1), a.dp(1), a.dp(1)) })
            if (pair.size == 1) row.addView(View(a), lp(0, 1, 1f))
            grid.addView(row, lp(MATCH, 0, 1f))
        }
        box.addView(grid, FrameLayout.LayoutParams(-1, -1))
        v.addView(box, lp(a.dp(34), a.dp(34)))
        v.addView(label(a, name, 11f).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
            setPadding(a.dp(2), a.dp(6), a.dp(2), 0)
        }, lp(MATCH, WRAP))
        return v
    }

    private fun folderMenu(v: View, e: PinEntry) {
        val loc = IntArray(2); v.getLocationOnScreen(loc)
        val name = e.folder ?: return
        showMenu(a, loc[0] + v.width / 2, loc[1] + v.height / 2, listOf(
            MenuItem("Buka folder", G.NEXT) { openFolder(name) },
            MenuItem("Ganti nama", G.NOTE) { renameDialog(name) },
            MenuItem("Bubarkan folder", G.CLOSE) { AppRepo.dissolveFolder(name); render() },
            MenuItem("")
        ) + moveItems(e))
    }

    private fun renameDialog(old: String) {
        val et = EditText(a).apply { setText(old); setSingleLine(); setSelection(old.length) }
        android.app.AlertDialog.Builder(a).setTitle("Ganti nama folder").setView(et)
            .setPositiveButton("OK") { _, _ -> AppRepo.renameFolder(old, et.text.toString()); render() }
            .setNegativeButton("Batal", null).show()
    }

    private fun openFolder(name: String) {
        val apps = AppRepo.pinEntries().firstOrNull { it.folder == name }?.apps ?: return
        val ov = FrameLayout(a).apply {
            setBackgroundColor(0x66000000); isClickable = true
            setOnClickListener { body.removeView(this) }
        }
        val card = vBox(a, 16).apply { background = rounded(a, Theme.surfaceSolid, 10, Theme.stroke); isClickable = true }
        card.addView(label(a, name, 14f, bold = true).apply { setPadding(a.dp(4), 0, 0, a.dp(8)) })
        for (rowApps in apps.chunked(4)) {
            val row = LinearLayout(a)
            for (ap in rowApps) row.addView(folderApp(ap, name), lp(a.dp(76), WRAP))
            card.addView(row)
        }
        ov.addView(card, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        body.addView(ov, FrameLayout.LayoutParams(-1, -1))
    }

    private fun folderApp(app: AppInfo, folder: String): View {
        val v = vBox(a).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(a.dp(2), a.dp(8), a.dp(2), a.dp(6))
            pressable(5, scale = true)
            setOnClickListener { a.launchApp(app) }
            setOnLongClickListener {
                a.showAppMenu(this, app, false, listOf(MenuItem("Keluarkan dari folder", G.BACK) {
                    AppRepo.removeFromFolder(app.pkg, folder); render()
                })); true
            }
        }
        v.addView(ImageView(a).apply { setImageDrawable(app.icon) }, lp(a.dp(34), a.dp(34)))
        v.addView(label(a, app.label, 11f).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
            setPadding(a.dp(2), a.dp(6), a.dp(2), 0)
        }, lp(MATCH, WRAP))
        return v
    }

    // ------------------------------------------------------------------ ALL APPS (virtual list)
    private fun allApps(): View {
        val wrap = vBox(a).apply { setPadding(a.dp(28), a.dp(8), a.dp(28), a.dp(4)) }
        val head = hBox(a)
        head.addView(hBox(a).apply {
            pressable(5); setPadding(a.dp(6), 0, a.dp(10), 0)
            addView(Glyph(a, G.BACK).apply { layoutParams = lp(a.dp(16), a.dp(16)) })
            addView(label(a, "Kembali", 12f).apply { setPadding(a.dp(4), 0, 0, 0) })
            setOnClickListener { mode = Mode.HOME; render() }
        }, lp(WRAP, a.dp(30)))
        head.addView(label(a, "Semua aplikasi", 13f, bold = true).apply { gravity = Gravity.END }, lp(0, WRAP, 1f))
        wrap.addView(head, lp(MATCH, a.dp(30)))

        val rows = ArrayList<Any>()
        var last = ""
        AppRepo.all.forEach { app ->
            val first = app.label.firstOrNull()?.uppercaseChar()?.let { if (it.isLetter()) it.toString() else "#" } ?: "#"
            if (first != last) { last = first; rows.add(first) }
            rows.add(app)
        }
        val lv = ListView(a).apply {
            divider = null; dividerHeight = 0
            isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
            adapter = AllAdapter(rows)
        }
        wrap.addView(lv, lp(MATCH, 0, 1f))
        return wrap
    }

    private inner class AllAdapter(private val rows: List<Any>) : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = if (rows[position] is String) 0 else 1

        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup?): View {
            val item = rows[position]
            if (item is String) {
                val tv = (convertView as? TextView) ?: label(a, "", 13f, Theme.accent, true).apply {
                    setPadding(a.dp(10), a.dp(10), 0, a.dp(4))
                    layoutParams = AbsListView.LayoutParams(MATCH, a.dp(40))
                }
                tv.text = item
                return tv
            }
            val app = item as AppInfo
            val row = (convertView as? LinearLayout) ?: hBox(a).apply {
                pressable(5); setPadding(a.dp(10), 0, a.dp(10), 0)
                addView(ImageView(a), lp(a.dp(28), a.dp(28)))
                addView(label(a, "", 12f).apply { setPadding(a.dp(12), 0, 0, 0) })
                layoutParams = AbsListView.LayoutParams(MATCH, a.dp(42))
            }
            (row.getChildAt(0) as ImageView).setImageDrawable(app.icon)
            (row.getChildAt(1) as TextView).text = app.label
            row.setOnClickListener { a.launchApp(app) }
            row.setOnLongClickListener { a.showAppMenu(row, app, false); true }
            return row
        }
    }

    // ------------------------------------------------------------------ SEARCH
    private fun hitRow(h: SearchHit): View = hBox(a).apply {
        pressable(5); setPadding(a.dp(10), 0, a.dp(10), 0)
        setOnClickListener { h.run() }
        addView(Glyph(a, h.glyph), lp(a.dp(22), a.dp(22)))
        val c = vBox(a)
        c.addView(label(a, h.title, 13f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        c.addView(label(a, h.sub, 10f, Theme.subText).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        addView(c, lp(0, WRAP, 1f).apply { leftMargin = a.dp(14) })
    }

    private fun section(col: LinearLayout, t: String) {
        col.addView(label(a, t, 12f, Theme.subText, true).apply { setPadding(a.dp(4), a.dp(10), 0, a.dp(4)) })
    }

    private fun results(): View {
        val col = vBox(a).apply { setPadding(a.dp(28), a.dp(8), a.dp(28), a.dp(12)) }
        val q = query.lowercase(Locale.getDefault())
        val hits = AppRepo.all.filter { it.label.lowercase(Locale.getDefault()).contains(q) }
        val bhits = Builtins.all.filter { it.name.lowercase(Locale.getDefault()).contains(q) }
        val shits = Search.settings(a, query)
        if (hits.isEmpty() && bhits.isEmpty() && shits.isEmpty()) {
            col.addView(label(a, "Mencari \"$query\"...", 13f, bold = true).apply { setPadding(0, 0, 0, a.dp(6)) })
        } else if (hits.isNotEmpty() || bhits.isNotEmpty()) {
            col.addView(label(a, "Aplikasi", 13f, bold = true).apply { setPadding(0, 0, 0, a.dp(6)) })
        }
        bhits.forEach { b ->
            col.addView(hBox(a).apply {
                pressable(5); setPadding(a.dp(10), 0, a.dp(10), 0)
                setOnClickListener { b.open(a) }
                addView(Glyph(a, b.glyph), lp(a.dp(24), a.dp(24)))
                val c = vBox(a)
                c.addView(label(a, b.name, 13f))
                c.addView(label(a, "Aplikasi bawaan", 10f, Theme.subText))
                addView(c, lp(0, WRAP, 1f).apply { leftMargin = a.dp(14) })
            }, lp(MATCH, a.dp(48)))
        }
        hits.take(8).forEachIndexed { i, app ->
            col.addView(hBox(a).apply {
                pressable(5)
                if (i == 0) background = hoverBg(a, 5, Theme.hover)
                setPadding(a.dp(10), 0, a.dp(10), 0)
                setOnClickListener { a.launchApp(app) }
                setOnLongClickListener { a.showAppMenu(this, app, false); true }
                addView(ImageView(a).apply { setImageDrawable(app.icon) }, lp(a.dp(28), a.dp(28)))
                val c = vBox(a)
                c.addView(label(a, app.label, 13f))
                c.addView(label(a, "Aplikasi", 10f, Theme.subText))
                addView(c, lp(0, WRAP, 1f).apply { leftMargin = a.dp(12) })
            }, lp(MATCH, a.dp(48)))
        }

        if (shits.isNotEmpty()) {
            section(col, "Pengaturan")
            shits.forEach { col.addView(hitRow(it), lp(MATCH, a.dp(46))) }
        }

        if (!Perms.hasContacts(a)) {
            section(col, "Kontak")
            col.addView(hBox(a).apply {
                pressable(5); setPadding(a.dp(10), 0, a.dp(10), 0)
                setOnClickListener { Perms.requestContacts(a) }
                addView(Glyph(a, G.USER), lp(a.dp(22), a.dp(22)))
                addView(label(a, "Ketuk untuk mengizinkan pencarian kontak", 12f, Theme.accent).apply { setPadding(a.dp(14), 0, 0, 0) })
            }, lp(MATCH, a.dp(44)))
        } else {
            val ch = Search.contacts(a, query)
            if (ch.isNotEmpty()) {
                section(col, "Kontak")
                ch.forEach { col.addView(hitRow(it), lp(MATCH, a.dp(46))) }
            }
        }

        // file: dicari di thread latar dengan jeda 350 ms agar tidak berat saat mengetik
        val fileBox = vBox(a)
        col.addView(fileBox, lp(MATCH, WRAP))
        val q0 = query
        fileBox.postDelayed({
            if (query == q0 && mode == Mode.SEARCH) {
                Search.files(a, q0) { fh ->
                    if (query == q0 && mode == Mode.SEARCH && fileBox.isAttachedToWindow && fh.isNotEmpty()) {
                        section(fileBox, "File")
                        fh.forEach { fileBox.addView(hitRow(it), lp(MATCH, a.dp(46))) }
                    }
                }
            }
        }, 350)

        // pencarian web cadangan: buka di Browser mini
        col.addView(hBox(a).apply {
            pressable(5); setPadding(a.dp(10), 0, a.dp(10), 0)
            setOnClickListener {
                a.openBrowser("https://www.google.com/search?q=" + android.net.Uri.encode(query))
            }
            addView(Glyph(a, G.SEARCH), lp(a.dp(20), a.dp(20)))
            addView(label(a, "Cari \"$query\" di web", 12f).apply { setPadding(a.dp(14), 0, 0, 0) })
        }, lp(MATCH, a.dp(44)).apply { topMargin = a.dp(8) })
        return ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(col) }
    }
}
