package br.com.ryuukossei.gymprogress.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.ryuukossei.gymprogress.GymProgressApp
import br.com.ryuukossei.gymprogress.data.remote.ApiException
import br.com.ryuukossei.gymprogress.data.repository.MissingTokenException
import java.util.concurrent.TimeUnit

/** Executa o envio em segundo plano (roda mesmo com o app fechado, quando houver internet). */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as GymProgressApp).container
        if (!container.syncStatus.state.value.autoSyncEnabled) return Result.success()
        return try {
            container.sync.pushPending()
            Result.success()
        } catch (e: ApiException.Network) {
            Result.retry()
        } catch (e: ApiException.Http) {
            // Erro do servidor (5xx) pode ser passageiro; dados recusados (4xx) não adianta repetir.
            if (e.code >= 500) Result.retry() else Result.failure()
        } catch (e: ApiException) {
            Result.failure()
        } catch (e: MissingTokenException) {
            Result.failure()
        }
    }
}

/** Agenda os envios: um imediato (quando há rede) e um periódico de segurança. */
class SyncScheduler(private val context: Context) {
    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun syncSoon() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelPeriodic() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_PERIODIC)
    }

    companion object {
        private const val WORK_NOW = "gymprogress-sync-now"
        private const val WORK_PERIODIC = "gymprogress-sync-periodic"
    }
}
