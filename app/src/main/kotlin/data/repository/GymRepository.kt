package br.com.ryuukossei.gymprogress.data.repository

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
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
    /** Avisado quando há algo novo para enviar ao servidor (ex.: treino finalizado). */
    private val onLocalChange: () -> Unit = {},
) {
    val dao = db.gymDao()

    /**
     * Versão dos dados: muda a cada gravação feita pelo app. As telas usam [observe] para
     * refazer a consulta quando ela muda, sem depender só do aviso automático do Room
     * (que, nesta versão, não chegava às telas depois de cancelar um treino).
     */
    private val dataVersion = MutableStateFlow(0L)

    fun notifyDataChanged() = dataVersion.update { it + 1 }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> observe(query: () -> Flow<T>): Flow<T> = dataVersion.flatMapLatest { query() }

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
        notifyDataChanged()
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
        notifyDataChanged()
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
        notifyDataChanged()
        return session.publicId
    }

    /** Grava (ou atualiza) uma série confirmada. */
    suspend fun saveSet(sessionId: String, workoutExerciseId: String, setNumber: Int, reps: Int, weight: Double) {
        dao.saveSet(sessionId, workoutExerciseId, setNumber, reps, weight, newId = newId())
        notifyDataChanged()
    }

    suspend fun finishSession(sessionId: String) {
        val session = dao.getSession(sessionId) ?: return
        if (session.finishedAt != null) return
        dao.upsertSession(session.copy(finishedAt = System.currentTimeMillis(), pendingSync = true))
        notifyDataChanged()
        onLocalChange()
    }

    /**
     * Descarta uma sessão em andamento e as séries dela. A exclusão fica registrada
     * para ser enviada ao servidor (se a sessão já existia lá, ela também é removida).
     */
    suspend fun cancelSession(sessionId: String) {
        if (dao.cancelInProgressSession(sessionId, ENTITY_SESSION)) {
            notifyDataChanged()
            onLocalChange()
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

        dao.replaceAll(
            muscles = export.targetMuscles.map { TargetMuscleEntity(it.id, it.name) },
            exercises = exercises,
            exerciseMuscles = exerciseMuscles,
            workouts = workouts,
            workoutExercises = workoutExercises,
            sessions = sessions,
            sets = sets,
        )

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
