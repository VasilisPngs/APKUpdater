package com.example.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.api.ApkMirrorService
import com.example.data.model.AppExistsApk
import com.example.data.model.AppExistsRequest
import com.example.data.model.AppExistsResponseData
import com.example.data.model.AppUpdateInfo
import com.example.data.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.security.MessageDigest

sealed interface ScanStatus {
    data object Idle : ScanStatus
    data class Scanning(val processed: Int, val total: Int, val currentBatch: String) : ScanStatus
    data class Success(val updates: List<AppUpdateInfo>, val allApps: List<InstalledApp>) : ScanStatus
    data class Error(val message: String, val partialUpdates: List<AppUpdateInfo>) : ScanStatus
}

class AppUpdateRepository(
    context: Context,
    private val service: ApkMirrorService = ApkMirrorService.create()
) {
    private val packageManager = context.packageManager
    private val deviceAbis = Build.SUPPORTED_ABIS.map(String::lowercase).toSet()
    private val deviceArch = when {
        deviceAbis.any { it == "x86" || it == "x86_64" } -> "x86"
        deviceAbis.any { it == "armeabi-v7a" } -> "arm"
        deviceAbis.any { it == "arm64-v8a" } -> "arm"
        else -> "arm"
    }

    suspend fun getInstalledApps(includeSystem: Boolean = true): List<InstalledApp> = withContext(Dispatchers.IO) {
        val packages = packageManager.getInstalledPackages(
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        )

        packages.mapNotNull { pkg ->
            runCatching {
                val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                if (!includeSystem && isSystem) return@mapNotNull null

                val signatureSha1 = pkg.signingInfo?.apkContentsSigners
                    ?.firstOrNull()
                    ?.let { signer ->
                        MessageDigest.getInstance("SHA-1")
                            .digest(signer.toByteArray())
                            .joinToString("") { byte -> "%02x".format(byte) }
                    }
                    .orEmpty()

                InstalledApp(
                    packageName = pkg.packageName,
                    appName = appInfo.loadLabel(packageManager).toString().trim().ifEmpty { pkg.packageName },
                    versionName = pkg.versionName ?: "Unknown",
                    versionCode = pkg.longVersionCode,
                    signatureSha1 = signatureSha1,
                    isSystemApp = isSystem,
                    firstInstallTime = pkg.firstInstallTime,
                    lastUpdateTime = pkg.lastUpdateTime
                )
            }.getOrNull()
        }.sortedWith(compareBy({ it.isSystemApp }, { it.appName.lowercase() }))
    }

    fun scanForUpdates(
        appsToCheck: List<InstalledApp>,
        onlyStable: Boolean = true
    ): Flow<ScanStatus> = flow {
        if (appsToCheck.isEmpty()) {
            emit(ScanStatus.Success(emptyList(), emptyList()))
            return@flow
        }

        val exclude = if (onlyStable) listOf("alpha", "beta") else emptyList()
        val batches = appsToCheck.chunked(100)
        val updates = mutableListOf<AppUpdateInfo>()
        var processed = 0
        var failedBatches = 0

        emit(ScanStatus.Scanning(0, appsToCheck.size, "Preparing scan…"))

        for (batch in batches) {
            emit(ScanStatus.Scanning(processed, appsToCheck.size, batch.first().appName))
            try {
                val response = service.appExists(AppExistsRequest(batch.map(InstalledApp::packageName), exclude))
                updates += parseUpdates(response.data, batch, onlyStable)
            } catch (_: Exception) {
                failedBatches++
            }
            processed += batch.size
            emit(ScanStatus.Scanning(processed, appsToCheck.size, batch.last().appName))
        }

        when {
            failedBatches == batches.size -> emit(ScanStatus.Error("Unable to reach APKMirror.", updates))
            failedBatches > 0 -> emit(ScanStatus.Error("Some applications could not be checked.", updates))
            else -> emit(ScanStatus.Success(updates, appsToCheck))
        }
    }.flowOn(Dispatchers.IO)

    private fun parseUpdates(
        apiDataList: List<AppExistsResponseData>,
        installedBatch: List<InstalledApp>,
        onlyStable: Boolean
    ): List<AppUpdateInfo> {
        val appMap = installedBatch.associateBy(InstalledApp::packageName)

        return apiDataList.asSequence()
            .filter { it.exists == true }
            .mapNotNull { data ->
                val installed = appMap[data.pname] ?: return@mapNotNull null
                val release = data.release ?: return@mapNotNull null

                if (onlyStable && isNonStableVersion(
                        release.version.orEmpty(),
                        release.link.orEmpty(),
                        release.whatsNew.orEmpty()
                    )
                ) {
                    return@mapNotNull null
                }

                val bestApk = data.apks.asSequence()
                    .filter { apk -> filterSignature(apk, installed.signatureSha1) }
                    .filter(::filterArch)
                    .filter(::filterMinApi)
                    .filter { apk ->
                        !onlyStable || !isNonStableVersion(apk.description.orEmpty(), apk.link)
                    }
                    .filter { apk -> apk.versionCode > installed.versionCode }
                    .maxByOrNull(AppExistsApk::versionCode)
                    ?: return@mapNotNull null

                val link = bestApk.link
                val url = if (link.startsWith("http://") || link.startsWith("https://")) {
                    link
                } else {
                    "https://www.apkmirror.com$link"
                }

                AppUpdateInfo(
                    packageName = installed.packageName,
                    appName = installed.appName,
                    currentVersionName = installed.versionName,
                    currentVersionCode = installed.versionCode,
                    newVersionName = release.version.orEmpty(),
                    newVersionCode = bestApk.versionCode,
                    publishDate = bestApk.publishDate ?: release.publishDate,
                    whatsNew = release.whatsNew,
                    apkMirrorUrl = url,
                    architectures = bestApk.arches,
                    isSystemApp = installed.isSystemApp
                )
            }
            .toList()
    }

    private fun filterSignature(apk: AppExistsApk, installedSignatureSha1: String): Boolean {
        val signatures = apk.signaturesSha1.orEmpty()
        return signatures.isEmpty() || signatures.any {
            it.equals(installedSignatureSha1, ignoreCase = true)
        }
    }

    private fun filterArch(apk: AppExistsApk): Boolean {
        if (apk.arches.isEmpty()) return true
        val arches = apk.arches.map(String::lowercase)
        if (arches.any { it == "universal" || it == "noarch" }) return true
        return arches.any { it in deviceAbis || it.contains(deviceArch) }
    }

    private fun filterMinApi(apk: AppExistsApk): Boolean = apk.minapi
        ?.toIntOrNull()
        ?.let { it <= Build.VERSION.SDK_INT }
        ?: true

    private fun isNonStableVersion(vararg texts: String): Boolean {
        val pattern = Regex("(^|[^a-z])(alpha|beta|pre[- ]?release|preview|rc|canary|dev|nightly|snapshot|experimental)([^a-z]|$)")
        return texts.any { pattern.containsMatchIn(it.lowercase()) }
    }
}
