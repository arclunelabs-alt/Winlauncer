package com.arclune.winlauncher

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import java.io.File
import java.util.Locale

/** Satu hasil pencarian (pengaturan / kontak / file). */
class SearchHit(val title: String, val sub: String, val glyph: G, val run: () -> Unit)

/** Pencarian lengkap: pengaturan, kontak, dan nama file. */
object Search {
    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var ticket = 0

    private class Entry(val name: String, val keys: String, val glyph: G, val open: (MainActivity) -> Unit)

    private fun sys(action: String): (MainActivity) -> Unit = { it.startSafe(Intent(action)) }

    private val entries = listOf(
        Entry("Wi-Fi", "wifi internet jaringan", G.WIFI, sys(Settings.ACTION_WIFI_SETTINGS)),
        Entry("Bluetooth", "bluetooth perangkat", G.BT, sys(Settings.ACTION_BLUETOOTH_SETTINGS)),
        Entry("Mode pesawat", "pesawat airplane", G.PLANE, sys(Settings.ACTION_AIRPLANE_MODE_SETTINGS)),
        Entry("Layar & kecerahan", "layar display brightness terang", G.SUN, sys(Settings.ACTION_DISPLAY_SETTINGS)),
        Entry("Suara & volume", "suara volume sound nada", G.VOLUME, sys(Settings.ACTION_SOUND_SETTINGS)),
        Entry("Penghemat baterai", "baterai battery hemat daya", G.SAVER, sys(Settings.ACTION_BATTERY_SAVER_SETTINGS)),
        Entry("Penyimpanan", "storage memori ruang", G.PC, sys(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
        Entry("Aplikasi terpasang", "aplikasi apps copot", G.APPS, sys(Settings.ACTION_APPLICATION_SETTINGS)),
        Entry("Aksesibilitas", "aksesibilitas accessibility", G.ACCESS, sys(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
        Entry("Lokasi", "lokasi gps location", G.GLOBE, sys(Settings.ACTION_LOCATION_SOURCE_SETTINGS)),
        Entry("Tanggal & waktu", "tanggal waktu jam date time", G.CLOCK, sys(Settings.ACTION_DATE_SETTINGS)),
        Entry("Bahasa", "bahasa language", G.GLOBE, sys(Settings.ACTION_LOCALE_SETTINGS)),
        Entry("Keamanan", "keamanan security kunci", G.LOCK, sys(Settings.ACTION_SECURITY_SETTINGS)),
        Entry("Akses notifikasi", "notifikasi notification", G.BELL, sys("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")),
        Entry("Pengaturan Android", "pengaturan settings sistem", G.SETTINGS, sys(Settings.ACTION_SETTINGS)),
        Entry("Personalisasi launcher", "personalisasi tema warna aksen wallpaper latar", G.PALETTE) { it.openSettings(1) },
        Entry("Skala UI", "skala ukuran besar kecil scale", G.PALETTE) { it.openSettings(1) },
        Entry("Layar kunci & PIN", "kunci lock pin boot suara", G.LOCK) { it.openSettings(4) },
        Entry("Tentang perangkat", "tentang about versi", G.PC) { it.openSettings(3) }
    )

    fun settings(a: MainActivity, q: String): List<SearchHit> {
        val low = q.lowercase(Locale.getDefault())
        return entries
            .filter { it.name.lowercase(Locale.getDefault()).contains(low) || it.keys.contains(low) }
            .take(5)
            .map { e -> SearchHit(e.name, "Pengaturan", e.glyph) { e.open(a); a.open(Panel.NONE) } }
    }

    fun contacts(a: MainActivity, q: String): List<SearchHit> {
        if (!Perms.hasContacts(a)) return emptyList()
        val out = ArrayList<SearchHit>()
        try {
            val p = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            val n = ContactsContract.CommonDataKinds.Phone.NUMBER
            a.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(p, n),
                "$p LIKE ?", arrayOf("%$q%"), "$p ASC")?.use { c ->
                val seen = HashSet<String>()
                while (c.moveToNext() && out.size < 6) {
                    val name = c.getString(0) ?: continue
                    val num = c.getString(1) ?: ""
                    if (!seen.add(name + num)) continue
                    out.add(SearchHit(name, num, G.USER) {
                        a.startSafe(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num))))
                    })
                }
            }
        } catch (e: Exception) { }
        return out
    }

    /** Cari nama file di penyimpanan (thread latar). Hasil lama dibatalkan bila ada pencarian baru. */
    fun files(a: MainActivity, q: String, cb: (List<SearchHit>) -> Unit) {
        if (!Perms.hasFiles(a)) { cb(emptyList()); return }
        val mine = ++ticket
        val low = q.lowercase(Locale.getDefault())
        Thread {
            val found = ArrayList<File>()
            fun walk(d: File, depth: Int) {
                if (mine != ticket || found.size >= 12 || depth > 4) return
                val list = d.listFiles() ?: return
                for (f in list) {
                    if (mine != ticket || found.size >= 12) return
                    if (f.name.startsWith(".")) continue
                    if (f.name.lowercase(Locale.getDefault()).contains(low)) found.add(f)
                    if (f.isDirectory && f.name != "Android") walk(f, depth + 1)
                }
            }
            try { walk(Environment.getExternalStorageDirectory(), 0) } catch (e: Throwable) { }
            if (mine != ticket) return@Thread
            val hits = found.map { f ->
                SearchHit(f.name, f.parentFile?.path?.removePrefix(Environment.getExternalStorageDirectory().path)?.ifEmpty { "/" } ?: "",
                    if (f.isDirectory) G.FOLDER else G.FILE) {
                    if (f.isDirectory) a.openExplorer()
                    else a.startSafe(Intent(Intent.ACTION_VIEW).setDataAndType(FileServe.uri(f), FileServe.mime(f))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
            ui.post { if (mine == ticket) cb(hits) }
        }.start()
    }

    fun cancel() { ticket++ }
}
