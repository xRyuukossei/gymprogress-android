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
import br.com.ryuukossei.gymprogress.data.remote.ApiException
import br.com.ryuukossei.gymprogress.data.remote.GymApi
import br.com.ryuukossei.gymprogress.data.remote.SyncExportDto
import br.com.ryuukossei.gymprogress.data.remote.canonicalPublicId
import br.com.ryuukossei.gymprogress.data.remote.validateSyncExport
import br.com.ryuukossei.gymprogress.data.settings.SettingsStore
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val transferMutex: Mutex,
    private val onDataReplaced: () -> Unit = {},
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

    /** Importação e envio compartilham o lock; gravações do treino continuam disponíveis durante a rede. */
    suspend fun connectAndImport(baseUrl: String, token: String): ImportResult = transferMutex.withLock {
        val url = GymApi.normalizeBaseUrl(baseUrl)
        val before = dao.pendingSnapshot()
        val preserveLocal = !before.isEmpty || dao.countInProgressSessions() > 0
        require(!preserveLocal || url == GymApi.normalizeBaseUrl(settings.state.value.baseUrl)) {
            "Envie ou resolva as alterações locais antes de conectar a outro servidor."
        }
        val export = api.export(url, token.trim())
        val connection = settings.prepareConnection(url, token.trim())
        val result = db.withTransaction {
            check(dao.pendingSnapshot() == before) { "Os dados locais mudaram durante o download. Tente novamente." }
            if (!preserveLocal) {
                replaceLocalData(export)
            } else {
                val counts = dao.getCounts()
                ImportResult(counts.exercises, counts.workouts, counts.sessions, counts.sets)
            }
        }
        settings.saveConnection(connection)
        if (!preserveLocal) {
            settings.setLastImportAt(System.currentTimeMillis())
            onDataReplaced()
        }
        notifyDataChanged()
        result
    }

    suspend fun reimport(discardLocalChanges: Boolean = false): ImportResult = transferMutex.withLock {
        val before = dao.pendingSnapshot()
        if (!before.isEmpty && !discardLocalChanges) throw PendingChangesException(dao.countPendingChanges())
        check(discardLocalChanges || dao.countInProgressSessions() == 0) {
            "Há treino em andamento. Finalize-o, cancele-o ou confirme explicitamente o descarte antes de reimportar."
        }
        val token = settings.token() ?: throw MissingTokenException()
        val export = api.export(settings.state.value.baseUrl, token)
        val result = db.withTransaction {
            check(dao.pendingSnapshot() == before) { "Os dados locais mudaram durante o download. Confira as alterações e tente novamente." }
            replaceLocalData(export)
        }
        settings.setLastImportAt(System.currentTimeMillis())
        onDataReplaced()
        notifyDataChanged()
        result
    }

    suspend fun disconnect() = transferMutex.withLock { settings.clearToken() }

    // ===== Execução do treino (tudo grava primeiro no celular) =====

    /** Cria uma sessão em andamento para o treino e devolve o id. */
    suspend fun startSession(workoutId: String): String {
        val id = db.withTransaction {
            val existing = dao.findInProgressSession(workoutId)
            if (existing != null) return@withTransaction existing.publicId
            val workout = dao.getWorkout(workoutId) ?: error("Treino não encontrado.")
            check(!workout.isArchived) { "Restaure este treino antes de iniciá-lo." }
            check(dao.activeWorkoutExercises(workoutId).isNotEmpty()) { "Adicione exercícios antes de iniciar este treino." }
            val session = SessionEntity(newId(), workoutId, System.currentTimeMillis(), null, null, pendingSync = true)
            dao.upsertSession(session)
            session.publicId
        }
        notifyDataChanged()
        return id
    }

    /** Todas as séries do lote são gravadas juntas; uma sessão encerrada não pode ser alterada. */
    suspend fun saveSessionSets(sessionId: String, exerciseId: String, values: List<Triple<Int, Int, Double>>) {
        db.withTransaction {
            val session = dao.getSession(sessionId) ?: error("Treino não encontrado.")
            check(session.finishedAt == null) { "Este treino já foi finalizado." }
            val exercise = requireNotNull(dao.getWorkoutExercise(exerciseId)) { "Exercício do treino não encontrado." }
            require(exercise.workoutPublicId == session.workoutPublicId && exercise.removedAt == null) { "Este exercício não pertence ao treino em andamento." }
            require(values.map { it.first }.distinct().size == values.size) { "Há séries duplicadas no lote." }
            values.forEach { (number, reps, weight) ->
                require(number in 1..exercise.sets) { "Número da série inválido." }
                require(reps in 0..100 && weight.isFinite() && weight in 0.0..1000.0) { "Revise a carga e as repetições." }
                dao.saveSet(sessionId, exerciseId, number, reps, weight, newId())
            }
        }
        notifyDataChanged()
    }

    suspend fun unconfirmSet(sessionId: String, exerciseId: String, number: Int) {
        db.withTransaction {
            val session = dao.getSession(sessionId) ?: error("Treino não encontrado.")
            check(session.finishedAt == null) { "Este treino já foi finalizado." }
            val saved = dao.findSet(sessionId, exerciseId, number) ?: return@withTransaction
            dao.deleteSet(sessionId, exerciseId, number)
            // Mesmo uma série importada precisa ser removida no servidor ao desfazer.
            dao.insertPendingDeletion(PendingDeletionEntity(ENTITY_SET, saved.publicId))
        }
        notifyDataChanged()
    }

    suspend fun finishSession(sessionId: String) {
        db.withTransaction {
            val session = dao.getSession(sessionId) ?: error("Treino não encontrado.")
            if (session.finishedAt == null) {
                dao.upsertSession(session.copy(finishedAt = System.currentTimeMillis(), pendingSync = true))
            }
        }
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

    // ===== Biblioteca de exercícios =====

    /**
     * Cria (sem [publicId]) ou atualiza um exercício. Lança [IllegalArgumentException]
     * com a mensagem para o usuário se os dados forem inválidos.
     */
    suspend fun saveExercise(publicId: String?, name: String, comment: String?, muscleIds: List<Int>): String {
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        val cleanComment = comment?.trim()?.takeIf { it.isNotEmpty() }
        require(cleanName.length >= 3) { "O nome precisa ter pelo menos 3 caracteres." }
        require(cleanName.length <= 100) { "O nome pode ter no máximo 100 caracteres." }
        require((cleanComment?.length ?: 0) <= 500) { "A observação pode ter no máximo 500 caracteres." }
        require(muscleIds.isNotEmpty()) { "Selecione ao menos um grupo muscular." }

        if (publicId != null) requireNotNull(dao.getExercise(publicId)) { "Este exercício não existe mais. Atualize a lista." }
        val id = publicId ?: newId()
        require(dao.findExerciseByName(cleanName, exceptId = id) == null) { "Já existe um exercício com esse nome." }

        dao.saveExercise(ExerciseEntity(publicId = id, name = cleanName, comment = cleanComment), muscleIds)
        notifyDataChanged()
        onLocalChange()
        return id
    }

    /** Exclui um exercício que não está em nenhum treino. Devolve false se estiver em uso. */
    suspend fun deleteExercise(publicId: String): Boolean {
        val deleted = dao.deleteExerciseIfUnused(publicId, ENTITY_EXERCISE)
        if (deleted) {
            notifyDataChanged()
            onLocalChange()
        }
        return deleted
    }

    // ===== Treinos =====

    /** Cria (sem [publicId]) ou atualiza um treino. Lança [IllegalArgumentException] se inválido. */
    suspend fun saveWorkout(publicId: String?, name: String, category: String, comment: String?): String {
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        val cleanCategory = category.trim().replace(Regex("\\s+"), " ")
        val cleanComment = comment?.trim()?.takeIf { it.isNotEmpty() }
        require(cleanName.length >= 3) { "O nome precisa ter pelo menos 3 caracteres." }
        require(cleanName.length <= 100) { "O nome pode ter no máximo 100 caracteres." }
        require(cleanCategory.isNotEmpty()) { "Informe a categoria." }
        require(cleanCategory.length <= 100) { "A categoria pode ter no máximo 100 caracteres." }
        require((cleanComment?.length ?: 0) <= 500) { "A observação pode ter no máximo 500 caracteres." }

        val existing = publicId?.let { requireNotNull(dao.getWorkout(it)) { "Este treino não existe mais. Atualize a lista." } }
        val workout = WorkoutEntity(
            publicId = existing?.publicId ?: newId(),
            name = cleanName,
            category = cleanCategory,
            comment = cleanComment,
            isArchived = existing?.isArchived ?: false,
            pendingSync = true,
        )
        dao.upsertWorkout(workout)
        changed()
        return workout.publicId
    }

    suspend fun setWorkoutArchived(publicId: String, archived: Boolean) {
        val workout = requireNotNull(dao.getWorkout(publicId)) { "Treino não encontrado." }
        if (workout.isArchived == archived) return
        dao.upsertWorkout(workout.copy(isArchived = archived, pendingSync = true))
        changed()
    }

    // ===== Planejamento (exercícios do treino) =====

    data class PlannedValues(
        val sets: Int,
        val repetitions: Int,
        val weight: Double,
        val restTimeSeconds: Int,
        val notes: String?,
    )

    private fun validate(values: PlannedValues): PlannedValues {
        require(values.sets in 1..20) { "Séries: de 1 a 20." }
        require(values.repetitions in 0..100) { "Repetições: de 0 a 100." }
        require(values.weight.isFinite() && values.weight in 0.0..1000.0) { "Carga: de 0 a 1000 kg." }
        require(values.restTimeSeconds in 0..3600) { "Descanso: de 0 a 3600 segundos." }
        val notes = values.notes?.trim()?.takeIf { it.isNotEmpty() }
        require((notes?.length ?: 0) <= 500) { "A observação pode ter no máximo 500 caracteres." }
        return values.copy(notes = notes)
    }

    suspend fun addPlannedExercise(workoutId: String, exerciseId: String, values: PlannedValues) {
        val v = validate(values)
        requireNotNull(dao.getExercise(exerciseId)) { "Exercício não encontrado." }
        dao.addWorkoutExercise(
            WorkoutExerciseEntity(
                publicId = newId(),
                workoutPublicId = workoutId,
                exercisePublicId = exerciseId,
                sets = v.sets,
                repetitions = v.repetitions,
                weight = v.weight,
                restTimeSeconds = v.restTimeSeconds,
                notes = v.notes,
                order = 0, // definido no DAO (fim da lista)
                removedAt = null,
            )
        )
        changed()
    }

    suspend fun updatePlannedExercise(publicId: String, values: PlannedValues) {
        val v = validate(values)
        val item = requireNotNull(dao.getWorkoutExercise(publicId)) { "Exercício do treino não encontrado." }
        dao.upsertWorkoutExercise(
            item.copy(
                sets = v.sets,
                repetitions = v.repetitions,
                weight = v.weight,
                restTimeSeconds = v.restTimeSeconds,
                notes = v.notes,
                pendingSync = true,
            )
        )
        changed()
    }

    /** Remove do treino (remoção lógica: as séries já feitas continuam no histórico). */
    suspend fun removePlannedExercise(publicId: String) {
        val item = requireNotNull(dao.getWorkoutExercise(publicId)) { "Exercício do treino não encontrado." }
        if (item.removedAt != null) return
        dao.upsertWorkoutExercise(item.copy(removedAt = System.currentTimeMillis(), pendingSync = true))
        changed()
    }

    suspend fun movePlannedExercise(workoutId: String, publicId: String, direction: Int) {
        dao.moveWorkoutExercise(workoutId, publicId, direction)
        changed()
    }

    /** Avisa as telas e agenda o envio ao servidor. */
    private fun changed() {
        notifyDataChanged()
        onLocalChange()
    }

    private fun newId() = UUID.randomUUID().toString()

    companion object {
        const val ENTITY_SET = "set"
        const val ENTITY_SESSION = "session"
        const val ENTITY_EXERCISE = "exercise"
    }

    private suspend fun replaceLocalData(export: SyncExportDto): ImportResult {
        try { validateSyncExport(export) }
        catch (e: IllegalArgumentException) { throw ApiException.InvalidResponse(e, e.message) }
        catch (e: java.time.format.DateTimeParseException) { throw ApiException.InvalidResponse(e, "Há uma data fora do formato ISO-8601 esperado.") }
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

    private fun id(value: String) = canonicalPublicId(value)

    private fun epochMillis(iso: String): Long = Instant.parse(iso).toEpochMilli()
}
