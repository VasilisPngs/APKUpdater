package com.android.gupdater.data.installer

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import com.android.gupdater.data.api.SharedHttpClient
import com.android.gupdater.data.appLabel
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

class GooglePlayInstaller(
    private val context: Context,
    private val authProvider: PlayAuthProvider
) {
    private val packageInstaller = PackageInstallerManager(context)

    suspend fun install(
        app: InstalledApp,
        versionCode: Long,
        onState: (InstallState) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val appName = context.packageManager.appLabel(app.packageName)
        val directory = File(context.cacheDir, "play_${app.packageName}_$versionCode")
        try {
            require(versionCode > app.versionCode) {
                "The requested version must be newer than the installed version."
            }

            onState(InstallState.Installing(appName))
            directory.deleteRecursively()
            directory.mkdirs()

            val session = openSession(app.packageName)
            val files = purchase(session, app.packageName, versionCode, session.details.offerType)

            val apkFiles = download(appName, files, directory, onState)
            installDependencies(session, appName, directory, onState)

            onState(InstallState.Installing(appName))
            packageInstaller.install(apkFiles.map(ApkSource::of)).getOrThrow()
            onState(InstallState.Success(appName))
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            onState(InstallState.Error(appName, exception.message ?: "Installation failed"))
            Result.failure(exception)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun openSession(packageName: String): PlaySession {
        val authData = authProvider.session()
        runCatching { AppDetailsHelper(authData).getAppByPackageName(packageName) }
            .getOrNull()
            ?.let { return PlaySession(authData, it) }

        val renewed = authProvider.renew(authData)
        return PlaySession(renewed, AppDetailsHelper(renewed).getAppByPackageName(packageName))
    }

    private fun purchase(
        session: PlaySession,
        packageName: String,
        versionCode: Long,
        offerType: Int
    ): List<PlayFile> {
        val files = try {
            PurchaseHelper(session.authData).purchase(
                packageName = packageName,
                versionCode = versionCode,
                offerType = offerType,
                certificateHash = certificateHash(packageName)
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw IllegalStateException(unavailable(packageName, versionCode), exception)
        }.filter { it.type == PlayFile.Type.BASE || it.type == PlayFile.Type.SPLIT }

        require(files.isNotEmpty()) { unavailable(packageName, versionCode) }
        return files
    }

    private fun unavailable(packageName: String, versionCode: Long): String =
        "Google Play has no version code $versionCode for $packageName."


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
                        val buffer = ByteArray(COPY_BUFFER_SIZE)
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
            if (isSharedLibraryInstalled(library.packageName, library.versionCode)) continue

            onState(InstallState.Installing(appName))
            val files = purchase(
                session = session,
                packageName = library.packageName,
                versionCode = library.versionCode,
                offerType = 0
            )

            val libraryDirectory = File(directory, library.packageName).apply { mkdirs() }
            val apkFiles = download(appName, files, libraryDirectory, onState)
            onState(InstallState.Installing(appName))
            packageInstaller.install(apkFiles.map(ApkSource::of)).getOrThrow()
        }
    }

    private fun isSharedLibraryInstalled(packageName: String, versionCode: Long): Boolean =
        context.packageManager
            .getSharedLibraries(PackageManager.PackageInfoFlags.of(0))
            .any { it.name == packageName && it.longVersion == versionCode }

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

}
