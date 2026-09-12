package com.android.gupdater.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.os.Build
import com.android.gupdater.data.api.ApkMirrorClient
import com.android.gupdater.data.model.ApkMirrorApk
import com.android.gupdater.data.model.ApkMirrorApp
import com.android.gupdater.data.model.AppUpdateInfo
import com.android.gupdater.data.model.InstalledApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.security.MessageDigest

sealed interface ScanStatus {
    data object Scanning : ScanStatus
    data class Success(val updates: List<AppUpdateInfo>) : ScanStatus
    data class Error(val message: String, val partialUpdates: List<AppUpdateInfo>) : ScanStatus
}

class AppUpdateRepository(
    context: Context,
    private val client: ApkMirrorClient = ApkMirrorClient()
) {
    private val packageManager = context.packageManager
    private val isAndroidTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    private val deviceAbis = Build.SUPPORTED_ABIS.map(String::lowercase)
    private val universalAbiRank = deviceAbis.size

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
                    versionName = packageInfo.versionName ?: "Unknown",
                    versionCode = packageInfo.longVersionCode,
                    signatureSha1s = packageInfo.signingInfo.sha1Signatures(),
                    isEnabled = appInfo.enabled
                )
            }.getOrNull()
        }
    }

    suspend fun getInstalledApp(packageName: String): InstalledApp? = withContext(Dispatchers.IO) {
        runCatching {
            val packageInfo = packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(
                    (PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()
                )
            )
            val appInfo = packageInfo.applicationInfo ?: return@runCatching null

            InstalledApp(
                packageName = packageInfo.packageName,
                versionName = packageInfo.versionName ?: "Unknown",
                versionCode = packageInfo.longVersionCode,
                signatureSha1s = packageInfo.signingInfo.sha1Signatures(),
                isEnabled = appInfo.enabled
            )
        }.getOrNull()
    }

    fun scanForUpdates(appsToCheck: List<InstalledApp>): Flow<ScanStatus> = flow {
        if (appsToCheck.isEmpty()) {
            emit(ScanStatus.Success(emptyList()))
            return@flow
        }

        val batches = appsToCheck.chunked(API_BATCH_SIZE)

        emit(ScanStatus.Scanning)

        val results = coroutineScope {
            batches.map { batch ->
                async {
                    try {
                        val apps = client.appExists(batch.map(InstalledApp::packageName))
                        Result.success(parseUpdates(apps, batch))
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        Result.failure(exception)
                    }
                }
            }.awaitAll()
        }

        val updates = results.mapNotNull(Result<List<AppUpdateInfo>>::getOrNull).flatten()
        val failedBatches = results.count(Result<List<AppUpdateInfo>>::isFailure)

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
        apps: List<ApkMirrorApp>,
        installedBatch: List<InstalledApp>
    ): List<AppUpdateInfo> {
        val installedByPackage = installedBatch.associateBy(InstalledApp::packageName)

        return apps.mapNotNull { app ->
            val installed = installedByPackage[app.packageName] ?: return@mapNotNull null
            if (!isStableRelease(app.versionName)) return@mapNotNull null

            val apk = bestApk(app.apks, installed) ?: return@mapNotNull null
            val url = apk.link.toAbsoluteApkMirrorUrl()
            if (!url.startsWith(GOOGLE_APKMIRROR_PREFIX)) return@mapNotNull null

            AppUpdateInfo(
                packageName = installed.packageName,
                appName = label(installed.packageName),
                newVersionName = app.versionName,
                newVersionCode = apk.versionCode,
                apkMirrorUrl = url
            )
        }
    }

    private fun bestApk(apks: List<ApkMirrorApk>, installed: InstalledApp): ApkMirrorApk? = apks
        .asSequence()
        .filter { it.versionCode > installed.versionCode }
        .filter { it.minimumApi <= Build.VERSION.SDK_INT }
        .filter { isStableRelease(it.link) }
        .filter(::matchesFormFactor)
        .filter { matchesSignature(it, installed.signatureSha1s) }
        .filter { abiRank(it) != UNSUPPORTED_ABI }
        .minWithOrNull(
            compareBy<ApkMirrorApk> { abiRank(it) }
                .thenByDescending(ApkMirrorApk::minimumApi)
                .thenByDescending(ApkMirrorApk::versionCode)
        )

    private fun abiRank(apk: ApkMirrorApk): Int {
        if (apk.architectures.isEmpty()) return universalAbiRank
        val architectures = apk.architectures.map(String::lowercase)
        if (architectures.any { it == "universal" || it == "noarch" }) return universalAbiRank
        return architectures.minOf(::abiIndex)
    }

    private fun abiIndex(architecture: String): Int {
        val index = deviceAbis.indexOf(architecture)
        if (index >= 0) return index
        if (architecture == "arm") {
            return deviceAbis
                .indexOfFirst { it == "armeabi-v7a" || it == "arm64-v8a" }
                .takeIf { it >= 0 }
                ?: UNSUPPORTED_ABI
        }
        return UNSUPPORTED_ABI
    }

    private fun matchesFormFactor(apk: ApkMirrorApk): Boolean {
        if (apk.capabilities.contains("wear_standalone")) return false
        return if (isAndroidTv) {
            apk.capabilities.contains("leanback_standalone") || apk.capabilities.contains("leanback")
        } else {
            !apk.capabilities.contains("leanback_standalone")
        }
    }

    private fun matchesSignature(apk: ApkMirrorApk, installedSignatures: Set<String>): Boolean =
        apk.signatureSha1s.isEmpty() || apk.signatureSha1s.any { it in installedSignatures }

    private fun label(packageName: String): String = runCatching {
        packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            .loadLabel(packageManager)
            .toString()
            .trim()
    }.getOrNull()?.ifEmpty { null } ?: packageName

    private fun isStableRelease(value: String): Boolean =
        value.isBlank() || !PRE_RELEASE_MARKER_PATTERN.containsMatchIn(value)

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
        const val UNSUPPORTED_ABI = Int.MAX_VALUE
        const val GOOGLE_APKMIRROR_PREFIX = "https://www.apkmirror.com/apk/google-inc/"
        val PRE_RELEASE_MARKER_PATTERN =
            Regex("(?:^|[^a-z])(alpha|beta|preview|canary|rc|release[-_ ]candidate|pre[-_ ]?release|prerelease|nightly|snapshot|debug|development|dev)(?:[^a-z]|$)", RegexOption.IGNORE_CASE)
    }
}
