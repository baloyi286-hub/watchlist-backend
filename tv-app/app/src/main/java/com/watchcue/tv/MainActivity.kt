package com.watchcue.tv

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient.Builder().readTimeout(15, TimeUnit.SECONDS).build()
    private lateinit var status: TextView
    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            textSize = 24f
            setPadding(48,48,48,48)
            text = "WatchCue TV\n\nConnecting..."
        }
        setContentView(status)
        createChannel()
        notifyOnline()
        startPolling()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }

    private fun startPolling() {
        pollJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    val jobs = pendingJobs()
                    status.text = "WatchCue TV\n\nConnected • ${jobs.length()} pending"
                    for (i in 0 until jobs.length()) deliver(jobs.getJSONObject(i))
                } catch (e: Exception) {
                    status.text = "WatchCue TV\n\nConnection error\n${e.message ?: ""}"
                }
                delay(15_000)
            }
        }
    }

    private suspend fun pendingJobs(): JSONArray = withContext(Dispatchers.IO) {
        val url = "${BuildConfig.API_BASE_URL}/tv/jobs/pending?deviceId=${BuildConfig.DEVICE_ID}"
        val req = Request.Builder().url(url).header("X-Bridge-Key", BuildConfig.BRIDGE_KEY).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) error("Backend HTTP ${res.code}")
            JSONArray(res.body?.string() ?: "[]")
        }
    }

    private suspend fun deliver(job: JSONObject) {
        showReminder(job.optString("title","WatchCue"), job.optString("message"))
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${BuildConfig.API_BASE_URL}/tv/jobs/${job.getString("id")}/delivered")
                .header("X-Bridge-Key", BuildConfig.BRIDGE_KEY)
                .post(RequestBody.create(null, ByteArray(0))).build()
            client.newCall(req).execute().close()
        }
    }

    private fun notifyOnline() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().put("deviceId", BuildConfig.DEVICE_ID).toString()
                val body = RequestBody.create("application/json".toMediaTypeOrNull(), json)
                val req = Request.Builder().url("${BuildConfig.API_BASE_URL}/tv/online").post(body).build()
                client.newCall(req).execute().close()
            } catch (_: Exception) {}
        }
    }

    private fun showReminder(title: String, message: String) {
        val notification = NotificationCompat.Builder(this, "watchcue")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(message.lineSequence().firstOrNull() ?: "Things to watch")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).build()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < 33) {
            NotificationManagerCompat.from(this).notify((System.currentTimeMillis()%Int.MAX_VALUE).toInt(), notification)
        }
    }

    private fun createChannel() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("watchcue","WatchCue reminders",NotificationManager.IMPORTANCE_HIGH))
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }
}
