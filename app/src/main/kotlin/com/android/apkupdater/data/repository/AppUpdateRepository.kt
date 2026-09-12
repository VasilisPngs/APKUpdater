package com.android.apkupdater.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.os.Build
import com.android.apkupdater.data.api.ApkMirrorService
import com.android.apkupdater.data.model.AppExistsApk
import com.android.apkupdater.data.model.AppExistsRequest
import com.android.apkupdater.data.model.AppExistsResponseData
import com.android.apkupdater.data.model.AppUpdateInfo
import com.android.apkupdater.data.model.InstalledApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.security.MessageDigest

sealed interface ScanStatus {
    data object Idle : ScanStatus
    data class Scanning(val processed: Int, val total: Int, val currentBatch: String) : ScanStatus
    data class Success(val updates: List<AppUpdateInfo>) : ScanStatus
    data class Error(val message: String, val partialUpdates: List<AppUpdateInfo>) : ScanStatus
}

class AppUpdateRepository(
    context: Context,
    private val service: ApkMirrorService = ApkMirrorService.create()
) {
    private val packageManager = context.packageManager
    private val isAndroidTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    private val deviceAbis = Build.SUPPORTED_ABIS.map(String::lowercase).toSet()

    suspend fun getInstalledApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        packageManager.getInstalledPackages(
            PackageManager.PackageInfoFlags.of(
                (PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()
            )
        ).mapNotNull { packageInfo ->
            runCatching {
                val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

                InstalledApp(
                    packageName = packageInfo.packageName,
                    appName = appInfo.loadLabel(packageManager).toString().trim()
                        .ifEmpty { packageInfo.packageName },
                    versionName = packageInfo.versionName ?: "Unknown",
                    versionCode = packageInfo.longVersionCode,
                    signatureSha1s = packageInfo.signingInfo.sha1Signatures(),
                    isSystemApp = isSystem,
                    isEnabled = appInfo.enabled
                )
            }.getOrNull()
        }.sortedBy { it.appName.lowercase() }
    }

    fun scanForUpdates(appsToCheck: List<InstalledApp>): Flow<ScanStatus> = flow {
        if (appsToCheck.isEmpty()) {
            emit(ScanStatus.Success(emptyList()))
            return@flow
        }

        val batches = appsToCheck.chunked(API_BATCH_SIZE)
        val updates = mutableListOf<AppUpdateInfo>()
        var processed = 0
        var failedBatches = 0

        emit(ScanStatus.Scanning(0, appsToCheck.size, "Preparing scan…"))

        for (batch in batches) {
            emit(ScanStatus.Scanning(processed, appsToCheck.size, batch.first().appName))

            try {
                val response = service.appExists(
                    AppExistsRequest(
                        pnames = batch.map(InstalledApp::packageName),
                        exclude = STABLE_RELEASE_EXCLUSIONS
                    )
                )
                updates += parseUpdates(response.data, batch)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                failedBatches++
            }

            processed += batch.size
            emit(ScanStatus.Scanning(processed, appsToCheck.size, batch.last().appName))
        }

        when {
            failedBatches == batches.size -> {
                emit(ScanStatus.Error("Unable to reach APKMirror.", updates))
            }
            failedBatches > 0 -> {
                emit(ScanStatus.Error("Some applications could not be checked.", updates))
            }
            else -> emit(ScanStatus.Success(updates))
        }
    }.flowOn(Dispatchers.IO)

    private fun parseUpdates(
        apiDataList: List<AppExistsResponseData>,
        installedBatch: List<InstalledApp>
    ): List<AppUpdateInfo> {
        val installedByPackage = installedBatch.associateBy(InstalledApp::packageName)

        return apiDataList.asSequence()
            .filter { it.exists == true }
            .mapNotNull { data ->
                val installed = installedByPackage[data.pname] ?: return@mapNotNull null
                val release = data.release ?: return@mapNotNull null
                val bestApk = data.apks.asSequence()
                    .filter { apk -> filterSignature(apk, installed.signatureSha1s) }
                    .filter(::filterArchitecture)
                    .filter(::filterMinimumApi)
                    .filter(::filterAndroidTv)
                    .filter(::filterWearOs)
                    .filter { it.versionCode > installed.versionCode }
                    .maxByOrNull(AppExistsApk::versionCode)
                    ?: return@mapNotNull null

                val url = bestApk.link.toAbsoluteApkMirrorUrl()
                if (!url.startsWith(GOOGLE_APKMIRROR_PREFIX)) return@mapNotNull null

                AppUpdateInfo(
                    packageName = installed.packageName,
                    appName = installed.appName,
                    currentVersionName = installed.versionName,
                    currentVersionCode = installed.versionCode,
                    newVersionName = release.version.orEmpty(),
                    newVersionCode = bestApk.versionCode,
                    apkMirrorUrl = url
                )
            }
            .toList()
    }

    private fun filterSignature(apk: AppExistsApk, installedSignatures: Set<String>): Boolean {
        val signatures = apk.signaturesSha1.orEmpty()
        return signatures.isEmpty() || signatures.any { it in installedSignatures }
    }

    private fun filterArchitecture(apk: AppExistsApk): Boolean {
        if (apk.arches.isEmpty()) return true
        val arches = apk.arches.map(String::lowercase)
        if (arches.any { it == "universal" || it == "noarch" }) return true
        return arches.any { arch ->
            arch in deviceAbis ||
                (arch == "arm" && deviceAbis.any { it == "armeabi-v7a" || it == "arm64-v8a" })
        }
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

    private fun filterMinimumApi(apk: AppExistsApk): Boolean = apk.minapi
        ?.toIntOrNull()
        ?.let { it <= Build.VERSION.SDK_INT }
        ?: true

    private fun SigningInfo?.sha1Signatures(): Set<String> = this
        ?.let { signingInfo ->
            val certificates = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners.toList()
            } else {
                signingInfo.signingCertificateHistory.toList()
            }
            certificates.mapTo(mutableSetOf()) { certificate -> certificate.sha1() }
        }
        ?: emptySet()

    private fun android.content.pm.Signature.sha1(): String =
        MessageDigest.getInstance("SHA-1")
            .digest(toByteArray())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun String.toAbsoluteApkMirrorUrl(): String = when {
        startsWith("https://") || startsWith("http://") -> this
        startsWith("/") -> "https://www.apkmirror.com$this"
        else -> "https://www.apkmirror.com/$this"
    }

    private companion object {
        const val API_BATCH_SIZE = 100
        const val GOOGLE_APKMIRROR_PREFIX = "https://www.apkmirror.com/apk/google-inc/"
        val STABLE_RELEASE_EXCLUSIONS = listOf("alpha", "beta")
    }
}
