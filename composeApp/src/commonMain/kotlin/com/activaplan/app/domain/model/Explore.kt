package com.activaplan.app.domain.model

/** Satu lubang di diagram board; [row] 0..1 dan [column] 0..5. */
data class BoardHole(val row: Int, val column: Int)

/**
 * Diagram board 2 baris × 6 lubang. Pola Biru (Dada) mengikuti Figma; pola zona lain mengikuti gaya yang sama
 * karena Figma baru memuat zona Biru.
 */
object BoardLayout {
    const val ROWS = 2
    const val COLUMNS = 6

    /** Thumbnail kartu memadatkan tiap dua kolom menjadi satu. */
    const val COMPACT_COLUMNS = COLUMNS / 2

    fun highlightedHoles(zone: BoardZone): Set<BoardHole> = when (zone) {
        BoardZone.BLUE -> holes(0 to 0, 0 to 5, 1 to 2, 1 to 3)
        BoardZone.RED -> holes(0 to 1, 0 to 4, 1 to 1, 1 to 4)
        BoardZone.YELLOW -> holes(0 to 0, 0 to 5, 1 to 1, 1 to 4)
        BoardZone.GREEN -> holes(0 to 2, 0 to 3, 1 to 2, 1 to 3)
    }

    /** Lubang tersorot untuk thumbnail 2 × 3. */
    fun compactHighlightedHoles(zone: BoardZone): Set<BoardHole> =
        highlightedHoles(zone).mapTo(mutableSetOf()) { BoardHole(it.row, it.column / 2) }

    private fun holes(vararg cells: Pair<Int, Int>): Set<BoardHole> = cells.mapTo(mutableSetOf()) { BoardHole(it.first, it.second) }
}

/** Kondisi filter di Jelajah; semua kondisi digabung AND. [category] dan [difficulty] null berarti semua. */
data class ExploreQuery(
    val mode: Mode,
    val text: String = "",
    val category: Category? = null,
    val difficulty: Difficulty? = null,
    /** [ActivityKind.CARDIO] mengabaikan [mode] dan [category]; hanya teks dan level yang berlaku. */
    val kind: ActivityKind = ActivityKind.STRENGTH,
)

/**
 * Latihan beserta tetangga levelnya. [regressionId] dan [progressionId] hanya terisi bila ID itu ada di katalog,
 * jadi tombol Turun/Naik level tidak pernah menuju ID tak dikenal. [safetyNotes] hanya terisi untuk mode tali.
 */
data class ExerciseDetail(
    val exercise: Exercise,
    val regressionId: String?,
    val progressionId: String?,
    val safetyNotes: List<String>,
)

/** Kardio beserta tetangga levelnya; ID hanya terisi bila ada di katalog kardio. */
data class CardioDetail(
    val workout: CardioWorkout,
    val regressionId: String?,
    val progressionId: String?,
)

/** Hasil menambah latihan ke rutinitas. */
sealed interface AddToRoutineResult {
    data class Added(val routineName: String) : AddToRoutineResult
    data object RoutineFull : AddToRoutineResult
    data object RoutineNotFound : AddToRoutineResult
    data object InvalidName : AddToRoutineResult
}
