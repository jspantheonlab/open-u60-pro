package com.openu60.agent.probe

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlin.math.sqrt

/**
 * Answers the one question the call-audio bridge depends on: does this device's audio HAL
 * let a shell-UID process capture the call downlink and play into the call uplink?
 *
 * Run it on the device during an active call, as shell (the same identity the bridge will use):
 *
 *   adb shell CLASSPATH=$(adb shell pm path com.openu60.agent | cut -d: -f2) \
 *       app_process /system/bin com.openu60.agent.probe.AudioProbe
 *
 * It prints one line per check and records nothing to disk.
 */
object AudioProbe {
    private const val RATE = 16000
    private const val SECONDS = 3

    @JvmStatic
    fun main(args: Array<String>) {
        println("uid=${android.os.Process.myUid()} sdk=${android.os.Build.VERSION.SDK_INT} model=${android.os.Build.MODEL}")
        val outputs = devices(AudioManager.GET_DEVICES_OUTPUTS)
        val inputs = devices(AudioManager.GET_DEVICES_INPUTS)
        println("outputs: " + outputs.joinToString { describe(it) })
        println("inputs:  " + inputs.joinToString { describe(it) })
        val telephonyTx = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_TELEPHONY }
        println("telephony_tx_output: ${if (telephonyTx != null) "yes" else "NO"}")
        println("telephony_rx_input:  ${if (inputs.any { it.type == AudioDeviceInfo.TYPE_TELEPHONY }) "yes" else "NO"}")

        for ((name, source) in listOf(
            "VOICE_DOWNLINK" to MediaRecorder.AudioSource.VOICE_DOWNLINK,
            "VOICE_UPLINK" to MediaRecorder.AudioSource.VOICE_UPLINK,
            "VOICE_CALL" to MediaRecorder.AudioSource.VOICE_CALL,
        )) {
            println("capture $name: ${capture(source)}")
        }
        println("playback to telephony_tx: ${if (telephonyTx == null) "skipped (no device)" else playback(telephonyTx)}")
        println("verdict: " + verdict(telephonyTx != null))
        System.out.flush()
        // Binder threads are not daemons: without this, app_process lingers after main returns.
        System.exit(0)
    }

    private var downlinkOk = false

    private fun verdict(hasTx: Boolean): String = when {
        downlinkOk && hasTx -> "bridge looks possible: downlink capture works and a Telephony Tx output exists"
        downlinkOk -> "half: downlink capture works, but no Telephony Tx output to inject the uplink"
        else -> "no downlink audio (run during an ACTIVE call; if it is active, the HAL does not allow it)"
    }

    @Suppress("UNCHECKED_CAST")
    private fun devices(flags: Int): List<AudioDeviceInfo> = try {
        // No Context in app_process: AudioManager.getDevicesStatic is the hidden static form.
        val m = AudioManager::class.java.getMethod("getDevicesStatic", Int::class.javaPrimitiveType)
        (m.invoke(null, flags) as Array<AudioDeviceInfo>).toList()
    } catch (e: Exception) {
        println("devices($flags) failed: ${e.javaClass.simpleName}: ${e.message}")
        emptyList()
    }

    private fun describe(d: AudioDeviceInfo) = "${d.type}:${d.productName}"

    /** Records [SECONDS] s and reports the RMS level; silence means the source is not wired. */
    private fun capture(source: Int): String {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return "no buffer size ($min)"
        val rec = try {
            @Suppress("MissingPermission")
            AudioRecord(source, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 4)
        } catch (e: Exception) {
            return "constructor threw ${e.javaClass.simpleName}: ${e.message}"
        }
        try {
            if (rec.state != AudioRecord.STATE_INITIALIZED) return "not initialized"
            rec.startRecording()
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) return "did not start"
            val buf = ShortArray(RATE / 10)
            var frames = 0L
            var sumSq = 0.0
            val end = System.nanoTime() + SECONDS * 1_000_000_000L
            while (System.nanoTime() < end) {
                val n = rec.read(buf, 0, buf.size)
                if (n < 0) return "read error $n after $frames frames"
                for (i in 0 until n) sumSq += buf[i].toDouble() * buf[i]
                frames += n
            }
            val rms = if (frames > 0) sqrt(sumSq / frames) else 0.0
            if (source == MediaRecorder.AudioSource.VOICE_DOWNLINK && frames > 0 && rms > 1.0) downlinkOk = true
            return "ok, $frames frames, rms=${"%.1f".format(rms)}" + if (rms <= 1.0) " (silent)" else ""
        } catch (e: Exception) {
            return "threw ${e.javaClass.simpleName}: ${e.message}"
        } finally {
            rec.release()
        }
    }

    /** Plays [SECONDS] s of a quiet 440 Hz tone routed to the Telephony Tx device. */
    private fun playback(tx: AudioDeviceInfo): String {
        val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                )
                .setBufferSizeInBytes(maxOf(min, RATE))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            return "builder threw ${e.javaClass.simpleName}: ${e.message}"
        }
        try {
            if (!track.setPreferredDevice(tx)) return "route to Telephony Tx rejected"
            track.play()
            val tone = ShortArray(RATE / 10) { i -> (2000 * kotlin.math.sin(2 * Math.PI * 440 * i / RATE)).toInt().toShort() }
            repeat(SECONDS * 10) {
                val n = track.write(tone, 0, tone.size)
                if (n < 0) return "write error $n"
            }
            val routed = track.routedDevice
            return "played; routed to ${routed?.let(::describe) ?: "unknown"} (the remote party should hear a beep)"
        } catch (e: Exception) {
            return "threw ${e.javaClass.simpleName}: ${e.message}"
        } finally {
            track.release()
        }
    }
}
