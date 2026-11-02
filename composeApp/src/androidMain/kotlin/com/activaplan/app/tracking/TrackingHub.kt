package com.activaplan.app.tracking

import com.activaplan.app.domain.tracking.TrackPoint
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Jembatan satu proses antara [TrackingService] (penerima lokasi) dan ViewModel sesi. Titik tidak disimpan. */
internal object TrackingHub {
    private val flow = MutableSharedFlow<TrackPoint>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val points: SharedFlow<TrackPoint> get() = flow

    fun emit(point: TrackPoint) {
        flow.tryEmit(point)
    }
}
