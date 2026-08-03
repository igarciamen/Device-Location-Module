package com.igarciamen.viewerapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.material3.ExperimentalMaterial3Api
data class ParadaDetallada(val punto: GeoPoint, val timestamp: Long?, val direccion: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleHistorialScreen(
    puntos: List<GeoPoint>,
    timestamps: List<Long?>,
    onVolver: () -> Unit
) {
    var paradas by remember { mutableStateOf<List<ParadaDetallada>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var indiceActual by remember { mutableStateOf(0) }

    LaunchedEffect(puntos) {
        cargando = true
        val resultado = mutableListOf<ParadaDetallada>()
        puntos.forEachIndexed { i, punto ->
            indiceActual = i + 1
            val direccion = obtenerDireccion(punto.latitude, punto.longitude)
            resultado.add(ParadaDetallada(punto, timestamps.getOrNull(i), direccion))
            if (i < puntos.size - 1) delay(1100) // Nominatim: máx. 1 petición/segundo
        }
        paradas = resultado
        cargando = false
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Detalle del recorrido") })
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            Button(onClick = onVolver, modifier = Modifier.padding(bottom = 12.dp)) {
                Text("← Volver al mapa")
            }

            if (cargando) {
                Text("Consultando direcciones... ($indiceActual/${puntos.size})")
                CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(paradas.size) { i ->
                        val parada = paradas[i]
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                "Parada ${i + 1} — ${formatearHora(parada.timestamp)}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(parada.direccion, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Lat: ${parada.punto.latitude}, Lng: ${parada.punto.longitude}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Divider()
                    }
                }
            }
        }
    }
}

private suspend fun obtenerDireccion(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lng&addressdetails=1")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "com.igarciamen.viewerapp")
        conn.connectTimeout = 8000
        val json = JSONObject(conn.inputStream.bufferedReader().readText())
        val addr = json.optJSONObject("address")
        val calle = addr?.optString("road").orEmpty()
        val numero = addr?.optString("house_number").orEmpty()
        val provincia = addr?.optString("province").orEmpty().ifBlank { addr?.optString("state").orEmpty() }
        listOf(calle, numero).filter { it.isNotBlank() }.joinToString(" ")
            .plus(if (provincia.isNotBlank()) " — $provincia" else "")
            .ifBlank { "Dirección no encontrada" }
    } catch (e: Exception) {
        "Error de red"
    }
}

private fun formatearHora(timestamp: Long?): String {
    if (timestamp == null) return "hora desconocida"
    val formatter = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("es", "ES"))
    return formatter.format(java.util.Date(timestamp))
}