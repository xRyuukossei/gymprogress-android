package br.com.ryuukossei.gymprogress.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Banco local do app. Espelha o banco do servidor, usando o PublicId (GUID, em
 * minúsculas) como chave primária: é o identificador compartilhado na sincronização.
 *
 * Datas ficam em milissegundos UTC (epoch). Cargas ficam em Double.
 * O campo pendingSync marca registros alterados no celular que ainda não foram
 * enviados ao servidor (usado a partir da etapa 4).
 */

@Entity(tableName = "target_muscles")
data class TargetMuscleEntity(
    @PrimaryKey val id: Int,
    val name: String,
)

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val publicId: String,
    val name: String,
    val comment: String?,
    val pendingSync: Boolean = false,
)

@Entity(
    tableName = "exercise_target_muscles",
    primaryKeys = ["exercisePublicId", "targetMuscleId"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["publicId"],
            childColumns = ["exercisePublicId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TargetMuscleEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetMuscleId"],
        ),
    ],
    indices = [Index("targetMuscleId")],
)
data class ExerciseTargetMuscleEntity(
    val exercisePublicId: String,
    val targetMuscleId: Int,
)

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey val publicId: String,
    val name: String,
    val category: String,
    val comment: String?,
    val isArchived: Boolean,
    val pendingSync: Boolean = false,
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["publicId"],
            childColumns = ["workoutPublicId"],
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["publicId"],
            childColumns = ["exercisePublicId"],
        ),
    ],
    indices = [Index("workoutPublicId"), Index("exercisePublicId")],
)
data class WorkoutExerciseEntity(
    @PrimaryKey val publicId: String,
    val workoutPublicId: String,
    val exercisePublicId: String,
    val sets: Int,
    val repetitions: Int,
    val weight: Double,
    val restTimeSeconds: Int,
    val notes: String?,
    @ColumnInfo(name = "sortOrder") val order: Int,
    /** Preenchido quando o exercício foi removido do treino (o histórico continua apontando para ele). */
    val removedAt: Long?,
    val pendingSync: Boolean = false,
)

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["publicId"],
            childColumns = ["workoutPublicId"],
        ),
    ],
    indices = [Index("workoutPublicId"), Index("startedAt")],
)
data class SessionEntity(
    @PrimaryKey val publicId: String,
    val workoutPublicId: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val notes: String?,
    val pendingSync: Boolean = false,
)

@Entity(
    tableName = "session_sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["publicId"],
            childColumns = ["sessionPublicId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["publicId"],
            childColumns = ["workoutExercisePublicId"],
        ),
    ],
    indices = [Index("sessionPublicId"), Index("workoutExercisePublicId")],
)
data class SessionSetEntity(
    @PrimaryKey val publicId: String,
    val sessionPublicId: String,
    val workoutExercisePublicId: String,
    val setNumber: Int,
    val performedRepetitions: Int,
    val performedWeight: Double,
    val pendingSync: Boolean = false,
)

/** Exclusões feitas no celular que ainda precisam ser enviadas ao servidor (etapa 4). */
@Entity(tableName = "pending_deletions", primaryKeys = ["entityType", "publicId"])
data class PendingDeletionEntity(
    val entityType: String,
    val publicId: String,
)
