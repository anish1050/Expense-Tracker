package com.anish1050.ledger.capture.notification

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.anish1050.ledger.capture.parser.RawCapture
import com.anish1050.ledger.container
import com.anish1050.ledger.data.db.Source

class TransactionNotificationListener : NotificationListenerService() {

    // Android unbinds the listener across reboots, app updates and process
    // death; payments notified in that gap would be lost. On reconnect, read
    // whatever is still in the shade. Re-reading one already captured is a
    // no-op: the dedupe key comes from the same amount and post time.
    override fun onListenerConnected() {
        runCatching { activeNotifications }.getOrNull()?.forEach(::onNotificationPosted)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val engine = container.ruleStore.engine.value
        // Cheap package check first; the engine may not be loaded yet on a cold
        // process, in which case the dispatcher loads it on its own thread.
        if (engine != null && !engine.acceptsSender(Source.notification, sbn.packageName)) return

        val extras = sbn.notification.extras
        val text = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
        ).joinToString(" ").trim()
        if (text.isEmpty()) return

        container.capture.submit(RawCapture(Source.notification, sbn.packageName, text, sbn.postTime))
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val self = ComponentName(context, TransactionNotificationListener::class.java).flattenToString()
            return enabled.split(':').any { it == self }
        }
    }
}
