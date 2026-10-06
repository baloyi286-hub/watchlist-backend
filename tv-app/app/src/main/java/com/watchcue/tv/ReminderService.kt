package com.watchcue.tv

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder\nimport android.provider.Settings
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*\nimport okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class ReminderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder().readTimeout(15, TimeUnit.SECONDS).build()

    override fun onCreate() {
        super.onCreate()
        createServiceChannel()
        startForeground(SERVICE_NOTIFICATION_ID, serviceNotification())
        scope.launch { pollLoop() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun pollLoop() {
        while (scope.isActive) {
            try {
                val jobs = pendingJobs()
                if (jobs.length() > 0) {
                    val job = jobs.getJSONObject(0)
                    if (Settings.canDrawOverlays(this@ReminderService)) {
                        ReminderOverlay.show(
                            context = this@ReminderService,
                            jobId = job.getString("id"),
                            title = job.optString("title", "WatchCue"),
                            message = job.optString("message", "Your watchlist is waiting."),
                            onDismissed = { acknowledge(job.getString("id")) }
                        )
                    } else {
                        val intent = Intent(this@ReminderService, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                }
            } catch (_: Exception) {
                // Keep the service alive and retry on the next poll.
            }
            delay(15_000)
        }
    }

    private fun acknowledge(jobId: String) {
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/tv/jobs/$jobId/delivered")
            .header("X-Bridge-Key", BuildConfig.BRIDGE_KEY)
            .post(ByteArray(0).toRequestBody(null))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Acknowledge HTTP ${response.code}")
        }
    }

    private fun pendingJobs(): JSONArray {
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/tv/jobs/pending?deviceId=${BuildConfig.DEVICE_ID}")
            .header("X-Bridge-Key", BuildConfig.BRIDGE_KEY)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Backend HTTP ${response.code}")
            return JSONArray(response.body?.string() ?: "[]")
        }
    }

    private fun createServiceChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    SERVICE_CHANNEL_ID,
                    "WatchCue background service",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun serviceNotification(): Notification =
        NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("WatchCue TV")
            .setContentText("Watching for TV reminders")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val SERVICE_CHANNEL_ID = "watchcue_background"
        private const val SERVICE_NOTIFICATION_ID = 2001

        fun start(context: Context) {
            val intent = Intent(context, ReminderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
