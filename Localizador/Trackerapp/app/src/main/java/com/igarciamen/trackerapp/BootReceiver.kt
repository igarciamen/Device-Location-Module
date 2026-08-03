package com.igarciamen.trackerapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Pequeño retraso para dar tiempo al sistema a estabilizarse tras
            // el arranque, antes de intentar publicar una notificación de
            // foreground service (Android puede rechazarla si se hace demasiado
            // pronto tras el boot).
            Handler(context.mainLooper).postDelayed({
                try {
                    val serviceIntent = Intent(context, LocalizadorService::class.java)
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (e: Exception) {
                    Log.e("BootReceiver", "No se pudo arrancar el Service tras el arranque: ${e.message}")
                }
            }, 3000)
        }
    }
}