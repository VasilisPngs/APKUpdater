package com.android.gupdater.data.installer

import android.content.Context
import com.android.gupdater.data.model.AppUpdateInfo
import com.android.gupdater.data.model.InstallState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class ApkMirrorInstaller(private val context: Context) {
    private val packageInstaller = PackageInstallerManager(context)
    private val httpClient = OkHttpClient()

    suspend fun install(
        update: AppUpdateInfo,
        onState: (InstallState) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "apkmirror_${update.packageName}_${update.newVersionCode}")
        try {
            onState(InstallState.Preparing(update.appName))
            directory.deleteRecursively()
            directory.mkdirs()

            onState(InstallState.Downloading(update.appName))
            val target = File(directory, "${update.packageName}.apk")
            val request = Request.Builder().url(update.apkMirrorUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("APKMirror download failed (${response.code}).")
                }
                val body = response.body ?: throw IllegalStateException("APKMirror returned an empty file.")
                target.outputStream().use { output -> body.byteStream().copyTo(output) }
            }

            onState(InstallState.Installing(update.appName, false))
            packageInstaller.install(listOf(target)).getOrThrow()
            onState(InstallState.Success(update.appName))
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            onState(InstallState.Error(update.appName, exception.message ?: "Installation failed"))
            Result.failure(exception)
        } finally {
            directory.deleteRecursively()
        }
    }
}
