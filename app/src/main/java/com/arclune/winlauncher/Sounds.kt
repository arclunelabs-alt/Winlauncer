package com.arclune.winlauncher

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Suara sistem disintesis dari nada sinus (tanpa file audio). Bisa dimatikan di Pengaturan. */
object Sounds {
    private const val RATE = 22050

    private class Note(val freq: Double, val startMs: Int, val durMs: Int, val vol: Float)

    private fun play(notes: List<Note>) {
        if (!Theme.sounds) return
        Thread {
            try {
                val totalMs = notes.maxOf { it.startMs + it.durMs }
                val n = totalMs * RATE / 1000
                val mix = FloatArray(n)
                for (note in notes) {
                    val s0 = note.startMs * RATE / 1000
                    val len = note.durMs * RATE / 1000
                    for (i in 0 until len) {
                        val idx = s0 + i
                        if (idx >= n) break
                        val t = i / RATE.toDouble()
                        val attack = minOf(1.0, i / (RATE * 0.004))
                        val env = attack * exp(-4.2 * i / len)
                        val w = sin(2 * PI * note.freq * t) + 0.35 * sin(4 * PI * note.freq * t)
                        mix[idx] += (w * env * note.vol * 0.28).toFloat()
                    }
                }
                val pcm = ShortArray(n) { (mix[it].coerceIn(-1f, 1f) * 32767).toInt().toShort() }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC).build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(totalMs + 150L)
                track.release()
            } catch (e: Throwable) { }
        }.start()
    }

    fun startup() = play(listOf(Note(523.25, 0, 900, 1f), Note(659.25, 140, 900, 0.9f),
        Note(783.99, 280, 1000, 0.9f), Note(1046.5, 420, 1400, 0.8f)))
    fun unlock() = play(listOf(Note(659.25, 0, 260, 0.8f), Note(880.0, 110, 420, 0.8f)))
    fun click() = play(listOf(Note(1900.0, 0, 40, 0.5f)))
    fun ping() = play(listOf(Note(880.0, 0, 220, 0.8f), Note(1174.7, 130, 380, 0.7f)))
    fun error() = play(listOf(Note(220.0, 0, 260, 0.9f), Note(196.0, 150, 360, 0.9f)))
}
