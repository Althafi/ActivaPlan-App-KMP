package com.activaplan.app.cue

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import com.activaplan.app.domain.cue.CuePlayer

/** Getar dan bunyi pendek untuk pergantian segmen; bunyi memakai volume notifikasi dan hilang bila perangkat senyap. */
class AndroidCuePlayer(private val context: Context) : CuePlayer {
    private val handler = Handler(Looper.getMainLooper())

    override fun segmentChange() {
        vibrate(longArrayOf(0, 250, 120, 250))
        beep(ToneGenerator.TONE_PROP_BEEP2, 350)
    }

    override fun sessionComplete() {
        vibrate(longArrayOf(0, 300, 120, 300, 120, 600))
        beep(ToneGenerator.TONE_PROP_ACK, 900)
    }

    private fun vibrate(pattern: LongArray) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun beep(tone: Int, durationMillis: Int) {
        try {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME)
            generator.startTone(tone, durationMillis)
            handler.postDelayed({ generator.release() }, durationMillis + RELEASE_MARGIN_MILLIS)
        } catch (_: RuntimeException) {
            // Perangkat tanpa generator nada: getar saja.
        }
    }

    private companion object {
        const val VOLUME = 80
        const val RELEASE_MARGIN_MILLIS = 300L
    }
}
