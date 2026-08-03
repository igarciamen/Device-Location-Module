package com.igarciamen.viewerapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.igarciamen.viewerapp.ui.theme.ViewerAppTheme
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

data class Dispositivo(val id: String, val nombre: String, val lastSeen: Long?)

private val ColorMenuPrincipal = Color(0xCC1E3A5F)
private val ColorElegirTelefono = Color(0xCC2E7D32)
private val ColorSolicitarUbicacion = Color(0xCC6A1B9A)
private val ColorElegirFecha = Color(0xCCEF6C00)
private val ColorVerHistorial = Color(0xCC00838F)
private val ColorQuitarFecha = Color(0xCCB71C1C)
private val ColorOcultar = Color(0xCC424242)
private val ColorCodigo = Color(0xCC00695C)
private val ColorCerrarSesion = Color(0xCC880E4F)
private val ColorVerDetalle = Color(0xCC5D4037)

// Ajusta aquí el aspecto del historial dibujado en el mapa.
private const val COLOR_LINEA_HISTORIAL = "#E10600"
private const val GROSOR_LINEA_HISTORIAL = 7f
private const val COLOR_FLECHA_HISTORIAL = "#FF7A00"
private const val TAMANO_FLECHA_HISTORIAL = 45
private const val INTERVALO_FLECHAS = 6

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().userAgentValue = packageName
        val auth = FirebaseAuth.getInstance()

        enableEdgeToEdge()
        setContent {
            ViewerAppTheme {
                var sesionActiva by remember { mutableStateOf(auth.currentUser != null) }

                if (!sesionActiva) {
                    LoginScreen(auth = auth, onLoginExitoso = {
                        val uid = auth.currentUser?.uid
                        if (uid != null) {
                            asegurarCodigoEmparejamiento(uid)
                        }
                        sesionActiva = true
                    })
                    return@ViewerAppTheme
                }

                val uid = auth.currentUser?.uid ?: return@ViewerAppTheme

                var dispositivos by remember { mutableStateOf<List<Dispositivo>>(emptyList()) }
                var seleccionado by remember { mutableStateOf<Dispositivo?>(null) }
                var menuAbierto by remember { mutableStateOf(false) }
                var menuPrincipalAbierto by remember { mutableStateOf(false) }
                var ubicacionActual by remember { mutableStateOf<GeoPoint?>(null) }
                var puntosHistorial by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
                var timestampsHistorial by remember { mutableStateOf<List<Long?>>(emptyList()) }
                var cargandoHistorial by remember { mutableStateOf(false) }
                var fechaSeleccionadaMillis by remember { mutableStateOf<Long?>(null) }
                var showDatePicker by remember { mutableStateOf(false) }
                var codigoEmparejamiento by remember { mutableStateOf<String?>(null) }
                var mostrarDialogoCodigo by remember { mutableStateOf(false) }
                var mostrarDetalle by remember { mutableStateOf(false) }

                if (mostrarDetalle) {
                    DetalleHistorialScreen(
                        puntos = puntosHistorial,
                        timestamps = timestampsHistorial,
                        onVolver = {
                            mostrarDetalle = false
                            puntosHistorial = emptyList()
                            timestampsHistorial = emptyList()

                        }
                    )
                    return@ViewerAppTheme
                }

                DisposableEffect(uid) {
                    val devicesRef = FirebaseDatabase.getInstance().getReference("users/$uid/devices")
                    val listener = object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val lista = snapshot.children.mapNotNull { child ->
                                val nombre = child.child("name").getValue(String::class.java)
                                val lastSeen = child.child("lastSeen").getValue(Long::class.java)
                                if (nombre != null) {
                                    Dispositivo(child.key ?: return@mapNotNull null, nombre, lastSeen)
                                } else null
                            }
                            dispositivos = lista
                            if (seleccionado == null && lista.isNotEmpty()) {
                                seleccionado = lista.first()
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {}
                    }
                    devicesRef.addValueEventListener(listener)
                    onDispose { devicesRef.removeEventListener(listener) }
                }

                DisposableEffect(uid) {
                    val codeRef = FirebaseDatabase.getInstance().getReference("users/$uid/pairingCode")
                    val listener = object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            codigoEmparejamiento = snapshot.getValue(String::class.java)
                        }

                        override fun onCancelled(error: DatabaseError) {}
                    }
                    codeRef.addValueEventListener(listener)
                    onDispose { codeRef.removeEventListener(listener) }
                }

                seleccionado?.let { dispositivo ->
                    val locationsRef = remember(dispositivo.id) {
                        FirebaseDatabase.getInstance().getReference("users/$uid/locations/${dispositivo.id}")
                    }
                    DisposableEffectListener(locationsRef) { lat, lng ->
                        ubicacionActual = GeoPoint(lat, lng)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        MapaOsm(
                            modifier = Modifier.fillMaxSize(),
                            ubicacion = ubicacionActual,
                            historial = puntosHistorial
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                        ) {
                            Button(
                                onClick = { menuPrincipalAbierto = !menuPrincipalAbierto },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorMenuPrincipal)
                            ) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menú")
                                Text(" Opciones ")
                                Icon(
                                    if (menuPrincipalAbierto) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null
                                )
                            }

                            AnimatedVisibility(
                                visible = menuPrincipalAbierto,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { menuAbierto = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = ColorElegirTelefono)
                                        ) {
                                            Text(
                                                seleccionado?.let { "${estadoDispositivo(it.lastSeen)} ${it.nombre}" }
                                                    ?: "Elegir teléfono"
                                            )
                                        }
                                        DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                                            dispositivos.forEach { dispositivo ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            "${estadoDispositivo(dispositivo.lastSeen)} ${dispositivo.nombre} (${tiempoDesde(dispositivo.lastSeen)})"
                                                        )
                                                    },
                                                    onClick = {
                                                        seleccionado = dispositivo
                                                        ubicacionActual = null
                                                        puntosHistorial = emptyList()
                                                        timestampsHistorial = emptyList()
                                                        menuAbierto = false
                                                    }
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                seleccionado?.let { dispositivo ->
                                                    val requestsRef = FirebaseDatabase.getInstance()
                                                        .getReference("users/$uid/requests/${dispositivo.id}")
                                                    requestsRef.setValue(System.currentTimeMillis())
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = ColorSolicitarUbicacion)
                                        ) {
                                            Text("Solicitar ubicación")
                                        }

                                        Button(
                                            onClick = { mostrarDialogoCodigo = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = ColorCodigo)
                                        ) {
                                            Text("Código de emparejamiento")
                                        }

                                        Button(
                                            onClick = {
                                                auth.signOut()
                                                sesionActiva = false
                                                dispositivos = emptyList()
                                                seleccionado = null
                                                ubicacionActual = null
                                                puntosHistorial = emptyList()
                                                timestampsHistorial = emptyList()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = ColorCerrarSesion)
                                        ) {
                                            Text("Cerrar sesión")
                                        }
                                    }

                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { showDatePicker = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = ColorElegirFecha)
                                        ) {
                                            Text(
                                                fechaSeleccionadaMillis?.let { "Fecha: ${formatearFecha(it)}" }
                                                    ?: "Elegir fecha"
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                val dispositivo = seleccionado ?: return@Button
                                                cargandoHistorial = true
                                                val historyRef = FirebaseDatabase.getInstance()
                                                    .getReference("users/$uid/history/${dispositivo.id}")

                                                val fecha = fechaSeleccionadaMillis
                                                val query = if (fecha != null) {
                                                    val (inicio, fin) = limitesDelDia(fecha)
                                                    Log.d(
                                                        "HistorialQuery",
                                                        "Filtrando entre $inicio y $fin (fecha elegida millis=$fecha)"
                                                    )
                                                    historyRef.orderByChild("timestamp")
                                                        .startAt(inicio.toDouble())
                                                        .endAt(fin.toDouble())
                                                } else {
                                                    Log.d("HistorialQuery", "Sin filtro de fecha, trayendo todo")
                                                    historyRef
                                                }

                                                query.get().addOnSuccessListener { snapshot ->
                                                    Log.d(
                                                        "HistorialQuery",
                                                        "Snapshot recibido, hijos: ${snapshot.childrenCount}"
                                                    )
                                                    val entradas = snapshot.children.mapNotNull { child ->
                                                        val lat = child.child("lat").getValue(Double::class.java)
                                                        val lng = child.child("lng").getValue(Double::class.java)
                                                        val ts = child.child("timestamp").getValue(Long::class.java)
                                                        if (lat != null && lng != null) Pair(GeoPoint(lat, lng), ts) else null
                                                    }
                                                    puntosHistorial = entradas.map { it.first }
                                                    timestampsHistorial = entradas.map { it.second }
                                                    cargandoHistorial = false
                                                }.addOnFailureListener { error ->
                                                    Log.e("HistorialQuery", "Error al consultar historial", error)
                                                    cargandoHistorial = false
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = ColorVerHistorial)
                                        ) {
                                            Text(if (cargandoHistorial) "Cargando..." else "Ver historial")
                                        }

                                        if (fechaSeleccionadaMillis != null) {
                                            Button(
                                                onClick = {
                                                    fechaSeleccionadaMillis = null
                                                    puntosHistorial = emptyList()
                                                    timestampsHistorial = emptyList()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = ColorQuitarFecha)
                                            ) {
                                                Text("Quitar fecha")
                                            }
                                        }

                                        if (puntosHistorial.isNotEmpty()) {
                                            Button(
                                                onClick = {
                                                    puntosHistorial = emptyList()
                                                    timestampsHistorial = emptyList()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = ColorOcultar)
                                            ) {
                                                Text("Ocultar")
                                            }

                                            Button(
                                                onClick = { mostrarDetalle = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = ColorVerDetalle)
                                            ) {
                                                Text("Ver detalle")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showDatePicker) {
                    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = fechaSeleccionadaMillis)
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                fechaSeleccionadaMillis = datePickerState.selectedDateMillis
                                showDatePicker = false
                            }) {
                                Text("Aceptar")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) {
                                Text("Cancelar")
                            }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }

                if (mostrarDialogoCodigo) {
                    AlertDialog(
                        onDismissRequest = { mostrarDialogoCodigo = false },
                        title = { Text("Código de emparejamiento") },
                        text = {
                            Text(
                                codigoEmparejamiento?.let {
                                    "Introduce este código en la app Tracker para vincularla a tu cuenta:\n\n$it"
                                } ?: "Generando código..."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { mostrarDialogoCodigo = false }) {
                                Text("Cerrar")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun asegurarCodigoEmparejamiento(uid: String) {
        val ref = FirebaseDatabase.getInstance().getReference("users/$uid/pairingCode")
        ref.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                val codigo = generarCodigo()
                ref.setValue(codigo)

                val indiceRef = FirebaseDatabase.getInstance().getReference("pairingCodes/$codigo")
                indiceRef.setValue(uid)
            }
        }
    }

    private fun generarCodigo(): String {
        val caracteres = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { caracteres[Random.nextInt(caracteres.length)] }.joinToString("")
    }
}

private fun estadoDispositivo(lastSeen: Long?): String {
    if (lastSeen == null) return "⚪"
    val minutos = (System.currentTimeMillis() - lastSeen) / 60000
    return when {
        minutos < 20 -> "🟢"
        minutos < 60 -> "🟡"
        else -> "🔴"
    }
}

private fun tiempoDesde(lastSeen: Long?): String {
    if (lastSeen == null) return "sin datos"
    val minutos = (System.currentTimeMillis() - lastSeen) / 60000
    return when {
        minutos < 1 -> "hace un momento"
        minutos < 60 -> "hace $minutos min"
        else -> "hace ${minutos / 60}h ${minutos % 60}min"
    }
}

private fun limitesDelDia(millisUtc: Long): Pair<Long, Long> {
    val calendarUtc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    calendarUtc.timeInMillis = millisUtc

    val calendarLocal = Calendar.getInstance()
    calendarLocal.set(Calendar.YEAR, calendarUtc.get(Calendar.YEAR))
    calendarLocal.set(Calendar.MONTH, calendarUtc.get(Calendar.MONTH))
    calendarLocal.set(Calendar.DAY_OF_MONTH, calendarUtc.get(Calendar.DAY_OF_MONTH))
    calendarLocal.set(Calendar.HOUR_OF_DAY, 0)
    calendarLocal.set(Calendar.MINUTE, 0)
    calendarLocal.set(Calendar.SECOND, 0)
    calendarLocal.set(Calendar.MILLISECOND, 0)
    val inicio = calendarLocal.timeInMillis

    calendarLocal.set(Calendar.HOUR_OF_DAY, 23)
    calendarLocal.set(Calendar.MINUTE, 59)
    calendarLocal.set(Calendar.SECOND, 59)
    calendarLocal.set(Calendar.MILLISECOND, 999)
    val fin = calendarLocal.timeInMillis

    return Pair(inicio, fin)
}

private fun formatearFecha(millis: Long): String {
    val formatter = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES"))
    return formatter.format(Date(millis))
}

@Composable
fun MapaOsm(
    modifier: Modifier = Modifier,
    ubicacion: GeoPoint?,
    historial: List<GeoPoint> = emptyList()
) {
    var marcador by remember { mutableStateOf<Marker?>(null) }
    var lineaHistorial by remember { mutableStateOf<Polyline?>(null) }
    var marcadoresHistorial by remember { mutableStateOf<List<Marker>>(emptyList()) }

    val bitmapFlecha = remember {
        crearIconoFlecha(AndroidColor.parseColor(COLOR_FLECHA_HISTORIAL), TAMANO_FLECHA_HISTORIAL)
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)

                val puntoInicial = GeoPoint(40.4168, -3.7038)
                controller.setZoom(15.0)
                controller.setCenter(puntoInicial)
            }
        },
        update = { mapView ->
            if (ubicacion != null) {
                if (marcador == null) {
                    val nuevoMarcador = Marker(mapView)
                    nuevoMarcador.position = ubicacion
                    nuevoMarcador.title = "Ubicación actual"
                    mapView.overlays.add(nuevoMarcador)
                    marcador = nuevoMarcador
                } else {
                    marcador?.position = ubicacion
                }
                mapView.controller.animateTo(ubicacion)
            }

            lineaHistorial?.let { mapView.overlays.remove(it) }
            marcadoresHistorial.forEach { mapView.overlays.remove(it) }

            if (historial.isNotEmpty()) {
                val linea = Polyline(mapView)
                linea.setPoints(historial)
                linea.outlinePaint.color = AndroidColor.parseColor(COLOR_LINEA_HISTORIAL)
                linea.outlinePaint.strokeWidth = GROSOR_LINEA_HISTORIAL
                linea.outlinePaint.pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
                mapView.overlays.add(linea)
                lineaHistorial = linea

                val nuevosMarcadores = mutableListOf<Marker>()

                for (i in 1 until historial.size) {
                    if (i % INTERVALO_FLECHAS != 0 && i != historial.size - 1) continue

                    val anterior = historial[i - 1]
                    val actual = historial[i]
                    val rumbo = anterior.bearingTo(actual).toFloat()

                    val flecha = Marker(mapView)
                    flecha.position = actual
                    flecha.icon = BitmapDrawable(mapView.resources, bitmapFlecha)
                    flecha.rotation = rumbo
                    flecha.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    flecha.title = "Punto ${i + 1}"
                    mapView.overlays.add(flecha)
                    nuevosMarcadores.add(flecha)
                }

                val inicio = Marker(mapView)
                inicio.position = historial.first()
                inicio.title = "Inicio del recorrido"
                mapView.overlays.add(inicio)
                nuevosMarcadores.add(inicio)

                marcadoresHistorial = nuevosMarcadores

                if (ubicacion == null) {
                    mapView.controller.animateTo(historial.last())
                }
            } else {
                lineaHistorial = null
                marcadoresHistorial = emptyList()
            }

            mapView.invalidate()
        }
    )
}

private fun crearIconoFlecha(color: Int, size: Int = 36): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    val path = Path().apply {
        moveTo(size / 2f, 0f)
        lineTo(size.toFloat(), size.toFloat())
        lineTo(size / 2f, size * 0.7f)
        lineTo(0f, size.toFloat())
        close()
    }
    canvas.drawPath(path, paint)
    return bitmap
}

@Composable
fun DisposableEffectListener(
    ref: DatabaseReference,
    onUbicacion: (Double, Double) -> Unit
) {
    DisposableEffect(ref) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("lat").getValue(Double::class.java)
                val lng = snapshot.child("lng").getValue(Double::class.java)
                if (lat != null && lng != null) {
                    onUbicacion(lat, lng)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }
}