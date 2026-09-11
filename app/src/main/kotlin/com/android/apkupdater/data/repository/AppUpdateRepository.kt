package com.android.apkupdater.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.android.apkupdater.data.api.ApkMirrorPageService
import com.android.apkupdater.data.api.ApkMirrorService
import com.android.apkupdater.data.model.AppExistsApk
import com.android.apkupdater.data.model.AppExistsRequest
import com.android.apkupdater.data.model.AppExistsResponseData
import com.android.apkupdater.data.model.AppUpdateInfo
import com.android.apkupdater.data.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.format.DateTimeFormatterBuilder
import java.time.ZoneOffset
import java.util.Locale

sealed interface ScanStatus {
    data object Idle : ScanStatus
    data class Scanning(val processed: Int, val total: Int, val currentBatch: String) : ScanStatus
    data class Success(val updates: List<AppUpdateInfo>) : ScanStatus
    data class Error(val message: String, val partialUpdates: List<AppUpdateInfo>) : ScanStatus
}

class AppUpdateRepository(
    context: Context,
    private val service: ApkMirrorService = ApkMirrorService.create(),
    private val pageService: ApkMirrorPageService = ApkMirrorPageService.create()
) {
    private val packageManager = context.packageManager
    private val isAndroidTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    private val deviceAbis = Build.SUPPORTED_ABIS.map(String::lowercase).toSet()
    private val deviceArch = when {
        deviceAbis.any { it == "x86" || it == "x86_64" } -> "x86"
        deviceAbis.any { it == "armeabi-v7a" || it == "arm64-v8a" } -> "arm"
        else -> "arm"
    }

    suspend fun getInstalledApps(includeSystem: Boolean = true): List<InstalledApp> = withContext(Dispatchers.IO) {
        val packages = packageManager.getInstalledPackages(
            PackageManager.PackageInfoFlags.of(
                (PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()
            )
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
                            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
                    }
                    .orEmpty()

                InstalledApp(
                    packageName = pkg.packageName,
                    appName = appInfo.loadLabel(packageManager).toString().trim().ifEmpty { pkg.packageName },
                    versionName = pkg.versionName ?: "Unknown",
                    versionCode = pkg.longVersionCode,
                    signatureSha1 = signatureSha1,
                    isSystemApp = isSystem,
                    isEnabled = appInfo.enabled
                )
            }.getOrNull()
        }.sortedWith(compareBy({ it.isSystemApp }, { it.appName.lowercase() }))
    }

    fun scanForUpdates(appsToCheck: List<InstalledApp>): Flow<ScanStatus> = flow {
        if (appsToCheck.isEmpty()) {
            emit(ScanStatus.Success(emptyList()))
            return@flow
        }

        val exclude = listOf("alpha", "beta")
        val batches = appsToCheck.chunked(100)
        val updates = mutableListOf<AppUpdateInfo>()
        var processed = 0
        var failedBatches = 0

        emit(ScanStatus.Scanning(0, appsToCheck.size, "Preparing scan…"))

        for (batch in batches) {
            emit(ScanStatus.Scanning(processed, appsToCheck.size, batch.first().appName))
            try {
                val response = service.appExists(
                    AppExistsRequest(batch.map(InstalledApp::packageName), exclude)
                )
                updates += parseUpdates(response.data, batch)
            } catch (_: Exception) {
                failedBatches++
            }
            processed += batch.size
            emit(ScanStatus.Scanning(processed, appsToCheck.size, batch.last().appName))
        }

        val timestampedUpdates = enrichWithApkMirrorUploadTimes(updates)

        when {
            failedBatches == batches.size -> emit(ScanStatus.Error("Unable to reach APKMirror.", timestampedUpdates))
            failedBatches > 0 -> emit(ScanStatus.Error("Some applications could not be checked.", timestampedUpdates))
            else -> emit(ScanStatus.Success(timestampedUpdates))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun enrichWithApkMirrorUploadTimes(
        updates: List<AppUpdateInfo>
    ): List<AppUpdateInfo> = coroutineScope {
        updates.map { update ->
            async {
                update.copy(apkMirrorUploadedAt = fetchApkMirrorUploadTime(update.apkMirrorUrl))
            }
        }.awaitAll()
    }

    private suspend fun fetchApkMirrorUploadTime(url: String): Long? = runCatching {
        val html = pageService.getReleasePage(url).string()
        val match = Regex(
            "Uploaded:\\s*([A-Za-z]+\\s+\\d{1,2},\\s+\\d{4}\\s+at\\s+\\d{1,2}:\\d{2}(?:AM|PM)\\s+UTC)"
        ).find(html)
        match?.groupValues?.get(1)?.let { value ->
            runCatching {
                DateTimeFormatterBuilder()
                    .appendPattern("MMMM d, uuuu 'at' h:mma 'UTC'")
                    .toFormatter(Locale.US)
                    .parse(value, java.time.LocalDateTime::from)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }.getOrNull()
        }
    }.getOrNull()

    private fun parseUpdates(
        apiDataList: List<AppExistsResponseData>,
        installedBatch: List<InstalledApp>
    ): List<AppUpdateInfo> {
        val appMap = installedBatch.associateBy(InstalledApp::packageName)

        return apiDataList.asSequence()
            .filter { it.exists == true }
            .mapNotNull { data ->
                val installed = appMap[data.pname] ?: return@mapNotNull null
                val release = data.release ?: return@mapNotNull null

                val bestApk = data.apks.asSequence()
                    .filter { apk -> filterSignature(apk, installed.signatureSha1) }
                    .filter(::filterArch)
                    .filter(::filterMinApi)
                    .filter(::filterAndroidTv)
                    .filter(::filterWearOs)
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
                    newVersionName = release.version.orEmpty(),
                    newVersionCode = bestApk.versionCode,
                    whatsNew = release.whatsNew,
                    apkMirrorUrl = url
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

    private fun filterAndroidTv(apk: AppExistsApk): Boolean {
        val capabilities = apk.capabilities.orEmpty()
        return if (isAndroidTv) {
            capabilities.contains("leanback_standalone") || capabilities.contains("leanback")
        } else {
            !capabilities.contains("leanback_standalone")
        }
    }

    private fun filterWearOs(apk: AppExistsApk): Boolean =
        !apk.capabilities.orEmpty().contains("wear_standalone")

    private fun filterMinApi(apk: AppExistsApk): Boolean = apk.minapi
        ?.toIntOrNull()
        ?.let { it <= Build.VERSION.SDK_INT }
        ?: true
}
