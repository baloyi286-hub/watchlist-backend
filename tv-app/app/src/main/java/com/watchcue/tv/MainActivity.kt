package com.watchcue.tv

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(TextView(this).apply {
            textSize = 24f
            setPadding(48, 48, 48, 48)
            text = "WatchCue TV\n\nBackground reminders are active.\n\nYou can leave this app."
        })

        ReminderService.start(this)
        notifyOnline()
    }

    private fun notifyOnline() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().put("deviceId", BuildConfig.DEVICE_ID).toString()
                val request = Request.Builder()
                    .url("${BuildConfig.API_BASE_URL}/tv/online")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().close()
            } catch (_: Exception) {
                // The background service continues polling even if this call fails.
            }
        }
    }
}
