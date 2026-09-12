package com.android.gupdater.data.play

import com.aurora.gplayapi.helpers.AppDetailsHelper

data class PlayApp(val versionCode: Long, val versionName: String)

class PlayCatalog(private val authProvider: PlayAuthProvider) {

    fun warmUp() {
        authProvider.session()
    }

    fun details(packageNames: List<String>): Map<String, PlayApp> {
        if (packageNames.isEmpty()) return emptyMap()

        return AppDetailsHelper(authProvider.session())
            .getAppByPackageName(packageNames)
            .associate { it.packageName to PlayApp(it.versionCode, it.versionName) }
    }
}
