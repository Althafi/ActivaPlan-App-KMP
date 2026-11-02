package com.activaplan.app.domain.cue

/** Isyarat bunyi dan getar untuk pelari yang tidak menatap layar. Implementasi per platform lewat `platformModule()`. */
interface CuePlayer {
    /** Pergantian segmen kardio: getar dan bunyi pendek. */
    fun segmentChange()

    /** Sesi kardio selesai: pola getar dan bunyi lebih panjang. */
    fun sessionComplete()
}

object NoCuePlayer : CuePlayer {
    override fun segmentChange() = Unit
    override fun sessionComplete() = Unit
}
