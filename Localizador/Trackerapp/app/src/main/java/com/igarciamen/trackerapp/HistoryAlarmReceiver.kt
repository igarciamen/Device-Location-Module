package com.igarciamen.trackerapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.database.FirebaseDatabase

class HistoryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("HistoryAlarmReceiver", "Alarma de historial disparada")

        // El AlarmManager despierta al dispositivo puntualmente; delegamos el
        // trabajo real (con permisos, corutinas y llamadas de red) a un
        // OneTimeWorkRequest de WorkManager, que sí sabe ejecutar CoroutineWorker.
        val request = OneTimeWorkRequestBuilder<LocationHistoryWorker>().build()
        WorkManager.getInstance(context).enqueue(request)

        val deviceId = DevicePrefs.getDeviceId(context)
        val ownerUid = DevicePrefs.getOwnerUid(context)
        if (deviceId != null && ownerUid != null) {
            FirebaseDatabase.getInstance()
                .getReference("users/$ownerUid/devices/$deviceId/lastSeen")
                .setValue(System.currentTimeMillis())
        }

        // Se reprograma a sí mismo para el próximo ciclo.
        HistoryScheduler.scheduleNext(context)
    }
}