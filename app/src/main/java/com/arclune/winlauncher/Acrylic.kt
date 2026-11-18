package com.arclune.winlauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View

/**
 * Efek kaca/Mica: wallpaper di-blur SEKALI (di bitmap kecil), lalu tiap panel hanya
 * menggambar potongan bitmap itu + tint. Tidak ada blur per-frame, jadi tetap ringan.
 */
object Acrylic {
    var blurred: Bitmap? = null
    var srcW = 1
    var srcH = 1

    fun rebuild(wall: WallpaperView, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        srcW = w; srcH = h
        val sw = (w / 10).coerceAtLeast(40)
        val sh = (h / 10).coerceAtLeast(24)
        val bmp = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(sw / w.toFloat(), sh / h.toFloat())
        wall.paintTo(c, w.toFloat(), h.toFloat())
        val px = IntArray(sw * sh)
        bmp.getPixels(px, 0, sw, 0, 0, sw, sh)
        repeat(3) { blurPass(px, sw, sh, 3) }
        bmp.setPixels(px, 0, sw, 0, 0, sw, sh)
        blurred = bmp
    }

    /** Box blur horizontal lalu vertikal, radius r (piksel bitmap kecil). */
    private fun blurPass(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        for (y in 0 until h) for (x in 0 until w) {
            var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
            for (k in -r..r) {
                val xx = (x + k).coerceIn(0, w - 1)
                val p = px[y * w + xx]
                a += p ushr 24; rr += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255; n++
            }
            tmp[y * w + x] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
        for (y in 0 until h) for (x in 0 until w) {
            var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
            for (k in -r..r) {
                val yy = (y + k).coerceIn(0, h - 1)
                val p = tmp[yy * w + x]
                a += p ushr 24; rr += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255; n++
            }
            px[y * w + x] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
    }
}

/** Latar panel: potongan wallpaper ter-blur + tint. Bila efek kaca mati, jatuh ke warna solid. */
class AcrylicDrawable(
    private val view: View,
    private val radiusPx: Float,
    private val topLineOnly: Boolean = false
) : Drawable() {
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val rect = RectF()
    private val clip = Path()
    private val src = Rect()

    override fun draw(c: Canvas) {
        rect.set(bounds)
        clip.reset(); clip.addRoundRect(rect, radiusPx, radiusPx, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        val b = Acrylic.blurred
        if (Theme.acrylic && b != null) {
            val sx = b.width / Acrylic.srcW.toFloat()
            val sy = b.height / Acrylic.srcH.toFloat()
            val ox = view.left + view.translationX
            val oy = view.top + view.translationY
            val l = (ox * sx).toInt().coerceIn(0, b.width - 2)
            val t = (oy * sy).toInt().coerceIn(0, b.height - 2)
            val r = ((ox + rect.width()) * sx).toInt().coerceIn(l + 1, b.width)
            val bt = ((oy + rect.height()) * sy).toInt().coerceIn(t + 1, b.height)
            src.set(l, t, r, bt)
            c.drawBitmap(b, src, rect, bmpPaint)
            fill.color = if (Theme.dark) Color.argb(178, 24, 24, 24) else Color.argb(182, 247, 247, 247)
            c.drawRect(rect, fill)
        } else {
            fill.color = Theme.surface
            c.drawRect(rect, fill)
        }
        c.restore()
        line.color = Theme.stroke
        if (topLineOnly) c.drawLine(rect.left, rect.top + 0.75f, rect.right, rect.top + 0.75f, line)
        else {
            rect.inset(0.75f, 0.75f)
            c.drawRoundRect(rect, radiusPx, radiusPx, line)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

fun acrylicBg(v: View, radiusDp: Int, topLineOnly: Boolean = false): AcrylicDrawable =
    AcrylicDrawable(v, v.context.dpf(radiusDp.toFloat()), topLineOnly)
