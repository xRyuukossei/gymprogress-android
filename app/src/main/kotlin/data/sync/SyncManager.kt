package br.com.ryuukossei.gymprogress.data.sync

import br.com.ryuukossei.gymprogress.data.local.AppDatabase
import br.com.ryuukossei.gymprogress.data.remote.GymApi
import br.com.ryuukossei.gymprogress.data.remote.SyncExerciseDto
import br.com.ryuukossei.gymprogress.data.remote.SyncPushDto
import br.com.ryuukossei.gymprogress.data.remote.SyncSessionDto
import br.com.ryuukossei.gymprogress.data.remote.SyncSetDto
import br.com.ryuukossei.gymprogress.data.remote.SyncWorkoutDto
import br.com.ryuukossei.gymprogress.data.remote.SyncWorkoutExerciseDto
import br.com.ryuukossei.gymprogress.data.repository.GymRepository
import br.com.ryuukossei.gymprogress.data.repository.MissingTokenException
import br.com.ryuukossei.gymprogress.data.settings.SettingsStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

data class SyncResult(val sent: Int, val warnings: List<String>)

/**
 * Envia ao servidor tudo que foi alterado no celular e ainda não foi enviado.
 *
 * O envio é idempotente (o servidor localiza cada registro pelo PublicId), então
 * repetir um envio interrompido não duplica nada. Depois do sucesso, só são marcados
 * como enviados os registros que não mudaram durante o envio; os que mudaram
 * continuam pendentes e vão no próximo.
 */
class SyncManager(
    private val db: AppDatabase,
    private val api: GymApi,
    private val settings: SettingsStore,
    private val status: SyncStatusStore,
) {
    private val dao = db.gymDao()
    private val mutex = Mutex()

    suspend fun pushPending(): SyncResult = mutex.withLock {
        status.setRunning(true)
        try {
            val result = pushInternal()
            status.recordSuccess(result.warnings)
            result
        } catch (e: Exception) {
            status.recordError(e.message ?: "Erro desconhecido ao sincronizar.")
            throw e
        } finally {
            status.setRunning(false)
        }
    }

    private suspend fun pushInternal(): SyncResult {
        val token = settings.token() ?: throw MissingTokenException()

        val exercises = dao.pendingExercises()
        val muscles = if (exercises.isEmpty()) emptyList() else dao.musclesOfExercises(exercises.map { it.publicId })
        val workouts = dao.pendingWorkouts()
        val workoutExercises = dao.pendingWorkoutExercises()
        val sessions = dao.pendingFinishedSessions()
        val sets = dao.pendingSetsOfFinishedSessions()
        val deletions = dao.pendingDeletions()
        val deletedSessions = deletions.filter { it.entityType == GymRepository.ENTITY_SESSION }.map { it.publicId }

        val total = exercises.size + workouts.size + workoutExercises.size + sessions.size + sets.size + deletedSessions.size
        if (total == 0) return SyncResult(0, emptyList())

        val musclesByExercise = muscles.groupBy({ it.exercisePublicId }, { it.targetMuscleId })
        val batch = SyncPushDto(
            exercises = exercises.map {
                SyncExerciseDto(it.publicId, it.name, it.comment, musclesByExercise[it.publicId].orEmpty())
            },
            workouts = workouts.map {
                SyncWorkoutDto(it.publicId, it.name, it.category, it.comment, it.isArchived)
            },
            workoutExercises = workoutExercises.map {
                SyncWorkoutExerciseDto(
                    publicId = it.publicId,
                    workoutPublicId = it.workoutPublicId,
                    exercisePublicId = it.exercisePublicId,
                    sets = it.sets,
                    repetitions = it.repetitions,
                    weight = it.weight,
                    restTimeSeconds = it.restTimeSeconds,
                    notes = it.notes,
                    order = it.order,
                    removedAt = it.removedAt?.let(::iso),
                )
            },
            sessions = sessions.map {
                SyncSessionDto(it.publicId, it.workoutPublicId, iso(it.startedAt), it.finishedAt?.let(::iso), it.notes)
            },
            sets = sets.map {
                SyncSetDto(it.publicId, it.sessionPublicId, it.workoutExercisePublicId, it.setNumber, it.performedRepetitions, it.performedWeight)
            },
            deletedSessions = deletedSessions,
        )

        val response = api.push(settings.state.value.baseUrl, token, batch)

        dao.markPushed(exercises, workouts, workoutExercises, sessions, sets, deletedSessions, GymRepository.ENTITY_SESSION)

        return SyncResult(total, response.warnings)
    }

    private fun iso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()
}
