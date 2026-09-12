package com.android.gupdater.data.play

import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.helpers.AppDetailsHelper

class PlayCatalog(private val authProvider: PlayAuthProvider) {

    fun warmUp() {
        authProvider.session()
    }

    fun availablePackages(packageNames: List<String>): Set<String> {
        if (packageNames.isEmpty()) return emptySet()

        return AppDetailsHelper(authProvider.session())
            .getAppByPackageName(packageNames)
            .mapTo(mutableSetOf(), App::packageName)
    }
}
