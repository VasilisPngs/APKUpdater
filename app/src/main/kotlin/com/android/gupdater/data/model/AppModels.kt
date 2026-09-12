package com.android.gupdater.data.model

data class InstalledApp(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val signatureSha1s: Set<String>,
    val isEnabled: Boolean
)

data class AppUpdateInfo(
    val packageName: String,
    val appName: String,
    val newVersionName: String,
    val newVersionCode: Long,
    val apkMirrorUrl: String
)
