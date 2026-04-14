package com.solovision.openclawagents.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.solovision.openclawagents.AgentAvatarCatalog
import com.solovision.openclawagents.MainActivity
import com.solovision.openclawagents.model.CollaborationRoom
import com.solovision.openclawagents.model.CronJob
import com.solovision.openclawagents.model.RoomMessage

private const val MESSAGE_CHANNEL_ID = "openclaw_messages"
private const val CRON_CHANNEL_ID = "openclaw_cron"
private const val BACKGROUND_SYNC_CHANNEL_ID = "openclaw_background_sync"

class AppNotificationManager(
    private val context: Context
) {

    init {
        ensureChannels()
    }

    fun hasPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun notifyNewMessages(room: CollaborationRoom, messages: List<RoomMessage>) {
        if (!hasPermission()) return
        val latestBySender = messages
            .groupBy { notificationSenderKey(it) }
            .values
            .mapNotNull { senderMessages -> senderMessages.lastOrNull()?.let { it to senderMessages.size } }

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(legacyRoomNotificationId(room.id))
        latestBySender.forEach { (message, count) ->
            val notification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(room.title)
                .setContentText(
                    if (count > 1) "${message.senderName}: ${message.body} (+${count - 1} more)"
                    else "${message.senderName}: ${message.body}"
                )
                .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(mainPendingIntent(room.id))
                .setLargeIcon(
                    AgentAvatarCatalog.bitmapForKey(context, message.senderId)
                        ?: AgentAvatarCatalog.bitmapForKey(context, message.senderName)
                )
                .build()

            @SuppressLint("MissingPermission")
            notificationManager.notify(senderNotificationId(room.id, message), notification)
        }
    }

    fun notifyCronUpdate(job: CronJob) {
        if (!hasPermission()) return
        val statusLabel = job.lastStatus?.replaceFirstChar { it.uppercase() } ?: "Updated"
        val body = when {
            !job.lastError.isNullOrBlank() -> job.lastError
            !job.lastRunAt.isNullOrBlank() -> "Last run ${job.lastRunAt}"
            else -> "Cron job activity detected."
        }
        val notification = NotificationCompat.Builder(context, CRON_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("${job.name} • $statusLabel")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (!job.lastError.isNullOrBlank()) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent("cron:${job.id}"))
            .setLargeIcon(
                AgentAvatarCatalog.bitmapForKey(context, job.agentId)
                    ?: AgentAvatarCatalog.bitmapForKey(context, job.sessionTarget)
            )
            .build()

        @SuppressLint("MissingPermission")
        NotificationManagerCompat.from(context).notify(("cron:" + job.id).hashCode(), notification)
    }

    fun buildBackgroundSyncNotification(): android.app.Notification {
        return NotificationCompat.Builder(context, BACKGROUND_SYNC_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("OpenClaw background sync")
            .setContentText("Watching chats and cron jobs for new activity.")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(mainPendingIntent("dashboard"))
            .build()
    }

    private fun mainPendingIntent(target: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_NOTIFICATION_TARGET, target)
        }
        return PendingIntent.getActivity(
            context,
            target.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val messageChannel = NotificationChannel(
            MESSAGE_CHANNEL_ID,
            "Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for new agent and group chat messages."
        }
        val cronChannel = NotificationChannel(
            CRON_CHANNEL_ID,
            "Cron Jobs",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for cron job completions and failures."
        }
        val backgroundSyncChannel = NotificationChannel(
            BACKGROUND_SYNC_CHANNEL_ID,
            "Background Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent service notification for always-on monitoring."
        }
        manager.createNotificationChannels(listOf(messageChannel, cronChannel, backgroundSyncChannel))
    }

    private fun notificationSenderKey(message: RoomMessage): String {
        return message.senderId.ifBlank { message.senderName.ifBlank { message.id } }
    }

    private fun senderNotificationId(roomId: String, message: RoomMessage): Int {
        return "$roomId:${notificationSenderKey(message)}".hashCode()
    }

    private fun legacyRoomNotificationId(roomId: String): Int {
        return roomId.hashCode()
    }
}
