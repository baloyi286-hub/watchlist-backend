package com.watchcue.tv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
            text = if (Settings.canDrawOverlays(this@MainActivity)) {
                "WatchCue TV\n\nBackground reminders are active.\n\nOverlay permission: enabled\n\nYou can leave this app."
            } else {
                "WatchCue TV\n\nOne-time setup required.\n\nEnable 'Display over other apps' for WatchCue TV, then return here."
            }
        })

        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        ReminderService.start(this)
        notifyOnline()
    }

    override fun onResume() {
        super.onResume()
        if (::class.java != null && Settings.canDrawOverlays(this)) {
            (findViewById<android.view.View>(android.R.id.content) as? android.view.ViewGroup)
                ?.getChildAt(0)
                ?.let { view ->
                    if (view is TextView) {
                        view.text = "WatchCue TV\n\nBackground reminders are active.\n\nOverlay permission: enabled\n\nYou can leave this app."
                    }
                }
        }
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
