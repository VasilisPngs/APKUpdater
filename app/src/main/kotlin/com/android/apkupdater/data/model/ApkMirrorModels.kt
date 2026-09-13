package com.android.apkupdater.data.model

data class ApkMirrorApp(
    val packageName: String,
    val versionName: String,
    val developerName: String,
    val publishDate: String,
    val apks: List<ApkMirrorApk>
)

data class ApkMirrorApk(
    val versionCode: Long,
    val versionName: String,
    val link: String,
    val publishDate: String,
    val architectures: List<String>,
    val densities: List<String>,
    val minimumApi: Int,
    val capabilities: List<String>,
    val signatureSha1s: List<String>,
    val signatureSha256s: List<String>
)
