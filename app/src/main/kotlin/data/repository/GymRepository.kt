package br.com.ryuukossei.gymprogress.data.repository

import androidx.room.withTransaction
import br.com.ryuukossei.gymprogress.data.local.AppDatabase
import br.com.ryuukossei.gymprogress.data.local.ExerciseEntity
import br.com.ryuukossei.gymprogress.data.local.ExerciseTargetMuscleEntity
import br.com.ryuukossei.gymprogress.data.local.PendingDeletionEntity
import br.com.ryuukossei.gymprogress.data.local.SessionEntity
import br.com.ryuukossei.gymprogress.data.local.SessionSetEntity
import br.com.ryuukossei.gymprogress.data.local.TargetMuscleEntity
import br.com.ryuukossei.gymprogress.data.local.WorkoutEntity
import br.com.ryuukossei.gymprogress.data.local.WorkoutExerciseEntity
import br.com.ryuukossei.gymprogress.data.remote.GymApi
import br.com.ryuukossei.gymprogress.data.remote.SyncExportDto
import br.com.ryuukossei.gymprogress.data.settings.SettingsStore
import java.time.Instant
import java.util.UUID

data class ImportResult(
    val exercises: Int,
    val workouts: Int,
    val sessions: Int,
    val sets: Int,
)

class PendingChangesException(count: Int) :
    Exception("Há $count alteração(ões) ainda não enviadas ao servidor. Sincronize antes de reimportar.")

class MissingTokenException : Exception("Nenhum token configurado.")

/**
 * Ponto único de acesso aos dados. As telas leem do banco local (gymDao);
 * a importação substitui o banco local pelo conteúdo do servidor.
 */
class GymRepository(
    private val db: AppDatabase,
    private val api: GymApi,
    private val settings: SettingsStore,
) {
    val dao = db.gymDao()

    /** Configuração inicial: valida o token baixando os dados e, se der certo, salva a conexão. */
    suspend fun connectAndImport(baseUrl: String, token: String): ImportResult {
        val url = GymApi.normalizeBaseUrl(baseUrl)
        val export = api.export(url, token.trim())
        // Ao trocar o token, alterações locais ainda não enviadas nunca são descartadas:
        // nesse caso só a conexão é salva, sem substituir os dados.
        val pending = dao.countPendingChanges()
        val result = if (pending == 0) {
            replaceLocalData(export).also { settings.setLastImportAt(System.currentTimeMillis()) }
        } else {
            ImportResult(export.exercises.size, export.workouts.size, export.sessions.size, export.sets.size)
        }
        settings.saveConnection(url, token.trim())
        return result
    }

    /**
     * Baixa tudo de novo do servidor. Sem [discardLocalChanges], recusa se houver
     * alterações do celular ainda não enviadas (para nunca perdê-las sem querer).
     */
    suspend fun reimport(discardLocalChanges: Boolean = false): ImportResult {
        val pending = dao.countPendingChanges()
        if (pending > 0 && !discardLocalChanges) throw PendingChangesException(pending)

        val token = settings.token() ?: throw MissingTokenException()
        val export = api.export(settings.state.value.baseUrl, token)
        val result = replaceLocalData(export)
        settings.setLastImportAt(System.currentTimeMillis())
        return result
    }

    fun disconnect() = settings.clearToken()

    // ===== Execução do treino (tudo grava primeiro no celular) =====

    /** Cria uma sessão em andamento para o treino e devolve o id. */
    suspend fun startSession(workoutId: String): String {
        val session = SessionEntity(
            publicId = newId(),
            workoutPublicId = workoutId,
            startedAt = System.currentTimeMillis(),
            finishedAt = null,
            notes = null,
            pendingSync = true,
        )
        dao.upsertSession(session)
        return session.publicId
    }

    /** Grava (ou atualiza) uma série confirmada. */
    suspend fun saveSet(sessionId: String, workoutExerciseId: String, setNumber: Int, reps: Int, weight: Double) {
        db.withTransaction {
            val existing = dao.findSet(sessionId, workoutExerciseId, setNumber)
            dao.upsertSet(
                SessionSetEntity(
                    publicId = existing?.publicId ?: newId(),
                    sessionPublicId = sessionId,
                    workoutExercisePublicId = workoutExerciseId,
                    setNumber = setNumber,
                    performedRepetitions = reps,
                    performedWeight = weight,
                    pendingSync = true,
                )
            )
        }
    }

    suspend fun finishSession(sessionId: String) {
        val session = dao.getSession(sessionId) ?: return
        if (session.finishedAt != null) return
        dao.upsertSession(session.copy(finishedAt = System.currentTimeMillis(), pendingSync = true))
    }

    /**
     * Descarta uma sessão em andamento e as séries dela. A exclusão fica registrada
     * para ser enviada ao servidor (se a sessão já existia lá, ela também é removida).
     */
    suspend fun cancelSession(sessionId: String) {
        db.withTransaction {
            val session = dao.getSession(sessionId) ?: return@withTransaction
            if (session.finishedAt != null) return@withTransaction
            dao.deleteSetsOfSession(sessionId)
            dao.deleteSession(sessionId)
            dao.insertPendingDeletion(PendingDeletionEntity(ENTITY_SESSION, sessionId))
        }
    }

    private fun newId() = UUID.randomUUID().toString()

    companion object {
        const val ENTITY_SESSION = "session"
    }

    private suspend fun replaceLocalData(export: SyncExportDto): ImportResult {
        val exercises = export.exercises.map {
            ExerciseEntity(publicId = id(it.publicId), name = it.name, comment = it.comment)
        }
        val exerciseMuscles = export.exercises.flatMap { e ->
            e.targetMuscleIds.distinct().map { ExerciseTargetMuscleEntity(id(e.publicId), it) }
        }
        val workouts = export.workouts.map {
            WorkoutEntity(
                publicId = id(it.publicId),
                name = it.name,
                category = it.category,
                comment = it.comment,
                isArchived = it.isArchived,
            )
        }
        val workoutExercises = export.workoutExercises.map {
            WorkoutExerciseEntity(
                publicId = id(it.publicId),
                workoutPublicId = id(it.workoutPublicId),
                exercisePublicId = id(it.exercisePublicId),
                sets = it.sets,
                repetitions = it.repetitions,
                weight = it.weight,
                restTimeSeconds = it.restTimeSeconds,
                notes = it.notes,
                order = it.order,
                removedAt = it.removedAt?.let(::epochMillis),
            )
        }
        val sessions = export.sessions.map {
            SessionEntity(
                publicId = id(it.publicId),
                workoutPublicId = id(it.workoutPublicId),
                startedAt = epochMillis(it.startedAt),
                finishedAt = it.finishedAt?.let(::epochMillis),
                notes = it.notes,
            )
        }
        val sets = export.sets.map {
            SessionSetEntity(
                publicId = id(it.publicId),
                sessionPublicId = id(it.sessionPublicId),
                workoutExercisePublicId = id(it.workoutExercisePublicId),
                setNumber = it.setNumber,
                performedRepetitions = it.performedRepetitions,
                performedWeight = it.performedWeight,
            )
        }

        db.withTransaction {
            // Apaga dos filhos para os pais (chaves estrangeiras) e insere na ordem inversa.
            dao.deleteAllSets()
            dao.deleteAllSessions()
            dao.deleteAllWorkoutExercises()
            dao.deleteAllWorkouts()
            dao.deleteAllExerciseMuscles()
            dao.deleteAllExercises()
            dao.deleteAllTargetMuscles()
            dao.deleteAllPendingDeletions()

            dao.insertTargetMuscles(export.targetMuscles.map { TargetMuscleEntity(it.id, it.name) })
            dao.insertExercises(exercises)
            dao.insertExerciseMuscles(exerciseMuscles)
            dao.insertWorkouts(workouts)
            dao.insertWorkoutExercises(workoutExercises)
            dao.insertSessions(sessions)
            dao.insertSets(sets)
        }

        return ImportResult(
            exercises = exercises.size,
            workouts = workouts.size,
            sessions = sessions.size,
            sets = sets.size,
        )
    }

    private fun id(value: String) = value.lowercase()

    private fun epochMillis(iso: String): Long = Instant.parse(iso).toEpochMilli()
}
