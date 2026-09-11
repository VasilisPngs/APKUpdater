package com.android.apkupdater.data.model

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val signatureSha1: String,
    val isSystemApp: Boolean
)

data class AppUpdateInfo(
    val packageName: String,
    val appName: String,
    val currentVersionName: String,
    val newVersionName: String,
    val newVersionCode: Long,
    val whatsNew: String?,
    val apkMirrorUrl: String
)
