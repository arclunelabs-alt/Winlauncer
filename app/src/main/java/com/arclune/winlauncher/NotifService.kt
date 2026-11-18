package com.arclune.winlauncher

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Membaca notifikasi asli untuk Notification Center dan banner. */
class NotifService : NotificationListenerService() {
    companion object {
        var instance: NotifService? = null
        /** Dipanggil (di thread mana pun) saat notifikasi baru muncul. */
        var onPosted: ((StatusBarNotification) -> Unit)? = null
        fun clearAll() { try { instance?.cancelAllNotifications() } catch (e: Exception) { } }
        fun list(): List<StatusBarNotification> =
            try { instance?.activeNotifications?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { instance = null }
    override fun onNotificationPosted(sbn: StatusBarNotification) { try { onPosted?.invoke(sbn) } catch (e: Exception) { } }
}
