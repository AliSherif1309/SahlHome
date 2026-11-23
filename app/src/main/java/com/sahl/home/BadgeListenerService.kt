package com.sahl.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

/**
 * Live Notification Badges for Sahl Home:
 * Counts active notifications per package and broadcasts updates to the home screen
 * so Sahl Home displays gold Islamic rosette/pill notification badges like Nova Prime.
 */
class BadgeListenerService : NotificationListenerService() {
    companion object {
        const val ACTION_BADGES_CHANGED = "com.sahl.home.ACTION_BADGES_CHANGED"
        private val counts = ConcurrentHashMap<String, Int>()

        fun countFor(pkg: String): Int = counts[pkg] ?: 0

        fun isEnabled(c: Context): Boolean {
            val flat = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false
            val cn = ComponentName(c, BadgeListenerService::class.java).flattenToString()
            return flat.contains(cn)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshCounts()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshCounts()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshCounts()
    }

    private fun refreshCounts() {
        try {
            val active = activeNotifications ?: return
            val map = HashMap<String, Int>()
            for (sbn in active) {
                if (sbn.isOngoing) continue
                val n = sbn.notification ?: continue
                val num = if (n.number > 1) n.number else 1
                map[sbn.packageName] = (map[sbn.packageName] ?: 0) + num
            }
            counts.clear()
            counts.putAll(map)
            sendBroadcast(Intent(ACTION_BADGES_CHANGED).setPackage(packageName))
        } catch (_: Exception) {}
    }
}
