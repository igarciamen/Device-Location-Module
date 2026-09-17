# Locator Project

A location tracking system between two or more Android phones, made up of two independent apps (**Tracker** and **Viewer**) that communicate through Firebase Realtime Database, with user authentication and pairing via code.

---
# Demo

https://github.com/user-attachments/assets/d4445d4c-2a65-46d8-b364-b404634f8628


##  Project Components

### Tracker app 
App installed on the phone you want to track. **Has no significant graphical interface**: just a minimal screen to link the device the first time. Once linked, it runs in the background as a Foreground Service, listening for location requests and responding with the phone's real GPS coordinates.

### Viewer app 
App with a full graphical interface, including a map (OpenStreetMap via osmdroid), user login, linked-device selector, on-demand location requests, route history with date filtering, and connection status for each Tracker.

---

##  Architecture

```
Tracker app  ──(GPS + Firebase)──▶  Realtime Database  ◀──(read)──  Viewer app
                                          │
                                    Firebase Auth
                                    (email/password
                                     for Viewer;
                                     pairing code
                                     for Tracker)
```

### Data structure in Firebase Realtime Database

```
pairingCodes/
  {code}: "{owner's uid}"

users/
  {uid}/
    pairingCode: "AB12CD"
    devices/
      {deviceId}/
        name: "Phone name"
        lastSeen: <timestamp>
    locations/
      {deviceId}/
        lat, lng, timestamp
    requests/
      {deviceId}: <timestamp>          # trigger to request a one-off location
    history/
      {deviceId}/
        {auto-generated key}/
          lat, lng, timestamp
    lastKnownLocation/
      {deviceId}/
        lat, lng, timestamp            # reference for the movement threshold
```

---

## Pairing flow

1. The user signs up in **Viewer** (email + password, Firebase Authentication).
2. When the account is created, a 6-character **pairing code** is generated automatically, shown in the app and stored under `users/{uid}/pairingCode` and in the `pairingCodes/{code}` index.
3. When installing **Tracker** on a phone, it asks for a name and that same code.
4. Tracker looks up `pairingCodes/{code}` to get the owner's `uid`, generates a unique `deviceId` (UUID), and stores both locally in `SharedPreferences` (`DevicePrefs`).
5. From then on, all writes from that Tracker go to `users/{uid}/.../{deviceId}`.

---

## ⚙️ How Tracker works (background)

### Reliability system components

| Component | Function |
|---|---|
| `LocalizadorService` | Main Foreground Service. Listens to `requests/{deviceId}` and responds with the current GPS location. |
| `onTaskRemoved()` | If the user closes the app, it tries to restart the Service after 1 second (exact alarm `setExactAndAllowWhileIdle`). |
| `WatchdogScheduler` + `WatchdogAlarmReceiver` | Recurring alarm every 15 minutes that checks and relaunches the Service if it's dead. Resistant to Doze. |
| `HistoryScheduler` + `HistoryAlarmReceiver` | Alarm every 15 minutes that triggers `LocationHistoryWorker` to log the route. |
| `LocationHistoryWorker` | Compares the current location with the last saved one; only writes to `history` if the movement exceeds 50 meters (avoids GPS noise while stationary). |
| `BootReceiver` | Starts the Service on phone restart (`BOOT_COMPLETED`), with retry and fault tolerance. |
| Self-healing in `onCreate()` | Every time the Service starts (by any path), it reschedules its own watchdog alarms — it doesn't depend on `MainActivity` being opened. |

##  Viewer functionality

- **Login / sign-up** with email and password (Firebase Auth).
- **Device selector** with real-time status indicator:
  - 🟢 active (less than 20 min ago)
  - 🟡 not confirmed recently (20–60 min)
  - 🔴 inactive (more than 60 min)
- **Request location**: asks the selected Tracker for a one-off position.
- **Route history**: draws the saved path as a dotted line with direction arrows on the map (OpenStreetMap).
- **Date filter**: lets you check the route for just one specific day.
- **Detail view**: separate screen with a list of stops, time, and approximate address (reverse geocoding via Nominatim/OpenStreetMap).
- **Dropdown menu**: all controls grouped under a "☰ Options" button so they don't cover the map.


##  Tech stack

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Maps**: osmdroid (OpenStreetMap) — free, no API key needed
- **Backend**: Firebase Realtime Database + Firebase Authentication
- **Background concurrency**: `AlarmManager` (exact alarms) + `WorkManager` (for `CoroutineWorker` async work)
- **Reverse geocoding**: Nominatim's public API (OpenStreetMap)

---

##  Known limitations

### Startup after phone restart
On devices running **Android 16** (and potentially other recent versions), the system restricts a `location`-type foreground service from starting with location access from a pure background context (such as `BootReceiver` right after boot). This can cause the Service to fail to start on the first attempt after a phone restart.

**Mitigation applied:** the watchdog alarms are scheduled *before* attempting to post the notification, so even if the first attempt fails, the system recovers on its own within a maximum of **15 minutes**, with no manual action needed. On Android 10 (older versions) the behavior is equivalent for other system-related reasons (`BootReceiver` itself doesn't always fire the app on the first attempt).

This is a limitation of the operating system, not of the application code — there's no legitimate way (without root privileges) to guarantee a 100% instant startup on every device and Android version.

### "Close all apps" on MIUI/HyperOS
Using the general recent-apps cleanup button can cancel the system's scheduled alarms (`AlarmManager`), beyond standard Android behavior. Closing the app individually (swiping its own card) doesn't have this issue. In any case, restarting the phone or opening the app once fully restores the system.

### Firebase indexes
Queries with a date filter (`orderByChild("timestamp")`) require the field to be declared as an index in the Realtime Database security rules (`.indexOn`). Without this index, Firebase rejects the query with an explicit error.

---

## Security

- Firebase Authentication protects access to each user's data (`users/{uid}/...`).
- Realtime Database rules must restrict read/write access to only the authenticated owner of each `uid`.
- The pairing code index (`pairingCodes/`) keeps Tracker from having to download the full list of users to link up.
- **Still to close out**: final review of the security rules for production (currently in development/test mode with an expiration date).

---

## Relevant file structure

### Tracker app
```
MainActivity.kt          — pairing screen (name + code)
LocalizadorService.kt     — main Foreground Service
DevicePrefs.kt            — local storage (deviceId, ownerUid, name)
BootReceiver.kt           — startup after restart
WatchdogScheduler.kt / WatchdogAlarmReceiver.kt   — Service monitoring
HistoryScheduler.kt / HistoryAlarmReceiver.kt     — history monitoring
LocationHistoryWorker.kt  — route logging logic with threshold
```

### Viewer app
```
MainActivity.kt           — main screen (map, menu, session logic)
LoginScreen.kt             — login/sign-up screen
DetalleHistorialScreen.kt  — route detail screen with addresses
```


