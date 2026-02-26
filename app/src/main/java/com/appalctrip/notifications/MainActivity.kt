package com.appalctrip.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val usernameInput = findViewById<TextInputEditText>(R.id.usernameInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)
        val connectButton = findViewById<Button>(R.id.connectButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        val statusText = findViewById<TextView>(R.id.statusText)

        usernameInput.setText(prefs.getString(KEY_USERNAME, "") ?: "")
        statusText.text = currentStatus()

        requestNotificationPermissionIfNeeded()

        connectButton.setOnClickListener {
            val username = usernameInput.text?.toString()?.trim().orEmpty()
            val password = passwordInput.text?.toString().orEmpty()

            if (username.isBlank() || password.isBlank()) {
                Snackbar.make(connectButton, "Preenche username e password.", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .putBoolean(KEY_MONITORING_ENABLED, true)
                .apply()

            enqueueWorker()
            statusText.text = currentStatus()
            Snackbar.make(connectButton, "Ligado com sucesso. A monitorização está ativa.", Snackbar.LENGTH_LONG).show()
        }

        stopButton.setOnClickListener {
            prefs.edit().putBoolean(KEY_MONITORING_ENABLED, false).apply()
            WorkManager.getInstance(this).cancelUniqueWork(NotificationsWorker.UNIQUE_WORK_NAME)
            statusText.text = currentStatus()
            Snackbar.make(stopButton, "Monitorização desligada.", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun enqueueWorker() {
        val request = PeriodicWorkRequestBuilder<NotificationsWorker>(15, TimeUnit.MINUTES)
            .addTag(NotificationsWorker.UNIQUE_WORK_NAME)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            NotificationsWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun currentStatus(): String {
        return if (prefs.getBoolean(KEY_MONITORING_ENABLED, false)) {
            "Estado: ligado e a monitorizar notificações"
        } else {
            "Estado: desligado"
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(permission)
            }
        }
    }

    companion object {
        const val PREFS_NAME = "icligo_prefs"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        const val KEY_LAST_FINGERPRINT = "last_notifications_fingerprint"
    }
}
