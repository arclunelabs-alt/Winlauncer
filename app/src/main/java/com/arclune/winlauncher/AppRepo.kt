package com.arclune.winlauncher

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import java.util.Locale

data class AppInfo(val label: String, val pkg: String, val icon: Drawable, val intent: Intent)

/** Satu entri di grid Start: aplikasi tunggal, atau folder berisi beberapa aplikasi. [idx] = posisi token di daftar pin. */
class PinEntry(val idx: Int, val app: AppInfo?, val folder: String?, val apps: List<AppInfo>)

/** Daftar aplikasi, pin taskbar/start, dan riwayat "Recommended". */
object AppRepo {
    var all: List<AppInfo> = emptyList()
    private val prefs get() = ctx.getSharedPreferences("win11apps", Context.MODE_PRIVATE)
    private lateinit var ctx: Context

    @Volatile var loaded = false

    /** Muat daftar aplikasi di thread latar (ikon dirender sekali ke bitmap seragam). */
    fun loadAsync(c: Context, done: () -> Unit) {
        ctx = c.applicationContext
        Thread {
            val list = scan(ctx)
            Handler(Looper.getMainLooper()).post { all = list; loaded = true; done() }
        }.start()
    }

    private fun scan(c: Context): List<AppInfo> {
        val pm = c.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val size = c.dp(64)
        return pm.queryIntentActivities(q, 0)
            .filter { it.activityInfo.packageName != c.packageName }
            .map {
                val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(it.activityInfo.packageName, it.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val icon = try { squircle(c, it.loadIcon(pm), size) } catch (e: Throwable) { it.loadIcon(pm) }
                AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName, icon, i)
            }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    /** Ikon seragam bergaya Windows 11: kotak membulat. Ikon adaptif diisi penuh, ikon lama dibiarkan apa adanya. */
    private fun squircle(c: Context, d: Drawable, px: Int): Drawable {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) {
            val r = px * 0.24f
            val clip = Path().apply { addRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), r, r, Path.Direction.CW) }
            cv.clipPath(clip)
            val full = (px * 108f / 78f).toInt()
            val off = (px - full) / 2
            val bg = d.background; val fg = d.foreground
            if (bg != null) { bg.setBounds(off, off, off + full, off + full); bg.draw(cv) }
            if (fg != null) { fg.setBounds(off, off, off + full, off + full); fg.draw(cv) }
        } else {
            val inset = (px * 0.04f).toInt()
            d.setBounds(inset, inset, px - inset, px - inset)
            d.draw(cv)
        }
        return BitmapDrawable(c.resources, bmp)
    }

    /** Muat sinkron (dipakai setelah paket berubah dari thread utama sudah cukup cepat lewat loadAsync). */
    fun load(c: Context) { ctx = c.applicationContext; all = scan(ctx); loaded = true }

    fun find(pkg: String) = all.firstOrNull { it.pkg == pkg }

    private fun getList(key: String): MutableList<String> =
        (prefs.getString(key, "") ?: "").split(",").filter { it.isNotBlank() }.toMutableList()

    private fun putList(key: String, l: List<String>) = prefs.edit().putString(key, l.joinToString(",")).apply()

    /** Pin Start: default ke 18 aplikasi pertama bila belum diatur. */
    private val favorites = listOf(
        "com.android.chrome", "com.google.android.apps.photos", "com.android.vending", "com.google.android.youtube",
        "com.whatsapp", "com.instagram.android", "com.google.android.apps.maps", "com.google.android.gm",
        "com.google.android.apps.messaging", "com.google.android.dialer", "com.android.camera", "com.sec.android.app.camera",
        "com.google.android.calendar", "com.spotify.music", "com.facebook.katana", "org.telegram.messenger"
    )

    private fun defaultPins(n: Int): List<String> {
        val fav = favorites.filter { f -> all.any { it.pkg == f } }
        return (fav + all.map { it.pkg }).distinct().take(n)
    }

    /** Pin Start: default 12 aplikasi populer yang terpasang. */
    fun pinned(): List<AppInfo> {
        if (all.isEmpty()) return emptyList()
        if (!prefs.contains("pinned")) putList("pinned", defaultPins(12))
        return getList("pinned").flatMap { if (isFolder(it)) folderPkgs(it) else listOf(it) }.mapNotNull { find(it) }
    }

    fun taskbarPins(): List<AppInfo> {
        if (all.isEmpty()) return emptyList()
        if (!prefs.contains("tbpins")) putList("tbpins", defaultPins(5))
        return getList("tbpins").mapNotNull { find(it) }
    }

    fun isPinned(pkg: String) = getList("pinned").any { it == pkg || (isFolder(it) && folderPkgs(it).contains(pkg)) }
    fun isTaskbarPinned(pkg: String) = getList("tbpins").contains(pkg)

    fun togglePin(pkg: String) {
        pinned() // pastikan terinisialisasi
        val l = getList("pinned")
        if (!l.remove(pkg)) {
            val fi = l.indexOfFirst { isFolder(it) && folderPkgs(it).contains(pkg) }
            if (fi >= 0) l[fi] = mkFolder(folderName(l[fi]), folderPkgs(l[fi]).filter { it != pkg }).takeIf { folderPkgs(it).isNotEmpty() } ?: "" 
            else l.add(pkg)
        }
        putList("pinned", l.filter { it.isNotBlank() })
    }

    // ------------------------------------------------------------------ folder di Start
    // Format token di daftar pin: "pkg" atau "f=Nama~pkg1;pkg2"
    private fun isFolder(t: String) = t.startsWith("f=")
    private fun folderName(t: String) = t.substring(2).substringBefore("~")
    private fun folderPkgs(t: String): List<String> = t.substringAfter("~", "").split(";").filter { it.isNotBlank() }
    private fun mkFolder(name: String, pk: List<String>) = "f=" + name + "~" + pk.joinToString(";")
    private fun cleanName(n: String) = n.replace(Regex("[,~;=]"), " ").trim().ifEmpty { "Folder" }

    fun pinEntries(): List<PinEntry> {
        if (all.isEmpty()) return emptyList()
        pinned() // pastikan terinisialisasi
        return getList("pinned").mapIndexedNotNull { i, t ->
            if (isFolder(t)) {
                val apps = folderPkgs(t).mapNotNull { find(it) }
                if (apps.isEmpty()) null else PinEntry(i, null, folderName(t), apps)
            } else find(t)?.let { PinEntry(i, it, null, emptyList()) }
        }
    }

    fun folderNames(): List<String> = getList("pinned").filter { isFolder(it) }.map { folderName(it) }

    /** Masukkan aplikasi ke folder bernama [folder]; bila belum ada, folder dibuat. */
    fun addToFolder(pkg: String, folder: String) {
        pinned()
        val l = getList("pinned")
        l.remove(pkg)
        val i = l.indexOfFirst { isFolder(it) && folderName(it) == folder }
        if (i >= 0) {
            val pk = folderPkgs(l[i]).toMutableList()
            if (!pk.contains(pkg)) pk.add(pkg)
            l[i] = mkFolder(folder, pk)
        } else l.add(mkFolder(cleanName(folder), listOf(pkg)))
        putList("pinned", l)
    }

    /** Buat folder baru berisi [pkg] dengan nama otomatis; mengembalikan namanya. */
    fun newFolder(pkg: String): String {
        val names = folderNames()
        var n = names.size + 1
        while (names.contains("Folder $n")) n++
        addToFolder(pkg, "Folder $n")
        return "Folder $n"
    }

    fun removeFromFolder(pkg: String, folder: String) {
        val l = getList("pinned")
        val i = l.indexOfFirst { isFolder(it) && folderName(it) == folder }
        if (i < 0) return
        val rest = folderPkgs(l[i]).filter { it != pkg }
        if (rest.isEmpty()) l.removeAt(i) else l[i] = mkFolder(folder, rest)
        l.add(minOf(i + 1, l.size), pkg)
        putList("pinned", l)
    }

    fun renameFolder(old: String, new: String) {
        val l = getList("pinned")
        val i = l.indexOfFirst { isFolder(it) && folderName(it) == old }
        if (i < 0) return
        val nn = cleanName(new)
        if (nn != old && folderNames().contains(nn)) return
        l[i] = mkFolder(nn, folderPkgs(l[i]))
        putList("pinned", l)
    }

    fun dissolveFolder(name: String) {
        val l = getList("pinned")
        val i = l.indexOfFirst { isFolder(it) && folderName(it) == name }
        if (i < 0) return
        val pk = folderPkgs(l[i])
        l.removeAt(i)
        l.addAll(i, pk)
        putList("pinned", l)
    }

    /** Geser entri di grid Start ke kiri (-1) atau kanan (+1). [idx] adalah PinEntry.idx. */
    fun moveEntry(idx: Int, delta: Int) {
        val l = getList("pinned")
        val j = idx + delta
        if (idx !in l.indices || j !in l.indices) return
        val t = l[idx]; l[idx] = l[j]; l[j] = t
        putList("pinned", l)
    }

    /** Folder tempat [pkg] berada, atau null. */
    fun folderOfApp(pkg: String): String? =
        getList("pinned").firstOrNull { isFolder(it) && folderPkgs(it).contains(pkg) }?.let { folderName(it) }

    fun toggleTaskbarPin(pkg: String) {
        taskbarPins()
        val l = getList("tbpins")
        if (!l.remove(pkg)) l.add(pkg)
        putList("tbpins", l)
    }

    fun recents(): List<AppInfo> = getList("recent").mapNotNull { find(it) }
    fun running(): List<AppInfo> = getList("running").mapNotNull { find(it) }

    fun markRunning(pkg: String, on: Boolean) {
        val l = getList("running"); l.remove(pkg); if (on) l.add(pkg); putList("running", l)
    }

    private fun addRecent(pkg: String) {
        val l = getList("recent"); l.remove(pkg); l.add(0, pkg)
        putList("recent", l.take(6))
    }

    fun launch(c: Context, app: AppInfo) {
        addRecent(app.pkg)
        markRunning(app.pkg, true)
        try {
            if (Theme.windowMode) {
                // Mode jendela: berfungsi bila perangkat mendukung freeform (Developer options).
                val dm = c.resources.displayMetrics
                val w = (dm.widthPixels * 0.62f).toInt()
                val h = (dm.heightPixels * 0.75f).toInt()
                val l = (dm.widthPixels - w) / 2
                val t = c.dp(24)
                val opts = ActivityOptions.makeBasic().setLaunchBounds(Rect(l, t, l + w, t + h))
                c.startActivity(app.intent, opts.toBundle())
            } else c.startActivity(app.intent)
        } catch (e: Exception) {
            markRunning(app.pkg, false)
        }
    }

    fun uninstall(c: Context, pkg: String) {
        c.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun appInfo(c: Context, pkg: String) {
        c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun isInstalled(c: Context, pkg: String) = try { c.packageManager.getPackageInfo(pkg, 0); true }
    catch (e: PackageManager.NameNotFoundException) { false }
}
