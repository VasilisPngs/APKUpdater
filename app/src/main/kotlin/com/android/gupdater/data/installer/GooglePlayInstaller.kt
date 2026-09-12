package com.android.gupdater.data.installer

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import com.android.gupdater.data.api.SharedHttpClient
import com.android.gupdater.data.model.InstallState
import com.android.gupdater.data.model.InstalledApp
import com.android.gupdater.data.play.PlayAuthProvider
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayFile
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

class GooglePlayInstaller(private val context: Context) {

    private val packageInstaller = PackageInstallerManager(context)
    private val authProvider = PlayAuthProvider(context)

    suspend fun install(
        app: InstalledApp,
        versionCode: Long,
        onState: (InstallState) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "play_${app.packageName}_$versionCode")
        try {
            require(versionCode > app.versionCode) {
                "The requested version must be newer than the installed version."
            }

            onState(InstallState.Preparing(app.appName))
            directory.deleteRecursively()
            directory.mkdirs()

            val session = openSession(app.packageName)
            val files = purchase(session, app, versionCode)

            val apkFiles = download(app.appName, files, directory, onState)
            installDependencies(session, app.appName, directory, onState)

            onState(InstallState.Installing(app.appName, false))
            packageInstaller.install(apkFiles.map(ApkSource::of)).getOrThrow()
            onState(InstallState.Success(app.appName))
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            onState(InstallState.Error(app.appName, exception.message ?: "Installation failed"))
            Result.failure(exception)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun openSession(packageName: String): PlaySession {
        val cached = authProvider.current()
        if (cached != null) {
            val details = runCatching { AppDetailsHelper(cached).getAppByPackageName(packageName) }
            details.getOrNull()?.let { return PlaySession(cached, it) }
            authProvider.invalidate()
        }
        val authData = authProvider.create()
        return PlaySession(authData, AppDetailsHelper(authData).getAppByPackageName(packageName))
    }

    private fun purchase(session: PlaySession, app: InstalledApp, versionCode: Long): List<PlayFile> = try {
        purchase(session, app.packageName, versionCode, session.details.offerType)
    } catch (exception: Exception) {
        val playVersionCode = session.details.versionCode
        if (playVersionCode == versionCode || playVersionCode <= app.versionCode) throw exception
        purchase(session, app.packageName, playVersionCode, session.details.offerType)
    }

    private fun purchase(
        session: PlaySession,
        packageName: String,
        versionCode: Long,
        offerType: Int
    ): List<PlayFile> {
        val files = PurchaseHelper(session.authData).purchase(
            packageName = packageName,
            versionCode = versionCode,
            offerType = offerType,
            certificateHash = certificateHash(packageName)
        ).filter { it.type == PlayFile.Type.BASE || it.type == PlayFile.Type.SPLIT }

        require(files.isNotEmpty()) { "Google Play returned no installable APK files." }
        return files
    }

    private fun download(
        appName: String,
        files: List<PlayFile>,
        directory: File,
        onState: (InstallState) -> Unit
    ): List<File> {
        val totalBytes = files.sumOf { it.size }.coerceAtLeast(1)
        var writtenBytes = 0L
        var reportedProgress = -1

        return files.mapIndexed { index, playFile ->
            val target = File(directory, playFile.name.ifBlank { "split_$index.apk" })
            val request = Request.Builder().url(playFile.url).build()
            SharedHttpClient.instance.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Google Play download failed (${response.code}).")
                }
                response.body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            writtenBytes += read
                            val progress = (writtenBytes * 100 / totalBytes).toInt()
                            if (progress != reportedProgress) {
                                reportedProgress = progress
                                onState(InstallState.Downloading(appName, progress / 100f))
                            }
                        }
                    }
                }
            }
            target
        }
    }

    private suspend fun installDependencies(
        session: PlaySession,
        appName: String,
        directory: File,
        onState: (InstallState) -> Unit
    ) {
        for (library in session.details.dependencies.dependentLibraries) {
            if (library.packageName.isBlank() || library.versionCode <= 0L) continue
            if (isUpToDate(library)) continue

            onState(InstallState.Preparing(appName))
            val libraryDetails = AppDetailsHelper(session.authData)
                .getAppByPackageName(library.packageName)
            val files = purchase(
                session = session,
                packageName = library.packageName,
                versionCode = library.versionCode,
                offerType = libraryDetails.offerType
            )

            val libraryDirectory = File(directory, library.packageName).apply { mkdirs() }
            val apkFiles = download(appName, files, libraryDirectory, onState)
            onState(InstallState.Installing(appName, true))
            packageInstaller.install(apkFiles.map(ApkSource::of)).getOrThrow()
        }
    }

    private fun isUpToDate(library: App): Boolean {
        val installedVersion = runCatching {
            context.packageManager.getPackageInfo(library.packageName, 0).longVersionCode
        }.getOrNull() ?: return false
        return installedVersion >= library.versionCode
    }

    private fun certificateHash(packageName: String): String? = runCatching {
        val signingInfo = context.packageManager.getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        ).signingInfo ?: return null
        val certificates = if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        }
        val digest = MessageDigest.getInstance("SHA-1").digest(certificates.last().toByteArray())
        Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }.getOrNull()

    private class PlaySession(val authData: AuthData, val details: App)

    private companion object {
        const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
    }
}
