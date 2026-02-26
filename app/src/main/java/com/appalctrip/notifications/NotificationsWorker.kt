package com.appalctrip.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationsWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    private val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!prefs.getBoolean(MainActivity.KEY_MONITORING_ENABLED, false)) {
            return@withContext Result.success()
        }

        val username = prefs.getString(MainActivity.KEY_USERNAME, null)
        val password = prefs.getString(MainActivity.KEY_PASSWORD, null)

        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            return@withContext Result.failure()
        }

        return@withContext try {
            val scraper = SiteNotificationsScraper()
            val currentFingerprint = scraper.fetchNotificationsFingerprint(username, password)

            val previousFingerprint = prefs.getString(MainActivity.KEY_LAST_FINGERPRINT, null)
            prefs.edit().putString(MainActivity.KEY_LAST_FINGERPRINT, currentFingerprint).apply()

            if (!previousFingerprint.isNullOrBlank() && previousFingerprint != currentFingerprint) {
                showMobileNotification("Nova notificação", "Existe uma nova atualização no portal iCLigo.")
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun showMobileNotification(title: String, text: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Notificações iCLigo",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "notifications_polling_worker"
        private const val CHANNEL_ID = "icligo_notifications"
    }
}
