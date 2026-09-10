package com.android.apkupdater.data.model

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val signatureSha1: String,
    val isSystemApp: Boolean,
    val firstInstallTime: Long = 0L,
    val lastUpdateTime: Long = 0L
)

data class AppUpdateInfo(
    val packageName: String,
    val appName: String,
    val currentVersionName: String,
    val currentVersionCode: Long,
    val newVersionName: String,
    val newVersionCode: Long,
    val publishDate: String?,
    val whatsNew: String?,
    val apkMirrorUrl: String,
    val architectures: List<String> = emptyList(),
    val isSystemApp: Boolean = false
)

enum class AppFilter {
    UPDATES_ONLY,
    USER_APPS,
    ALL_APPS,
    SYSTEM_APPS
}
