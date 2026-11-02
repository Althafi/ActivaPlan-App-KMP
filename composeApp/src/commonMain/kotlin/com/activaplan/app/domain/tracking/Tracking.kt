package com.activaplan.app.domain.tracking

import com.activaplan.app.domain.reminder.PermissionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Satu pembacaan GPS. [timeMillis] = waktu pembacaan (epoch ms), [accuracyMeters] = radius galat horizontal bila diketahui. */
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val timeMillis: Long,
    val accuracyMeters: Float? = null,
)

/** Penerima lokasi perangkat (GPS). Tidak memakai internet: titik dihitung dan disimpan di perangkat. */
interface LocationTracker {
    /** Titik baru selama pelacakan aktif; aliran panas, tidak mengulang titik lama untuk pelanggan baru. */
    val points: Flow<TrackPoint>

    /** Status izin lokasi, dibaca ulang setiap dipanggil. */
    suspend fun permission(): PermissionState

    /** Membuka pengaturan sistem aplikasi, untuk izin yang sudah ditolak. */
    fun openSettings()

    /**
     * Mulai menerima lokasi. Di Android ini menyalakan layanan latar dengan notifikasi ([title], [text]) agar
     * pelacakan lanjut saat layar terkunci. Aman dipanggil berulang.
     */
    fun start(title: String, text: String)

    fun stop()
}

/** Pelacak kosong: tanpa titik, izin ditolak. Untuk pratinjau dan tes. */
object NoLocationTracker : LocationTracker {
    override val points: Flow<TrackPoint> = emptyFlow()
    override suspend fun permission(): PermissionState = PermissionState.DENIED
    override fun openSettings() = Unit
    override fun start(title: String, text: String) = Unit
    override fun stop() = Unit
}

/** Hasil akumulasi: [distanceMeters] total, [movingMillis] waktu bergerak, [currentPaceSecPerKm] pace ±20 detik terakhir (null bila belum cukup data). */
data class TrackStats(
    val distanceMeters: Double = 0.0,
    val movingMillis: Long = 0,
    val currentPaceSecPerKm: Int? = null,
) {
    /** Pace rata-rata saat bergerak (detik per km); null bila jarak masih di bawah 50 m. */
    val averagePaceSecPerKm: Int?
        get() = if (distanceMeters >= MIN_AVERAGE_METERS && movingMillis > 0) {
            ((movingMillis / 1000.0) / (distanceMeters / 1000.0)).roundToInt()
        } else {
            null
        }

    val roundedMeters: Int get() = distanceMeters.roundToInt()

    private companion object {
        const val MIN_AVERAGE_METERS = 50.0
    }
}

/** Keadaan GPS yang ditampilkan di sesi. */
enum class GpsStatus { OFF, SEARCHING, ACTIVE }

data class TrackingUi(val status: GpsStatus = GpsStatus.OFF, val stats: TrackStats = TrackStats())

/**
 * Menghitung jarak dan pace dari titik GPS secara murni (tanpa jam dan tanpa platform), jadi bisa dites.
 * Titik dibuang bila: akurasi buruk, waktu mundur, belum cukup jauh dari titik acuan (getaran GPS saat berdiri),
 * loncatan tak masuk akal (> [MAX_SPEED_MPS]), atau terlalu lambat (< [MIN_SPEED_MPS]). Jeda antartitik lebih dari [MAX_GAP_MILLIS] memasang jangkar baru
 * tanpa menambah jarak. [detach] melepas jangkar saat sesi jeda atau di segmen non-kardio.
 */
class TrackAccumulator private constructor(
    val stats: TrackStats,
    private val last: TrackPoint?,
    private val window: List<Step>,
) {
    constructor() : this(TrackStats(), null, emptyList())

    private data class Step(val timeMillis: Long, val meters: Double, val millis: Long)

    fun add(point: TrackPoint): TrackAccumulator {
        if ((point.accuracyMeters ?: 0f) > MAX_ACCURACY_METERS) return this
        val previous = last ?: return TrackAccumulator(stats, point, emptyList())
        val dt = point.timeMillis - previous.timeMillis
        if (dt <= 0) return this
        if (dt > MAX_GAP_MILLIS) return TrackAccumulator(stats, point, emptyList())
        val meters = haversineMeters(previous, point)
        // Getaran GPS saat berdiri: jangkar tidak digeser sampai titik baru cukup jauh dari jangkar (histeresis).
        val threshold = maxOf(MIN_STEP_METERS, (point.accuracyMeters ?: 0f) * ACCURACY_FACTOR)
        if (meters < threshold) return this
        val speed = meters / (dt / 1000.0)
        if (speed > MAX_SPEED_MPS || speed < MIN_SPEED_MPS) return this

        val recent = (window + Step(point.timeMillis, meters, dt)).filter { point.timeMillis - it.timeMillis <= PACE_WINDOW_MILLIS }
        val windowMeters = recent.sumOf { it.meters }
        val windowMillis = recent.sumOf { it.millis }
        val pace = if (windowMeters >= MIN_PACE_METERS) ((windowMillis / 1000.0) / (windowMeters / 1000.0)).roundToInt() else null
        return TrackAccumulator(
            TrackStats(stats.distanceMeters + meters, stats.movingMillis + dt, pace),
            point,
            recent,
        )
    }

    /** Titik berikutnya menjadi jangkar baru; total jarak dipertahankan, pace saat ini dikosongkan. */
    fun detach(): TrackAccumulator =
        if (last == null && window.isEmpty()) this else TrackAccumulator(stats.copy(currentPaceSecPerKm = null), null, emptyList())

    companion object {
        const val MAX_ACCURACY_METERS = 30f
        const val MIN_STEP_METERS = 3.0
        const val ACCURACY_FACTOR = 0.75
        const val MAX_SPEED_MPS = 11.0
        const val MIN_SPEED_MPS = 0.7
        const val MAX_GAP_MILLIS = 15_000L
        const val PACE_WINDOW_MILLIS = 20_000L
        const val MIN_PACE_METERS = 10.0
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        /** Jarak lingkaran besar antara dua titik, dalam meter. */
        fun haversineMeters(a: TrackPoint, b: TrackPoint): Double {
            val lat1 = a.latitude * PI / 180
            val lat2 = b.latitude * PI / 180
            val dLat = lat2 - lat1
            val dLon = (b.longitude - a.longitude) * PI / 180
            val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
        }
    }
}
