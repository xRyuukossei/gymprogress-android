package br.com.ryuukossei.gymprogress.timer

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import br.com.ryuukossei.gymprogress.MainActivity
import br.com.ryuukossei.gymprogress.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado do cronômetro: horário de término (epoch ms) e a sessão a que ele pertence. */
data class RestTimerState(val endAt: Long, val totalSeconds: Int, val sessionId: String?)

/**
 * Cronômetro de descanso que não depende da tela nem do app estar aberto:
 *  - grava o horário de término (sobrevive ao processo ser encerrado);
 *  - agenda um alarme exato no sistema para o fim do descanso;
 *  - mostra uma notificação com contagem regressiva nativa enquanto corre;
 *  - no fim, o [RestTimerReceiver] dispara uma notificação com som e vibração.
 */
class RestTimer(private val context: Context) {

    private val prefs = context.getSharedPreferences("gymprogress_timer", Context.MODE_PRIVATE)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notifications = NotificationManagerCompat.from(context)

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<RestTimerState?> = _state.asStateFlow()

    init {
        createChannels()
    }

    fun start(seconds: Int, sessionId: String?) {
        val total = seconds.coerceAtLeast(1)
        val endAt = System.currentTimeMillis() + total * 1000L
        val newState = RestTimerState(endAt, total, sessionId)
        save(newState)
        scheduleAlarm(endAt, sessionId)
        showRunningNotification(newState)
    }

    fun stop() {
        save(null)
        alarmManager.cancel(finishPendingIntent(null))
        notifications.cancel(NOTIFICATION_RUNNING)
    }

    /** Chamado pelo alarme no fim do descanso. */
    fun onFinished() {
        val finished = _state.value
        save(null)
        notifications.cancel(NOTIFICATION_RUNNING)
        showFinishedNotification(finished?.sessionId)
    }

    /** Se o app abrir depois do horário de término (ex.: alarme perdido), limpa o estado. */
    fun clearIfExpired() {
        val current = _state.value ?: return
        if (current.endAt <= System.currentTimeMillis()) {
            save(null)
            notifications.cancel(NOTIFICATION_RUNNING)
        }
    }

    // ===== Alarme =====

    private fun scheduleAlarm(endAt: Long, sessionId: String?) {
        val operation = finishPendingIntent(sessionId)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                // setAlarmClock é o tipo de alarme mais confiável: dispara no horário mesmo com o
                // celular em economia de energia (Doze) e a tela bloqueada.
                val info = AlarmManager.AlarmClockInfo(endAt, openAppPendingIntent(sessionId))
                alarmManager.setAlarmClock(info, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, operation)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, operation)
        }
    }

    private fun finishPendingIntent(sessionId: String?): PendingIntent {
        val intent = Intent(context, RestTimerReceiver::class.java)
            .setAction(RestTimerReceiver.ACTION_FINISHED)
            .putExtra(EXTRA_SESSION_ID, sessionId)
        return PendingIntent.getBroadcast(
            context, REQUEST_FINISH, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopPendingIntent(): PendingIntent {
        val intent = Intent(context, RestTimerReceiver::class.java).setAction(RestTimerReceiver.ACTION_STOP)
        return PendingIntent.getBroadcast(
            context, REQUEST_STOP, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppPendingIntent(sessionId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_SESSION_ID, sessionId)
        return PendingIntent.getActivity(
            context, REQUEST_OPEN, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ===== Notificações =====

    private fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val running = NotificationChannel(CHANNEL_RUNNING, "Descanso em andamento", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Contagem regressiva do descanso entre séries."
            setShowBadge(false)
        }
        val done = NotificationChannel(CHANNEL_DONE, "Fim do descanso", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Aviso com som e vibração quando o descanso termina."
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        manager.createNotificationChannels(listOf(running, done))
    }

    @SuppressLint("MissingPermission") // permissão verificada em canNotify()
    private fun showRunningNotification(state: RestTimerState) {
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_nav_workouts)
            .setContentTitle("Descanso")
            .setContentText("Próxima série quando o tempo acabar")
            .setWhen(state.endAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setTimeoutAfter(state.totalSeconds * 1000L + 2_000L)
            .setContentIntent(openAppPendingIntent(state.sessionId))
            .addAction(0, "Parar", stopPendingIntent())
            .build()
        notifications.notify(NOTIFICATION_RUNNING, notification)
    }

    @SuppressLint("MissingPermission") // permissão verificada em canNotify()
    private fun showFinishedNotification(sessionId: String?) {
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_nav_workouts)
            .setContentTitle("Descanso terminado")
            .setContentText("Hora da próxima série!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000L)
            .setContentIntent(openAppPendingIntent(sessionId))
            .build()
        notifications.notify(NOTIFICATION_DONE, notification)
    }

    private fun canNotify(): Boolean = notifications.areNotificationsEnabled()

    // ===== Persistência =====

    private fun save(state: RestTimerState?) {
        prefs.edit {
            if (state == null) {
                clear()
            } else {
                putLong(KEY_END_AT, state.endAt)
                putInt(KEY_TOTAL, state.totalSeconds)
                putString(KEY_SESSION, state.sessionId)
            }
        }
        _state.value = state
    }

    private fun loadState(): RestTimerState? {
        val endAt = prefs.getLong(KEY_END_AT, 0L)
        if (endAt <= System.currentTimeMillis()) return null
        return RestTimerState(endAt, prefs.getInt(KEY_TOTAL, 0), prefs.getString(KEY_SESSION, null))
    }

    companion object {
        const val EXTRA_SESSION_ID = "br.com.ryuukossei.gymprogress.SESSION_ID"
        private const val CHANNEL_RUNNING = "rest_timer_running"
        private const val CHANNEL_DONE = "rest_timer_done"
        private const val NOTIFICATION_RUNNING = 1001
        private const val NOTIFICATION_DONE = 1002
        private const val REQUEST_FINISH = 2001
        private const val REQUEST_STOP = 2002
        private const val REQUEST_OPEN = 2003
        private const val KEY_END_AT = "end_at"
        private const val KEY_TOTAL = "total_seconds"
        private const val KEY_SESSION = "session_id"
    }
}
