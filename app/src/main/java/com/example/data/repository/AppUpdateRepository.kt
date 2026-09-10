package com.example.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
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
    object Idle : ScanStatus
    data class Scanning(val processed: Int, val total: Int, val currentBatch: String) : ScanStatus
    data class Success(val updates: List<AppUpdateInfo>, val allApps: List<InstalledApp>) : ScanStatus
    data class Error(val message: String, val partialUpdates: List<AppUpdateInfo>) : ScanStatus
}

class AppUpdateRepository(
    private val context: Context,
    private val service: ApkMirrorService = ApkMirrorService.create()
) {
    private val packageManager: PackageManager = context.packageManager
    private val deviceAbis: List<String> = Build.SUPPORTED_ABIS.toList()

    suspend fun getInstalledApps(includeSystem: Boolean = true): List<InstalledApp> = withContext(Dispatchers.IO) {
        val packages: List<PackageInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
            } else {
                packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
            }
        } catch (e: Exception) {
            Log.e("AppUpdateRepo", "Error getting installed packages", e)
            emptyList()
        }

        packages.mapNotNull { pkg ->
            try {
                val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!includeSystem && isSystem) return@mapNotNull null

                val label = appInfo.loadLabel(packageManager)?.toString()?.trim()
                val appName = if (label.isNullOrEmpty()) pkg.packageName else label
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkg.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkg.versionCode.toLong()
                }

                InstalledApp(
                    packageName = pkg.packageName,
                    appName = appName,
                    versionName = pkg.versionName ?: "Unknown",
                    versionCode = versionCode,
                    isSystemApp = isSystem,
                    firstInstallTime = pkg.firstInstallTime,
                    lastUpdateTime = pkg.lastUpdateTime
                )
            } catch (e: Exception) {
                null
            }
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

        val exclude = if (onlyStable) {
            listOf("alpha", "beta", "pre-release", "dev")
        } else {
            emptyList()
        }

        val allUpdates = mutableListOf<AppUpdateInfo>()
        val batches = appsToCheck.chunked(30)
        var processedCount = 0

        emit(ScanStatus.Scanning(processed = 0, total = appsToCheck.size, currentBatch = "Preparing scan..."))

        for (batch in batches) {
            val pnames = batch.map { it.packageName }
            emit(
                ScanStatus.Scanning(
                    processed = processedCount,
                    total = appsToCheck.size,
                    currentBatch = batch.firstOrNull()?.appName ?: "Scanning batch..."
                )
            )

            try {
                val response = service.appExists(
                    AppExistsRequest(
                        pnames = pnames,
                        exclude = exclude
                    )
                )

                val updatesFromBatch = parseUpdates(response.data, batch, onlyStable = onlyStable)
                allUpdates.addAll(updatesFromBatch)
            } catch (e: Exception) {
                Log.e("AppUpdateRepo", "Error querying APKMirror batch: ${e.message}")
            }

            processedCount += batch.size
            emit(
                ScanStatus.Scanning(
                    processed = minOf(processedCount, appsToCheck.size),
                    total = appsToCheck.size,
                    currentBatch = batch.lastOrNull()?.appName ?: "Processing..."
                )
            )
        }

        emit(ScanStatus.Success(updates = allUpdates, allApps = appsToCheck))
    }.flowOn(Dispatchers.IO)

    private fun parseUpdates(
        apiDataList: List<AppExistsResponseData>,
        installedBatch: List<InstalledApp>,
        onlyStable: Boolean = true
    ): List<AppUpdateInfo> {
        val appMap = installedBatch.associateBy { it.packageName }

        return apiDataList
            .filter { it.exists == true }
            .mapNotNull { data ->
                val installed = appMap[data.pname] ?: return@mapNotNull null

                val releaseVersion = data.release?.version.orEmpty()
                val releaseLink = data.release?.link.orEmpty()

                if (onlyStable && isNonStableVersion(releaseVersion, releaseLink)) {
                    return@mapNotNull null
                }

                val bestApk = data.apks
                    .asSequence()
                    .filter { filterArch(it) }
                    .filter { filterMinApi(it) }
                    .filter { apk ->
                        if (!onlyStable) true
                        else !isNonStableVersion(apk.description.orEmpty(), apk.link)
                    }
                    .filter { it.versionCode > installed.versionCode }
                    .maxByOrNull { it.versionCode }

                if (bestApk != null) {
                    val linkSuffix = bestApk.link.ifEmpty { data.release?.link ?: data.app?.link.orEmpty() }
                    val fullUrl = if (linkSuffix.startsWith("http")) linkSuffix else "https://www.apkmirror.com$linkSuffix"

                    AppUpdateInfo(
                        packageName = data.pname,
                        appName = installed.appName,
                        currentVersionName = installed.versionName,
                        currentVersionCode = installed.versionCode,
                        newVersionName = data.release?.version ?: "Update Available",
                        newVersionCode = bestApk.versionCode,
                        publishDate = bestApk.publishDate ?: data.release?.publishDate,
                        whatsNew = data.release?.whatsNew,
                        apkMirrorUrl = fullUrl,
                        architectures = bestApk.arches,
                        isSystemApp = installed.isSystemApp
                    )
                } else {
                    null
                }
            }
    }

    private fun isNonStableVersion(vararg texts: String): Boolean {
        val nonStableKeywords = listOf(
            "alpha", "beta", "pre-release", "prerelease", "preview",
            " rc", "-rc", ".rc", "rc0", "rc1", "rc2", "rc3", "rc4", "rc5",
            "canary", "dev", "nightly", "snapshot", "experimental"
        )
        return texts.any { text ->
            val lower = text.lowercase()
            nonStableKeywords.any { keyword -> lower.contains(keyword) }
        }
    }

    private fun filterArch(apk: AppExistsApk): Boolean {
        if (apk.arches.isEmpty()) return true
        if (apk.arches.any { it.equals("universal", ignoreCase = true) || it.equals("noarch", ignoreCase = true) }) {
            return true
        }
        return apk.arches.any { apkArch ->
            deviceAbis.any { devAbi ->
                devAbi.contains(apkArch, ignoreCase = true) || apkArch.contains(devAbi, ignoreCase = true)
            }
        }
    }

    private fun filterMinApi(apk: AppExistsApk): Boolean {
        val minApiInt = apk.minapi?.toIntOrNull() ?: 0
        return minApiInt <= Build.VERSION.SDK_INT
    }
}
