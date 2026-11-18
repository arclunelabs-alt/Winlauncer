package com.arclune.winlauncher

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import java.io.File
import java.util.Locale

/** Media Player: daftar musik/video dari penyimpanan, kontrol putar, seek, dan pemutar video. */
class MediaApp(private val a: MainActivity) : FrameLayout(a) {
    private var tab = 0
    private var audio: List<File> = emptyList()
    private var video: List<File> = emptyList()
    private val list = ListView(a)
    private val tabHolder = LinearLayout(a)
    private var mp: MediaPlayer? = null
    private var cur = -1
    private val titleTv = label(a, "Tidak ada lagu diputar", 12f)
    private val timeTv = label(a, "0:00 / 0:00", 10f, Theme.subText)
    private lateinit var slider: FluentSlider
    private lateinit var playGlyph: Glyph
    private val player = LinearLayout(a)
    private val videoLayer = FrameLayout(a)
    private var videoView: VideoView? = null
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            val p = mp
            if (p != null) {
                try {
                    if (p.isPlaying && p.duration > 0) {
                        slider.value = p.currentPosition / p.duration.toFloat(); slider.invalidate()
                        timeTv.text = fmt(p.currentPosition) + " / " + fmt(p.duration)
                    }
                } catch (e: Exception) { }
            }
            handler.postDelayed(this, 500)
        }
    }

    init {
        setBackgroundColor(if (Theme.dark) 0xFF272727.toInt() else 0xFFF9F9F9.toInt())
        val page = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }

        drawTabs()
        page.addView(tabHolder, lp(MATCH, WRAP).apply { setMargins(a.dp(12), a.dp(8), a.dp(12), a.dp(4)) })
        list.apply {
            divider = null; dividerHeight = 0; isVerticalScrollBarEnabled = false
            adapter = Adapter()
            setOnItemClickListener { _, _, pos, _ -> if (tab == 0) playAudio(pos) else openVideo(video[pos]) }
        }
        page.addView(list, lp(MATCH, 0, 1f))

        // bar pemutar musik
        player.orientation = LinearLayout.VERTICAL
        player.setBackgroundColor(if (Theme.dark) 0x14FFFFFF else 0x0F000000)
        player.setPadding(a.dp(14), a.dp(6), a.dp(14), a.dp(6))
        titleTv.apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        player.addView(titleTv, lp(MATCH, a.dp(20)))
        slider = FluentSlider(a, 0f) { v -> mp?.let { try { it.seekTo((v * it.duration).toInt()) } catch (e: Exception) { } } }
        player.addView(slider, lp(MATCH, a.dp(24)))
        val ctl = hBox(a)
        ctl.addView(timeTv, lp(0, WRAP, 1f))
        ctl.addView(ctlBtn(G.BACK) { stepAudio(-1) }, lp(a.dp(40), a.dp(34)))
        playGlyph = Glyph(a, G.PLAY)
        ctl.addView(FrameLayout(a).apply {
            pressable(6)
            addView(playGlyph, LayoutParams(a.dp(20), a.dp(20), Gravity.CENTER))
            setOnClickListener { togglePlay() }
        }, lp(a.dp(44), a.dp(34)))
        ctl.addView(ctlBtn(G.NEXT) { stepAudio(1) }, lp(a.dp(40), a.dp(34)))
        ctl.addView(View(a), lp(0, 1, 1f))
        player.addView(ctl, lp(MATCH, WRAP))
        page.addView(player, lp(MATCH, WRAP))
        addView(page, LayoutParams(-1, -1))

        videoLayer.setBackgroundColor(0xFF000000.toInt())
        videoLayer.visibility = View.GONE
        addView(videoLayer, LayoutParams(-1, -1))
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); handler.post(tick); scan() }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(tick)
        try { mp?.release() } catch (e: Exception) { }
        mp = null
        videoView?.stopPlayback()
    }

    private fun fmt(ms: Int): String { val s = ms / 1000; return String.format(Locale.US, "%d:%02d", s / 60, s % 60) }

    private fun ctlBtn(g: G, click: () -> Unit): View = FrameLayout(a).apply {
        pressable(6)
        addView(Glyph(a, g), LayoutParams(a.dp(18), a.dp(18), Gravity.CENTER))
        setOnClickListener { click() }
    }

    private fun drawTabs() {
        tabHolder.removeAllViews()
        tabHolder.addView(segmented(a, listOf("Musik", "Video"), tab) { i ->
            tab = i; drawTabs()
            player.visibility = if (tab == 0) View.VISIBLE else View.GONE
            (list.adapter as? BaseAdapter)?.notifyDataSetChanged()
        })
    }

    private fun scan() {
        if (!Perms.hasFiles(a)) { Toast.makeText(a, "Beri izin akses file dulu (lewat File Explorer)", Toast.LENGTH_LONG).show(); Perms.requestFiles(a); return }
        Thread {
            val au = try { MediaScan.scan(MediaScan.audioExt, listOf("Music", "Download", "Recordings", "Podcasts"), 500) } catch (e: Throwable) { emptyList() }
            val vi = try { MediaScan.scan(MediaScan.videoExt, listOf("Movies", "DCIM", "Download", "Video"), 300) } catch (e: Throwable) { emptyList() }
            handler.post { audio = au; video = vi; (list.adapter as BaseAdapter).notifyDataSetChanged() }
        }.start()
    }

    // ------------------------------------------------------------------ audio
    private fun playAudio(i: Int) {
        if (i !in audio.indices) return
        try { mp?.release() } catch (e: Exception) { }
        cur = i
        try {
            mp = MediaPlayer().apply {
                setDataSource(audio[i].path)
                setOnCompletionListener { stepAudio(1) }
                prepare(); start()
            }
            titleTv.text = audio[i].nameWithoutExtension
            playGlyph.g = G.PAUSE; playGlyph.invalidate()
        } catch (e: Exception) {
            Toast.makeText(a, "Tidak bisa memutar file ini", Toast.LENGTH_SHORT).show()
        }
        (list.adapter as BaseAdapter).notifyDataSetChanged()
    }

    private fun stepAudio(d: Int) { if (audio.isNotEmpty()) playAudio((if (cur < 0) 0 else cur + d + audio.size) % audio.size) }

    private fun togglePlay() {
        val p = mp
        if (p == null) { if (audio.isNotEmpty()) playAudio(0); return }
        try {
            if (p.isPlaying) { p.pause(); playGlyph.g = G.PLAY } else { p.start(); playGlyph.g = G.PAUSE }
            playGlyph.invalidate()
        } catch (e: Exception) { }
    }

    // ------------------------------------------------------------------ video
    private fun openVideo(f: File) {
        try { mp?.pause(); playGlyph.g = G.PLAY; playGlyph.invalidate() } catch (e: Exception) { }
        videoLayer.removeAllViews()
        val vv = VideoView(a)
        videoView = vv
        vv.setVideoPath(f.path)
        vv.setOnPreparedListener { it.start() }
        vv.setOnCompletionListener { closeVideo() }
        vv.setOnClickListener { if (vv.isPlaying) vv.pause() else vv.start() }
        videoLayer.addView(vv, LayoutParams(-1, -1, Gravity.CENTER))
        val back = label(a, "‹ Kembali", 12f, 0xFFFFFFFF.toInt()).apply {
            setPadding(a.dp(14), 0, a.dp(14), 0); gravity = Gravity.CENTER
            setBackgroundColor(0x88000000.toInt())
            setOnClickListener { closeVideo() }
        }
        videoLayer.addView(back, LayoutParams(WRAP, a.dp(34), Gravity.TOP or Gravity.START))
        videoLayer.visibility = View.VISIBLE
    }

    private fun closeVideo() {
        videoView?.stopPlayback()
        videoView = null
        videoLayer.removeAllViews()
        videoLayer.visibility = View.GONE
    }

    // ------------------------------------------------------------------ daftar
    private inner class Adapter : BaseAdapter() {
        private fun items(): List<File> = if (tab == 0) audio else video
        override fun getCount() = items().size
        override fun getItem(position: Int): Any = items()[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? LinearLayout) ?: hBox(a).apply {
                pressable(5); setPadding(a.dp(14), 0, a.dp(14), 0)
                addView(Glyph(a, G.MUSIC), lp(a.dp(20), a.dp(20)))
                addView(label(a, "", 12.5f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(a.dp(12), 0, 0, 0) }, lp(0, WRAP, 1f))
                layoutParams = AbsListView.LayoutParams(MATCH, a.dp(40))
            }
            val f = items()[position]
            val g = row.getChildAt(0) as Glyph
            g.g = if (tab == 0) (if (position == cur) G.PLAY else G.MUSIC) else G.PLAY
            g.invalidate()
            val t = row.getChildAt(1) as TextView
            t.text = f.nameWithoutExtension
            t.setTextColor(if (tab == 0 && position == cur) Theme.accent else Theme.text)
            return row
        }
    }
}
