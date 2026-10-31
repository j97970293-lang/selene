package io.selene.app.downloading

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import io.selene.app.R
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Singleton
class DownloadNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        val progress = NotificationChannel(
            CHANNEL_PROGRESS,
            context.getString(R.string.notification_channel_downloads),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = null
            setShowBadge(false)
        }
        val done = NotificationChannel(
            CHANNEL_DONE,
            context.getString(R.string.notification_channel_done),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannels(listOf(progress, done))
    }

    fun foregroundInfo(
        taskId: String,
        title: String,
        downloaded: Long,
        total: Long?,
        paused: Boolean = false,
    ): ForegroundInfo {
        val percent = total?.takeIf { it > 0 }?.let { (downloaded * 100 / it).toInt() }
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(
                if (percent != null) {
                    context.getString(R.string.notification_progress, title, percent)
                } else {
                    title
                },
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent ?: 0, total == null)

        builder.addAction(action(STOP_ACTION, taskId, R.string.download_cancel))

        return ForegroundInfo(taskId.hashCode(), builder.build())
    }

    fun notifyCompleted(taskId: String, title: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setAutoCancel(true)
            .build()
        manager.notify(taskId.hashCode() + 1, notification)
    }

    fun cancel(taskId: String) {
        manager.cancel(taskId.hashCode())
        manager.cancel(taskId.hashCode() + 1)
    }

    private fun action(actionId: String, taskId: String, labelRes: Int): NotificationCompat.Action {
        // Le récepteur d'action vit dans DownloadWorker (voir DownloadActionReceiver).
        val intent = Intent(context, DownloadActionReceiver::class.java).apply {
            this.action = actionId
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getBroadcast(context, taskId.hashCode() + labelRes, intent, flags)
        return NotificationCompat.Action.Builder(0, context.getString(labelRes), pending).build()
    }

    /** Notification minimale utile aux tests et aux appels système. */
    fun buildPlaceholder(title: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .build()

    companion object {
        const val CHANNEL_PROGRESS = "downloads.progress"
        const val CHANNEL_DONE = "downloads.done"
        const val STOP_ACTION = "io.selene.app.action.STOP_DOWNLOAD"
        const val EXTRA_TASK_ID = "io.selene.app.extra.TASK_ID"
    }
}
