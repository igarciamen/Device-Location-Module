package com.igarciamen.trackerapp

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationHistoryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val UMBRAL_METROS = 50f
    }

    override suspend fun doWork(): Result {
        val deviceId = DevicePrefs.getDeviceId(applicationContext) ?: return Result.success()
        val ownerUid = DevicePrefs.getOwnerUid(applicationContext) ?: return Result.success()

        if (ContextCompat.checkSelfPermission(
                applicationContext, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("LocationHistoryWorker", "Sin permiso de ubicación, se omite este ciclo")
            return Result.success()
        }

        val location = obtenerUbicacionActual() ?: run {
            Log.d("LocationHistoryWorker", "location es null, se omite este ciclo")
            return Result.success()
        }

        val database = FirebaseDatabase.getInstance()
        val historyRef = database.getReference("users/$ownerUid/history/$deviceId")
        val lastKnownRef = database.getReference("users/$ownerUid/lastKnownLocation/$deviceId")

        val lastKnown = leerUltimaConocida(lastKnownRef)

        val datosNuevos = mapOf(
            "lat" to location.latitude,
            "lng" to location.longitude,
            "timestamp" to System.currentTimeMillis()
        )

        if (lastKnown == null) {
            historyRef.push().setValue(datosNuevos)
            lastKnownRef.setValue(datosNuevos)
            Log.d("LocationHistoryWorker", "Primera entrada de historial guardada")
            return Result.success()
        }

        val ubicacionAnterior = Location("firebase").apply {
            latitude = lastKnown.first
            longitude = lastKnown.second
        }
        val distancia = location.distanceTo(ubicacionAnterior)

        if (distancia >= UMBRAL_METROS) {
            historyRef.push().setValue(datosNuevos)
            lastKnownRef.setValue(datosNuevos)
            Log.d("LocationHistoryWorker", "Movimiento de ${distancia}m, entrada guardada en history")
        } else {
            Log.d("LocationHistoryWorker", "Movimiento de ${distancia}m, por debajo del umbral, no se guarda")
        }

        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private suspend fun obtenerUbicacionActual(): Location? =
        suspendCancellableCoroutine { continuation ->
            val client = LocationServices.getFusedLocationProviderClient(applicationContext)
            client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { location ->
                continuation.resume(location)
            }.addOnFailureListener {
                continuation.resume(null)
            }
        }

    private suspend fun leerUltimaConocida(
        ref: com.google.firebase.database.DatabaseReference
    ): Pair<Double, Double>? = suspendCancellableCoroutine { continuation ->
        ref.get().addOnSuccessListener { snapshot ->
            val lat = snapshot.child("lat").getValue(Double::class.java)
            val lng = snapshot.child("lng").getValue(Double::class.java)
            if (lat != null && lng != null) {
                continuation.resume(Pair(lat, lng))
            } else {
                continuation.resume(null)
            }
        }.addOnFailureListener {
            continuation.resume(null)
        }
    }
}