package com.android.gupdater.data.play

import com.aurora.gplayapi.helpers.AppDetailsHelper

data class PlayApp(val versionCode: Long, val versionName: String, val developerName: String)

class PlayCatalog(private val authProvider: PlayAuthProvider) {

    fun details(packageNames: List<String>): Map<String, PlayApp> {
        if (packageNames.isEmpty()) return emptyMap()

        return AppDetailsHelper(authProvider.session())
            .getAppByPackageName(packageNames)
            .associate {
                it.packageName to PlayApp(it.versionCode, it.versionName, it.developerName)
            }
    }
}
