package com.arclune.winlauncher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.KeyguardManager
import android.app.Notification
import android.graphics.Canvas
import android.content.res.Configuration
import android.service.notification.StatusBarNotification
import android.view.InputDevice
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.ImageView
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.FileOutputStream
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast

enum class Panel { NONE, START, SEARCH, WIDGETS, QS, NOTIF, CLIP }

class MainActivity : Activity() {
    lateinit var taskbar: Taskbar
    lateinit var windows: Windows
    var batteryLevel = 1f
    var charging = false
    var currentPanel = Panel.NONE
        private set

    private lateinit var outer: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var wallpaper: WallpaperView
    lateinit var desktopView: DesktopView
    private lateinit var winLayer: FrameLayout
    private lateinit var scrim: View
    private lateinit var start: StartMenu
    private lateinit var quick: QuickPanel
    private lateinit var notif: NotifPanel
    private lateinit var widgets: WidgetsPanel
    private lateinit var clip: ClipPanel
    private var lockView: LockScreen? = null
    private var toastView: View? = null
    private var metaDown = false
    private var metaUsed = false
    private var blockedHit = false
    private val receivers = ArrayList<BroadcastReceiver>()
    private var taskView: TaskView? = null
    private var touchX = 0
    private var touchY = 0
    private val taskbarH get() = dp(48)

    // ------------------------------------------------------------------ lifecycle
    /** Skala UI: ubah densityDpi untuk activity ini, sehingga semua dp & sp ikut membesar/mengecil. */
    override fun attachBaseContext(newBase: Context) {
        val scale = newBase.getSharedPreferences("win11", Context.MODE_PRIVATE).getFloat("uiScale", Theme.DEFAULT_SCALE)
        if (scale == 1f) { super.attachBaseContext(newBase); return }
        val cfg = Configuration(newBase.resources.configuration)
        cfg.densityDpi = (cfg.densityDpi * scale).toInt().coerceAtLeast(120)
        super.attachBaseContext(newBase.createConfigurationContext(cfg))
    }

    fun setUiScale(v: Float) {
        Theme.uiScale = v
        Theme.save(this)
        getSharedPreferences("win11", Context.MODE_PRIVATE).edit().putBoolean("reopenSettings", true).apply()
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.load(this)
        AppRepo.loadAsync(this) { onAppsLoaded() }
        getSharedPreferences("win11apps", Context.MODE_PRIVATE).edit().remove("running").apply()

        outer = FrameLayout(this)
        wallpaper = WallpaperView(this)
        WallpaperView.loadCustom(this)
        rebuildAcrylic()
        outer.addView(wallpaper, FrameLayout.LayoutParams(-1, -1))
        content = FrameLayout(this)
        outer.addView(content, FrameLayout.LayoutParams(-1, -1))
        setContentView(outer)

        // desktop + layer jendela
        desktopView = DesktopView(this)
        content.addView(desktopView, FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = taskbarH })
        winLayer = FrameLayout(this)
        content.addView(winLayer, FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = taskbarH })
        windows = Windows(this, winLayer)

        // scrim untuk menutup panel saat tap di luar
        scrim = View(this).apply {
            visibility = View.GONE
            isClickable = true
            setOnClickListener { open(Panel.NONE) }
        }
        content.addView(scrim, FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = taskbarH })

        // panel
        start = StartMenu(this); quick = QuickPanel(this); notif = NotifPanel(this); widgets = WidgetsPanel(this); clip = ClipPanel(this)
        for (p in listOf<View>(start, quick, notif, widgets, clip)) { p.visibility = View.GONE; content.addView(p) }

        // taskbar
        taskbar = Taskbar(this)
        content.addView(taskbar, FrameLayout.LayoutParams(-1, taskbarH, Gravity.BOTTOM))

        desktopView.build()
        handleInsets()
        registerReceivers()

        NotifService.onPosted = { sbn -> runOnUiThread { showToast(sbn) } }

        val prefs = getSharedPreferences("win11", Context.MODE_PRIVATE)
        if (prefs.getBoolean("reopenSettings", false)) {
            prefs.edit().remove("reopenSettings").apply()
            content.post { openSettings(1) }
        }

        // layar kunci + animasi boot hanya saat start dingin (bukan saat recreate karena ganti skala/tema)
        if (savedInstanceState == null) {
            if (Theme.lockOnStart) lock()
            if (Theme.showBoot) {
                val b = BootView(this) { }
                outer.addView(b, FrameLayout.LayoutParams(-1, -1))
                b.run()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        for (r in receivers) { try { unregisterReceiver(r) } catch (e: Exception) { } }
        receivers.clear()
        NotifService.onPosted = null
    }

    /** Lupa PIN: verifikasi dengan kunci layar Android (pola/PIN/sidik jari), lalu PIN launcher dihapus. */
    fun forgotPin() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val i = if (km.isDeviceSecure)
            km.createConfirmDeviceCredentialIntent("Reset PIN launcher", "Verifikasi dengan kunci layar Android untuk menghapus PIN launcher")
        else null
        if (i == null) {
            Toast.makeText(this, "HP ini tidak punya kunci layar Android. Hapus data aplikasi di Pengaturan Android untuk mereset.", Toast.LENGTH_LONG).show()
            return
        }
        try { startActivityForResult(i, 78) } catch (e: Exception) {
            Toast.makeText(this, "Tidak bisa membuka verifikasi", Toast.LENGTH_SHORT).show()
        }
    }

    /** Tangkapan layar launcher (tersimpan di Pictures/Win11Launcher). */
    fun screenshot() {
        open(Panel.NONE)
        content.postDelayed({
            try {
                val v = window.decorView
                val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(Canvas(bmp))
                Sounds.click()
                Thread {
                    val ok = Shots.save(this, bmp, "Screenshot")
                    runOnUiThread {
                        Toast.makeText(this, if (ok) "Tangkapan layar tersimpan di Pictures/Win11Launcher" else "Gagal menyimpan tangkapan layar",
                            Toast.LENGTH_SHORT).show()
                    }
                }.start()
            } catch (e: Throwable) {
                Toast.makeText(this, "Gagal mengambil tangkapan layar", Toast.LENGTH_SHORT).show()
            }
        }, 250)
    }

    // ------------------------------------------------------------------ layar kunci
    fun lock() {
        if (lockView != null) return
        open(Panel.NONE)
        taskView?.let { toggleTaskView() }
        val l = LockScreen(this) { lockView = null }
        lockView = l
        outer.addView(l, FrameLayout.LayoutParams(-1, -1))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) { hideSystemBars(); ClipStore.capture(this) }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // tombol Home = tombol Windows: buka/tutup Start
        if (lockView != null) return
        if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) {
            if (currentPanel == Panel.NONE) open(Panel.START) else open(Panel.NONE)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (lockView == null && currentPanel != Panel.NONE) open(Panel.NONE) }

    override fun onResume() {
        super.onResume()
        taskbar.refresh()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            touchX = ev.rawX.toInt(); touchY = ev.rawY.toInt()
            // klik kanan mouse = tekan lama
            if (lockView == null && ev.isFromSource(InputDevice.SOURCE_MOUSE) &&
                (ev.buttonState and MotionEvent.BUTTON_SECONDARY) != 0 && rightClick(touchX, touchY)) return true
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun rightClick(x: Int, y: Int): Boolean {
        blockedHit = false
        val v = longTarget(window.decorView, x, y)
        if (v != null) { v.performLongClick(); return true }
        return blockedHit
    }

    private fun longTarget(v: View, x: Int, y: Int): View? {
        if (v !is ViewGroup) return null
        for (i in v.childCount - 1 downTo 0) {
            val c = v.getChildAt(i)
            if (c.visibility != View.VISIBLE) continue
            val loc = IntArray(2); c.getLocationOnScreen(loc)
            if (x < loc[0] || x >= loc[0] + c.width || y < loc[1] || y >= loc[1] + c.height) continue
            val r = longTarget(c, x, y)
            if (r != null) return r
            if (blockedHit) return null
            if (c.isLongClickable) return c
            if (c is WindowFrame || c is StartMenu || c is QuickPanel || c is NotifPanel ||
                c is WidgetsPanel || c is ClipPanel || c is TaskView) { blockedHit = true; return null }
        }
        return null
    }

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        if (lockView != null) return super.dispatchKeyEvent(ev)
        val code = ev.keyCode
        if (code == KeyEvent.KEYCODE_META_LEFT || code == KeyEvent.KEYCODE_META_RIGHT) {
            if (ev.action == KeyEvent.ACTION_DOWN) { if (ev.repeatCount == 0) { metaDown = true; metaUsed = false } }
            else if (ev.action == KeyEvent.ACTION_UP) {
                if (metaDown && !metaUsed) open(Panel.START)
                metaDown = false
            }
            return true
        }
        if (ev.action != KeyEvent.ACTION_DOWN || ev.repeatCount != 0) return super.dispatchKeyEvent(ev)
        if (metaDown && winShortcut(ev)) { metaUsed = true; return true }
        if (ev.isAltPressed && code == KeyEvent.KEYCODE_TAB) { windows.cycle(); return true }
        if (ev.isAltPressed && code == KeyEvent.KEYCODE_F4) { windows.focused()?.let { windows.close(it) }; return true }
        if (code == KeyEvent.KEYCODE_SYSRQ) { screenshot(); return true }
        if (ev.isCtrlPressed && ev.isShiftPressed && code == KeyEvent.KEYCODE_ESCAPE) { openTaskMgr(); return true }
        if (code == KeyEvent.KEYCODE_ESCAPE && currentPanel != Panel.NONE) { open(Panel.NONE); return true }
        return super.dispatchKeyEvent(ev)
    }

    /** Pintasan Win+tombol. Mengembalikan true bila dikenali. */
    private fun winShortcut(ev: KeyEvent): Boolean {
        val ctrl = ev.isCtrlPressed
        when (ev.keyCode) {
            KeyEvent.KEYCODE_D -> if (ctrl) windows.addDesktop() else showDesktop()
            KeyEvent.KEYCODE_E -> openExplorer()
            KeyEvent.KEYCODE_I -> openSettings(0)
            KeyEvent.KEYCODE_L -> lock()
            KeyEvent.KEYCODE_S -> if (ev.isShiftPressed) screenshot() else open(Panel.SEARCH)
            KeyEvent.KEYCODE_V -> open(Panel.CLIP)
            KeyEvent.KEYCODE_A -> open(Panel.QS)
            KeyEvent.KEYCODE_N -> open(Panel.NOTIF)
            KeyEvent.KEYCODE_W -> open(Panel.WIDGETS)
            KeyEvent.KEYCODE_B -> openBrowser()
            KeyEvent.KEYCODE_TAB -> toggleTaskView()
            KeyEvent.KEYCODE_DPAD_LEFT -> if (ctrl) windows.switchDesktop(windows.current - 1) else windows.focused()?.snap(Zone.LEFT)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (ctrl) windows.switchDesktop(windows.current + 1) else windows.focused()?.snap(Zone.RIGHT)
            KeyEvent.KEYCODE_DPAD_UP -> windows.focused()?.snap(Zone.MAX)
            KeyEvent.KEYCODE_DPAD_DOWN -> windows.focused()?.let { if (it.snapped) it.toggleMax() else windows.minimize(it) }
            KeyEvent.KEYCODE_F4 -> if (ctrl) windows.removeDesktop(windows.current)
            else -> return false
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    /** Hindari ikon tertutup poni kamera (cutout) di sisi kiri/kanan. */
    private fun handleInsets() {
        outer.setOnApplyWindowInsetsListener { _, ins ->
            if (Build.VERSION.SDK_INT >= 28) {
                val c = ins.displayCutout
                content.setPadding(c?.safeInsetLeft ?: 0, 0, c?.safeInsetRight ?: 0, 0)
            }
            ins
        }
        outer.requestApplyInsets()
    }

    private fun registerReceivers() {
        val bat = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                batteryLevel = lvl / scale.toFloat()
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
                if (::taskbar.isInitialized) taskbar.updateStatus()
            }
        }
        reg(bat, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val pkg = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                AppRepo.loadAsync(this@MainActivity) { onAppsLoaded() }
            }
        }
        reg(pkg, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package")
        })

        val off = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { if (Theme.lockOnScreenOff) lock() }
        }
        reg(off, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    private fun reg(r: BroadcastReceiver, f: IntentFilter) { registerReceiver(r, f); receivers.add(r) }

    // ------------------------------------------------------------------ tema
    fun applyTheme() {
        Theme.save(this)
        rebuildAcrylic()
        wallpaper.invalidate()
        desktopView.build()
        taskbar.refresh()
    }

    private fun rebuildAcrylic() {
        val dm = resources.displayMetrics
        Acrylic.rebuild(wallpaper, maxOf(dm.widthPixels, dm.heightPixels), minOf(dm.widthPixels, dm.heightPixels))
    }

    /** Daftar aplikasi selesai dimuat (thread latar) -> segarkan tampilan yang bergantung padanya. */
    private fun onAppsLoaded() {
        if (!::taskbar.isInitialized) return
        taskbar.refresh()
        desktopView.build()
    }

    // ------------------------------------------------------------------ desktop
    fun desktopMenu() {
        showMenu(this, touchX, touchY, listOf(
            MenuItem("Segarkan", G.BACK) { AppRepo.loadAsync(this) { onAppsLoaded() } },
            MenuItem(""),
            MenuItem("Ganti wallpaper", G.PALETTE) { Theme.wallpaper = (Theme.wallpaper + 1) % 3; applyTheme() },
            MenuItem(if (Theme.dark) "Mode terang" else "Mode gelap", G.NIGHT) { Theme.dark = !Theme.dark; applyTheme() },
            MenuItem(if (Theme.showDesktopIcons) "Sembunyikan ikon desktop" else "Tampilkan ikon desktop", G.FOLDER) {
                Theme.showDesktopIcons = !Theme.showDesktopIcons; applyTheme()
            },
            MenuItem(""),
            MenuItem("Pulihkan ikon bawaan", G.FOLDER) { desktopView.restoreDefaults() },
            MenuItem("Kunci layar", G.LOCK) { lock() },
            MenuItem("Pengelola Tugas", G.PULSE) { openTaskMgr() },
            MenuItem("Pengaturan tampilan", G.SUN) { startSafe(Intent(Settings.ACTION_DISPLAY_SETTINGS)) },
            MenuItem("Personalisasi", G.PALETTE) { openSettings(1) }
        ))
    }

    // ------------------------------------------------------------------ panel
    fun open(p: Panel) {
        hideKeyboardIfNeeded()
        val target = if (p == currentPanel) Panel.NONE else p
        panelView(currentPanel)?.let { hidePanel(it) }
        currentPanel = target
        scrim.visibility = if (target == Panel.NONE) View.GONE else View.VISIBLE
        if (target == Panel.NONE) return
        val v = panelView(target) ?: return
        val dm = resources.displayMetrics
        val gap = dp(12)
        val lp: FrameLayout.LayoutParams
        when (target) {
            Panel.START, Panel.SEARCH -> {
                start.show(if (target == Panel.SEARCH) 1 else 0)
                lp = FrameLayout.LayoutParams(start.panelWidth(), start.panelHeight(),
                    if (Theme.centered) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.BOTTOM or Gravity.START)
                if (!Theme.centered) lp.leftMargin = gap
            }
            Panel.QS -> {
                quick.refresh()
                lp = FrameLayout.LayoutParams(quick.panelWidth(), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END)
                lp.rightMargin = gap
            }
            Panel.NOTIF -> {
                notif.refresh()
                lp = FrameLayout.LayoutParams(notif.panelWidth(), notif.panelHeight(), Gravity.BOTTOM or Gravity.END)
                lp.rightMargin = gap
            }
            Panel.CLIP -> {
                clip.refresh()
                lp = FrameLayout.LayoutParams(clip.panelWidth(), clip.panelHeight(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            }
            else -> {
                widgets.refresh()
                lp = FrameLayout.LayoutParams(widgets.panelWidth(), widgets.panelHeight(), Gravity.BOTTOM or Gravity.START)
                lp.leftMargin = gap
            }
        }
        lp.bottomMargin = taskbarH + gap
        v.layoutParams = lp
        v.visibility = View.VISIBLE
        v.alpha = 0f
        v.translationY = dp(36).toFloat()
        if (target == Panel.WIDGETS) { v.translationY = 0f; v.translationX = -dp(60).toFloat() }
        v.animate().alpha(1f).translationY(0f).translationX(0f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun panelView(p: Panel): View? = when (p) {
        Panel.START, Panel.SEARCH -> start
        Panel.QS -> quick
        Panel.NOTIF -> notif
        Panel.WIDGETS -> widgets
        Panel.CLIP -> clip
        Panel.NONE -> null
    }

    private fun hidePanel(v: View) {
        v.animate().alpha(0f).translationY(dp(24).toFloat()).setDuration(120).withEndAction {
            // jangan sembunyikan bila panel yang sama sudah dibuka lagi
            if (panelView(currentPanel) !== v) v.visibility = View.GONE
        }.start()
    }

    private fun hideKeyboardIfNeeded() { if (::start.isInitialized) start.hideKeyboard() }

    // ------------------------------------------------------------------ jendela
    fun openWindow(title: String, glyph: G, view: View, wDp: Int, hDp: Int) {
        open(Panel.NONE)
        val d = resources.displayMetrics.density
        val maxW = ((resources.displayMetrics.widthPixels) / d).toInt() - 24
        val maxH = ((resources.displayMetrics.heightPixels - taskbarH) / d).toInt() - 12
        val f = WindowFrame(this, title, glyph, view, minOf(wDp, maxW), minOf(hDp, maxH))
        windows.open(f)
        taskView?.let { toggleTaskView() }
    }

    fun openExplorer() = openWindow("File Explorer", G.FOLDER, FileExplorer(this), 640, 340)
    fun openCalc() = openWindow("Kalkulator", G.CALC, MiniApps.calculator(this), 480, 290)
    fun openNotes() = openWindow("Notepad", G.NOTE, MiniApps.notepad(this), 460, 290)
    fun openClock() = openWindow("Jam", G.CLOCK, MiniApps.clock(this), 460, 270)
    fun openBin() = openWindow("Recycle Bin", G.TRASH, Apps.recycle(this), 420, 260)

    fun openThisPc() = openWindow("Perangkat ini", G.PC, Apps.thisPc(this), 520, 300)

    /** Bila jendela dengan judul ini sudah ada, cukup pulihkan/fokuskan (tanpa membangun ulang isinya). */
    private fun reuse(title: String): Boolean {
        val w = windows.find(title) ?: return false
        open(Panel.NONE)
        windows.open(w)
        return true
    }

    fun openBrowser(url: String? = null) {
        val w = windows.find("Browser")
        if (w != null) {
            if (url != null) (w.content as? BrowserApp)?.openUrl(url)
            reuse("Browser"); return
        }
        openWindow("Browser", G.GLOBE, BrowserApp(this, url ?: "https://www.google.com"), 720, 360)
    }

    fun openTaskMgr() { if (!reuse("Pengelola Tugas")) openWindow("Pengelola Tugas", G.PULSE, TaskManagerApp(this), 560, 320) }
    fun openPaint() { if (!reuse("Paint")) openWindow("Paint", G.BRUSH, PaintApp(this), 560, 320) }
    fun openPhotos() { if (!reuse("Foto")) openWindow("Foto", G.IMAGE, PhotosApp(this), 600, 340) }
    fun openMedia() { if (!reuse("Media Player")) openWindow("Media Player", G.MUSIC, MediaApp(this), 520, 320) }

    fun reopenTaskView() {
        taskView?.let { content.removeView(it); taskView = null }
        toggleTaskView()
    }

    fun showTrayMenu() {
        val dm = resources.displayMetrics
        showMenu(this, dm.widthPixels - dp(170), dm.heightPixels - taskbarH, listOf(
            MenuItem("Riwayat clipboard", G.CLIP) { open(Panel.CLIP) },
            MenuItem("Pengelola Tugas", G.PULSE) { openTaskMgr() },
            MenuItem("Kunci layar", G.LOCK) { lock() },
            MenuItem("Tangkapan layar", G.IMAGE) { screenshot() },
            MenuItem(if (Theme.focus) "Matikan mode Fokus" else "Aktifkan mode Fokus", G.BELL) { Theme.focus = !Theme.focus; Theme.save(this) },
            MenuItem(""),
            MenuItem("Desktop virtual baru", G.TASKVIEW) { windows.addDesktop() }
        ), above = true)
    }

    fun taskbarMenu() {
        showMenu(this, touchX, touchY, listOf(
            MenuItem("Pengelola Tugas", G.PULSE) { openTaskMgr() },
            MenuItem(if (Theme.centered) "Ikon taskbar ke kiri" else "Ikon taskbar ke tengah", G.TASKVIEW) {
                Theme.centered = !Theme.centered; applyTheme()
            },
            MenuItem("Tangkapan layar", G.IMAGE) { screenshot() },
            MenuItem("Pengaturan taskbar", G.SETTINGS) { openSettings(1) }
        ), above = true, centerOnX = true)
    }

    // ------------------------------------------------------------------ banner notifikasi
    private fun showToast(sbn: StatusBarNotification) {
        if (!Theme.toasts || Theme.focus || lockView != null) return
        if (sbn.packageName == packageName || sbn.isOngoing) return
        val n = sbn.notification
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val title = n.extras.getCharSequence("android.title")?.toString() ?: ""
        val text = n.extras.getCharSequence("android.text")?.toString() ?: ""
        if (title.isEmpty() && text.isEmpty()) return
        Sounds.ping()
        toastView?.let { content.removeView(it) }
        val act = this
        val box = LinearLayout(act)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.setPadding(dp(14), dp(10), dp(14), dp(10))
        box.background = rounded(act, Theme.surfaceSolid, 8, Theme.stroke)
        box.elevation = dpf(20f)
        val ic = ImageView(act)
        try { ic.setImageDrawable(n.smallIcon?.loadDrawable(act)); ic.setColorFilter(Theme.text) } catch (e: Exception) { }
        box.addView(ic, LinearLayout.LayoutParams(dp(20), dp(20)))
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.addView(label(act, title, 12.5f, Theme.text, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        col.addView(label(act, text, 11.5f, Theme.subText).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
        box.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(12) })
        toastView = box
        content.addView(box, FrameLayout.LayoutParams(dp(300), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
            .apply { topMargin = dp(12); rightMargin = dp(12) })
        fun dismiss() {
            box.animate().translationX(dp(320).toFloat()).alpha(0f).setDuration(200).withEndAction {
                content.removeView(box); if (toastView === box) toastView = null
            }.start()
        }
        box.setOnClickListener { try { n.contentIntent?.send() } catch (e: Exception) { }; dismiss() }
        box.translationX = dp(320).toFloat()
        box.animate().translationX(0f).setDuration(260).setInterpolator(Anim.ease).start()
        box.postDelayed({ if (box.parent != null) dismiss() }, 4500)
    }

    /** Buka jendela Pengaturan. [reopen] mempertahankan posisi saat ganti halaman/tema. */
    fun openSettings(page: Int, reopen: Boolean = false) {
        val old = windows.closeNow("Pengaturan")
        openWindow("Pengaturan", G.SETTINGS, Apps.settings(this, page), 660, 330)
        if (old != null) windows.all().firstOrNull { it.title == "Pengaturan" }?.let { w ->
            w.post { w.x = old.first; w.y = old.second }
        }
    }

    fun toggleTaskView() {
        val existing = taskView
        if (existing != null) {
            content.removeView(existing); taskView = null; return
        }
        open(Panel.NONE)
        val tv = TaskView(this)
        taskView = tv
        content.addView(tv, content.indexOfChild(taskbar), FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = taskbarH })
        tv.alpha = 0f; tv.animate().alpha(1f).setDuration(160).start()
    }

    fun showDesktop() {
        open(Panel.NONE)
        taskView?.let { toggleTaskView() }
        windows.minimizeAll()
    }

    // ------------------------------------------------------------------ aksi umum
    fun launchApp(app: AppInfo) {
        open(Panel.NONE)
        AppRepo.launch(this, app)
        taskbar.refresh()
    }

    fun showAppMenu(anchor: View, app: AppInfo, fromTaskbar: Boolean, extra: List<MenuItem> = emptyList()) =
        appMenu(this, anchor, app, fromTaskbar, extra)

    fun showPowerMenu(anchor: View) {
        val loc = IntArray(2); anchor.getLocationOnScreen(loc)
        showMenu(this, loc[0] + anchor.width, loc[1], listOf(
            MenuItem("Tutup semua jendela", G.CLOSE) { windows.all().forEach { windows.close(it) }; open(Panel.NONE) },
            MenuItem("Muat ulang launcher", G.BACK) { recreate() },
            MenuItem(""),
            MenuItem("Pengaturan Android", G.SETTINGS) { startSafe(Intent(Settings.ACTION_SETTINGS)) }
        ), above = true)
    }

    fun startSafe(i: Intent) {
        open(Panel.NONE)
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { Toast.makeText(this, "Tidak tersedia di perangkat ini", Toast.LENGTH_SHORT).show() }
    }

    fun openFiles() {
        val pm = packageManager
        val i = listOf("com.google.android.documentsui", "com.android.documentsui", "com.sec.android.app.myfiles")
            .firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
        if (i != null) startSafe(i) else startSafe(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
    }

    // ------------------------------------------------------------------ wallpaper dari galeri
    fun pickWallpaper() {
        open(Panel.NONE)
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), 77)
        } catch (e: Exception) {
            Toast.makeText(this, "Galeri tidak tersedia", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 78) {
            if (resultCode == RESULT_OK) {
                Pin.set(this, "")
                Toast.makeText(this, "PIN launcher dihapus", Toast.LENGTH_SHORT).show()
                lockView?.unlock()
            }
            return
        }
        if (requestCode != 77 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        Thread {
            try {
                val dm = resources.displayMetrics
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= dm.widthPixels && bounds.outHeight / (sample * 2) >= dm.heightPixels) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                if (bmp != null) {
                    FileOutputStream(WallpaperView.file(this)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    runOnUiThread {
                        WallpaperView.loadCustom(this)
                        Theme.wallpaper = WallpaperView.CUSTOM
                        applyTheme()
                        openSettings(1, true)
                    }
                }
            } catch (e: Throwable) {
                runOnUiThread { Toast.makeText(this, "Gagal memuat foto", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }
}
