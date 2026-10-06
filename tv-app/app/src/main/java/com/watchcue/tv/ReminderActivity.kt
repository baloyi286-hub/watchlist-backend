package com.watchcue.tv

import android.app.AlertDialog
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ReminderActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showReminder()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (dialog?.isShowing != true) showReminder()
    }

    private fun showReminder() {
        val jobId = intent.getStringExtra("jobId") ?: return finish()
        val title = intent.getStringExtra("title") ?: "WatchCue"
        val message = intent.getStringExtra("message") ?: "Your watchlist is waiting."

        val body = TextView(this).apply {
            text = message
            textSize = 22f
            setPadding(36, 24, 36, 24)
        }

        dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(body)
            .setCancelable(false)
            .setPositiveButton("DISMISS") { _, _ ->
                lifecycleScope.launch {
                    acknowledge(jobId)
                    finish()
                }
            }
            .create()

        dialog?.setOnShowListener {
            dialog?.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.75).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
        }
        dialog?.show()
    }

    private suspend fun acknowledge(jobId: String) = withContext(Dispatchers.IO) {
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
