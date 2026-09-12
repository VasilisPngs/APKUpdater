package com.android.apkupdater.data.repository

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import com.android.apkupdater.data.api.ApkMirrorClient
import com.android.apkupdater.data.model.ApkMirrorApk
import com.android.apkupdater.data.model.ApkMirrorApp
import com.android.apkupdater.data.model.AppUpdateInfo
import com.android.apkupdater.data.model.InstalledApp
import com.android.apkupdater.data.play.PlayApp
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface ScanStatus {
    data object Scanning : ScanStatus
    data class Success(val updates: List<AppUpdateInfo>) : ScanStatus
    data class Error(val message: String, val partialUpdates: List<AppUpdateInfo>) : ScanStatus
}

class AppUpdateRepository(
    context: Context,
    private val playCatalog: PlayCatalog,
    private val client: ApkMirrorClient = ApkMirrorClient()
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

        val play: Map<String, PlayApp>?
        val results: List<Result<List<AppUpdateInfo>>>

        coroutineScope {
            val playDetails = async { playUpdates(appsToCheck) }
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
            play = playDetails.await()
        }

        val updates = merge(
            results.mapNotNull(Result<List<AppUpdateInfo>>::getOrNull).flatten(),
            play.orEmpty()
        )
        val failedBatches = results.count(Result<List<AppUpdateInfo>>::isFailure)
        val failures = buildList {
            when {
                failedBatches == batches.size -> add("APKMirror could not be reached.")
                failedBatches > 0 -> add("Some applications could not be checked on APKMirror.")
            }
            if (play == null) add("Google Play could not be reached.")
        }

        if (failures.isEmpty()) {
            emit(ScanStatus.Success(updates))
        } else {
            emit(ScanStatus.Error(failures.joinToString(" "), updates))
        }
    }.flowOn(Dispatchers.IO)

    private fun playUpdates(apps: List<InstalledApp>): Map<String, PlayApp>? {
        val details = runCatching { playCatalog.details(apps.map(InstalledApp::packageName)) }
            .getOrNull()
            ?: return null
        val installedVersions = apps.associate { it.packageName to it.versionCode }

        return details.filter { (packageName, playApp) ->
            playApp.versionCode > (installedVersions[packageName] ?: Long.MAX_VALUE)
        }
    }

    private fun merge(
        apkMirrorUpdates: List<AppUpdateInfo>,
        playUpdates: Map<String, PlayApp>
    ): List<AppUpdateInfo> {
        val byPackage = LinkedHashMap<String, AppUpdateInfo>()
        apkMirrorUpdates.forEach { byPackage[it.packageName] = it }

        playUpdates.forEach { (packageName, playApp) ->
            val update = byPackage[packageName]
            byPackage[packageName] = update?.copy(
                newVersionName = playApp.versionName.ifBlank { update.newVersionName },
                newVersionCode = playApp.versionCode,
                playVersionCode = playApp.versionCode
            ) ?: AppUpdateInfo(
                packageName = packageName,
                appName = label(packageName),
                newVersionName = playApp.versionName,
                newVersionCode = playApp.versionCode,
                publishedAt = null,
                playVersionCode = playApp.versionCode,
                apkMirrorUrl = null
            )
        }

        return byPackage.values.sortedWith(NEWEST_FIRST)
    }

    private fun parseUpdates(
        apps: List<ApkMirrorApp>,
        installedBatch: List<InstalledApp>
    ): List<AppUpdateInfo> {
        val installedByPackage = installedBatch.associateBy(InstalledApp::packageName)

        return apps.mapNotNull { app ->
            val installed = installedByPackage[app.packageName] ?: return@mapNotNull null
            if (!isStableRelease(app.versionName)) return@mapNotNull null

            val apk = bestApk(app.apks, installed) ?: return@mapNotNull null

            AppUpdateInfo(
                packageName = installed.packageName,
                appName = label(installed.packageName),
                newVersionName = app.versionName,
                newVersionCode = apk.versionCode,
                publishedAt = publishedAt(apk.publishDate.ifBlank { app.publishDate }),
                playVersionCode = null,
                apkMirrorUrl = apk.link.toAbsoluteApkMirrorUrl()
            )
        }
    }

    private fun bestApk(apks: List<ApkMirrorApk>, installed: InstalledApp): ApkMirrorApk? = apks
        .asSequence()
        .filter { it.versionCode > installed.versionCode }
        .filter { it.minimumApi <= Build.VERSION.SDK_INT }
        .filter(::matchesFormFactor)
        .filter { matchesSignature(it, installed) }
        .filter { abiRank(it) != UNSUPPORTED_ABI }
        .minWithOrNull(
            compareBy<ApkMirrorApk> { abiRank(it) }
                .thenBy(::densityRank)
                .thenByDescending(ApkMirrorApk::minimumApi)
                .thenByDescending(ApkMirrorApk::versionCode)
        )

    private fun abiRank(apk: ApkMirrorApk): Int {
        if (apk.architectures.isEmpty()) return universalAbiRank
        if (apk.architectures.any { it in UNIVERSAL_ARCHITECTURES }) return universalAbiRank
        return apk.architectures.minOf(::abiIndex)
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

    private fun densityRank(apk: ApkMirrorApk): Int {
        if (apk.densities.any { it in UNIVERSAL_DENSITIES }) return UNIVERSAL_DENSITY_RANK

        val buckets = apk.densities.mapNotNull(::densityBucket)
        return when {
            buckets.isEmpty() -> UNIVERSAL_DENSITY_RANK
            deviceDensityBucket in buckets -> MATCHING_DENSITY_RANK
            else -> FOREIGN_DENSITY_RANK
        }
    }

    private fun densityBucket(density: String): Int? =
        DENSITY_ALIASES[density] ?: density.removeSuffix("dpi").toIntOrNull()

    private fun matchesFormFactor(apk: ApkMirrorApk): Boolean {
        if (apk.capabilities.contains("wear_standalone")) return false
        return if (isAndroidTv) {
            apk.capabilities.contains("leanback_standalone") || apk.capabilities.contains("leanback")
        } else {
            !apk.capabilities.contains("leanback_standalone")
        }
    }

    private fun matchesSignature(apk: ApkMirrorApk, installed: InstalledApp): Boolean = when {
        apk.signatureSha256s.isNotEmpty() && installed.signatureSha256s.isNotEmpty() ->
            apk.signatureSha256s.any { it in installed.signatureSha256s }
        apk.signatureSha1s.isNotEmpty() && installed.signatureSha1s.isNotEmpty() ->
            apk.signatureSha1s.any { it in installed.signatureSha1s }
        else -> true
    }

    private fun label(packageName: String): String = runCatching {
        packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            .loadLabel(packageManager)
            .toString()
            .trim()
    }.getOrNull()?.ifEmpty { null } ?: packageName

    private fun publishedAt(value: String): Long? {
        val text = value.trim()
        if (text.isEmpty()) return null

        for (format in PUBLISH_DATE_FORMATS) {
            val parsed = runCatching { format.parse(text) }.getOrNull() ?: continue
            val instant = runCatching { Instant.from(parsed) }
                .recoverCatching { LocalDateTime.from(parsed).toInstant(ZoneOffset.UTC) }
                .recoverCatching { LocalDate.from(parsed).atStartOfDay(ZoneOffset.UTC).toInstant() }
                .getOrNull()
            if (instant != null) return instant.toEpochMilli()
        }

        return null
    }

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

    private fun String.toAbsoluteApkMirrorUrl(): String = when {
        startsWith("https://") || startsWith("http://") -> this
        startsWith("/") -> "https://www.apkmirror.com$this"
        else -> "https://www.apkmirror.com/$this"
    }

    private companion object {
        const val API_BATCH_SIZE = 100
        val NEWEST_FIRST = compareByDescending<AppUpdateInfo> { it.publishedAt ?: Long.MIN_VALUE }
            .thenBy { it.appName.lowercase(Locale.ROOT) }
        val PUBLISH_DATE_FORMATS = listOf(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
        )
        const val UNSUPPORTED_ABI = Int.MAX_VALUE
        const val MATCHING_DENSITY_RANK = 0
        const val UNIVERSAL_DENSITY_RANK = 1
        const val FOREIGN_DENSITY_RANK = 2
        val PACKAGE_FLAGS: PackageManager.PackageInfoFlags = PackageManager.PackageInfoFlags.of(
            (PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()
        )
        val UNIVERSAL_ARCHITECTURES = setOf("universal", "noarch")
        val UNIVERSAL_DENSITIES = setOf("nodpi", "anydpi", "universal")
        val DENSITY_BUCKETS = listOf(120, 160, 213, 240, 320, 480, 640)
        val DENSITY_ALIASES = mapOf(
            "ldpi" to 120,
            "mdpi" to 160,
            "tvdpi" to 213,
            "hdpi" to 240,
            "xhdpi" to 320,
            "xxhdpi" to 480,
            "xxxhdpi" to 640
        )
        val PRE_RELEASE_MARKER_PATTERN =
            Regex("(?:^|[^a-z])(alpha|beta|preview|canary|rc|release[-_ ]candidate|pre[-_ ]?release|prerelease|nightly|snapshot|debug|development|dev)(?:[^a-z]|$)", RegexOption.IGNORE_CASE)
    }
}
