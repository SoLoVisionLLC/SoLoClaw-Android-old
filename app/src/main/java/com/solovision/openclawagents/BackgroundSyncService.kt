package com.solovision.openclawagents

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.solovision.openclawagents.data.AppNotificationManager
import com.solovision.openclawagents.data.NotificationPreferencesStore
import com.solovision.openclawagents.data.OpenClawRepository
import com.solovision.openclawagents.data.buildOpenClawRuntimeDependencies
import com.solovision.openclawagents.model.CollaborationRoom
import com.solovision.openclawagents.model.CronJob
import com.solovision.openclawagents.model.MessageSenderType
import com.solovision.openclawagents.model.RoomMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BackgroundSyncService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private lateinit var repository: OpenClawRepository
    private var missionControlService: com.solovision.openclawagents.data.MissionControlService? = null
    private lateinit var notificationPreferencesStore: NotificationPreferencesStore
    private lateinit var appNotificationManager: AppNotificationManager

    override fun onCreate() {
        super.onCreate()
        val runtime = buildOpenClawRuntimeDependencies(applicationContext)
        repository = runtime.repository
        missionControlService = runtime.missionControlService
        notificationPreferencesStore = NotificationPreferencesStore(applicationContext)
        appNotificationManager = AppNotificationManager(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPolling()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                startForeground(NOTIFICATION_ID, appNotificationManager.buildBackgroundSyncNotification())
                startPolling()
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        stopPolling()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = serviceScope.launch {
            var cycle = 0
            while (isActive) {
                val settings = notificationPreferencesStore.readSettings(appNotificationManager.hasPermission())
                if (!settings.enabled || !settings.backgroundSyncEnabled) {
                    stopSelf()
                    return@launch
                }

                runCatching { pollRoomsForNotifications(settings) }
                    .onFailure { error ->
                        Log.w(LOG_TAG, "Room background sync failed", error)
                    }

                if (cycle % 2 == 0) {
                    runCatching { pollCronJobsForNotifications(settings) }
                        .onFailure { error ->
                            Log.w(LOG_TAG, "Cron background sync failed", error)
                        }
                }

                cycle += 1
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun pollRoomsForNotifications(settings: com.solovision.openclawagents.model.NotificationSettingsState) {
        if (!settings.messageNotificationsEnabled) return
        val rooms = repository.getRooms()
        rooms.forEach { room ->
            if (!settings.isRoomEnabled(room.id)) return@forEach
            val messages = repository.getRoomMessages(room.id)
            maybeNotifyForRoom(room, messages)
        }
    }

    private suspend fun pollCronJobsForNotifications(settings: com.solovision.openclawagents.model.NotificationSettingsState) {
        if (!settings.cronNotificationsEnabled) return
        val service = missionControlService ?: return
        val jobs = service.listCronJobs()
        jobs.forEach { job ->
            if (!settings.isCronJobEnabled(job.id)) return@forEach
            maybeNotifyForCron(job)
        }
    }

    private fun maybeNotifyForRoom(room: CollaborationRoom, messages: List<RoomMessage>) {
        val notificationEligibleMessages = messages.filter { message ->
            message.senderType != MessageSenderType.USER && !message.internal
        }
        val latestMessageKey = notificationEligibleMessages.lastOrNull()?.messageKey ?: return
        val lastNotifiedMessageKey = notificationPreferencesStore.readLastNotifiedMessageKey(room.id)

        if (lastNotifiedMessageKey == null) {
            notificationPreferencesStore.writeLastNotifiedMessageKey(room.id, latestMessageKey)
            return
        }

        val lastNotifiedIndex = notificationEligibleMessages.indexOfLast { it.messageKey == lastNotifiedMessageKey }
        if (lastNotifiedIndex < 0 || lastNotifiedIndex >= notificationEligibleMessages.lastIndex) {
            notificationPreferencesStore.writeLastNotifiedMessageKey(room.id, latestMessageKey)
            return
        }

        val newMessages = notificationEligibleMessages.subList(lastNotifiedIndex + 1, notificationEligibleMessages.size)
        notificationPreferencesStore.writeLastNotifiedMessageKey(room.id, latestMessageKey)
        if (newMessages.isEmpty()) return

        appNotificationManager.notifyNewMessages(room, newMessages)
    }

    private fun maybeNotifyForCron(job: CronJob) {
        val signature = cronNotificationSignature(job) ?: return
        val lastSignature = notificationPreferencesStore.readLastNotifiedCronSignature(job.id)

        if (lastSignature == null) {
            notificationPreferencesStore.writeLastNotifiedCronSignature(job.id, signature)
            return
        }

        if (lastSignature == signature) return

        notificationPreferencesStore.writeLastNotifiedCronSignature(job.id, signature)
        appNotificationManager.notifyCronUpdate(job)
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun cronNotificationSignature(job: CronJob): String? {
        val lastRunAt = job.lastRunAt ?: return null
        return listOf(lastRunAt, job.lastStatus.orEmpty(), job.lastError.orEmpty()).joinToString("|")
    }

    companion object {
        private const val LOG_TAG = "BackgroundSyncService"
        private const val NOTIFICATION_ID = 4107
        private const val POLL_INTERVAL_MS = 20_000L
        private const val ACTION_START = "com.solovision.openclawagents.action.START_BACKGROUND_SYNC"
        private const val ACTION_STOP = "com.solovision.openclawagents.action.STOP_BACKGROUND_SYNC"

        fun startIntent(context: Context): Intent {
            return Intent(context, BackgroundSyncService::class.java).apply {
                action = ACTION_START
            }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, BackgroundSyncService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}
