package com.android.gupdater.data.preferences

import android.content.Context

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    var includeDisabledApps: Boolean
        get() = preferences.getBoolean(KEY_INCLUDE_DISABLED_APPS, false)
        set(value) = preferences.edit().putBoolean(KEY_INCLUDE_DISABLED_APPS, value).apply()

    companion object {
        private const val KEY_INCLUDE_DISABLED_APPS = "include_disabled_apps"
    }
}
