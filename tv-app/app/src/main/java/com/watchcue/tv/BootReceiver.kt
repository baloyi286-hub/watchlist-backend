package com.watchcue.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Android TV restricts background activity launches on newer versions.
            // The receiver records boot availability; opening WatchCue TV starts cloud polling.
            context.getSharedPreferences("watchcue", Context.MODE_PRIVATE)
                .edit().putLong("lastBoot", System.currentTimeMillis()).apply()
        }
    }
}
