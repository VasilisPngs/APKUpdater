package com.android.apkupdater.data.repository

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import android.util.DisplayMetrics
import com.android.apkupdater.data.api.ApkMirrorClient
import com.android.apkupdater.data.appLabel
import com.android.apkupdater.data.model.ApkMirrorApk
import com.android.apkupdater.data.model.ApkMirrorApp
import com.android.apkupdater.data.model.AppUpdateInfo
import com.android.apkupdater.data.model.InstalledApp
import com.android.apkupdater.data.play.PlayCatalog
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale

sealed interface ScanStatus {
    data object Scanning : ScanStatus
    data class Success(val updates: List<AppUpdateInfo>) : ScanStatus
    data class Error(val message: String, val partialUpdates: List<AppUpdateInfo>) : ScanStatus
}

class AppUpdateRepository(
    context: Context,
    private val playCatalog: PlayCatalog,
    private val client: ApkMirrorClient = ApkMirrorClient(context.packageName)
) {
    private val packageManager = context.packageManager
    private val isAndroidTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    private val deviceAbis = Build.SUPPORTED_ABIS.map(String::lowercase)
    private val universalAbiRank = deviceAbis.size
    private val deviceDensityBucket = DENSITY_BUCKETS
        .firstOrNull { it >= context.resources.displayMetrics.densityDpi }
        ?: DENSITY_BUCKETS.last()

    suspend fun getInstalledApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        packageManager.getInstalledPackages(PACKAGE_FLAGS)
            .mapNotNull { packageInfo -> runCatching { packageInfo.toInstalledApp() }.getOrNull() }
    }

    suspend fun getInstalledApp(packageName: String): InstalledApp? = withContext(Dispatchers.IO) {
        runCatching { packageManager.getPackageInfo(packageName, PACKAGE_FLAGS).toInstalledApp() }
            .getOrNull()
    }

    fun scanForUpdates(appsToCheck: List<InstalledApp>): Flow<ScanStatus> = flow {
        if (appsToCheck.isEmpty()) {
            emit(ScanStatus.Success(emptyList()))
            return@flow
        }

        val batches = appsToCheck.chunked(API_BATCH_SIZE)

        emit(ScanStatus.Scanning)

        val play: Result<Set<String>>
        val results: List<Result<List<AppUpdateInfo>>>

        coroutineScope {
            val deliverable = async {
                runCatching { playCatalog.deliverablePackages(appsToCheck.map(InstalledApp::packageName)) }
            }
            results = batches.map { batch ->
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
            play = deliverable.await()
        }

        val deliverable = play.getOrNull().orEmpty()
        val updates = results.mapNotNull(Result<List<AppUpdateInfo>>::getOrNull)
            .flatten()
            .map { it.copy(manualAvailable = it.packageName in deliverable) }
            .sortedWith(NEWEST_FIRST)
        val failedBatches = results.count(Result<List<AppUpdateInfo>>::isFailure)
        val apkMirrorFailure = results.firstNotNullOfOrNull { it.exceptionOrNull() }?.let(::reason)
        val failures = buildList {
            when {
                failedBatches == batches.size -> add("APKMirror: $apkMirrorFailure")
                failedBatches > 0 -> add("Some applications were not checked on APKMirror: $apkMirrorFailure")
            }
            play.exceptionOrNull()?.let { add("Google Play: ${reason(it)}") }
        }

        if (failures.isEmpty()) {
            emit(ScanStatus.Success(updates))
        } else {
            emit(ScanStatus.Error(failures.joinToString(" "), updates))
        }
    }.flowOn(Dispatchers.IO)

    private fun reason(exception: Throwable): String =
        exception.message?.takeIf(String::isNotBlank) ?: exception::class.simpleName.orEmpty()

    private fun parseUpdates(
        apps: List<ApkMirrorApp>,
        installedBatch: List<InstalledApp>
    ): List<AppUpdateInfo> {
        val installedByPackage = installedBatch.associateBy(InstalledApp::packageName)

        return apps.mapNotNull { app ->
            val installed = installedByPackage[app.packageName] ?: return@mapNotNull null
            if (!isStableRelease(app.packageName)) return@mapNotNull null
            if (!isStableRelease(app.versionName)) return@mapNotNull null

            val apk = bestApk(app.apks, installed) ?: return@mapNotNull null

            AppUpdateInfo(
                packageName = installed.packageName,
                appName = packageManager.appLabel(installed.packageName),
                newVersionName = app.versionName,
                newVersionCode = apk.versionCode,
                publishedAt = publishedAt(apk.publishDate.ifBlank { app.publishDate }),
                apkMirrorUrl = apk.link.toAbsoluteApkMirrorUrl(),
                manualAvailable = false
            )
        }
    }

    private fun bestApk(apks: List<ApkMirrorApk>, installed: InstalledApp): ApkMirrorApk? {
        val leanbackApp = isAndroidTv && hasLeanbackLauncher(installed.packageName)

        return apks
            .asSequence()
            .filter { it.versionCode > installed.versionCode }
            .filter { it.minimumApi <= Build.VERSION.SDK_INT }
            .filter { isStableLink(it.link) }
            .filter { matchesFormFactor(it, leanbackApp) }
            .filter { matchesSignature(it, installed) }
            .filter { abiRank(it) != UNSUPPORTED_ABI }
            .minWithOrNull(
                compareBy<ApkMirrorApk> { leanbackRank(it) }
                    .thenBy(::abiRank)
                    .thenBy(::densityRank)
                    .thenByDescending(ApkMirrorApk::minimumApi)
                    .thenByDescending(ApkMirrorApk::versionCode)
            )
    }

    private fun abiRank(apk: ApkMirrorApk): Int {
        if (apk.architectures.isEmpty()) return universalAbiRank
        if (apk.architectures.any { it in UNIVERSAL_ARCHITECTURES }) return universalAbiRank
        return apk.architectures.minOf(::abiIndex)
    }

    private fun abiIndex(architecture: String): Int =
        deviceAbis.indexOf(architecture).takeIf { it >= 0 } ?: UNSUPPORTED_ABI

    private fun densityRank(apk: ApkMirrorApk): Int {
        if (apk.densities.contains(NO_DENSITY)) return UNIVERSAL_DENSITY_RANK

        val buckets = apk.densities.mapNotNull(::densityBucket)
        return when {
            buckets.isEmpty() -> UNIVERSAL_DENSITY_RANK
            deviceDensityBucket in buckets -> MATCHING_DENSITY_RANK
            else -> FOREIGN_DENSITY_RANK
        }
    }

    private fun densityBucket(density: String): Int? = density.toIntOrNull()

    private fun matchesFormFactor(apk: ApkMirrorApk, leanbackApp: Boolean): Boolean {
        if (apk.capabilities.contains(WEAR_STANDALONE)) return false

        return when {
            leanbackApp -> apk.isLeanback
            isAndroidTv -> true
            else -> !apk.capabilities.contains(LEANBACK_STANDALONE)
        }
    }

    private fun leanbackRank(apk: ApkMirrorApk): Int = if (apk.isLeanback == isAndroidTv) 0 else 1

    private fun hasLeanbackLauncher(packageName: String): Boolean =
        packageManager.getLeanbackLaunchIntentForPackage(packageName) != null

    private val ApkMirrorApk.isLeanback: Boolean
        get() = capabilities.contains(LEANBACK) || capabilities.contains(LEANBACK_STANDALONE)

    private fun matchesSignature(apk: ApkMirrorApk, installed: InstalledApp): Boolean = when {
        apk.signatureSha256s.isNotEmpty() && installed.signatureSha256s.isNotEmpty() ->
            apk.signatureSha256s.any { it in installed.signatureSha256s }
        apk.signatureSha1s.isNotEmpty() && installed.signatureSha1s.isNotEmpty() ->
            apk.signatureSha1s.any { it in installed.signatureSha1s }
        else -> true
    }

    private fun publishedAt(value: String): Long? {
        val text = value.trim().replace(' ', 'T')
        if (text.isEmpty()) return null

        return runCatching { OffsetDateTime.parse(text).toInstant() }
            .recoverCatching { LocalDateTime.parse(text).toInstant(ZoneOffset.UTC) }
            .recoverCatching { LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant() }
            .getOrNull()
            ?.toEpochMilli()
    }

    private fun isStableLink(link: String): Boolean = link
        .substringAfter(APKMIRROR_PATH_PREFIX, "")
        .split('/')
        .drop(1)
        .all(::isStableRelease)

    private fun isStableRelease(value: String): Boolean =
        value.isBlank() || !PRE_RELEASE_MARKER_PATTERN.containsMatchIn(value)

    private fun PackageInfo.toInstalledApp(): InstalledApp? {
        val appInfo = applicationInfo ?: return null
        val certificates = signingInfo.certificates()

        return InstalledApp(
            packageName = packageName,
            versionName = versionName ?: "Unknown",
            versionCode = longVersionCode,
            signatureSha1s = certificates.digests("SHA-1"),
            signatureSha256s = certificates.digests("SHA-256"),
            isEnabled = appInfo.enabled
        )
    }

    private fun SigningInfo?.certificates(): List<Signature> = when {
        this == null -> emptyList()
        hasMultipleSigners() -> apkContentsSigners.toList()
        else -> signingCertificateHistory.toList()
    }

    private fun List<Signature>.digests(algorithm: String): Set<String> {
        if (isEmpty()) return emptySet()
        val digest = MessageDigest.getInstance(algorithm)
        return mapTo(mutableSetOf()) { signature ->
            digest.digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }
    }

    private fun String.toAbsoluteApkMirrorUrl(): String = APKMIRROR_URL + this

    private companion object {
        const val API_BATCH_SIZE = 100
        const val APKMIRROR_URL = "https://www.apkmirror.com"
        const val APKMIRROR_PATH_PREFIX = "/apk/"
        const val NO_DENSITY = "nodpi"
        const val WEAR_STANDALONE = "wear_standalone"
        const val LEANBACK = "leanback"
        const val LEANBACK_STANDALONE = "leanback_standalone"
        val NEWEST_FIRST = compareByDescending<AppUpdateInfo> { it.publishedAt ?: Long.MIN_VALUE }
            .thenBy { it.appName.lowercase(Locale.ROOT) }
        const val UNSUPPORTED_ABI = Int.MAX_VALUE
        const val MATCHING_DENSITY_RANK = 0
        const val UNIVERSAL_DENSITY_RANK = 1
        const val FOREIGN_DENSITY_RANK = 2
        val PACKAGE_FLAGS: PackageManager.PackageInfoFlags = PackageManager.PackageInfoFlags.of(
            (PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()
        )
        val UNIVERSAL_ARCHITECTURES = setOf("universal", "noarch")
        val DENSITY_BUCKETS = listOf(
            DisplayMetrics.DENSITY_LOW,
            DisplayMetrics.DENSITY_MEDIUM,
            DisplayMetrics.DENSITY_TV,
            DisplayMetrics.DENSITY_HIGH,
            DisplayMetrics.DENSITY_XHIGH,
            DisplayMetrics.DENSITY_XXHIGH,
            DisplayMetrics.DENSITY_XXXHIGH
        )
        val PRE_RELEASE_MARKER_PATTERN =
            Regex(
                "(?:^|[^a-z])(alpha|beta|rc|canary|dev|preview)(?:[^a-z]|$)",
                RegexOption.IGNORE_CASE
            )
    }
}
