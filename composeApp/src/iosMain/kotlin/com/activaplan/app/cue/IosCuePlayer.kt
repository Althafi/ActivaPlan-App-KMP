package com.activaplan.app.cue

import com.activaplan.app.domain.cue.CuePlayer
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.AudioToolbox.kSystemSoundID_Vibrate

/** Getar dan nada sistem pendek; nada mengikuti sakelar senyap perangkat. */
class IosCuePlayer : CuePlayer {
    override fun segmentChange() {
        AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        AudioServicesPlaySystemSound(TONE_SEGMENT)
    }

    override fun sessionComplete() {
        AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        AudioServicesPlaySystemSound(TONE_COMPLETE)
    }

    private companion object {
        const val TONE_SEGMENT = 1057u
        const val TONE_COMPLETE = 1025u
    }
}
