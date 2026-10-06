package com.watchcue.tv

import android.app.AlertDialog
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient.Builder().readTimeout(15, TimeUnit.SECONDS).build()
    private lateinit var status: TextView
    private var pollJob: Job? = null
    private var reminderDialog: AlertDialog? = null
    private val displayedJobIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply {
            textSize = 24f
            setPadding(48, 48, 48, 48)
            text = "WatchCue TV\n\nConnecting..."
        }
        setContentView(status)

        notifyOnline()
        startPolling()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        reminderDialog?.dismiss()
        super.onDestroy()
    }

    private fun startPolling() {
        pollJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    val jobs = pendingJobs()
                    status.text = "WatchCue TV\n\nConnected • ${jobs.length()} pending"

                    for (i in 0 until jobs.length()) {
                        val job = jobs.getJSONObject(i)
                        val jobId = job.getString("id")
                        if (jobId !in displayedJobIds) {
                            displayedJobIds += jobId
                            showReminder(job)
                            break
                        }
                    }
                } catch (e: Exception) {
                    status.text = "WatchCue TV\n\nConnection error\n${e.message ?: ""}"
                }

                delay(15_000)
            }
        }
    }

    private suspend fun pendingJobs(): JSONArray = withContext(Dispatchers.IO) {
        val url = "${BuildConfig.API_BASE_URL}/tv/jobs/pending?deviceId=${BuildConfig.DEVICE_ID}"
        val request = Request.Builder()
            .url(url)
            .header("X-Bridge-Key", BuildConfig.BRIDGE_KEY)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Backend HTTP ${response.code}")
            JSONArray(response.body?.string() ?: "[]")
        }
    }

    private fun showReminder(job: JSONObject) {
        if (isFinishing || isDestroyed || reminderDialog?.isShowing == true) return

        val title = job.optString("title", "WatchCue")
        val message = job.optString("message", "Your watchlist is waiting.")
        val jobId = job.getString("id")

        val body = TextView(this).apply {
            text = message
            textSize = 22f
            setPadding(36, 24, 36, 24)
        }

        reminderDialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(body)
            .setCancelable(false)
            .setPositiveButton("DISMISS") { _, _ ->
                lifecycleScope.launch {
                    acknowledge(jobId)
                    displayedJobIds.remove(jobId)
                    reminderDialog = null
                    refreshStatus()
                }
            }
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.window?.setLayout(
                        (resources.displayMetrics.widthPixels * 0.75).toInt(),
                        WindowManager.LayoutParams.WRAP_CONTENT
                    )
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
                }
                dialog.show()
            }
    }

    private suspend fun acknowledge(jobId: String) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${BuildConfig.API_BASE_URL}/tv/jobs/$jobId/delivered")
                .header("X-Bridge-Key", BuildConfig.BRIDGE_KEY)
                .post(ByteArray(0).toRequestBody(null))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Acknowledge HTTP ${response.code}")
            }
        }
    }

    private suspend fun refreshStatus() {
        try {
            val jobs = pendingJobs()
            status.text = "WatchCue TV\n\nConnected • ${jobs.length()} pending"
        } catch (_: Exception) {
            // The normal polling loop will retry.
        }
    }

    private fun notifyOnline() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject()
                    .put("deviceId", BuildConfig.DEVICE_ID)
                    .toString()

                val body = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("${BuildConfig.API_BASE_URL}/tv/online")
                    .post(body)
                    .build()

                client.newCall(request).execute().close()
            } catch (_: Exception) {
                // Polling remains the source of truth if this best-effort call fails.
            }
        }
    }
}
