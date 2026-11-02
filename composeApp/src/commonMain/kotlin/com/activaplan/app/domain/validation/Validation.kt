package com.activaplan.app.domain.validation

import com.activaplan.app.domain.i18n.ExerciseDictionary
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.CardioRules
import com.activaplan.app.domain.model.CardioWorkout
import com.activaplan.app.domain.model.Category
import com.activaplan.app.domain.model.Difficulty
import com.activaplan.app.domain.model.PlanDay
import com.activaplan.app.domain.model.PlanDayType
import com.activaplan.app.domain.model.PlanItem
import com.activaplan.app.domain.model.Program
import com.activaplan.app.domain.model.Exercise
import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.model.Routine
import com.activaplan.app.domain.model.ScheduleEntry
import com.activaplan.app.domain.model.SegmentKind
import com.activaplan.app.domain.model.RoutineRules
import com.activaplan.app.domain.model.Template
import com.activaplan.app.domain.model.WeeklyPlan

/** Satu temuan validasi; [subjectId] adalah ID entitas yang bermasalah. */
data class ValidationIssue(val subjectId: String, val message: String)

object ExerciseCatalogValidator {

    fun validate(exercises: List<Exercise>, templates: List<Template>): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val ids = exercises.map { it.id }.toSet()

        exercises.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
            issues += ValidationIssue(it, "ID latihan duplikat")
        }

        exercises.forEach { e ->
            if (e.mode == Mode.PUSHUP_BOARD && e.boardZone == null) {
                issues += ValidationIssue(e.id, "Mode PUSHUP_BOARD wajib boardZone")
            }
            if (e.mode != Mode.PUSHUP_BOARD && e.boardZone != null) {
                issues += ValidationIssue(e.id, "boardZone harus null untuk mode ${e.mode}")
            }
            if ((e.reps == null) == (e.durationSeconds == null)) {
                issues += ValidationIssue(e.id, "Tepat satu dari reps atau durationSeconds harus terisi")
            }
            e.progressionId?.takeIf { it !in ids }?.let {
                issues += ValidationIssue(e.id, "progressionId '$it' tidak ada")
            }
            e.regressionId?.takeIf { it !in ids }?.let {
                issues += ValidationIssue(e.id, "regressionId '$it' tidak ada")
            }
        }

        templates.forEach { t ->
            t.items.filter { it !in ids }.forEach {
                issues += ValidationIssue(t.id, "Item template '$it' tidak ada di exercises")
            }
        }
        return issues
    }
}

object CardioCatalogValidator {

    /** [strengthIds] = ID latihan kekuatan; ID kardio tidak boleh bentrok karena Rutinitas memakai satu ruang ID. */
    fun validate(workouts: List<CardioWorkout>, strengthIds: Set<String>): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val ids = workouts.map { it.id }.toSet()

        workouts.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
            issues += ValidationIssue(it, "ID kardio duplikat")
        }

        workouts.forEach { w ->
            if (w.id in strengthIds) issues += ValidationIssue(w.id, "ID kardio bentrok dengan ID latihan kekuatan")
            if (w.blocks.isEmpty()) issues += ValidationIssue(w.id, "Kardio wajib punya minimal satu blok")
            w.blocks.forEach { b ->
                if (b.repeat !in CardioRules.REPEAT) {
                    issues += ValidationIssue(w.id, "repeat ${b.repeat} di luar ${CardioRules.REPEAT}")
                }
                if (b.segments.isEmpty()) issues += ValidationIssue(w.id, "Blok tanpa segmen")
                b.segments.forEach { s ->
                    if (s.durationSeconds !in 1..CardioRules.MAX_SEGMENT_SECONDS) {
                        issues += ValidationIssue(w.id, "durationSeconds ${s.durationSeconds} tidak valid")
                    }
                    if (s.rpe !in CardioRules.RPE) issues += ValidationIssue(w.id, "rpe ${s.rpe} di luar ${CardioRules.RPE}")
                }
            }
            if (w.expand().none { it.kind == SegmentKind.WORK || it.kind == SegmentKind.STEADY }) {
                issues += ValidationIssue(w.id, "Kardio wajib punya segmen WORK atau STEADY")
            }
            w.progressionId?.takeIf { it !in ids }?.let { issues += ValidationIssue(w.id, "progressionId '$it' tidak ada") }
            w.regressionId?.takeIf { it !in ids }?.let { issues += ValidationIssue(w.id, "regressionId '$it' tidak ada") }
        }
        return issues
    }

    /** Memeriksa terjemahan kardio untuk [language]: tiap kardio punya teks, jumlah tips dan kesalahan sama dengan sumber. */
    fun validateTranslation(
        workouts: List<CardioWorkout>,
        dictionary: ExerciseDictionary,
        language: Language,
    ): List<ValidationIssue> {
        val pack = dictionary.packs[language.code]
            ?: return listOf(ValidationIssue(language.code, "Pack bahasa '${language.code}' tidak ada"))
        val issues = mutableListOf<ValidationIssue>()
        workouts.forEach { w ->
            val t = pack.cardio[w.id]
            if (t == null) {
                issues += ValidationIssue(w.id, "Terjemahan kardio '${language.code}' tidak ada")
                return@forEach
            }
            if (t.name.isBlank()) issues += ValidationIssue(w.id, "Nama terjemahan kosong")
            if (t.summary.isBlank()) issues += ValidationIssue(w.id, "Ringkasan terjemahan kosong")
            if (t.tips.size != w.tips.size) issues += ValidationIssue(w.id, "Jumlah tips berbeda dari sumber")
            if (t.commonMistakes.size != w.commonMistakes.size) {
                issues += ValidationIssue(w.id, "Jumlah commonMistakes berbeda dari sumber")
            }
        }
        val ids = workouts.map { it.id }.toSet()
        pack.cardio.keys.filter { it !in ids }.forEach {
            issues += ValidationIssue(it, "Terjemahan yatim: kardio tidak ada di sumber")
        }
        return issues
    }
}

/**
 * Aturan rencana mingguan: tujuh hari lengkap, minimal satu hari istirahat, ID latihan ada dan sesuai jenis hari,
 * tidak ada latihan di atas level rencana, nilai timpaan dalam batas Builder, nama rutinitas muat, dan lari keras
 * tidak jatuh sehari sebelum hari kaki.
 */
object WeeklyPlanValidator {
    /** Hari kaki = hari kekuatan dengan minimal dua latihan kategori kaki. */
    private const val LEG_EXERCISES_FOR_LEG_DAY = 2

    fun validate(
        plans: List<WeeklyPlan>,
        strengthById: Map<String, Exercise>,
        cardioById: Map<String, CardioWorkout>,
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        plans.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { issues += ValidationIssue(it, "ID rencana duplikat") }
        plans.forEach { plan ->
            val id = plan.id
            if (plan.days.map { it.dayOfWeek }.sorted() != (1..7).toList()) issues += ValidationIssue(id, "Rencana wajib memuat hari 1..7 tepat sekali")
            if (plan.days.none { it.type == PlanDayType.REST }) issues += ValidationIssue(id, "Rencana wajib punya minimal satu hari istirahat")
            plan.days.forEach { day -> issues += validateDay(plan, day, strengthById, cardioById) }
            val byDay = plan.days.associateBy { it.dayOfWeek }
            (1..7).forEach { dow ->
                val today = byDay[dow] ?: return@forEach
                val tomorrow = byDay[dow % 7 + 1] ?: return@forEach
                if (isHardCardio(today, cardioById) && isLegDay(tomorrow, strengthById)) {
                    issues += ValidationIssue(id, "Lari keras hari $dow jatuh sehari sebelum hari kaki")
                }
            }
        }
        return issues
    }

    private fun validateDay(
        plan: WeeklyPlan,
        day: PlanDay,
        strengthById: Map<String, Exercise>,
        cardioById: Map<String, CardioWorkout>,
    ): List<ValidationIssue> {
        val id = plan.id
        val where = "hari ${day.dayOfWeek}"
        val issues = mutableListOf<ValidationIssue>()
        if (day.dayOfWeek !in 1..7) issues += ValidationIssue(id, "dayOfWeek ${day.dayOfWeek} di luar 1..7")
        if (day.name.isBlank() || day.name.length > RoutineRules.MAX_NAME_LENGTH) issues += ValidationIssue(id, "Nama $where kosong atau lebih dari ${RoutineRules.MAX_NAME_LENGTH} karakter")
        when (day.type) {
            PlanDayType.REST -> if (day.items.isNotEmpty()) issues += ValidationIssue(id, "Hari istirahat ($where) tidak boleh punya latihan")
            PlanDayType.STRENGTH, PlanDayType.CARDIO -> {
                if (day.items.isEmpty() || day.items.size > RoutineRules.MAX_ITEMS) issues += ValidationIssue(id, "Jumlah latihan $where harus 1..${RoutineRules.MAX_ITEMS}")
            }
        }
        day.items.forEach { item ->
            when (day.type) {
                PlanDayType.STRENGTH -> {
                    val exercise = strengthById[item.id]
                    if (exercise == null) {
                        issues += ValidationIssue(id, "Latihan '${item.id}' ($where) tidak ada di katalog kekuatan")
                    } else {
                        if (exercise.difficulty.ordinal > plan.difficulty.ordinal) issues += ValidationIssue(id, "'${item.id}' lebih sulit dari level rencana")
                        issues += validateOverrides(id, where, item, exercise)
                    }
                }
                PlanDayType.CARDIO -> {
                    if (item.id !in cardioById) issues += ValidationIssue(id, "Kardio '${item.id}' ($where) tidak ada di katalog")
                    if (item.sets != null || item.reps != null || item.seconds != null || item.restSeconds != null) {
                        issues += ValidationIssue(id, "Kardio '${item.id}' ($where) tidak boleh punya timpaan set/rep")
                    }
                }
                PlanDayType.REST -> Unit
            }
        }
        return issues
    }

    private fun validateOverrides(id: String, where: String, item: PlanItem, exercise: Exercise): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        item.sets?.let { if (it !in RoutineRules.SETS) issues += ValidationIssue(id, "sets $it ($where) di luar ${RoutineRules.SETS}") }
        item.reps?.let {
            if (exercise.reps == null) issues += ValidationIssue(id, "reps pada latihan durasi '${item.id}' ($where)")
            if (it !in RoutineRules.REPS) issues += ValidationIssue(id, "reps $it ($where) di luar ${RoutineRules.REPS}")
        }
        item.seconds?.let {
            if (exercise.durationSeconds == null) issues += ValidationIssue(id, "seconds pada latihan repetisi '${item.id}' ($where)")
            if (it !in RoutineRules.SECONDS || it % RoutineRules.SECONDS_STEP != 0) issues += ValidationIssue(id, "seconds $it ($where) tidak valid")
        }
        item.restSeconds?.let { if (it !in RoutineRules.REST_SECONDS) issues += ValidationIssue(id, "restSeconds $it ($where) di luar ${RoutineRules.REST_SECONDS}") }
        return issues
    }

    private fun isHardCardio(day: PlanDay, cardioById: Map<String, CardioWorkout>) =
        day.type == PlanDayType.CARDIO && day.items.any { cardioById[it.id]?.difficulty == Difficulty.HARD }

    private fun isLegDay(day: PlanDay, strengthById: Map<String, Exercise>) =
        day.type == PlanDayType.STRENGTH &&
            day.items.count { strengthById[it.id]?.category == Category.LEGS } >= LEG_EXERCISES_FOR_LEG_DAY

    /** Terjemahan rencana untuk [language]: nama, ringkasan, jumlah tips, dan nama tiap hari (catatan wajib di hari istirahat). */
    fun validateTranslation(plans: List<WeeklyPlan>, dictionary: ExerciseDictionary, language: Language): List<ValidationIssue> {
        val pack = dictionary.packs[language.code]
            ?: return listOf(ValidationIssue(language.code, "Pack bahasa '${language.code}' tidak ada"))
        val issues = mutableListOf<ValidationIssue>()
        plans.forEach { plan ->
            val text = pack.plans[plan.id]
            if (text == null) {
                issues += ValidationIssue(plan.id, "Terjemahan rencana '${language.code}' tidak ada")
                return@forEach
            }
            if (text.name.isBlank() || text.summary.isBlank()) issues += ValidationIssue(plan.id, "Nama atau ringkasan terjemahan kosong")
            if (text.tips.size != plan.tips.size) issues += ValidationIssue(plan.id, "Jumlah tips berbeda dari sumber")
            plan.days.forEach { day ->
                val dayText = text.days[day.dayOfWeek.toString()]
                when {
                    dayText == null -> issues += ValidationIssue(plan.id, "Terjemahan hari ${day.dayOfWeek} tidak ada")
                    dayText.name.isBlank() || dayText.name.length > RoutineRules.MAX_NAME_LENGTH ->
                        issues += ValidationIssue(plan.id, "Nama terjemahan hari ${day.dayOfWeek} kosong atau lebih dari ${RoutineRules.MAX_NAME_LENGTH} karakter")
                    day.type == PlanDayType.REST && dayText.note.isNullOrBlank() ->
                        issues += ValidationIssue(plan.id, "Catatan terjemahan hari ${day.dayOfWeek} kosong")
                }
            }
        }
        val ids = plans.map { it.id }.toSet()
        pack.plans.keys.filter { it !in ids }.forEach { issues += ValidationIssue(it, "Terjemahan yatim: rencana tidak ada di sumber") }
        return issues
    }
}

/**
 * Aturan program bertahap: ID unik, pekan bernomor 1..n berurutan dengan n paling banyak [Program.MAX_WEEKS], judul dan fokus
 * terisi, serta setiap pekan lolos [WeeklyPlanValidator] (sebagai rencana mingguan penuh).
 */
object ProgramValidator {

    fun validate(
        programs: List<Program>,
        strengthById: Map<String, Exercise>,
        cardioById: Map<String, CardioWorkout>,
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        programs.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { issues += ValidationIssue(it, "ID program duplikat") }
        programs.forEach { program ->
            val id = program.id
            if (program.name.isBlank() || program.summary.isBlank()) issues += ValidationIssue(id, "Nama atau ringkasan program kosong")
            val numbers = program.weeks.map { it.week }
            if (numbers.isEmpty() || numbers.size > Program.MAX_WEEKS || numbers != (1..numbers.size).toList()) {
                issues += ValidationIssue(id, "Pekan wajib bernomor 1..n berurutan, paling banyak ${Program.MAX_WEEKS}")
            }
            program.weeks.forEach { week ->
                if (week.title.isBlank() || week.focus.isBlank()) issues += ValidationIssue(id, "Judul atau fokus pekan ${week.week} kosong")
                if (week.days.none { it.type == PlanDayType.CARDIO } || week.days.none { it.type == PlanDayType.STRENGTH }) {
                    issues += ValidationIssue(id, "Pekan ${week.week} wajib memadukan kekuatan dan kardio")
                }
            }
            issues += WeeklyPlanValidator.validate(program.weekPlans, strengthById, cardioById)
        }
        return issues
    }

    /** Terjemahan program untuk [language]: teks program, tiap pekan, dan nama tiap hari (catatan wajib di hari istirahat). */
    fun validateTranslation(programs: List<Program>, dictionary: ExerciseDictionary, language: Language): List<ValidationIssue> {
        val pack = dictionary.packs[language.code]
            ?: return listOf(ValidationIssue(language.code, "Pack bahasa '${language.code}' tidak ada"))
        val issues = mutableListOf<ValidationIssue>()
        programs.forEach { program ->
            val text = pack.programs[program.id]
            if (text == null) {
                issues += ValidationIssue(program.id, "Terjemahan program '${language.code}' tidak ada")
                return@forEach
            }
            if (text.name.isBlank() || text.summary.isBlank()) issues += ValidationIssue(program.id, "Nama atau ringkasan terjemahan kosong")
            if (text.tips.size != program.tips.size) issues += ValidationIssue(program.id, "Jumlah tips program berbeda dari sumber")
            program.weeks.forEach { week ->
                val weekText = text.weeks[week.week.toString()]
                if (weekText == null) {
                    issues += ValidationIssue(program.id, "Terjemahan pekan ${week.week} tidak ada")
                    return@forEach
                }
                if (weekText.title.isBlank() || weekText.focus.isBlank()) issues += ValidationIssue(program.id, "Judul atau fokus terjemahan pekan ${week.week} kosong")
                if (weekText.tips.size != week.tips.size) issues += ValidationIssue(program.id, "Jumlah tips pekan ${week.week} berbeda dari sumber")
                week.days.forEach { day ->
                    val dayText = weekText.days[day.dayOfWeek.toString()]
                    when {
                        dayText == null -> issues += ValidationIssue(program.id, "Terjemahan pekan ${week.week} hari ${day.dayOfWeek} tidak ada")
                        dayText.name.isBlank() || dayText.name.length > RoutineRules.MAX_NAME_LENGTH ->
                            issues += ValidationIssue(program.id, "Nama terjemahan pekan ${week.week} hari ${day.dayOfWeek} kosong atau lebih dari ${RoutineRules.MAX_NAME_LENGTH} karakter")
                        day.type == PlanDayType.REST && dayText.note.isNullOrBlank() ->
                            issues += ValidationIssue(program.id, "Catatan terjemahan pekan ${week.week} hari ${day.dayOfWeek} kosong")
                    }
                }
            }
            val weekKeys = program.weeks.map { it.week.toString() }.toSet()
            text.weeks.keys.filter { it !in weekKeys }.forEach { issues += ValidationIssue(program.id, "Terjemahan yatim: pekan $it tidak ada di sumber") }
        }
        val ids = programs.map { it.id }.toSet()
        pack.programs.keys.filter { it !in ids }.forEach { issues += ValidationIssue(it, "Terjemahan yatim: program tidak ada di sumber") }
        return issues
    }
}

/** Hasil validasi data pengguna: entri valid dipertahankan, yang rusak diabaikan dan dilaporkan di [issues]. */
data class UserDataValidation(
    val routines: List<Routine>,
    val schedule: List<ScheduleEntry>,
    val issues: List<ValidationIssue>,
) {
    val hasBrokenData: Boolean get() = issues.isNotEmpty()
}

object UserDataValidator {

    fun validate(
        routines: List<Routine>,
        schedule: List<ScheduleEntry>,
        exerciseIds: Set<String>,
    ): UserDataValidation {
        val issues = mutableListOf<ValidationIssue>()

        val validRoutines = routines.map { routine ->
            val (kept, dropped) = routine.items.partition { it.exerciseId in exerciseIds }
            dropped.forEach {
                issues += ValidationIssue(routine.id, "Latihan '${it.exerciseId}' tidak ada, item diabaikan")
            }
            routine.copy(items = kept)
        }
        val routineIds = validRoutines.map { it.id }.toSet()

        val validSchedule = schedule.filter { entry ->
            val problem = when {
                entry.routineId !in routineIds -> "Rutinitas '${entry.routineId}' tidak ada, jadwal diabaikan"
                entry.dayOfWeek !in 1..7 -> "dayOfWeek ${entry.dayOfWeek} di luar 1..7, jadwal diabaikan"
                entry.timeMinutes !in 0..1439 -> "timeMinutes ${entry.timeMinutes} di luar 0..1439, jadwal diabaikan"
                else -> null
            }
            problem?.let { issues += ValidationIssue(entry.id, it) }
            problem == null
        }
        return UserDataValidation(validRoutines, validSchedule, issues)
    }
}

object TranslationValidator {

    /** Memeriksa [dictionary] untuk [language] terhadap data sumber: tiap latihan dan template harus punya terjemahan. */
    fun validate(
        exercises: List<Exercise>,
        templates: List<Template>,
        dictionary: ExerciseDictionary,
        language: Language,
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val muscleTerms = dictionary.muscles.flatMap { it.terms }.toSet()
        val pack = dictionary.packs[language.code]
            ?: return listOf(ValidationIssue(language.code, "Pack bahasa '${language.code}' tidak ada"))

        exercises.forEach { e ->
            val t = pack.exercises[e.id]
            if (t == null) {
                issues += ValidationIssue(e.id, "Terjemahan '${language.code}' tidak ada")
                return@forEach
            }
            if (t.name.isBlank()) issues += ValidationIssue(e.id, "Nama terjemahan kosong")
            if (t.steps.size != e.steps.size) issues += ValidationIssue(e.id, "Jumlah steps berbeda dari sumber")
            if (t.tips.size != e.tips.size) issues += ValidationIssue(e.id, "Jumlah tips berbeda dari sumber")
            if (t.commonMistakes.size != e.commonMistakes.size) {
                issues += ValidationIssue(e.id, "Jumlah commonMistakes berbeda dari sumber")
            }
            e.primaryMuscles.filter { it.lowercase() !in muscleTerms }.forEach {
                issues += ValidationIssue(e.id, "Istilah otot '$it' tidak ada di kamus")
            }
        }
        templates.filter { it.id !in pack.templates }.forEach {
            issues += ValidationIssue(it.id, "Terjemahan nama template '${language.code}' tidak ada")
        }
        val exerciseIds = exercises.map { it.id }.toSet()
        pack.exercises.keys.filter { it !in exerciseIds }.forEach {
            issues += ValidationIssue(it, "Terjemahan yatim: latihan tidak ada di sumber")
        }
        return issues
    }
}
