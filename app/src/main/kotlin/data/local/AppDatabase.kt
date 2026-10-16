package br.com.ryuukossei.gymprogress.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TargetMuscleEntity::class,
        ExerciseEntity::class,
        ExerciseTargetMuscleEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        SessionEntity::class,
        SessionSetEntity::class,
        PendingDeletionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gymDao(): GymDao

    companion object {
        fun build(context: Context): AppDatabase =
            // Sem "fallbackToDestructiveMigration": mudanças de esquema futuras
            // terão migrations explícitas, para nunca apagar treinos do celular.
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "gymprogress.db")
                .build()
    }
}
