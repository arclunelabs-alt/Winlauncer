package com.arclune.winlauncher

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Jendela File Explorer: sidebar akses cepat, navigasi folder, buka/ganti nama/hapus/folder baru. */
class FileExplorer(private val a: MainActivity) : LinearLayout(a) {
    private val root: File = Environment.getExternalStorageDirectory()
    private var dir: File = root
    private val history = ArrayList<File>()
    private val list = ListView(a)
    private val pathText = label(a, "", 12f)
    private val status = label(a, "", 11f, Theme.subText)
    private val banner = LinearLayout(a)
    private var entries: List<File> = emptyList()
    private val fmt = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())

    init {
        setBackgroundColor(if (Theme.dark) 0xFF272727.toInt() else 0xFFF9F9F9.toInt())
        orientation = HORIZONTAL
        addView(sidebar(), lp(a.dp(150), MATCH))
        addView(main(), lp(0, MATCH, 1f))
        go(root, false)
    }

    private fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else a.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + a.packageName))
            a.startSafe(i)
        } else a.requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 12)
    }

    // ------------------------------------------------------------------ sidebar
    private fun sidebar(): View {
        val col = vBox(a, 8)
        col.setBackgroundColor(if (Theme.dark) 0x14FFFFFF else 0x0F000000)
        fun item(name: String, g: G, f: File) {
            col.addView(hBox(a).apply {
                pressable(5); setPadding(a.dp(10), 0, a.dp(8), 0)
                setOnClickListener { go(f, true) }
                addView(Glyph(a, g).apply { layoutParams = lp(a.dp(16), a.dp(16)) })
                addView(label(a, name, 12f).apply { setPadding(a.dp(10), 0, 0, 0) })
            }, lp(MATCH, a.dp(34)))
        }
        col.addView(label(a, "Akses cepat", 11f, Theme.subText).apply { setPadding(a.dp(10), a.dp(4), 0, a.dp(6)) })
        item("Penyimpanan", G.PC, root)
        item("Unduhan", G.FOLDER, File(root, "Download"))
        item("Dokumen", G.FILE, File(root, "Documents"))
        item("Gambar", G.IMAGE, File(root, "Pictures"))
        item("Kamera", G.IMAGE, File(root, "DCIM"))
        item("Musik", G.MUSIC, File(root, "Music"))
        return vScroll(a, col)
    }

    // ------------------------------------------------------------------ area utama
    private fun main(): View {
        val col = vBox(a)
        // toolbar
        val bar = hBox(a).apply { setPadding(a.dp(8), a.dp(4), a.dp(8), a.dp(4)) }
        bar.addView(tool(G.BACK) { back() }, lp(a.dp(32), a.dp(32)))
        bar.addView(tool(G.UP) { up() }, lp(a.dp(32), a.dp(32)))
        bar.addView(pathText.apply {
            setPadding(a.dp(10), 0, a.dp(10), 0); maxLines = 1; ellipsize = TextUtils.TruncateAt.START
            background = rounded(a, Theme.card, 5, Theme.stroke); gravity = Gravity.CENTER_VERTICAL
        }, lp(0, a.dp(30), 1f).apply { setMargins(a.dp(6), 0, a.dp(6), 0) })
        bar.addView(tool(G.PLUS) { newFolder() }, lp(a.dp(32), a.dp(32)))
        col.addView(bar, lp(MATCH, WRAP))

        // banner izin
        banner.apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(8))
            background = rounded(a, Theme.accent, 5)
            addView(label(a, "Beri akses semua file agar isi penyimpanan tampil.", 11f, 0xFFFFFFFF.toInt()), lp(0, WRAP, 1f))
            addView(label(a, "Izinkan", 11f, 0xFFFFFFFF.toInt(), true).apply {
                setPadding(a.dp(10), a.dp(4), a.dp(10), a.dp(4)); background = rounded(a, 0x33FFFFFF, 4)
                setOnClickListener { requestAccess() }
            })
            visibility = View.GONE
        }
        col.addView(banner, lp(MATCH, WRAP).apply { setMargins(a.dp(8), 0, a.dp(8), a.dp(4)) })

        list.apply {
            divider = null; dividerHeight = 0; isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            adapter = Adapter()
            setOnItemClickListener { _, _, pos, _ -> open(entries[pos]) }
            setOnItemLongClickListener { v, _, pos, _ -> fileMenu(v, entries[pos]); true }
        }
        col.addView(list, lp(MATCH, 0, 1f))
        col.addView(status.apply { setPadding(a.dp(14), a.dp(4), 0, a.dp(4)) }, lp(MATCH, WRAP))
        return col
    }

    private fun tool(g: G, click: () -> Unit): View = android.widget.FrameLayout(a).apply {
        pressable(5)
        addView(Glyph(a, g), android.widget.FrameLayout.LayoutParams(a.dp(16), a.dp(16), Gravity.CENTER))
        setOnClickListener { click() }
    }

    // ------------------------------------------------------------------ navigasi
    private fun go(f: File, push: Boolean) {
        if (push) history.add(dir)
        dir = f
        refresh()
    }

    private fun back() { if (history.isNotEmpty()) { dir = history.removeAt(history.size - 1); refresh() } }
    private fun up() { val p = dir.parentFile; if (p != null && dir.path != root.path) go(p, true) }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); refresh() }

    fun refresh() {
        banner.visibility = if (hasAccess()) View.GONE else View.VISIBLE
        val files = try { dir.listFiles() } catch (e: Exception) { null }
        entries = (files?.toList() ?: emptyList())
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.getDefault()) }))
        (list.adapter as BaseAdapter).notifyDataSetChanged()
        val rel = dir.path.removePrefix(root.path).trim('/')
        pathText.text = "  Penyimpanan internal" + if (rel.isEmpty()) "" else " › " + rel.replace("/", " › ")
        status.text = if (files == null) "Folder tidak dapat dibaca" else "${entries.size} item"
    }

    private fun open(f: File) {
        if (f.isDirectory) { go(f, true); return }
        try {
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(FileServe.uri(f), FileServe.mime(f))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            a.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(a, "Tidak ada aplikasi untuk membuka file ini", Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------ menu & dialog
    private fun fileMenu(v: View, f: File) {
        val loc = IntArray(2); v.getLocationOnScreen(loc)
        showMenu(a, loc[0] + v.width / 2, loc[1] + v.height / 2, listOf(
            MenuItem("Buka", G.NEXT) { open(f) },
            MenuItem("Ganti nama", G.NOTE) { rename(f) },
            MenuItem("Info", G.FILE) {
                val sz = if (f.isDirectory) "Folder" else size(f.length())
                Toast.makeText(a, "${f.name}\n$sz · ${fmt.format(Date(f.lastModified()))}", Toast.LENGTH_LONG).show()
            },
            MenuItem(""),
            MenuItem("Hapus", G.TRASH) { confirmDelete(f) }
        ), centerOnX = true)
    }

    private fun input(title: String, initial: String, ok: (String) -> Unit) {
        val et = EditText(a).apply { setText(initial); setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT; selectAll() }
        AlertDialog.Builder(a).setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ -> val s = et.text.toString().trim(); if (s.isNotEmpty()) ok(s) }
            .setNegativeButton("Batal", null).show()
    }

    private fun newFolder() = input("Folder baru", "Folder baru") {
        if (!File(dir, it).mkdirs()) Toast.makeText(a, "Gagal membuat folder (izin?)", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun rename(f: File) = input("Ganti nama", f.name) {
        if (!f.renameTo(File(f.parentFile, it))) Toast.makeText(a, "Gagal mengganti nama", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun confirmDelete(f: File) {
        AlertDialog.Builder(a).setTitle("Hapus \"${f.name}\"?")
            .setMessage(if (f.isDirectory) "Folder beserta isinya akan dihapus permanen." else "File akan dihapus permanen.")
            .setPositiveButton("Hapus") { _, _ ->
                if (!f.deleteRecursively()) Toast.makeText(a, "Gagal menghapus", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton("Batal", null).show()
    }

    // ------------------------------------------------------------------ adapter
    private fun size(b: Long): String = when {
        b >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", b / (1024.0 * 1024 * 1024))
        b >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024))
        b >= 1L shl 10 -> String.format(Locale.US, "%d KB", b / 1024)
        else -> "$b B"
    }

    private fun glyphFor(f: File): G {
        if (f.isDirectory) return G.FOLDER
        return when (f.extension.lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> G.IMAGE
            "mp3", "m4a", "wav", "flac", "ogg", "aac", "mp4", "mkv", "mov", "webm", "3gp" -> G.MUSIC
            else -> G.FILE
        }
    }

    private class Holder(val icon: Glyph, val name: TextView, val meta: TextView)

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = entries.size
        override fun getItem(position: Int): Any = entries[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row: View
            val h: Holder
            if (convertView == null) {
                val ic = Glyph(a, G.FILE)
                val nm = label(a, "", 12.5f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
                val mt = label(a, "", 11f, Theme.subText).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
                val r = hBox(a).apply { setPadding(a.dp(12), 0, a.dp(12), 0); pressable(5) }
                r.addView(ic, lp(a.dp(20), a.dp(20)))
                r.addView(nm, lp(0, WRAP, 1f).apply { leftMargin = a.dp(12) })
                r.addView(mt, lp(WRAP, WRAP))
                r.layoutParams = ViewGroup.LayoutParams(MATCH, a.dp(38))
                h = Holder(ic, nm, mt); r.tag = h; row = r
            } else { row = convertView; h = convertView.tag as Holder }
            val f = entries[position]
            h.icon.g = glyphFor(f); h.icon.invalidate()
            h.name.text = f.name
            h.meta.text = (if (f.isDirectory) "" else size(f.length()) + "   ") + fmt.format(Date(f.lastModified()))
            return row
        }
    }
}
