package com.android.gupdater.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.os.Build
import com.android.gupdater.data.api.ApkMirrorService
import com.android.gupdater.data.model.AppExistsApk
import com.android.gupdater.data.model.AppExistsRequest
import com.android.gupdater.data.model.AppExistsResponseData
import com.android.gupdater.data.model.AppUpdateInfo
import com.android.gupdater.data.model.InstalledApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.security.MessageDigest

sealed interface ScanStatus {
    data object Idle : ScanStatus
    data object Scanning : ScanStatus
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

                InstalledApp(
                    packageName = packageInfo.packageName,
                    appName = appInfo.loadLabel(packageManager).toString().trim()
                        .ifEmpty { packageInfo.packageName },
                    versionName = packageInfo.versionName ?: "Unknown",
                    versionCode = packageInfo.longVersionCode,
                    signatureSha1s = packageInfo.signingInfo.sha1Signatures(),
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
        var failedBatches = 0

        emit(ScanStatus.Scanning)

        for (batch in batches) {
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
                    .filter(::filterStableRelease)
                    .filter { it.versionCode > installed.versionCode }
                    .maxByOrNull(AppExistsApk::versionCode)
                    ?: return@mapNotNull null

                val url = bestApk.link.toAbsoluteApkMirrorUrl()
                if (!url.startsWith(GOOGLE_APKMIRROR_PREFIX)) return@mapNotNull null
                if (!isStableRelease(release.version) || !isStableRelease(url)) return@mapNotNull null

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

    private fun filterStableRelease(apk: AppExistsApk): Boolean =
        isStableRelease(apk.link)

    private fun isStableRelease(value: String?): Boolean {
        if (value.isNullOrBlank()) return true
        return !PRE_RELEASE_MARKER_PATTERN.containsMatchIn(value)
    }

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
        val PRE_RELEASE_MARKER_PATTERN =
            Regex("(?:^|[^a-z])(alpha|beta|preview|canary|rc|release[-_ ]candidate|pre[-_ ]?release|prerelease|nightly|snapshot|debug|development|dev)(?:[^a-z]|$)", RegexOption.IGNORE_CASE)
    }
}
