package com.arclune.winlauncher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class ClipItem(val text: String, var pinned: Boolean)

/**
 * Riwayat clipboard ala Win+V.
 * Catatan: Android 10+ hanya mengizinkan membaca clipboard saat aplikasi aktif di layar,
 * jadi salinan baru ditangkap setiap kali kamu kembali ke launcher.
 */
object ClipStore {
    val items = ArrayList<ClipItem>()
    private var loaded = false

    private fun prefs(c: Context) = c.getSharedPreferences("win11", Context.MODE_PRIVATE)

    fun ensure(c: Context) {
        if (loaded) return
        loaded = true
        try {
            val arr = JSONArray(prefs(c).getString("clips", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(ClipItem(o.getString("t"), o.optBoolean("p", false)))
            }
        } catch (e: Exception) { }
    }

    fun save(c: Context) {
        val arr = JSONArray()
        for (it in items) arr.put(JSONObject().put("t", it.text).put("p", it.pinned))
        prefs(c).edit().putString("clips", arr.toString()).apply()
    }

    fun add(c: Context, text: String) {
        ensure(c)
        val t = text.trim()
        if (t.isEmpty() || t.length > 5000) return
        if (items.any { it.text == t && it.pinned }) return
        items.removeAll { it.text == t }
        items.add(0, ClipItem(t, false))
        while (items.count { !it.pinned } > 25) items.remove(items.last { !it.pinned })
        save(c)
    }

    /** Baca salinan terakhir dari sistem (hanya berhasil saat launcher sedang aktif). */
    fun capture(c: Context) {
        try {
            val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            val t = clip.getItemAt(0).coerceToText(c)?.toString() ?: return
            add(c, t)
        } catch (e: Throwable) { }
    }
}

/** Panel riwayat clipboard (Win+V). */
class ClipPanel(private val a: MainActivity) : LinearLayout(a) {
    init { orientation = VERTICAL; elevation = a.dpf(24f) }

    fun panelWidth() = minOf(a.dp(380), (a.resources.displayMetrics.widthPixels * 0.45f).toInt())
    fun panelHeight() = minOf(a.dp(400), a.resources.displayMetrics.heightPixels - a.dp(48 + 16))

    fun refresh() {
        ClipStore.capture(a)
        ClipStore.ensure(a)
        removeAllViews()
        background = acrylicBg(this, 8)
        setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(12))

        val head = hBox(a)
        head.addView(label(a, "Riwayat clipboard", 14f, bold = true), lp(0, WRAP, 1f))
        head.addView(label(a, "Hapus semua", 11f, Theme.accent).apply {
            setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6)); pressable(5)
            setOnClickListener {
                ClipStore.items.removeAll { !it.pinned }
                ClipStore.save(a); refresh()
            }
        })
        addView(head, lp(MATCH, a.dp(34)))

        val col = vBox(a)
        val list = ClipStore.items.sortedByDescending { it.pinned }
        if (list.isEmpty()) {
            col.addView(label(a, "Belum ada riwayat. Salin teks di aplikasi mana pun, lalu kembali ke launcher.", 12f, Theme.subText)
                .apply { setPadding(a.dp(4), a.dp(10), a.dp(4), 0) })
        }
        for (item in list) col.addView(card(item), lp(MATCH, WRAP).apply { bottomMargin = a.dp(6) })
        addView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(col) }, lp(MATCH, 0, 1f))
    }

    private fun card(item: ClipItem): View {
        val row = hBox(a).apply {
            setPadding(a.dp(12), a.dp(10), a.dp(8), a.dp(10))
            background = rounded(a, Theme.card, 6, if (item.pinned) Theme.accent else Theme.stroke)
            setOnClickListener {
                val cm = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("riwayat", item.text))
                Toast.makeText(a, "Tersalin", Toast.LENGTH_SHORT).show()
            }
        }
        row.addView(label(a, item.text, 12f).apply {
            maxLines = 3; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER_VERTICAL
        }, lp(0, WRAP, 1f))
        row.addView(label(a, if (item.pinned) "Lepas" else "Sematkan", 10f, Theme.accent).apply {
            setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6)); pressable(5)
            setOnClickListener { item.pinned = !item.pinned; ClipStore.save(a); refresh() }
        })
        row.addView(Glyph(a, G.CLOSE).apply {
            setOnClickListener { ClipStore.items.remove(item); ClipStore.save(a); refresh() }
        }, lp(a.dp(24), a.dp(24)))
        return row
    }
}
