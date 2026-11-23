package com.sahl.home

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

/**
 * Live Notification Badges for Sahl 1×1 App Widgets (WhatsApp, etc.):
 * Counts only real, clearable, non-summary, non-ongoing notifications per package
 * and refreshes Sahl Home Screen Widgets immediately when notifications are posted or cleared.
 */
class BadgeListenerService : NotificationListenerService() {
    companion object {
        private val counts = ConcurrentHashMap<String, Int>()
        @Volatile
        private var instance: BadgeListenerService? = null

        fun countFor(pkg: String): Int = counts[pkg] ?: 0

        fun isEnabled(c: Context): Boolean {
            val flat = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false
            val cn = ComponentName(c, BadgeListenerService::class.java).flattenToString()
            return flat.contains(cn)
        }

        fun syncIfConnected(c: Context) {
            val inst = instance
            if (inst != null) {
                inst.refreshCounts()
            } else if (isEnabled(c)) {
                try {
                    requestRebind(ComponentName(c, BadgeListenerService::class.java))
                } catch (_: Exception) {}
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        refreshCounts()
    }

    override fun onListenerDisconnected() {
        instance = null
        counts.clear()
        try { SahlWidgets.updateAll(applicationContext) } catch (_: Exception) {}
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshCounts()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshCounts()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        refreshCounts()
    }

    private fun isRealUnreadNotification(sbn: StatusBarNotification): Boolean {
        if (sbn.isOngoing || !sbn.isClearable) return false
        val n = sbn.notification ?: return false
        val ignoreFlags = Notification.FLAG_ONGOING_EVENT or
            Notification.FLAG_FOREGROUND_SERVICE or
            Notification.FLAG_NO_CLEAR or
            Notification.FLAG_GROUP_SUMMARY
        if ((n.flags and ignoreFlags) != 0) return false
        val cat = n.category
        if (cat == Notification.CATEGORY_PROGRESS ||
            cat == Notification.CATEGORY_SERVICE ||
            cat == Notification.CATEGORY_STATUS ||
            cat == Notification.CATEGORY_SYSTEM
        ) return false
        return true
    }

    fun refreshCounts() {
        try {
            val active = activeNotifications ?: emptyArray()
            val map = HashMap<String, Int>()
            for (sbn in active) {
                if (!isRealUnreadNotification(sbn)) continue
                val n = sbn.notification ?: continue
                val num = if (n.number > 1) n.number else 1
                map[sbn.packageName] = (map[sbn.packageName] ?: 0) + num
            }
            counts.clear()
            counts.putAll(map)
            SahlWidgets.updateAll(applicationContext)
        } catch (_: Exception) {
            counts.clear()
            try { SahlWidgets.updateAll(applicationContext) } catch (_: Exception) {}
        }
    }
}
