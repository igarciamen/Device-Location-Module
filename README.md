# Proyecto Localizador

Sistema de localización entre dos o más teléfonos Android propios, formado por dos aplicaciones independientes (**Tracker** y **Viewer**) que se comunican a través de Firebase Realtime Database, con autenticación de usuario y emparejamiento por código.

---

## Demo


https://github.com/user-attachments/assets/e352dd5b-93cb-44cc-9ea1-3f6891fc7cef



## 📱 Componentes del proyecto

### Tracker app (`com.igarciamen.trackerapp`)
App instalada en el teléfono que se quiere localizar. **No tiene interfaz gráfica significativa**: solo una pantalla mínima para vincular el dispositivo la primera vez. Una vez vinculada, corre en segundo plano como un Foreground Service, escuchando peticiones de ubicación y respondiendo con las coordenadas GPS reales del teléfono.

### Viewer app (`com.igarciamen.viewerapp`)
App con interfaz gráfica completa, con mapa (OpenStreetMap vía osmdroid), login de usuario, selector de dispositivos vinculados, solicitud de ubicación puntual, historial de recorrido con filtro por fecha, y estado de conexión de cada Tracker.

---

## 🏗️ Arquitectura

```
Tracker app  ──(GPS + Firebase)──▶  Realtime Database  ◀──(lectura)──  Viewer app
                                          │
                                    Firebase Auth
                                    (email/contraseña
                                     para Viewer;
                                     código de
                                     emparejamiento
                                     para Tracker)
```

### Estructura de datos en Firebase Realtime Database

```
pairingCodes/
  {codigo}: "{uid del propietario}"

users/
  {uid}/
    pairingCode: "AB12CD"
    devices/
      {deviceId}/
        name: "Nombre del teléfono"
        lastSeen: <timestamp>
    locations/
      {deviceId}/
        lat, lng, timestamp
    requests/
      {deviceId}: <timestamp>          # trigger para pedir ubicación puntual
    history/
      {deviceId}/
        {clave autogenerada}/
          lat, lng, timestamp
    lastKnownLocation/
      {deviceId}/
        lat, lng, timestamp            # referencia para el umbral de movimiento
```

---

## 🔑 Flujo de emparejamiento

1. El usuario se registra en **Viewer** (email + contraseña, Firebase Authentication).
2. Al crear la cuenta, se genera automáticamente un **código de emparejamiento** de 6 caracteres, mostrado en la app y guardado en `users/{uid}/pairingCode` y en el índice `pairingCodes/{codigo}`.
3. Al instalar **Tracker** en un teléfono, se le pide un nombre y ese mismo código.
4. Tracker consulta `pairingCodes/{codigo}` para obtener el `uid` del propietario, genera un `deviceId` único (UUID), y guarda ambos en `SharedPreferences` locales (`DevicePrefs`).
5. A partir de ahí, todas las escrituras de ese Tracker van a `users/{uid}/.../{deviceId}`.

---

## ⚙️ Funcionamiento de Tracker (segundo plano)

### Piezas del sistema de fiabilidad

| Componente | Función |
|---|---|
| `LocalizadorService` | Foreground Service principal. Escucha `requests/{deviceId}` y responde con la ubicación GPS actual. |
| `onTaskRemoved()` | Si el usuario cierra la app, intenta reiniciar el Service en 1 segundo (alarma exacta `setExactAndAllowWhileIdle`). |
| `WatchdogScheduler` + `WatchdogAlarmReceiver` | Alarma recurrente cada 15 minutos que comprueba y relanza el Service si está muerto. Resistente a Doze. |
| `HistoryScheduler` + `HistoryAlarmReceiver` | Alarma cada 15 minutos que dispara `LocationHistoryWorker` para registrar el recorrido. |
| `LocationHistoryWorker` | Compara la ubicación actual con la última guardada; solo escribe en `history` si el desplazamiento supera 50 metros (evita ruido del GPS en reposo). |
| `BootReceiver` | Arranca el Service al reiniciar el teléfono (`BOOT_COMPLETED`), con reintento y tolerancia a fallos. |
| Autocuración en `onCreate()` | Cada vez que el Service arranca (por cualquier vía), reprograma sus propias alarmas de watchdog — no depende de que se abra `MainActivity`. |

### Permisos necesarios (Tracker)
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`
- `POST_NOTIFICATIONS`
- `RECEIVE_BOOT_COMPLETED`
- `SCHEDULE_EXACT_ALARM`
- `INTERNET`

---

## 🗺️ Funcionalidad de Viewer

- **Login / registro** con email y contraseña (Firebase Auth).
- **Selector de dispositivo** con indicador de estado en tiempo real:
  - 🟢 activo (latido hace menos de 20 min)
  - 🟡 sin confirmar recientemente (20–60 min)
  - 🔴 inactivo (más de 60 min sin latido)
- **Solicitar ubicación**: pide una posición puntual al Tracker seleccionado.
- **Historial de recorrido**: dibuja el trazado guardado como línea punteada con flechas de dirección sobre el mapa (OpenStreetMap).
- **Filtro por fecha**: permite consultar solo el recorrido de un día concreto.
- **Vista de detalle**: pantalla aparte con lista de paradas, hora y dirección aproximada (geocodificación inversa vía Nominatim/OpenStreetMap).
- **Menú desplegable**: todos los controles agrupados bajo un botón "☰ Opciones" para no tapar el mapa.

---

## 🧰 Stack técnico

- **Lenguaje**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Mapas**: osmdroid (OpenStreetMap) — sin coste, sin API Key
- **Backend**: Firebase Realtime Database + Firebase Authentication
- **Concurrencia en segundo plano**: `AlarmManager` (alarmas exactas) + `WorkManager` (para el trabajo asíncrono de `CoroutineWorker`)
- **Geocodificación inversa**: API pública de Nominatim (OpenStreetMap)

---

## ⚠️ Limitaciones conocidas

### Arranque tras reinicio del teléfono
En dispositivos con **Android 16** (y potencialmente otras versiones recientes), el sistema restringe que un foreground service de tipo `location` arranque con acceso a ubicación desde un contexto de background puro (como `BootReceiver` justo tras el arranque). Esto puede provocar que, tras reiniciar el teléfono, el Service no consiga arrancar en el primer intento.

**Mitigación aplicada:** las alarmas del watchdog se programan *antes* de intentar publicar la notificación, por lo que aunque el primer intento falle, el sistema se recupera solo en un máximo de **15 minutos**, sin intervención manual. En Android 10 (versiones antiguas) el comportamiento es equivalente por otros motivos del sistema (el propio `BootReceiver` no siempre dispara la app en el primer intento).

Esto es una limitación del sistema operativo, no del código de la aplicación — no existe forma legítima (sin privilegios de root) de garantizar un arranque instantáneo al 100 % en todos los dispositivos y versiones de Android.

### "Cerrar todas las apps" en MIUI/HyperOS
Usar el botón de limpieza general de aplicaciones recientes puede cancelar las alarmas programadas del sistema (`AlarmManager`), más allá del comportamiento estándar de Android. Cerrar la app individualmente (deslizando su propia tarjeta) no tiene este problema. En cualquier caso, un reinicio del teléfono o abrir la app una vez restaura el sistema por completo.

### Índices de Firebase
Las consultas con filtro por fecha (`orderByChild("timestamp")`) requieren que el campo esté declarado como índice en las reglas de seguridad de Realtime Database (`.indexOn`). Sin este índice, Firebase rechaza la consulta con un error explícito.

---

## 🔐 Seguridad

- Firebase Authentication protege el acceso a los datos de cada usuario (`users/{uid}/...`).
- Las reglas de Realtime Database deben restringir lectura/escritura únicamente al propietario autenticado de cada `uid`.
- El índice de códigos de emparejamiento (`pairingCodes/`) evita que Tracker tenga que descargar la lista completa de usuarios para vincularse.
- **Pendiente de cierre**: revisión final de las reglas de seguridad para producción (actualmente en modo de desarrollo/prueba con fecha de expiración).

---

## 📂 Estructura de archivos relevante

### Tracker app
```
MainActivity.kt          — pantalla de vinculación (nombre + código)
LocalizadorService.kt     — Foreground Service principal
DevicePrefs.kt            — almacenamiento local (deviceId, ownerUid, nombre)
BootReceiver.kt           — arranque tras reinicio
WatchdogScheduler.kt / WatchdogAlarmReceiver.kt   — vigilancia del Service
HistoryScheduler.kt / HistoryAlarmReceiver.kt     — vigilancia del historial
LocationHistoryWorker.kt  — lógica de registro de recorrido con umbral
```

### Viewer app
```
MainActivity.kt           — pantalla principal (mapa, menú, lógica de sesión)
LoginScreen.kt             — pantalla de login/registro
DetalleHistorialScreen.kt  — pantalla de detalle del recorrido con direcciones
```

---

## 🚧 Estado del proyecto

- ✅ Localización puntual bajo demanda
- ✅ Historial de recorrido con umbral de movimiento
- ✅ Soporte multi-dispositivo con login y emparejamiento por código
- ✅ Recuperación automática frente a Doze, cierre de apps y reinicio (con las limitaciones documentadas arriba)
- ✅ Visualización de historial en mapa con filtro por fecha y detalle de direcciones
- ⏳ Pendiente: revisión final de reglas de seguridad de Firebase para uso en producción
