package br.com.ryuukossei.gymprogress.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.ryuukossei.gymprogress.GymProgressApp

/** Recebe o alarme do fim do descanso e o botão "Parar" da notificação. */
class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timer = (context.applicationContext as GymProgressApp).container.restTimer
        when (intent.action) {
            ACTION_FINISHED -> timer.onFinished()
            ACTION_STOP -> timer.stop()
        }
    }

    companion object {
        const val ACTION_FINISHED = "br.com.ryuukossei.gymprogress.REST_FINISHED"
        const val ACTION_STOP = "br.com.ryuukossei.gymprogress.REST_STOP"
    }
}
