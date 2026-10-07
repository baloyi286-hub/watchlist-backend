package com.watchcue.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class ReminderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        createServiceChannel()
        startForeground(SERVICE_NOTIFICATION_ID, serviceNotification())
        Log.i(TAG, "ReminderService created; overlayAllowed=${Settings.canDrawOverlays(this)}")
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
                Log.d(TAG, "Polling backend for device=${BuildConfig.DEVICE_ID}")
                val jobs = pendingJobs()
                Log.i(TAG, "Poll complete; pendingJobs=${jobs.length()}; overlayAllowed=${Settings.canDrawOverlays(this@ReminderService)}")

                if (jobs.length() > 0) {
                    val job = jobs.getJSONObject(0)
                    val jobId = job.getString("id")

                    if (Settings.canDrawOverlays(this@ReminderService)) {
                        Log.i(TAG, "Attempting overlay for job=$jobId")
                        withContext(Dispatchers.Main) {
                            ReminderOverlay.show(
                                context = this@ReminderService,
                                jobId = jobId,
                                title = job.optString("title", "WatchCue"),
                                message = job.optString(
                                    "message",
                                    "Your watchlist is waiting."
                                ),
                                onDismissed = {
                                    Log.i(TAG, "Overlay dismissed; acknowledging job=$jobId")
                                    acknowledge(jobId)
                                }
                            )
                        }
                        Log.i(TAG, "Overlay show() returned for job=$jobId")
                    } else {
                        Log.e(TAG, "Pending job exists but SYSTEM_ALERT_WINDOW is not allowed")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reminder polling/display failed", e)
            }

            delay(POLL_INTERVAL_MS)
        }
    }

    private fun acknowledge(jobId: String) {
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/tv/jobs/$jobId/delivered")
            .header("X-Bridge-Key", BuildConfig.BRIDGE_KEY)
            .post(ByteArray(0).toRequestBody(null))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Acknowledge HTTP ${response.code}")
            }
        }
    }

    private fun pendingJobs(): JSONArray {
        val request = Request.Builder()
            .url(
                "${BuildConfig.API_BASE_URL}/tv/jobs/pending" +
                    "?deviceId=${BuildConfig.DEVICE_ID}"
            )
            .header("X-Bridge-Key", BuildConfig.BRIDGE_KEY)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Backend HTTP ${response.code}")
            }

            return JSONArray(response.body?.string() ?: "[]")
        }
    }

    private fun createServiceChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "WatchCue background service",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun serviceNotification(): Notification {
        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("WatchCue TV")
            .setContentText("Watching for TV reminders")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "WatchCueTV"
        private const val SERVICE_CHANNEL_ID = "watchcue_background"
        private const val SERVICE_NOTIFICATION_ID = 2001
        private const val POLL_INTERVAL_MS = 15_000L

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
