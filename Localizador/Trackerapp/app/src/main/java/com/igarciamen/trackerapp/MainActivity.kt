package com.igarciamen.trackerapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.igarciamen.trackerapp.ui.theme.TrackerAppTheme
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

import com.google.firebase.database.FirebaseDatabase
import java.util.UUID


class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("TrackerApp", "Permiso concedido, pidiendo ubicación...")
            obtenerUbicacionActual()
        } else {
            Log.d("TrackerApp", "Permiso DENEGADO por el usuario")
        }
    }

    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("TrackerApp", "Permiso de notificaciones concedido: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    Log.d("TrackerApp", "Sesión anónima iniciada correctamente")
                }
                .addOnFailureListener {
                    Log.e("TrackerApp", "Error al iniciar sesión anónima: ${it.message}")
                }
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            obtenerUbicacionActual()
        } else {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        WatchdogScheduler.scheduleNext(this)
        HistoryScheduler.scheduleNext(this)

        enableEdgeToEdge()
        setContent {
            TrackerAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
                        Greeting(name = "Android")

                        var nombreTexto by remember {
                            mutableStateOf(DevicePrefs.getDeviceName(this@MainActivity) ?: "")
                        }
                        var codigoTexto by remember { mutableStateOf("") }
                        var servicioActivo by remember { mutableStateOf(false) }
                        var mensajeError by remember { mutableStateOf<String?>(null) }
                        var buscando by remember { mutableStateOf(false) }
                        val yaVinculado = DevicePrefs.getOwnerUid(this@MainActivity) != null

                        if (!yaVinculado) {
                            OutlinedTextField(
                                value = nombreTexto,
                                onValueChange = { nombreTexto = it },
                                label = { Text("Nombre de este teléfono") }
                            )
                            OutlinedTextField(
                                value = codigoTexto,
                                onValueChange = { codigoTexto = it.uppercase() },
                                label = { Text("Código de emparejamiento") }
                            )
                            if (mensajeError != null) {
                                Text(mensajeError!!)
                            }
                        } else {
                            Text("Este teléfono: ${DevicePrefs.getDeviceName(this@MainActivity)}")
                        }

                        Button(onClick = {
                            if (!yaVinculado) {
                                if (nombreTexto.isBlank() || codigoTexto.isBlank()) {
                                    mensajeError = "Rellena el nombre y el código"
                                    return@Button
                                }
                                buscando = true
                                mensajeError = null

                                val indiceRef = FirebaseDatabase.getInstance().getReference("pairingCodes/$codigoTexto")
                                indiceRef.addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(snapshot: DataSnapshot) {
                                        buscando = false
                                        val uidEncontrado = snapshot.getValue(String::class.java)

                                        if (uidEncontrado == null) {
                                            mensajeError = "Código no válido, revísalo e inténtalo de nuevo"
                                            return
                                        }


                                        val id = "tel_" + java.util.UUID.randomUUID().toString().take(8)
                                        DevicePrefs.saveDevice(this@MainActivity, id, nombreTexto, uidEncontrado)

                                        val deviceRef = FirebaseDatabase.getInstance()
                                            .getReference("users/$uidEncontrado/devices/$id")
                                        deviceRef.setValue(mapOf("name" to nombreTexto))

                                        val intent = Intent(this@MainActivity, LocalizadorService::class.java)
                                        ContextCompat.startForegroundService(this@MainActivity, intent)
                                        servicioActivo = true
                                    }

                                    override fun onCancelled(error: DatabaseError) {
                                        buscando = false
                                        mensajeError = "Error de conexión: ${error.message}"
                                    }
                                })
                            } else {
                                val intent = Intent(this@MainActivity, LocalizadorService::class.java)
                                ContextCompat.startForegroundService(this@MainActivity, intent)
                                servicioActivo = true
                            }
                        }) {
                            Text(
                                when {
                                    buscando -> "Comprobando código..."
                                    servicioActivo -> "Localizador activo ✓"
                                    !yaVinculado -> "Vincular y activar localizador"
                                    else -> "Activar localizador"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun obtenerUbicacionActual() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                Log.d("TrackerApp", "Ubicación obtenida: lat=${location.latitude}, lng=${location.longitude}")
            } else {
                Log.d("TrackerApp", "location es null (prueba a mover el emulador o esperar unos segundos)")
            }
        }.addOnFailureListener {
            Log.d("TrackerApp", "Error al obtener ubicación: ${it.message}")
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TrackerAppTheme {
        Greeting("Android")
    }
}