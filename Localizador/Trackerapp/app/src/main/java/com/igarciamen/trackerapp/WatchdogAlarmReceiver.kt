package com.igarciamen.trackerapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.database.FirebaseDatabase

class WatchdogAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("WatchdogAlarmReceiver", "Watchdog disparado, comprobando Service")

        val serviceIntent = Intent(context, LocalizadorService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)

        val deviceId = DevicePrefs.getDeviceId(context)
        val ownerUid = DevicePrefs.getOwnerUid(context)
        if (deviceId != null && ownerUid != null) {
            FirebaseDatabase.getInstance()
                .getReference("users/$ownerUid/devices/$deviceId/lastSeen")
                .setValue(System.currentTimeMillis())
        }

        // Se reprograma a sí mismo para el próximo ciclo.
        WatchdogScheduler.scheduleNext(context)
    }
}