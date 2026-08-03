package com.igarciamen.trackerapp

import android.content.Context

object DevicePrefs {
    private const val PREFS_NAME = "tracker_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_OWNER_UID = "owner_uid"

    fun getDeviceId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_ID, null)
    }

    fun getDeviceName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_NAME, null)
    }

    fun getOwnerUid(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_OWNER_UID, null)
    }

    fun saveDevice(context: Context, id: String, name: String, ownerUid: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DEVICE_ID, id)
            .putString(KEY_DEVICE_NAME, name)
            .putString(KEY_OWNER_UID, ownerUid)
            .apply()
    }
}