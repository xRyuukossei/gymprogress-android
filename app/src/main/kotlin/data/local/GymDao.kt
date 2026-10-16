package br.com.ryuukossei.gymprogress.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class GymDao {

    // ===== Leitura (telas) =====

    @Query("SELECT * FROM workouts")
    abstract fun observeWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE publicId = :workoutId")
    abstract fun observeWorkout(workoutId: String): Flow<WorkoutEntity?>

    @Query(
        """
        SELECT we.publicId, we.exercisePublicId, e.name AS exerciseName, we.sets, we.repetitions,
               we.weight, we.restTimeSeconds, we.notes, we.sortOrder
        FROM workout_exercises we
        JOIN exercises e ON e.publicId = we.exercisePublicId
        WHERE we.workoutPublicId = :workoutId AND we.removedAt IS NULL
        ORDER BY we.sortOrder
        """
    )
    abstract fun observePlannedExercises(workoutId: String): Flow<List<PlannedExerciseRow>>

    @Query(
        """
        SELECT etm.exercisePublicId, tm.name AS muscleName
        FROM exercise_target_muscles etm
        JOIN target_muscles tm ON tm.id = etm.targetMuscleId
        ORDER BY tm.id
        """
    )
    abstract fun observeExerciseMuscles(): Flow<List<ExerciseMuscleRow>>

    @Query(
        """
        SELECT s.publicId, s.workoutPublicId, w.name AS workoutName, s.startedAt, s.finishedAt, s.pendingSync,
               (SELECT COUNT(*) FROM session_sets x WHERE x.sessionPublicId = s.publicId) AS setCount
        FROM sessions s
        JOIN workouts w ON w.publicId = s.workoutPublicId
        ORDER BY s.startedAt DESC
        """
    )
    abstract fun observeSessionSummaries(): Flow<List<SessionSummaryRow>>

    @Query(
        """
        SELECT s.publicId, s.workoutPublicId, w.name AS workoutName, s.startedAt, s.finishedAt, s.pendingSync,
               (SELECT COUNT(*) FROM session_sets x WHERE x.sessionPublicId = s.publicId) AS setCount
        FROM sessions s
        JOIN workouts w ON w.publicId = s.workoutPublicId
        WHERE s.publicId = :sessionId
        """
    )
    abstract fun observeSessionSummary(sessionId: String): Flow<SessionSummaryRow?>

    @Query(
        """
        SELECT x.publicId, x.workoutExercisePublicId, e.name AS exerciseName, we.sortOrder AS exerciseOrder,
               x.setNumber, x.performedRepetitions, x.performedWeight
        FROM session_sets x
        JOIN workout_exercises we ON we.publicId = x.workoutExercisePublicId
        JOIN exercises e ON e.publicId = we.exercisePublicId
        WHERE x.sessionPublicId = :sessionId
        ORDER BY we.sortOrder, x.workoutExercisePublicId, x.setNumber
        """
    )
    abstract fun observeSessionSets(sessionId: String): Flow<List<SessionSetRow>>

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM exercises) AS exercises,
            (SELECT COUNT(*) FROM workouts) AS workouts,
            (SELECT COUNT(*) FROM sessions) AS sessions,
            (SELECT COUNT(*) FROM session_sets) AS sets
        """
    )
    abstract fun observeCounts(): Flow<LocalCounts>

    // ===== Execução do treino =====

    @Query("SELECT * FROM sessions WHERE publicId = :sessionId")
    abstract fun observeSession(sessionId: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE publicId = :sessionId")
    abstract suspend fun getSession(sessionId: String): SessionEntity?

    @Query("SELECT * FROM session_sets WHERE sessionPublicId = :sessionId ORDER BY setNumber")
    abstract fun observeSetsOfSession(sessionId: String): Flow<List<SessionSetEntity>>

    @Query(
        """
        SELECT * FROM session_sets
        WHERE sessionPublicId = :sessionId AND workoutExercisePublicId = :workoutExerciseId AND setNumber = :setNumber
        LIMIT 1
        """
    )
    abstract suspend fun findSet(sessionId: String, workoutExerciseId: String, setNumber: Int): SessionSetEntity?

    /** Séries de sessões finalizadas para os exercícios informados (mais recentes primeiro). */
    @Query(
        """
        SELECT we.exercisePublicId, s.publicId AS sessionPublicId, s.startedAt,
               x.setNumber, x.performedRepetitions, x.performedWeight
        FROM session_sets x
        JOIN sessions s ON s.publicId = x.sessionPublicId
        JOIN workout_exercises we ON we.publicId = x.workoutExercisePublicId
        WHERE s.finishedAt IS NOT NULL AND s.publicId != :excludeSessionId
        ORDER BY s.startedAt DESC, x.setNumber
        """
    )
    abstract fun observePreviousSets(excludeSessionId: String): Flow<List<PreviousSetRow>>

    @Query("SELECT * FROM sessions WHERE finishedAt IS NULL AND workoutPublicId = :workoutId ORDER BY startedAt DESC LIMIT 1")
    abstract fun observeInProgressSessionOfWorkout(workoutId: String): Flow<SessionEntity?>

    // @Upsert atualiza a linha existente no lugar. NÃO usar REPLACE aqui: o REPLACE do SQLite
    // apaga a linha antiga e, pela chave estrangeira com CASCADE, apagaria as séries da sessão.
    @Upsert abstract suspend fun upsertSession(session: SessionEntity)
    @Upsert abstract suspend fun upsertSet(set: SessionSetEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertPendingDeletion(item: PendingDeletionEntity)

    @Query("DELETE FROM session_sets WHERE sessionPublicId = :sessionId") abstract suspend fun deleteSetsOfSession(sessionId: String)
    @Query("DELETE FROM sessions WHERE publicId = :sessionId") abstract suspend fun deleteSession(sessionId: String)

    // ===== Pendências de sincronização =====

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM exercises WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM workouts WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM workout_exercises WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM sessions WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM session_sets WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM pending_deletions)
        """
    )
    abstract suspend fun countPendingChanges(): Int

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM exercises WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM workouts WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM workout_exercises WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM sessions WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM session_sets WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM pending_deletions)
        """
    )
    abstract fun observePendingChanges(): Flow<Int>

    // ===== Envio ao servidor (etapa 4) =====
    // Sessões em andamento (e suas séries) só são enviadas depois de finalizadas.

    @Query("SELECT * FROM exercises WHERE pendingSync = 1") abstract suspend fun pendingExercises(): List<ExerciseEntity>
    @Query("SELECT * FROM workouts WHERE pendingSync = 1") abstract suspend fun pendingWorkouts(): List<WorkoutEntity>
    @Query("SELECT * FROM workout_exercises WHERE pendingSync = 1") abstract suspend fun pendingWorkoutExercises(): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM sessions WHERE pendingSync = 1 AND finishedAt IS NOT NULL")
    abstract suspend fun pendingFinishedSessions(): List<SessionEntity>

    @Query(
        """
        SELECT x.* FROM session_sets x
        JOIN sessions s ON s.publicId = x.sessionPublicId
        WHERE x.pendingSync = 1 AND s.finishedAt IS NOT NULL
        """
    )
    abstract suspend fun pendingSetsOfFinishedSessions(): List<SessionSetEntity>

    @Query("SELECT * FROM pending_deletions") abstract suspend fun pendingDeletions(): List<PendingDeletionEntity>

    @Query("SELECT * FROM exercise_target_muscles WHERE exercisePublicId IN (:exerciseIds)")
    abstract suspend fun musclesOfExercises(exerciseIds: List<String>): List<ExerciseTargetMuscleEntity>

    @Query("SELECT * FROM exercises WHERE publicId IN (:ids)") abstract suspend fun exercisesByIds(ids: List<String>): List<ExerciseEntity>
    @Query("SELECT * FROM workouts WHERE publicId IN (:ids)") abstract suspend fun workoutsByIds(ids: List<String>): List<WorkoutEntity>
    @Query("SELECT * FROM workout_exercises WHERE publicId IN (:ids)") abstract suspend fun workoutExercisesByIds(ids: List<String>): List<WorkoutExerciseEntity>
    @Query("SELECT * FROM sessions WHERE publicId IN (:ids)") abstract suspend fun sessionsByIds(ids: List<String>): List<SessionEntity>
    @Query("SELECT * FROM session_sets WHERE publicId IN (:ids)") abstract suspend fun setsByIds(ids: List<String>): List<SessionSetEntity>

    @Query("UPDATE exercises SET pendingSync = 0 WHERE publicId IN (:ids)") abstract suspend fun markExercisesSynced(ids: List<String>)
    @Query("UPDATE workouts SET pendingSync = 0 WHERE publicId IN (:ids)") abstract suspend fun markWorkoutsSynced(ids: List<String>)
    @Query("UPDATE workout_exercises SET pendingSync = 0 WHERE publicId IN (:ids)") abstract suspend fun markWorkoutExercisesSynced(ids: List<String>)
    @Query("UPDATE sessions SET pendingSync = 0 WHERE publicId IN (:ids)") abstract suspend fun markSessionsSynced(ids: List<String>)
    @Query("UPDATE session_sets SET pendingSync = 0 WHERE publicId IN (:ids)") abstract suspend fun markSetsSynced(ids: List<String>)

    @Query("DELETE FROM pending_deletions WHERE entityType = :entityType AND publicId IN (:ids)")
    abstract suspend fun removePendingDeletions(entityType: String, ids: List<String>)

    // ===== Importação completa (substitui tudo) =====

    @Query("DELETE FROM session_sets") abstract suspend fun deleteAllSets()
    @Query("DELETE FROM sessions") abstract suspend fun deleteAllSessions()
    @Query("DELETE FROM workout_exercises") abstract suspend fun deleteAllWorkoutExercises()
    @Query("DELETE FROM workouts") abstract suspend fun deleteAllWorkouts()
    @Query("DELETE FROM exercise_target_muscles") abstract suspend fun deleteAllExerciseMuscles()
    @Query("DELETE FROM exercises") abstract suspend fun deleteAllExercises()
    @Query("DELETE FROM target_muscles") abstract suspend fun deleteAllTargetMuscles()
    @Query("DELETE FROM pending_deletions") abstract suspend fun deleteAllPendingDeletions()

    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertTargetMuscles(items: List<TargetMuscleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertExercises(items: List<ExerciseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertExerciseMuscles(items: List<ExerciseTargetMuscleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertWorkouts(items: List<WorkoutEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertWorkoutExercises(items: List<WorkoutExerciseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertSessions(items: List<SessionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertSets(items: List<SessionSetEntity>)

    // ===== Operações em transação =====
    // Gravações que precisam acontecer juntas usam @Transaction do próprio Room: assim as
    // telas que observam o banco são avisadas depois que tudo foi gravado.

    /** Grava (ou atualiza) uma série confirmada. */
    @Transaction
    open suspend fun saveSet(sessionId: String, workoutExerciseId: String, setNumber: Int, reps: Int, weight: Double, newId: String) {
        val existing = findSet(sessionId, workoutExerciseId, setNumber)
        upsertSet(
            SessionSetEntity(
                publicId = existing?.publicId ?: newId,
                sessionPublicId = sessionId,
                workoutExercisePublicId = workoutExerciseId,
                setNumber = setNumber,
                performedRepetitions = reps,
                performedWeight = weight,
                pendingSync = true,
            )
        )
    }

    /** Descarta uma sessão em andamento e registra a exclusão para enviar ao servidor. */
    @Transaction
    open suspend fun cancelInProgressSession(sessionId: String, entityType: String): Boolean {
        val session = getSession(sessionId) ?: return false
        if (session.finishedAt != null) return false
        deleteSetsOfSession(sessionId)
        deleteSession(sessionId)
        insertPendingDeletion(PendingDeletionEntity(entityType, sessionId))
        return true
    }

    /** Substitui todo o banco local pelos dados importados do servidor. */
    @Transaction
    open suspend fun replaceAll(
        muscles: List<TargetMuscleEntity>,
        exercises: List<ExerciseEntity>,
        exerciseMuscles: List<ExerciseTargetMuscleEntity>,
        workouts: List<WorkoutEntity>,
        workoutExercises: List<WorkoutExerciseEntity>,
        sessions: List<SessionEntity>,
        sets: List<SessionSetEntity>,
    ) {
        // Apaga dos filhos para os pais (chaves estrangeiras) e insere na ordem inversa.
        deleteAllSets()
        deleteAllSessions()
        deleteAllWorkoutExercises()
        deleteAllWorkouts()
        deleteAllExerciseMuscles()
        deleteAllExercises()
        deleteAllTargetMuscles()
        deleteAllPendingDeletions()

        insertTargetMuscles(muscles)
        insertExercises(exercises)
        insertExerciseMuscles(exerciseMuscles)
        insertWorkouts(workouts)
        insertWorkoutExercises(workoutExercises)
        insertSessions(sessions)
        insertSets(sets)
    }

    /**
     * Depois de um envio bem-sucedido, marca como enviado só o que continua igual ao
     * que foi enviado (o que mudou durante o envio fica pendente para o próximo).
     */
    @Transaction
    open suspend fun markPushed(
        exercises: List<ExerciseEntity>,
        workouts: List<WorkoutEntity>,
        workoutExercises: List<WorkoutExerciseEntity>,
        sessions: List<SessionEntity>,
        sets: List<SessionSetEntity>,
        deletedSessionIds: List<String>,
        sessionEntityType: String,
    ) {
        markExercisesSynced(unchanged(exercises, exercisesByIds(exercises.map { it.publicId })) { it.publicId })
        markWorkoutsSynced(unchanged(workouts, workoutsByIds(workouts.map { it.publicId })) { it.publicId })
        markWorkoutExercisesSynced(unchanged(workoutExercises, workoutExercisesByIds(workoutExercises.map { it.publicId })) { it.publicId })
        markSessionsSynced(unchanged(sessions, sessionsByIds(sessions.map { it.publicId })) { it.publicId })
        markSetsSynced(unchanged(sets, setsByIds(sets.map { it.publicId })) { it.publicId })
        if (deletedSessionIds.isNotEmpty()) removePendingDeletions(sessionEntityType, deletedSessionIds)
    }

    private fun <T> unchanged(sent: List<T>, current: List<T>, id: (T) -> String): List<String> {
        val sentById = sent.associateBy(id)
        return current.filter { sentById[id(it)] == it }.map(id)
    }
}
