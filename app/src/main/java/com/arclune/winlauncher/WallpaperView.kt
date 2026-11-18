package com.arclune.winlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import java.io.File
import kotlin.math.sin

/** Wallpaper prosedural (6 preset) + foto dari galeri (indeks 6). */
class WallpaperView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private val path = Path()

    companion object {
        const val PRESETS = 6
        const val CUSTOM = 6
        var custom: Bitmap? = null

        fun file(c: Context) = File(c.filesDir, "wallpaper.jpg")

        fun loadCustom(c: Context): Boolean {
            val f = file(c)
            custom = try { if (f.exists()) BitmapFactory.decodeFile(f.path) else null } catch (e: Throwable) { null }
            return custom != null
        }

        /** Warna ringkas untuk thumbnail di Pengaturan: [atas, bawah]. */
        val thumbs = listOf(
            intArrayOf(0xFF061A4A.toInt(), 0xFF1E6FE0.toInt()),
            intArrayOf(0xFF1B0B3A.toInt(), 0xFF6C3BD4.toInt()),
            intArrayOf(0xFF021F1C.toInt(), 0xFF0E8F76.toInt()),
            intArrayOf(0xFF0B0F1A.toInt(), 0xFFB026FF.toInt()),
            intArrayOf(0xFF0B3C5D.toInt(), 0xFF1FB5C9.toInt()),
            intArrayOf(0xFF3A1C71.toInt(), 0xFFFF8A4C.toInt())
        )
    }

    // [latar atas, latar bawah, petal terang, petal gelap]
    private val dark = arrayOf(
        intArrayOf(0xFF061A4A.toInt(), 0xFF1E6FE0.toInt(), 0xFF7FC4FF.toInt(), 0xFF0A3FA8.toInt()),
        intArrayOf(0xFF1B0B3A.toInt(), 0xFF6C3BD4.toInt(), 0xFFD6A8FF.toInt(), 0xFF4A1D9E.toInt()),
        intArrayOf(0xFF021F1C.toInt(), 0xFF0E8F76.toInt(), 0xFF8CF0D0.toInt(), 0xFF05604F.toInt())
    )
    private val light = arrayOf(
        intArrayOf(0xFFBFE0FF.toInt(), 0xFF5AA7F5.toInt(), 0xFFFFFFFF.toInt(), 0xFF2F7CE0.toInt()),
        intArrayOf(0xFFE8D8FF.toInt(), 0xFFA98BEA.toInt(), 0xFFFFFFFF.toInt(), 0xFF7B52D6.toInt()),
        intArrayOf(0xFFCDF5E9.toInt(), 0xFF5FCFB0.toInt(), 0xFFFFFFFF.toInt(), 0xFF22A187.toInt())
    )

    override fun onDraw(c: Canvas) = paintTo(c, width.toFloat(), height.toFloat())

    /** Dipisah dari onDraw agar bisa dipakai juga untuk membuat bitmap blur (Acrylic). */
    fun paintTo(c: Canvas, w: Float, h: Float) {
        val idx = Theme.wallpaper
        val img = custom
        if (idx == CUSTOM && img != null) { cover(c, img, w, h); return }
        when (idx.coerceIn(0, PRESETS - 1)) {
            0, 1, 2 -> bloom(c, w, h, (if (Theme.dark) dark else light)[idx.coerceIn(0, 2)])
            3 -> glow(c, w, h)
            4 -> waves(c, w, h)
            else -> sunrise(c, w, h)
        }
        p.shader = null
    }

    private fun cover(c: Canvas, b: Bitmap, w: Float, h: Float) {
        val s = maxOf(w / b.width, h / b.height)
        val sw = w / s; val sh = h / s
        val l = (b.width - sw) / 2f; val t = (b.height - sh) / 2f
        p.shader = null
        c.drawBitmap(b, Rect(l.toInt(), t.toInt(), (l + sw).toInt(), (t + sh).toInt()), RectF(0f, 0f, w, h), p)
    }

    private fun bloom(c: Canvas, w: Float, h: Float, pal: IntArray) {
        p.shader = LinearGradient(0f, 0f, w * 0.3f, h, pal[0], pal[1], Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        val cx = w * 0.5f; val cy = h * 0.52f; val r = h * 0.62f
        p.shader = RadialGradient(cx, cy, r * 1.3f, intArrayOf(pal[2] and 0x00FFFFFF or 0x66000000, 0x00000000),
            null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 1.3f, p)
        val petals = 8
        for (i in 0 until petals) {
            c.save()
            c.rotate(i * (360f / petals) + 12f, cx, cy)
            oval.set(cx - r * 0.20f, cy - r * 0.95f, cx + r * 0.20f, cy + r * 0.05f)
            p.shader = LinearGradient(cx, cy - r * 0.95f, cx, cy,
                intArrayOf(pal[3] and 0x00FFFFFF or 0xCC000000.toInt(), pal[2] and 0x00FFFFFF or 0xDD000000.toInt()),
                null, Shader.TileMode.CLAMP)
            c.drawOval(oval, p)
            c.scale(0.62f, 0.62f, cx, cy)
            p.shader = LinearGradient(cx, cy - r * 0.95f, cx, cy,
                intArrayOf(pal[2] and 0x00FFFFFF or 0x99000000.toInt(), Color.argb(40, 255, 255, 255)),
                null, Shader.TileMode.CLAMP)
            c.drawOval(oval, p)
            c.restore()
        }
        p.shader = RadialGradient(cx, cy, w * 0.75f, intArrayOf(0x00000000, if (Theme.dark) 0x66000000 else 0x1A000000),
            null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
    }

    private fun glow(c: Canvas, w: Float, h: Float) {
        val d = Theme.dark
        p.shader = null
        p.color = if (d) 0xFF0B0F1A.toInt() else 0xFFF1F4FF.toInt()
        c.drawRect(0f, 0f, w, h, p)
        fun blob(x: Float, y: Float, r: Float, col: Int) {
            p.shader = RadialGradient(x, y, r, intArrayOf(col and 0x00FFFFFF or (if (d) 0xBB000000.toInt() else 0x99000000.toInt()), 0x00000000),
                null, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, p)
        }
        blob(w * 0.2f, h * 0.25f, h * 0.9f, 0xFF00C2FF.toInt())
        blob(w * 0.85f, h * 0.8f, h * 1.0f, 0xFFB026FF.toInt())
        blob(w * 0.55f, h * 0.5f, h * 0.7f, 0xFF3B5BFF.toInt())
    }

    private fun waves(c: Canvas, w: Float, h: Float) {
        val d = Theme.dark
        p.shader = LinearGradient(0f, 0f, 0f, h,
            if (d) 0xFF0B3C5D.toInt() else 0xFFCFF1F7.toInt(), if (d) 0xFF1FB5C9.toInt() else 0xFF4BC6D6.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        for (i in 0 until 5) {
            val base = h * (0.45f + i * 0.11f)
            val amp = h * (0.05f + i * 0.012f)
            path.reset(); path.moveTo(0f, h)
            var x = 0f
            while (x <= w + 12f) {
                path.lineTo(x, base + amp * sin(x / w * (3.2f + i * 0.5f) * 3.14159f + i * 1.3f))
                x += 12f
            }
            path.lineTo(w, h); path.close()
            p.color = if (d) Color.argb(60 + i * 18, 255, 255, 255) else Color.argb(50 + i * 16, 255, 255, 255)
            c.drawPath(path, p)
        }
    }

    private fun sunrise(c: Canvas, w: Float, h: Float) {
        val d = Theme.dark
        p.shader = LinearGradient(0f, 0f, 0f, h,
            intArrayOf(if (d) 0xFF2A1B5E.toInt() else 0xFF8E7CD6.toInt(), if (d) 0xFFD8467A.toInt() else 0xFFFF9FB2.toInt(),
                if (d) 0xFFFF9A4C.toInt() else 0xFFFFD08A.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(w * 0.5f, h * 0.78f, h * 0.55f, intArrayOf(0xFFFFE9A8.toInt(), 0x00FFE9A8), null, Shader.TileMode.CLAMP)
        c.drawCircle(w * 0.5f, h * 0.78f, h * 0.55f, p)
        p.shader = null
        for (i in 0 until 3) {
            val base = h * (0.74f + i * 0.09f)
            path.reset(); path.moveTo(0f, h)
            var x = 0f
            while (x <= w + 16f) {
                path.lineTo(x, base - h * 0.06f * sin(x / w * (2.4f + i) * 3.14159f + i * 2f))
                x += 16f
            }
            path.lineTo(w, h); path.close()
            p.color = if (d) Color.argb(120 + i * 45, 20, 8, 45) else Color.argb(100 + i * 45, 90, 60, 140)
            c.drawPath(path, p)
        }
    }
}
