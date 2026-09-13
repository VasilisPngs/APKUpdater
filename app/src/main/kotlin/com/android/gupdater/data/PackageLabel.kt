package com.android.gupdater.data

import android.content.pm.PackageManager

internal fun PackageManager.appLabel(packageName: String): String = runCatching {
    getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        .loadLabel(this)
        .toString()
        .trim()
}.getOrNull()?.ifEmpty { null } ?: packageName
