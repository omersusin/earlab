package com.omersusin.earlab.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

private const val SR = 44100

/**
 * Programmatic test-signal generator. Everything is synthesized locally —
 * no assets, no network. All output starts with a 2 s fade-in for safety.
 */
object ToneGen {
    private var track: AudioTrack? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    private val main = Handler(Looper.getMainLooper())

    enum class Ear { LEFT, RIGHT, BOTH }

    fun stop() {
        running = false
        worker?.join(500)
        worker = null
        try {
            track?.stop()
            track?.release()
        } catch (_: Exception) {
        }
        track = null
    }

    val isRunning get() = running

    private fun open(channels: Int): AudioTrack {
        stop()
        val cfg = AudioFormat.Builder()
            .setSampleRate(SR)
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(
                if (channels == 1) AudioFormat.CHANNEL_OUT_MONO
                else AudioFormat.CHANNEL_OUT_STEREO,
            ).build()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val t = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(cfg)
            .setBufferSizeInBytes(AudioTrack.getMinBufferSize(SR, cfg.channelMask, cfg.encoding) * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        return t
    }

    private fun fadeIn(i: Int, total: Int): Float {
        val fade = (2 * SR).coerceAtMost(total).toFloat()
        return (i / fade).coerceIn(0f, 1f)
    }

    /** Logarithmic 20 Hz → 20 kHz sweep. Calls [onFreq] with the live frequency. */
    fun sweep(seconds: Int = 30, onFreq: (Float) -> Unit, onDone: () -> Unit) {
        val total = seconds * SR
        val t = open(2)
        running = true
        worker = Thread {
            val buf = FloatArray(2 * 2048)
            var i = 0
            var phase = 0.0
            t.play()
            while (running && i < total) {
                val n = minOf(buf.size / 2, total - i)
                val f0 = 20.0 * 1000.0.pow(i.toDouble() / total)
                for (k in 0 until n) {
                    val f = 20.0 * 1000.0.pow((i + k).toDouble() / total)
                    phase += 2 * PI * f / SR
                    val s = (sin(phase) * 0.5 * fadeIn(i + k, total)).toFloat()
                    buf[2 * k] = s
                    buf[2 * k + 1] = s
                }
                t.write(buf, 0, n * 2, AudioTrack.WRITE_BLOCKING)
                i += n
                val fNow = f0.toFloat()
                main.post { if (running) onFreq(fNow) }
            }
            main.post { stop(); onDone() }
        }.also { it.start() }
    }

    /** 440 Hz identification tone on one or both channels. */
    fun channel(ear: Ear, seconds: Int = 2, onDone: () -> Unit = {}) {
        val total = seconds * SR
        val t = open(2)
        running = true
        worker = Thread {
            val buf = FloatArray(2 * 2048)
            var i = 0
            var phase = 0.0
            t.play()
            while (running && i < total) {
                val n = minOf(buf.size / 2, total - i)
                for (k in 0 until n) {
                    phase += 2 * PI * 440.0 / SR
                    val s = (sin(phase) * 0.5 * fadeIn(i + k, total)).toFloat()
                    buf[2 * k] = if (ear == Ear.RIGHT) 0f else s
                    buf[2 * k + 1] = if (ear == Ear.LEFT) 0f else s
                }
                t.write(buf, 0, n * 2, AudioTrack.WRITE_BLOCKING)
                i += n
            }
            main.post { stop(); onDone() }
        }.also { it.start() }
    }

    /** Pure tone for hearing-threshold steps. [dbFs] in −46..0. */
    fun tone(
        freqHz: Float,
        dbFs: Float,
        ear: Ear,
        seconds: Int = 3,
        onDone: () -> Unit = {},
    ) {
        val amp = 10.0.pow(dbFs / 20.0).toFloat().coerceIn(0f, 1f)
        val total = seconds * SR
        val t = open(2)
        running = true
        worker = Thread {
            val buf = FloatArray(2 * 2048)
            var i = 0
            var phase = 0.0
            t.play()
            while (running && i < total) {
                val n = minOf(buf.size / 2, total - i)
                for (k in 0 until n) {
                    phase += 2 * PI * freqHz / SR
                    // gentle 50 ms edges so clicks never startle
                    val edge = minOf(1f, (i + k) / (0.05f * SR), (total - (i + k)) / (0.05f * SR))
                    val s = (sin(phase) * amp * edge).toFloat()
                    buf[2 * k] = if (ear == Ear.RIGHT) 0f else s
                    buf[2 * k + 1] = if (ear == Ear.LEFT) 0f else s
                }
                t.write(buf, 0, n * 2, AudioTrack.WRITE_BLOCKING)
                i += n
            }
            main.post { stop(); onDone() }
        }.also { it.start() }
    }

    /** Looping noise for burn-in / masking. Pink via Paul Kellet's filter. */
    fun noise(pink: Boolean, onDone: () -> Unit = {}) {
        val t = open(2)
        running = true
        worker = Thread {
            val b = FloatArray(7)
            t.play()
            val buf = FloatArray(2 * 2048)
            var seed = 1L
            fun rnd(): Float {
                seed = seed * 6364136223846793005L + 1442695040888963407L
                return ((seed ushr 33).toFloat() / Int.MAX_VALUE) - 1f
            }
            while (running) {
                for (k in buf.indices step 2) {
                    val w = rnd()
                    val s = if (!pink) {
                        w * 0.25f
                    } else {
                        b[0] = 0.99886f * b[0] + w * 0.0555179f
                        b[1] = 0.99332f * b[1] + w * 0.0750759f
                        b[2] = 0.96900f * b[2] + w * 0.1538520f
                        b[3] = 0.86650f * b[3] + w * 0.3104856f
                        b[4] = 0.55000f * b[4] + w * 0.5329522f
                        b[5] = -0.7616f * b[5] - w * 0.0168980f
                        b[6] = w * 0.115926f
                        (b[0] + b[1] + b[2] + b[3] + b[4] + b[5] + b[6] + w * 0.5362f) * 0.11f
                    }
                    buf[k] = s
                    buf[k + 1] = s
                }
                t.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
            }
            main.post { stop(); onDone() }
        }.also { it.start() }
    }

    fun fmtFreq(hz: Float): String = when {
        hz < 1000 -> "${hz.toInt()} Hz"
        hz < 10000 -> "%.2f kHz".format(hz / 1000)
        else -> "%.1f kHz".format(hz / 1000)
    }
}
