package com.watchcue.tv

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ReminderOverlay {
    @Volatile private var visibleJobId: String? = null

    fun show(
        context: Context,
        jobId: String,
        title: String,
        message: String,
        onDismissed: () -> Unit
    ) {
        if (visibleJobId != null) return
        visibleJobId = jobId

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(40), dp(32), dp(40), dp(28))
            background = GradientDrawable().apply {
                setColor(0xEE151515.toInt())
                cornerRadius = dp(18).toFloat()
            }
            isFocusable = true
            isFocusableInTouchMode = true
        }

        panel.addView(TextView(context).apply {
            text = title
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
        })

        panel.addView(TextView(context).apply {
            text = message
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(0xFFE8E8E8.toInt())
            setPadding(0, dp(22), 0, dp(26))
        })

        val dismiss = Button(context).apply {
            text = "DISMISS"
            textSize = 18f
        }
        panel.addView(dismiss)

        val params = WindowManager.LayoutParams(
            (context.resources.displayMetrics.widthPixels * 0.72).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        fun close() {
            try { wm.removeView(panel) } catch (_: Exception) {}
            visibleJobId = null
            CoroutineScope(Dispatchers.IO).launch { onDismissed() }
        }

        dismiss.setOnClickListener { close() }
        panel.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP &&
                (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE)) {
                close()
                true
            } else false
        }

        wm.addView(panel, params)
        dismiss.requestFocus()
    }
}
