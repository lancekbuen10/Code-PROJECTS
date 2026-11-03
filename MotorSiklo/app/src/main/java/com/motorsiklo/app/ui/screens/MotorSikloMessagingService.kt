package com.motorsiklo.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random
import android.util.Log

class MotorSikloMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data

        val title = data["title"]
            ?: remoteMessage.notification?.title
            ?: "Violation Detected"

        val message = data["body"]
            ?: remoteMessage.notification?.body
            ?: "A new incident requires your attention."

        val capturedMs = data["captured_ms"]?.toLongOrNull()
            ?: data["captured_at_ms"]?.toLongOrNull()

        val notifMs = System.currentTimeMillis()
        val latencyMs = capturedMs?.let { notifMs - it }

        Log.d(
            "MotorSikloFCM",
            "captured_ms=$capturedMs notif_ms=$notifMs latency_ms=$latencyMs data=$data"
        )

        sendNotification(title, message)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Required by Firebase, though we rely on Topics instead of direct tokens
    }

    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "motorsiklo_alerts"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // TODO: Replace with R.drawable.ic_motorsiklo_olive_logo later
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 250, 500)) // Distinct double-buzz pattern
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android 8.0+ requires a dedicated Notification Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Live Violation Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Critical alerts from the YOLOv8 AI Core"
            channel.enableVibration(true)
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(Random.nextInt(), notificationBuilder.build())
    }
}