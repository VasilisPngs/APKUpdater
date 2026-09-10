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

    suspend fun getInstalledApps(includeSystem: Boolean = true): List<InstalledApp> = withContext(Dispatchers.IO) {
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(0)
        }

        packages.mapNotNull { pkg ->
            runCatching {
                val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                if (!includeSystem && isSystem) return@mapNotNull null
                InstalledApp(
                    packageName = pkg.packageName,
                    appName = appInfo.loadLabel(packageManager).toString().trim().ifEmpty { pkg.packageName },
                    versionName = pkg.versionName ?: "Unknown",
                    versionCode = pkg.longVersionCode,
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

        val exclude = if (onlyStable) listOf("alpha", "beta", "pre-release", "dev") else emptyList()
        val batches = appsToCheck.chunked(30)
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

        if (failedBatches == batches.size) {
            emit(ScanStatus.Error("Unable to reach APKMirror.", updates))
        } else if (failedBatches > 0) {
            emit(ScanStatus.Error("Some applications could not be checked.", updates))
        } else {
            emit(ScanStatus.Success(updates, appsToCheck))
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
                if (onlyStable && isNonStableVersion(data.release?.version.orEmpty(), data.release?.link.orEmpty())) return@mapNotNull null

                val bestApk = data.apks.asSequence()
                    .filter(::filterArch)
                    .filter(::filterMinApi)
                    .filter { !onlyStable || !isNonStableVersion(it.description.orEmpty(), it.link) }
                    .filter { it.versionCode > installed.versionCode }
                    .maxByOrNull(AppExistsApk::versionCode)
                    ?: return@mapNotNull null

                val link = bestApk.link.ifEmpty { data.release?.link ?: data.app?.link.orEmpty() }
                val url = if (link.startsWith("http://") || link.startsWith("https://")) link else "https://www.apkmirror.com$link"

                AppUpdateInfo(
                    packageName = data.pname,
                    appName = installed.appName,
                    currentVersionName = installed.versionName,
                    currentVersionCode = installed.versionCode,
                    newVersionName = data.release?.version ?: "Update Available",
                    newVersionCode = bestApk.versionCode,
                    publishDate = bestApk.publishDate ?: data.release?.publishDate,
                    whatsNew = data.release?.whatsNew,
                    apkMirrorUrl = url,
                    architectures = bestApk.arches,
                    isSystemApp = installed.isSystemApp
                )
            }
            .toList()
    }

    private fun isNonStableVersion(vararg texts: String): Boolean {
        val pattern = Regex("(^|[^a-z])(alpha|beta|pre[- ]?release|preview|rc|canary|dev|nightly|snapshot|experimental)([^a-z]|$)")
        return texts.any { pattern.containsMatchIn(it.lowercase()) }
    }

    private fun filterArch(apk: AppExistsApk): Boolean {
        if (apk.arches.isEmpty()) return true
        val arches = apk.arches.map(String::lowercase)
        if (arches.any { it == "universal" || it == "noarch" }) return true
        return arches.any { it in deviceAbis }
    }

    private fun filterMinApi(apk: AppExistsApk): Boolean = apk.minapi?.toIntOrNull()?.let { it <= Build.VERSION.SDK_INT } ?: true
}
