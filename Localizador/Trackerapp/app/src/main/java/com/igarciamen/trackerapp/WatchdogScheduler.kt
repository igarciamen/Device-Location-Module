package com.igarciamen.trackerapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

object WatchdogScheduler {

    private const val REQUEST_CODE = 2001
    private const val INTERVALO_MS = 15 * 60 * 1000L // 15 minutos

    fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Sin permiso de alarmas exactas todavía; no programamos nada.
            // (Se resuelve en el mismo sitio donde ya gestionas este permiso en ToDoList: SettingsScreen.)
            return
        }

        val intent = Intent(context, WatchdogAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + INTERVALO_MS,
            pendingIntent
        )
    }
}
