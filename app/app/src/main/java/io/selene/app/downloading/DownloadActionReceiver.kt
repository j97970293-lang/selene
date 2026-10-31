package io.selene.app.downloading

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

/**
 * Action de la notification : arrête le travail et laisse le worker persister
 * l'état PAUSED (il traite l'annulation comme une pause).
 */
class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(DownloadNotifier.EXTRA_TASK_ID) ?: return
        if (intent.action == DownloadNotifier.STOP_ACTION) {
            WorkManager.getInstance(context).cancelUniqueWork("download-$taskId")
        }
    }
}
