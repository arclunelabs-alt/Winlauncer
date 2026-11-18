package com.arclune.winlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class G {
    START, SEARCH, TASKVIEW, WIDGETS, WIFI, VOLUME, BATTERY, BELL, CHEVRON, POWER, FOLDER, PC, TRASH,
    SETTINGS, BT, PLANE, SAVER, ACCESS, CAST, SUN, USER, CLOSE, MIN, MAX, RESTORE, BACK, NEXT, NIGHT, PALETTE, APPS,
    CALC, NOTE, CLOCK, UP, PLUS, FILE, IMAGE, MUSIC, GLOBE, PULSE, BRUSH, LOCK, CLIP, REFRESH, ERASER,
    PLAY, PAUSE, STAR, MORE
}

/** Ikon garis bergaya Fluent, digambar langsung dengan Canvas (tanpa aset gambar). */
class Glyph(ctx: Context, var g: G, var tint: Int = 0, var level: Float = 1f) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val path = Path()

    private fun col(): Int = when {
        tint != 0 -> tint
        g == G.START -> if (Theme.dark) 0xFF4CC2FF.toInt() else 0xFF0067C0.toInt()
        else -> Theme.text
    }

    private fun stroke() { p.style = Paint.Style.STROKE; p.strokeWidth = 1.7f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND }
    private fun fill() { p.style = Paint.Style.FILL }
    private fun line(c: Canvas, a: Float, b: Float, x: Float, y: Float) = c.drawLine(a, b, x, y, p)
    private fun rr(c: Canvas, l: Float, t: Float, rt: Float, b: Float, rad: Float) {
        r.set(l, t, rt, b); c.drawRoundRect(r, rad, rad, p)
    }

    override fun onDraw(c: Canvas) {
        val s = min(width, height) / 24f
        c.save()
        c.translate((width - 24f * s) / 2f, (height - 24f * s) / 2f)
        c.scale(s, s)
        p.color = col()
        when (g) {
            G.START -> {
                fill()
                rr(c, 3f, 3f, 11.2f, 11.2f, 1.2f); rr(c, 12.8f, 3f, 21f, 11.2f, 1.2f)
                rr(c, 3f, 12.8f, 11.2f, 21f, 1.2f); rr(c, 12.8f, 12.8f, 21f, 21f, 1.2f)
            }
            G.SEARCH -> { stroke(); c.drawCircle(10f, 10f, 6.5f, p); line(c, 15f, 15f, 20.5f, 20.5f) }
            G.TASKVIEW -> { stroke(); rr(c, 3f, 7f, 17f, 19f, 2.5f); rr(c, 7f, 4f, 21f, 16f, 2.5f) }
            G.WIDGETS -> {
                stroke()
                rr(c, 3f, 3f, 11f, 13f, 2f); rr(c, 13f, 3f, 21f, 9f, 2f)
                rr(c, 3f, 15f, 11f, 21f, 2f); rr(c, 13f, 11f, 21f, 21f, 2f)
            }
            G.WIFI -> {
                stroke()
                for (rad in floatArrayOf(14f, 9f, 4.5f)) {
                    r.set(12f - rad, 19f - rad, 12f + rad, 19f + rad); c.drawArc(r, 227f, 86f, false, p)
                }
                fill(); c.drawCircle(12f, 19f, 1.5f, p)
            }
            G.VOLUME -> {
                fill()
                path.reset(); path.moveTo(3f, 9f); path.lineTo(7.5f, 9f); path.lineTo(13f, 4.5f)
                path.lineTo(13f, 19.5f); path.lineTo(7.5f, 15f); path.lineTo(3f, 15f); path.close()
                c.drawPath(path, p)
                stroke()
                for (rad in floatArrayOf(4f, 8f)) {
                    r.set(13f - rad, 12f - rad, 13f + rad, 12f + rad); c.drawArc(r, 315f, 90f, false, p)
                }
            }
            G.BATTERY, G.SAVER -> {
                stroke(); rr(c, 2.5f, 7.5f, 19f, 16.5f, 2.2f)
                fill(); rr(c, 20.2f, 10.5f, 21.7f, 13.5f, 0.7f)
                val w = (level.coerceIn(0.05f, 1f)) * 13.4f
                rr(c, 4.3f, 9.3f, 4.3f + w, 14.7f, 1f)
                if (g == G.SAVER) {
                    p.color = if (Theme.dark) 0xFF202020.toInt() else 0xFFFFFFFF.toInt()
                    stroke(); p.strokeWidth = 1.5f
                    line(c, 11f, 10.5f, 11f, 13.5f); line(c, 9.5f, 12f, 12.5f, 12f)
                }
            }
            G.BELL -> {
                stroke()
                r.set(6f, 4f, 18f, 16f); c.drawArc(r, 180f, 180f, false, p)
                line(c, 6f, 10f, 6f, 17f); line(c, 18f, 10f, 18f, 17f); line(c, 4f, 17.5f, 20f, 17.5f)
                fill(); c.drawCircle(12f, 20.5f, 1.5f, p)
            }
            G.CHEVRON -> { stroke(); path.reset(); path.moveTo(7f, 15f); path.lineTo(12f, 10f); path.lineTo(17f, 15f); c.drawPath(path, p) }
            G.BACK -> { stroke(); path.reset(); path.moveTo(15f, 5f); path.lineTo(8f, 12f); path.lineTo(15f, 19f); c.drawPath(path, p) }
            G.NEXT -> { stroke(); path.reset(); path.moveTo(9f, 5f); path.lineTo(16f, 12f); path.lineTo(9f, 19f); c.drawPath(path, p) }
            G.POWER -> {
                stroke(); r.set(5f, 6f, 19f, 20f); c.drawArc(r, 300f, 300f, false, p); line(c, 12f, 3f, 12f, 12f)
            }
            G.FOLDER -> {
                fill(); p.color = 0xFFFFC83D.toInt()
                rr(c, 2.5f, 4.5f, 10.5f, 9f, 1.5f); p.color = 0xFFFFD968.toInt(); rr(c, 2.5f, 7f, 21.5f, 19.5f, 2f)
            }
            G.PC -> { stroke(); rr(c, 3f, 4f, 21f, 16f, 2f); line(c, 8f, 20f, 16f, 20f); line(c, 12f, 16f, 12f, 20f) }
            G.TRASH -> {
                stroke(); line(c, 4f, 6f, 20f, 6f); rr(c, 6f, 6f, 18f, 21f, 2f); rr(c, 9f, 3f, 15f, 6f, 1f)
                line(c, 10f, 10f, 10f, 17f); line(c, 14f, 10f, 14f, 17f)
            }
            G.SETTINGS -> {
                stroke(); c.drawCircle(12f, 12f, 3.2f, p); c.drawCircle(12f, 12f, 7.2f, p)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    line(c, 12f + 7.2f * cos(a).toFloat(), 12f + 7.2f * sin(a).toFloat(),
                        12f + 10f * cos(a).toFloat(), 12f + 10f * sin(a).toFloat())
                }
            }
            G.BT -> {
                stroke(); path.reset(); path.moveTo(7.5f, 8f); path.lineTo(16.5f, 16f); path.lineTo(12f, 20f)
                path.lineTo(12f, 4f); path.lineTo(16.5f, 8f); path.lineTo(7.5f, 16f); c.drawPath(path, p)
            }
            G.PLANE -> {
                stroke(); line(c, 12f, 3f, 12f, 21f)
                path.reset(); path.moveTo(12f, 9f); path.lineTo(21f, 14f); path.lineTo(21f, 16f); path.lineTo(12f, 13.5f)
                path.lineTo(3f, 16f); path.lineTo(3f, 14f); path.close(); c.drawPath(path, p)
                line(c, 9f, 20.5f, 15f, 20.5f)
            }
            G.ACCESS -> {
                stroke(); c.drawCircle(12f, 12f, 9f, p)
                line(c, 7.5f, 10f, 16.5f, 10f); line(c, 12f, 10f, 12f, 14f)
                line(c, 12f, 14f, 9.5f, 18f); line(c, 12f, 14f, 14.5f, 18f)
                fill(); c.drawCircle(12f, 7.3f, 1.3f, p)
            }
            G.CAST -> {
                stroke(); path.reset(); path.moveTo(3f, 6f); path.lineTo(21f, 6f); path.lineTo(21f, 18f); path.lineTo(14f, 18f)
                c.drawPath(path, p)
                for (rad in floatArrayOf(5f, 9f)) {
                    r.set(3f - rad, 19f - rad, 3f + rad, 19f + rad); c.drawArc(r, 270f, 90f, false, p)
                }
                fill(); c.drawCircle(3.5f, 18.5f, 1.2f, p)
            }
            G.SUN -> {
                stroke(); c.drawCircle(12f, 12f, 4f, p)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    line(c, 12f + 7f * cos(a).toFloat(), 12f + 7f * sin(a).toFloat(),
                        12f + 9.5f * cos(a).toFloat(), 12f + 9.5f * sin(a).toFloat())
                }
            }
            G.USER -> {
                fill(); c.drawCircle(12f, 8f, 4.2f, p)
                r.set(4f, 14.5f, 20f, 30f); c.drawArc(r, 180f, 180f, true, p)
            }
            G.CLOSE -> { stroke(); p.strokeWidth = 1.5f; line(c, 6f, 6f, 18f, 18f); line(c, 18f, 6f, 6f, 18f) }
            G.MIN -> { stroke(); p.strokeWidth = 1.5f; line(c, 6f, 12f, 18f, 12f) }
            G.MAX -> { stroke(); p.strokeWidth = 1.5f; rr(c, 6f, 6f, 18f, 18f, 1.5f) }
            G.RESTORE -> {
                stroke(); p.strokeWidth = 1.5f
                rr(c, 5.5f, 9f, 15f, 18.5f, 1.5f)
                path.reset(); path.moveTo(9f, 9f); path.lineTo(9f, 6f); path.lineTo(18.5f, 6f); path.lineTo(18.5f, 15f); path.lineTo(15f, 15f)
                c.drawPath(path, p)
            }
            G.NIGHT -> {
                fill(); val a = Path().apply { addCircle(11f, 12f, 8f, Path.Direction.CW) }
                val b = Path().apply { addCircle(16f, 8.5f, 7f, Path.Direction.CW) }
                path.reset(); path.op(a, b, Path.Op.DIFFERENCE); c.drawPath(path, p)
            }
            G.PALETTE -> {
                stroke(); c.drawCircle(12f, 12f, 9f, p)
                fill()
                c.drawCircle(8.5f, 10f, 1.4f, p); c.drawCircle(12f, 7.5f, 1.4f, p); c.drawCircle(15.5f, 10f, 1.4f, p)
            }
            G.CALC -> {
                stroke(); rr(c, 4.5f, 3f, 19.5f, 21f, 2.5f); rr(c, 7.5f, 6f, 16.5f, 10f, 1f)
                fill()
                for (x in floatArrayOf(8.5f, 12f, 15.5f)) for (y in floatArrayOf(13.5f, 17.5f)) c.drawCircle(x, y, 1.1f, p)
            }
            G.NOTE -> {
                stroke(); rr(c, 5f, 3f, 19f, 21f, 2.5f)
                line(c, 8.5f, 8f, 15.5f, 8f); line(c, 8.5f, 12f, 15.5f, 12f); line(c, 8.5f, 16f, 13f, 16f)
            }
            G.CLOCK -> {
                stroke(); c.drawCircle(12f, 12f, 9f, p); line(c, 12f, 12f, 12f, 6.5f); line(c, 12f, 12f, 16f, 14.5f)
            }
            G.UP -> {
                stroke(); line(c, 12f, 19f, 12f, 5f)
                path.reset(); path.moveTo(6f, 11f); path.lineTo(12f, 5f); path.lineTo(18f, 11f); c.drawPath(path, p)
            }
            G.PLUS -> { stroke(); line(c, 12f, 5f, 12f, 19f); line(c, 5f, 12f, 19f, 12f) }
            G.FILE -> {
                stroke(); path.reset(); path.moveTo(6f, 3f); path.lineTo(14f, 3f); path.lineTo(19f, 8f)
                path.lineTo(19f, 21f); path.lineTo(6f, 21f); path.close(); c.drawPath(path, p)
                path.reset(); path.moveTo(14f, 3f); path.lineTo(14f, 8f); path.lineTo(19f, 8f); c.drawPath(path, p)
            }
            G.IMAGE -> {
                stroke(); rr(c, 3f, 5f, 21f, 19f, 2f)
                path.reset(); path.moveTo(4f, 18f); path.lineTo(10f, 12f); path.lineTo(14f, 16f); path.lineTo(17f, 13f); path.lineTo(20f, 17f)
                c.drawPath(path, p)
                fill(); c.drawCircle(8.5f, 9.5f, 1.6f, p)
            }
            G.MUSIC -> {
                fill(); c.drawCircle(8f, 18f, 2.6f, p)
                stroke(); line(c, 10.6f, 18f, 10.6f, 5f); line(c, 10.6f, 5f, 18f, 7.5f); line(c, 18f, 7.5f, 18f, 9.5f)
            }
            G.GLOBE -> {
                stroke(); c.drawCircle(12f, 12f, 9f, p)
                r.set(8f, 3f, 16f, 21f); c.drawOval(r, p)
                line(c, 3f, 12f, 21f, 12f); line(c, 4.6f, 7.5f, 19.4f, 7.5f); line(c, 4.6f, 16.5f, 19.4f, 16.5f)
            }
            G.PULSE -> {
                stroke(); path.reset(); path.moveTo(3f, 12f); path.lineTo(8f, 12f); path.lineTo(10.5f, 5.5f)
                path.lineTo(14f, 18.5f); path.lineTo(16.5f, 12f); path.lineTo(21f, 12f); c.drawPath(path, p)
            }
            G.BRUSH -> {
                stroke(); c.save(); c.rotate(45f, 12f, 12f)
                rr(c, 10f, 2.5f, 14f, 14f, 2f); line(c, 12f, 14f, 12f, 21f)
                c.restore()
            }
            G.LOCK -> {
                stroke(); rr(c, 5f, 10.5f, 19f, 21f, 2.5f)
                r.set(8f, 4f, 16f, 14f); c.drawArc(r, 180f, 180f, false, p)
                line(c, 8f, 9f, 8f, 10.5f); line(c, 16f, 9f, 16f, 10.5f)
                fill(); c.drawCircle(12f, 15.8f, 1.4f, p)
            }
            G.CLIP -> {
                stroke(); rr(c, 6f, 5f, 18f, 21f, 2f); rr(c, 9f, 3f, 15f, 7f, 1.5f)
                line(c, 9f, 12f, 15f, 12f); line(c, 9f, 16f, 15f, 16f)
            }
            G.REFRESH -> {
                stroke(); r.set(5f, 5f, 19f, 19f); c.drawArc(r, 40f, 290f, false, p)
                line(c, 18.6f, 9.6f, 18.6f, 5.4f); line(c, 18.6f, 9.6f, 14.4f, 9.6f)
            }
            G.ERASER -> {
                stroke(); c.save(); c.rotate(-45f, 12f, 12f)
                rr(c, 5f, 8.5f, 19f, 16f, 2f); line(c, 11f, 8.5f, 11f, 16f)
                c.restore()
            }
            G.PLAY -> {
                fill(); path.reset(); path.moveTo(8f, 5f); path.lineTo(19f, 12f); path.lineTo(8f, 19f); path.close(); c.drawPath(path, p)
            }
            G.PAUSE -> { fill(); rr(c, 6.5f, 5f, 10.5f, 19f, 1.2f); rr(c, 13.5f, 5f, 17.5f, 19f, 1.2f) }
            G.STAR -> {
                if (level >= 0.5f) fill() else stroke()
                path.reset()
                for (i in 0 until 10) {
                    val rad = if (i % 2 == 0) 9.5f else 4.2f
                    val ang = Math.toRadians(-90.0 + i * 36.0)
                    val px = 12f + rad * cos(ang).toFloat(); val py = 12.5f + rad * sin(ang).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close(); c.drawPath(path, p)
            }
            G.MORE -> { fill(); for (y in floatArrayOf(5f, 12f, 19f)) c.drawCircle(12f, y, 1.7f, p) }
            G.APPS -> {
                fill()
                for (x in floatArrayOf(5f, 12f, 19f)) for (y in floatArrayOf(5f, 12f, 19f)) c.drawCircle(x, y, 1.6f, p)
            }
        }
        c.restore()
    }
}
