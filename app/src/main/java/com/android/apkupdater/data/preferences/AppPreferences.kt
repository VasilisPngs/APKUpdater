package com.android.apkupdater.data.preferences

import android.content.Context

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    var includeSystemApps: Boolean
        get() = preferences.getBoolean(KEY_INCLUDE_SYSTEM_APPS, false)
        set(value) = preferences.edit().putBoolean(KEY_INCLUDE_SYSTEM_APPS, value).apply()

    companion object {
        private const val KEY_INCLUDE_SYSTEM_APPS = "include_system_apps"
    }
}
