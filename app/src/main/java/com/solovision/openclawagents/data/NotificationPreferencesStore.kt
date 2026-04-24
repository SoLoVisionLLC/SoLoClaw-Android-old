package com.solovision.openclawagents.data

import android.content.Context
import android.content.SharedPreferences
import com.solovision.openclawagents.model.NotificationSettingsState

private const val NOTIFICATION_PREFS = "openclaw_notification_preferences"
private const val PREF_NOTIFICATIONS_ENABLED = "notifications_enabled"
private const val PREF_MESSAGE_NOTIFICATIONS_ENABLED = "message_notifications_enabled"
private const val PREF_CRON_NOTIFICATIONS_ENABLED = "cron_notifications_enabled"
private const val PREF_BACKGROUND_SYNC_ENABLED = "background_sync_enabled"
private const val PREF_DISABLED_ROOM_IDS = "disabled_room_ids"
private const val PREF_DISABLED_CRON_JOB_IDS = "disabled_cron_job_ids"
private const val PREF_LAST_NOTIFIED_MESSAGE_PREFIX = "last_notified_message:"
private const val PREF_LAST_NOTIFIED_CRON_PREFIX = "last_notified_cron:"

class NotificationPreferencesStore private constructor(
    private val prefs: SharedPreferences?
) {

    constructor() : this(null)

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(NOTIFICATION_PREFS, Context.MODE_PRIVATE)
    )

    fun readSettings(permissionGranted: Boolean): NotificationSettingsState {
        return NotificationSettingsState(
            enabled = prefs?.getBoolean(PREF_NOTIFICATIONS_ENABLED, true) ?: true,
            messageNotificationsEnabled = prefs?.getBoolean(PREF_MESSAGE_NOTIFICATIONS_ENABLED, true) ?: true,
            cronNotificationsEnabled = prefs?.getBoolean(PREF_CRON_NOTIFICATIONS_ENABLED, true) ?: true,
            backgroundSyncEnabled = prefs?.getBoolean(PREF_BACKGROUND_SYNC_ENABLED, false) ?: false,
            permissionGranted = permissionGranted,
            disabledRoomIds = prefs?.getStringSet(PREF_DISABLED_ROOM_IDS, emptySet()).orEmpty(),
            disabledCronJobIds = prefs?.getStringSet(PREF_DISABLED_CRON_JOB_IDS, emptySet()).orEmpty()
        )
    }

    fun writeNotificationsEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PREF_NOTIFICATIONS_ENABLED, enabled)?.apply()
    }

    fun writeMessageNotificationsEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PREF_MESSAGE_NOTIFICATIONS_ENABLED, enabled)?.apply()
    }

    fun writeCronNotificationsEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PREF_CRON_NOTIFICATIONS_ENABLED, enabled)?.apply()
    }

    fun writeBackgroundSyncEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PREF_BACKGROUND_SYNC_ENABLED, enabled)?.apply()
    }

    fun writeRoomEnabled(roomId: String, enabled: Boolean) {
        writeSetValue(PREF_DISABLED_ROOM_IDS, roomId, enabled)
    }

    fun writeCronJobEnabled(jobId: String, enabled: Boolean) {
        writeSetValue(PREF_DISABLED_CRON_JOB_IDS, jobId, enabled)
    }

    fun readLastNotifiedMessageKey(roomId: String): String? {
        return prefs?.getString(PREF_LAST_NOTIFIED_MESSAGE_PREFIX + roomId.trim(), null)
    }

    fun writeLastNotifiedMessageKey(roomId: String, messageKey: String?) {
        val editor = prefs?.edit() ?: return
        val key = PREF_LAST_NOTIFIED_MESSAGE_PREFIX + roomId.trim()
        if (messageKey.isNullOrBlank()) {
            editor.remove(key)
        } else {
            editor.putString(key, messageKey)
        }
        editor.apply()
    }

    fun readLastNotifiedCronSignature(jobId: String): String? {
        return prefs?.getString(PREF_LAST_NOTIFIED_CRON_PREFIX + jobId.trim(), null)
    }

    fun writeLastNotifiedCronSignature(jobId: String, signature: String?) {
        val editor = prefs?.edit() ?: return
        val key = PREF_LAST_NOTIFIED_CRON_PREFIX + jobId.trim()
        if (signature.isNullOrBlank()) {
            editor.remove(key)
        } else {
            editor.putString(key, signature)
        }
        editor.apply()
    }

    private fun writeSetValue(prefKey: String, id: String, enabled: Boolean) {
        val normalizedId = id.trim()
        if (normalizedId.isEmpty()) return
        val current = prefs?.getStringSet(prefKey, emptySet()).orEmpty().toMutableSet()
        if (enabled) {
            current.remove(normalizedId)
        } else {
            current.add(normalizedId)
        }
        prefs?.edit()?.putStringSet(prefKey, current)?.apply()
    }
}
