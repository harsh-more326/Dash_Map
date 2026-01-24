package com.example.dash_map

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MediaNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}