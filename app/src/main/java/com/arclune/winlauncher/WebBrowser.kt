package com.arclune.winlauncher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

/** Browser mini ala Edge: tab, address bar, navigasi, progress. Memakai WebView sistem. */
class BrowserApp(private val a: MainActivity, start: String = "https://www.google.com") : LinearLayout(a) {
    private class Tab(val web: WebView) {
        var title: String = "Tab baru"
        var url: String = ""
    }

    private val tabs = ArrayList<Tab>()
    private var cur = 0
    private val tabRow = LinearLayout(a)
    private val urlBox = EditText(a)
    private val progress = View(a)
    private val holder = FrameLayout(a)
    private lateinit var backBtn: FrameLayoutBtn
    private lateinit var fwdBtn: FrameLayoutBtn

    /** Tombol ikon kecil dengan status aktif/nonaktif. */
    private class FrameLayoutBtn(val box: FrameLayout, val glyph: Glyph)

    init {
        orientation = VERTICAL
        setBackgroundColor(if (Theme.dark) 0xFF202020.toInt() else 0xFFF3F3F3.toInt())

        // baris tab
        val sc = HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(tabRow) }
        addView(sc, lp(MATCH, a.dp(36)))

        // toolbar
        val bar = hBox(a).apply {
            setPadding(a.dp(6), a.dp(2), a.dp(6), a.dp(4))
            setBackgroundColor(if (Theme.dark) 0xFF2B2B2B.toInt() else 0xFFFFFFFF.toInt())
        }
        backBtn = iconBtn(G.BACK) { tabs.getOrNull(cur)?.web?.let { if (it.canGoBack()) it.goBack() } }
        fwdBtn = iconBtn(G.NEXT) { tabs.getOrNull(cur)?.web?.let { if (it.canGoForward()) it.goForward() } }
        val reload = iconBtn(G.REFRESH) { tabs.getOrNull(cur)?.web?.reload() }
        bar.addView(backBtn.box, lp(a.dp(32), a.dp(32)))
        bar.addView(fwdBtn.box, lp(a.dp(32), a.dp(32)))
        bar.addView(reload.box, lp(a.dp(32), a.dp(32)))

        urlBox.apply {
            hint = "Cari atau ketik alamat web"
            setHintTextColor(Theme.subText); setTextColor(Theme.text); textSize = 12.5f
            setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            background = rounded(a, Theme.card, 16, Theme.stroke)
            setPadding(a.dp(14), 0, a.dp(14), 0)
            setSelectAllOnFocus(true)
            setOnEditorActionListener { _, _, _ -> navigate(text.toString()); true }
        }
        bar.addView(urlBox, lp(0, a.dp(32), 1f).apply { setMargins(a.dp(6), 0, a.dp(2), 0) })
        addView(bar, lp(MATCH, WRAP))

        // progress
        progress.setBackgroundColor(Theme.accent)
        progress.pivotX = 0f
        progress.scaleX = 0f
        addView(progress, lp(MATCH, a.dp(2)))

        addView(holder, lp(MATCH, 0, 1f))
        newTab(start)
    }

    /** Buka URL di tab baru (dipakai berita, pencarian web, dll). */
    fun openUrl(url: String) = newTab(url)

    private fun iconBtn(g: G, click: () -> Unit): FrameLayoutBtn {
        val gl = Glyph(a, g)
        val f = FrameLayout(a).apply {
            pressable(16)
            addView(gl, FrameLayout.LayoutParams(a.dp(16), a.dp(16), Gravity.CENTER))
            setOnClickListener { click() }
        }
        return FrameLayoutBtn(f, gl)
    }

    private fun tabOf(v: WebView?): Tab? = tabs.firstOrNull { it.web === v }

    // ------------------------------------------------------------------ tab
    private fun newTab(url: String?) {
        val w = WebView(a)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
            loadWithOverviewMode = true; useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        val t = Tab(w)
        w.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, u: String?, favicon: Bitmap?) {
                val tab = tabOf(view) ?: return
                tab.url = u ?: ""
                if (tabs.getOrNull(cur) === tab && !urlBox.hasFocus()) urlBox.setText(tab.url)
            }
            override fun onPageFinished(view: WebView?, u: String?) {
                val tab = tabOf(view) ?: return
                tab.url = u ?: tab.url
                tab.title = view?.title?.takeIf { it.isNotBlank() } ?: tab.url
                drawTabs(); updateNav()
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val s = uri.toString()
                if (s.startsWith("http://") || s.startsWith("https://") || s.startsWith("about:")) return false
                try { a.startSafe(Intent(Intent.ACTION_VIEW, uri)) } catch (e: Exception) { }
                return true
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (tabs.getOrNull(cur)?.web !== view) return
                progress.scaleX = newProgress / 100f
                progress.visibility = if (newProgress < 100) View.VISIBLE else View.INVISIBLE
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                val tab = tabOf(view) ?: return
                if (!title.isNullOrBlank()) { tab.title = title; drawTabs() }
            }
        }
        tabs.add(t)
        holder.addView(w, FrameLayout.LayoutParams(-1, -1))
        select(tabs.size - 1)
        if (url != null) w.loadUrl(url)
    }

    private fun select(i: Int) {
        cur = i.coerceIn(0, tabs.size - 1)
        tabs.forEachIndexed { idx, t -> t.web.visibility = if (idx == cur) View.VISIBLE else View.GONE }
        val t = tabs[cur]
        urlBox.setText(t.url)
        progress.scaleX = 0f
        drawTabs(); updateNav()
    }

    private fun closeTab(i: Int) {
        val t = tabs.removeAt(i)
        holder.removeView(t.web)
        t.web.destroy()
        if (tabs.isEmpty()) newTab("https://www.google.com") else select(if (cur >= tabs.size) tabs.size - 1 else cur)
    }

    private fun drawTabs() {
        tabRow.removeAllViews()
        tabs.forEachIndexed { i, t ->
            val active = i == cur
            val chip = hBox(a).apply {
                setPadding(a.dp(10), 0, a.dp(4), 0)
                background = rounded(a, if (active) (if (Theme.dark) 0xFF2B2B2B.toInt() else 0xFFFFFFFF.toInt()) else 0, 6)
                setOnClickListener { select(i) }
            }
            chip.addView(label(a, t.title, 11.5f, if (active) Theme.text else Theme.subText).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END; maxWidth = a.dp(130)
            })
            chip.addView(FrameLayout(a).apply {
                pressable(10)
                addView(Glyph(a, G.CLOSE), FrameLayout.LayoutParams(a.dp(12), a.dp(12), Gravity.CENTER))
                setOnClickListener { closeTab(i) }
            }, lp(a.dp(22), a.dp(22)).apply { leftMargin = a.dp(4) })
            tabRow.addView(chip, lp(WRAP, a.dp(30)).apply { setMargins(a.dp(4), a.dp(5), 0, 0) })
        }
        val plus = FrameLayout(a).apply {
            pressable(8)
            addView(Glyph(a, G.PLUS), FrameLayout.LayoutParams(a.dp(14), a.dp(14), Gravity.CENTER))
            setOnClickListener { newTab("https://www.google.com") }
        }
        tabRow.addView(plus, lp(a.dp(30), a.dp(30)).apply { setMargins(a.dp(6), a.dp(5), 0, 0) })
    }

    private fun updateNav() {
        val w = tabs.getOrNull(cur)?.web ?: return
        backBtn.glyph.alpha = if (w.canGoBack()) 1f else 0.35f
        fwdBtn.glyph.alpha = if (w.canGoForward()) 1f else 0.35f
    }

    private fun navigate(input: String) {
        val t = input.trim()
        if (t.isEmpty()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains(" ") || !t.contains(".") -> "https://www.google.com/search?q=" + Uri.encode(t)
            else -> "https://$t"
        }
        tabs.getOrNull(cur)?.web?.loadUrl(url)
        urlBox.clearFocus()
        (a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(urlBox.windowToken, 0)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        for (t in tabs) { try { t.web.destroy() } catch (e: Exception) { } }
    }
}
