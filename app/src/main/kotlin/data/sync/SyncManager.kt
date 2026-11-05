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
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val transferMutex: Mutex,
) {
    private val dao = db.gymDao()

    suspend fun pushPending(): SyncResult = transferMutex.withLock {
        status.setRunning(true)
        try {
            val result = pushInternal()
            when {
                result.warnings.isNotEmpty() -> status.recordWarnings(result.warnings)
                result.sent > 0 -> status.recordSuccess(emptyList())
                else -> status.clearError()
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status.recordError(e.message ?: "Erro desconhecido ao sincronizar.")
            throw e
        } finally {
            status.setRunning(false)
        }
    }

    private suspend fun pushInternal(): SyncResult {
        val token = settings.token() ?: throw MissingTokenException()

        val snapshot = dao.pendingSnapshot()
        val exercises = snapshot.exercises
        val muscles = snapshot.muscles
        val workouts = snapshot.workouts
        val workoutExercises = snapshot.workoutExercises
        val sessions = snapshot.sessions.filter { it.finishedAt != null }
        val finishedIds = snapshot.setParents.filter { it.finishedAt != null }.map { it.publicId }.toSet()
        val sets = snapshot.sets.filter { it.sessionPublicId in finishedIds }
        val deletedSessions = snapshot.deletions.filter { it.entityType == GymRepository.ENTITY_SESSION }.map { it.publicId }
        val deletedExercises = snapshot.deletions.filter { it.entityType == GymRepository.ENTITY_EXERCISE }.map { it.publicId }
        val deletedSets = snapshot.deletions.filter { it.entityType == GymRepository.ENTITY_SET }.map { it.publicId }
        val total = exercises.size + workouts.size + workoutExercises.size + sessions.size + sets.size +
            deletedSessions.size + deletedExercises.size + deletedSets.size
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
            deletedExercises = deletedExercises,
            deletedSessions = deletedSessions,
            deletedSets = deletedSets,
        )

        val response = api.push(settings.state.value.baseUrl, token, batch)

        // Sem confirmação por registro, avisos podem indicar rejeição parcial.
        // Mantemos o lote pendente: o reenvio é idempotente pelos PublicIds.
        if (response.warnings.isNotEmpty()) return SyncResult(0, response.warnings)

        dao.markPushed(
            exercises, workouts, workoutExercises, sessions, sets,
            deletedSessions, GymRepository.ENTITY_SESSION,
            deletedExercises, GymRepository.ENTITY_EXERCISE,
            deletedSets, GymRepository.ENTITY_SET,
        )

        return SyncResult(total, response.warnings)
    }

    private fun iso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()
}
