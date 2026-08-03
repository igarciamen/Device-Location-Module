package com.igarciamen.trackerapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class LocalizadorService : Service() {

    companion object {
        private const val CHANNEL_ID = "localizador_channel"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var deviceId: String
    private lateinit var ownerUid: String

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var requestsRef: com.google.firebase.database.DatabaseReference
    private lateinit var locationsRef: com.google.firebase.database.DatabaseReference

    private val requestListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (snapshot.exists()) {
                Log.d("LocalizadorService", "Petición recibida, obteniendo ubicación...")
                obtenerYSubirUbicacion()
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Log.d("LocalizadorService", "Error escuchando peticiones: ${error.message}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("LocalizadorService", "Service creado")
        crearCanalNotificacion()

        // Las alarmas del watchdog se programan ANTES de intentar publicar la
        // notificación. Así, aunque el arranque falle justo aquí (por ejemplo,
        // justo tras un reinicio del teléfono, cuando Android puede matar el
        // proceso por "demasiados procesos vacíos" antes de terminar de
        // inicializarse), quede al menos un watchdog vivo que reintentará en
        // el próximo ciclo, en vez de quedar sin ningún reintento programado.
        WatchdogScheduler.scheduleNext(this)
        HistoryScheduler.scheduleNext(this)

        try {
            val notification = construirNotificacion()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                } catch (e: Exception) {
                    Log.e(
                        "LocalizadorService",
                        "No se pudo arrancar con tipo location, reintentando sin tipo: ${e.message}"
                    )
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("LocalizadorService", "Error al publicar la notificación: ${e.message}")
            stopSelf()
            return
        }

        deviceId = DevicePrefs.getDeviceId(this) ?: "telefonoA"
        ownerUid = DevicePrefs.getOwnerUid(this) ?: ""

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val database = FirebaseDatabase.getInstance()
        requestsRef = database.getReference("users/$ownerUid/requests/$deviceId")
        locationsRef = database.getReference("users/$ownerUid/locations/$deviceId")

        requestsRef.addValueEventListener(requestListener)
        Log.d("LocalizadorService", "Escuchando peticiones en users/$ownerUid/requests/$deviceId")

        enviarLatido()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocalizadorService", "Service iniciado (onStartCommand)")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::requestsRef.isInitialized) {
            requestsRef.removeEventListener(requestListener)
        }
        Log.d("LocalizadorService", "Service destruido")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("LocalizadorService", "Tarea eliminada, reprogramando reinicio del Service")

        val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.d("LocalizadorService", "Sin permiso de alarmas exactas, no se puede reprogramar")
            return
        }

        val restartIntent = Intent(applicationContext, LocalizadorService::class.java)
        val pendingIntent = android.app.PendingIntent.getService(
            applicationContext,
            1,
            restartIntent,
            android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            pendingIntent
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun obtenerYSubirUbicacion() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("LocalizadorService", "Sin permiso de ubicación, no se puede responder")
            return
        }

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).addOnSuccessListener { location ->
            if (location != null) {
                val datos = mapOf(
                    "lat" to location.latitude,
                    "lng" to location.longitude,
                    "timestamp" to System.currentTimeMillis()
                )
                locationsRef.setValue(datos)
                Log.d(
                    "LocalizadorService",
                    "Ubicación subida: lat=${location.latitude}, lng=${location.longitude}"
                )
                enviarLatido()
            } else {
                Log.d("LocalizadorService", "location es null al responder a la petición")
            }
        }.addOnFailureListener {
            Log.d("LocalizadorService", "Error al obtener ubicación: ${it.message}")
        }
    }

    private fun enviarLatido() {
        if (ownerUid.isBlank()) return
        val deviceRef = FirebaseDatabase.getInstance()
            .getReference("users/$ownerUid/devices/$deviceId")
        deviceRef.child("lastSeen").setValue(System.currentTimeMillis())
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sincronización redmi",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun construirNotificacion(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sincronización redmi")
            .setContentText("Esperando peticiones de ubicación")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }
}