package com.wa.beastclient.domain.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wa.beastclient.core.service.WaRealtimeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Triggered by [android.app.AlarmManager] when a scheduled message is due.
 */
class ScheduledMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appCtx = context.applicationContext
        Log.i(TAG, "ScheduledMessageReceiver woke up for alarm action=${intent?.action}")

        // Ensure background service is running so connection is active
        WaRealtimeService.ensureStarted(appCtx)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ScheduledMessageManager.processDueMessages(appCtx)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ScheduledMsgReceiver"
        const val ACTION_FIRE_SCHEDULED = "com.wa.beastclient.action.FIRE_SCHEDULED"
    }
}
