package com.activaplan.app.domain.usecase

import com.activaplan.app.data.repository.CardioRepository
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.data.repository.UserDataRepository
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.DraftLoad
import com.activaplan.app.domain.model.Routine
import com.activaplan.app.domain.model.RoutineDraft
import com.activaplan.app.domain.model.RoutineRules
import com.activaplan.app.domain.model.SaveRoutineResult
import com.activaplan.app.domain.model.TemplateDetail
import kotlinx.coroutines.flow.first

/**
 * Memuat rutinitas [routineId] ke Builder; null membuat rutinitas baru yang kosong.
 * Item yang latihannya tidak ada di katalog dilewati dan dihitung di [DraftLoad.Loaded.skippedItems].
 */
class LoadRoutineDraftUseCase(
    private val userData: UserDataRepository,
    private val exercises: ExerciseRepository,
    private val newId: () -> String = ::randomRoutineId,
    private val cardio: CardioRepository? = null,
) {

    suspend operator fun invoke(routineId: String?): DraftLoad {
        if (routineId == null) {
            val id = newId()
            return DraftLoad.Loaded(RoutineDraft(id), Routine(id, "", emptyList()), skippedItems = 0)
        }
        val routine = userData.routines.first().firstOrNull { it.id == routineId } ?: return DraftLoad.NotFound
        val known = exercises.catalog().exercises.mapTo(mutableSetOf()) { it.id }
        cardio?.catalog()?.workouts?.mapTo(known) { it.id }
        val kept = routine.copy(items = routine.items.filter { it.exerciseId in known })
        return DraftLoad.Loaded(RoutineDraft.from(kept), kept, routine.items.size - kept.items.size)
    }
}

/** Menyimpan hasil edit Builder setelah memeriksa nama (1-40 karakter) dan jumlah latihan (1-12). */
class SaveRoutineUseCase(private val userData: UserDataRepository) {

    suspend operator fun invoke(draft: RoutineDraft): SaveRoutineResult {
        val result = when {
            !draft.isNameValid -> SaveRoutineResult.InvalidName
            draft.items.size < RoutineRules.MIN_ITEMS -> SaveRoutineResult.NoItems
            draft.items.size > RoutineRules.MAX_ITEMS -> SaveRoutineResult.TooManyItems
            else -> SaveRoutineResult.Saved
        }
        if (result == SaveRoutineResult.Saved) userData.saveRoutine(draft.toRoutine())
        return result
    }
}

/** Menghapus rutinitas beserta jadwal terkait. */
class DeleteRoutineUseCase(private val userData: UserDataRepository) {
    suspend operator fun invoke(routineId: String) = userData.deleteRoutine(routineId)
}

/**
 * Menyalin template menjadi rutinitas milik pengguna (ID baru, item baru), jadi mengubah salinan tidak
 * memengaruhi template atau salinan lain. Mengembalikan null bila template tidak ada atau tak punya latihan yang dikenal.
 */
class CreateRoutineFromTemplateUseCase(
    private val userData: UserDataRepository,
    private val exercises: ExerciseRepository,
    private val newId: () -> String = ::randomRoutineId,
) {

    suspend operator fun invoke(templateId: String, language: Language): Routine? {
        val template = exercises.templates(language).firstOrNull { it.id == templateId } ?: return null
        val byId = exercises.catalog().exercises.associateBy { it.id }
        val items = byId.routineItems(template.items)
        if (items.isEmpty()) return null
        val routine = Routine(newId(), template.name.trim().take(RoutineRules.MAX_NAME_LENGTH), items)
        userData.saveRoutine(routine)
        return routine
    }
}

/** Isi pratinjau template: latihannya (diterjemahkan) dan estimasi menit. */
class GetTemplateDetailUseCase(
    private val exercises: ExerciseRepository,
    private val estimate: EstimateRoutineMinutesUseCase = EstimateRoutineMinutesUseCase(),
) {

    suspend operator fun invoke(templateId: String, language: Language): TemplateDetail? {
        val template = exercises.templates(language).firstOrNull { it.id == templateId } ?: return null
        val byId = exercises.exercises(language = language).associateBy { it.id }
        val list = template.items.mapNotNull { byId[it] }
        if (list.isEmpty()) return null
        return TemplateDetail(template.id, template.name, template.difficulty, list, estimate(list.map { it.toRoutineItem() }))
    }
}
