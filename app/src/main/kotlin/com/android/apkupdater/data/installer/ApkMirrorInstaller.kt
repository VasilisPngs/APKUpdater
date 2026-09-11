package com.android.apkupdater.data.installer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import com.android.apkupdater.data.api.ApkMirrorPageService
import com.android.apkupdater.data.api.ApkMirrorService
import com.android.apkupdater.data.model.AppExistsRequest
import com.android.apkupdater.data.model.InstalledApp
import com.android.apkupdater.data.model.InstallState
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

class ApkMirrorInstaller(context: Context) {

    private val context = context.applicationContext
    private val pageService = ApkMirrorPageService.create()
    private val apiService = ApkMirrorService.create()
    private val packageInstaller = PackageInstallerManager(context)

    suspend fun install(
        app: InstalledApp,
        targetVersionCode: Long,
        releasePageUrl: String? = null,
        onState: (InstallState) -> Unit = {}
    ): Result<Unit> {
        if (targetVersionCode <= app.versionCode) {
            return Result.failure(IllegalArgumentException("The requested version is not newer than the installed version."))
        }

        return try {
            onState(InstallState.Preparing(app.appName))
            val mainPage = releasePageUrl?.takeIf(String::isNotBlank)
                ?: findVersionPage(app.packageName, targetVersionCode)
            val resolvedPackages = resolvePackageTree(mainPage, linkedSetOf())
            val mainPackage = resolvedPackages.lastOrNull()
                ?: throw IllegalStateException("Could not resolve the APKMirror package.")
            if (mainPackage.packageName != app.packageName || mainPackage.versionCode != targetVersionCode) {
                throw SecurityException("APKMirror returned a different package or version.")
            }

            onState(InstallState.Downloading(app.appName))
            val downloadedPackages = resolvedPackages.mapIndexed { index, resolved ->
                val alreadyInstalled = index < resolvedPackages.lastIndex &&
                    isInstalledAtVersionOrNewer(resolved.packageName, resolved.versionCode)
                if (alreadyInstalled) {
                    resolved.copy(files = emptyList())
                } else {
                    onState(InstallState.Downloading(app.appName))
                    val files = downloadPackage(resolved)
                    if (index == resolvedPackages.lastIndex) {
                        validateMainPackage(app, files.first(), targetVersionCode)
                    } else {
                        validateInstalledPackageSignature(resolved.packageName, files.first())
                    }
                    resolved.copy(files = files)
                }
            }

            downloadedPackages.dropLast(1).forEach { resolved ->
                if (resolved.files.isEmpty()) return@forEach
                onState(InstallState.Installing(app.appName, dependency = true))
                packageInstaller.install(resolved.files).getOrThrow()
            }

            val main = downloadedPackages.last()
            onState(InstallState.Installing(app.appName, dependency = false))
            packageInstaller.install(main.files).getOrThrow()
            onState(InstallState.Success(app.appName))
            Result.success(Unit)
        } catch (exception: Exception) {
            onState(InstallState.Error(app.appName, exception.message ?: "Installation failed"))
            Result.failure(exception)
        }
    }

    private suspend fun resolvePackageTree(
        releasePageUrl: String,
        visiting: MutableSet<String>
    ): List<ResolvedPackage> {
        val metadata = loadMetadata(releasePageUrl)
        if (!visiting.add(metadata.packageName)) return emptyList()

        val dependencies = metadata.dependencies.flatMap { dependencyUrl ->
            resolvePackageTree(dependencyUrl, visiting)
        }
        visiting.remove(metadata.packageName)
        return dependencies.distinctBy(ResolvedPackage::packageName) + ResolvedPackage(
            packageName = metadata.packageName,
            versionCode = metadata.versionCode,
            pageUrl = releasePageUrl,
            files = emptyList()
        )
    }

    private suspend fun downloadPackage(packageInfo: ResolvedPackage): List<File> {
        val page = pageService.getReleasePage(packageInfo.pageUrl).use { it.string() }
        val downloadUrl = findDownloadUrl(page, packageInfo.pageUrl)
        val downloadFile = File(context.cacheDir, "apk_${packageInfo.packageName}_${packageInfo.versionCode}")
        pageService.download(downloadUrl).use { body ->
            FileOutputStream(downloadFile).use { output -> body.byteStream().copyTo(output) }
        }

        if (!isZip(downloadFile)) {
            downloadFile.delete()
            throw IllegalStateException("APKMirror did not return an APK download.")
        }

        return extractApks(downloadFile, packageInfo)
    }

    private fun extractApks(download: File, packageInfo: ResolvedPackage): List<File> {
        val extractedDir = File(context.cacheDir, "apk_${packageInfo.packageName}_${packageInfo.versionCode}").apply {
            deleteRecursively()
            mkdirs()
        }

        val entries = mutableListOf<File>()
        ZipFile(download).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
                .forEachIndexed { index, entry ->
                    val sourceName = entry.name.substringAfterLast('/')
                    val name = if (sourceName.equals("base.apk", true)) {
                        "base.apk"
                    } else {
                        "split_${index}_${sourceName.replace(Regex("[^A-Za-z0-9._-]"), "_")}"
                    }
                    val output = File(extractedDir, name)
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(output).use { out -> input.copyTo(out) }
                    }
                    entries += output
                }
        }

        if (entries.isNotEmpty()) {
            download.delete()
            return entries.sortedWith(compareByDescending<File> { it.name.equals("base.apk", true) }.thenBy(File::name))
        }

        val apk = File(extractedDir, "base.apk")
        download.copyTo(apk, overwrite = true)
        download.delete()
        return listOf(apk)
    }

    private suspend fun findVersionPage(packageName: String, versionCode: Long): String {
        val response = apiService.appExists(AppExistsRequest(listOf(packageName)))
        response.data.asSequence()
            .flatMap { it.apks.asSequence() }
            .firstOrNull { it.versionCode == versionCode }
            ?.link
            ?.takeIf(String::isNotBlank)
            ?.let { return it }

        val searchUrl = "https://www.apkmirror.com/?post_type=app_release&searchtype=app&s=" +
            Uri.encode("$packageName $versionCode")
        val searchPage = pageService.getReleasePage(searchUrl).use { it.string() }
        val candidates = Regex(
            "href=[\\\"](https://www\\.apkmirror\\.com/apk/[^\\\"]+/[^\\\"]+android-apk-download/)[\\\"]",
            RegexOption.IGNORE_CASE
        ).findAll(searchPage).map { it.groupValues[1] }.distinct().take(20)

        for (candidate in candidates) {
            val metadata = loadMetadata(candidate)
            if (metadata.packageName == packageName && metadata.versionCode == versionCode) return candidate
        }
        throw IllegalStateException("Version code $versionCode was not found on APKMirror.")
    }

    private suspend fun loadMetadata(url: String): PageMetadata {
        val html = pageService.getReleasePage(url).use { it.string() }
        val packageName = Regex("Package:\\s*([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
            ?: throw IllegalStateException("Could not read the package name from APKMirror.")
        val versionCode = Regex("Version:\\s*[^<\\n(]+\\((\\d+)\\)", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.toLongOrNull()
            ?: throw IllegalStateException("Could not read the version code from APKMirror.")
        val dependencies = Regex(
            "This APK requires a library:\\s*<a[^>]+href=[\\\"]([^\\\"]+)[\\\"]",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(html).map { resolveUrl(url, it.groupValues[1]) }.distinct().toList()
        return PageMetadata(packageName, versionCode, dependencies)
    }

    private fun findDownloadUrl(html: String, pageUrl: String): String {
        val href = Regex(
            "href=[\\\"]([^\\\"]*?/download/\\?key=[^\\\"]+)[\\\"]",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.get(1)
            ?: throw IllegalStateException("APKMirror's automatic download link is unavailable.")
        return resolveUrl(pageUrl, href)
    }

    private fun validateMainPackage(app: InstalledApp, apkFile: File, targetVersionCode: Long) {
        val packageInfo = readArchivePackageInfo(apkFile)
        if (packageInfo.packageName != app.packageName || packageInfo.longVersionCode != targetVersionCode) {
            throw SecurityException("The downloaded APK does not match the requested app version.")
        }
        val certificate = packageInfo.signingInfo.apkContentsSigners.firstOrNull()
            ?: throw SecurityException("The downloaded APK has no signing certificate.")
        if (sha1(certificate.toByteArray()) != app.signatureSha1) {
            throw SecurityException("The downloaded APK is signed with a different certificate.")
        }
    }

    private fun validateInstalledPackageSignature(packageName: String, apkFile: File) {
        val installed = runCatching {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }.getOrNull() ?: return
        val archive = readArchivePackageInfo(apkFile)
        val installedSignatures = installed.signingInfo.apkContentsSigners.map { it.toByteArray().toList() }.toSet()
        val archiveSignatures = archive.signingInfo.apkContentsSigners.map { it.toByteArray().toList() }.toSet()
        if (installedSignatures.intersect(archiveSignatures).isEmpty()) {
            throw SecurityException("The dependency is signed differently from the installed package.")
        }
    }

    private fun readArchivePackageInfo(file: File): PackageInfo =
        context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw SecurityException("The downloaded APK is invalid.")

    private fun isInstalledAtVersionOrNewer(packageName: String, requiredVersionCode: Long): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(packageName, 0).longVersionCode >= requiredVersionCode
        }.getOrDefault(false)

    private fun resolveUrl(baseUrl: String, url: String): String =
        if (url.startsWith("http://") || url.startsWith("https://")) url
        else "https://www.apkmirror.com" + if (url.startsWith("/")) url else "/$url"

    private fun isZip(file: File): Boolean =
        file.inputStream().use { input -> input.read() == 0x50 && input.read() == 0x4b }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private data class PageMetadata(
        val packageName: String,
        val versionCode: Long,
        val dependencies: List<String>
    )

    private data class ResolvedPackage(
        val packageName: String,
        val versionCode: Long,
        val pageUrl: String,
        val files: List<File>
    )
}
