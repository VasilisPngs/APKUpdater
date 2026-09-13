package com.android.apkupdater.data.play

import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.helpers.AppDetailsHelper

class PlayCatalog(private val authProvider: PlayAuthProvider) {

    fun deliverablePackages(packageNames: List<String>): Set<String> {
        if (packageNames.isEmpty()) return emptySet()

        val details = AppDetailsHelper(authProvider.session())
        return packageNames.chunked(BATCH_SIZE)
            .flatMapTo(mutableSetOf()) { batch ->
                details.getAppByPackageName(batch).map(App::packageName)
            }
    }

    private companion object {
        const val BATCH_SIZE = 100
    }
}
