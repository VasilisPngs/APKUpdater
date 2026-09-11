package com.android.apkupdater.data.installer

import android.content.Context
import com.android.apkupdater.data.model.InstalledApp
import com.android.apkupdater.data.model.InstallState
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayFile
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class GooglePlayInstaller(context: Context) {
    private val context = context.applicationContext
    private val tokenProvider = GoogleAccountTokenProvider(this.context)
    private val httpClient = OkHttpClient()
    private val packageInstaller = PackageInstallerManager(this.context)

    suspend fun install(
        app: InstalledApp,
        versionCode: Long,
        accountEmail: String,
        onState: (InstallState) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(versionCode > app.versionCode) {
                "The requested version is not newer than the installed version."
            }
            onState(InstallState.Preparing(app.appName))

            val playAuthToken = tokenProvider.fetchPlayAuthToken(accountEmail)
            val authData = AuthHelper.build(
                email = accountEmail,
                token = playAuthToken,
                tokenType = AuthHelper.Token.AAS
            )
            installPackage(
                authData = authData,
                packageName = app.packageName,
                versionCode = versionCode,
                displayName = app.appName,
                installingDependency = false,
                visiting = linkedSetOf(),
                onState = onState
            )
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = {
                onState(InstallState.Error(app.appName, it.message ?: "Installation failed."))
                Result.failure(it)
            }
        )
    }

    private fun installPackage(
        authData: AuthData,
        packageName: String,
        versionCode: Long,
        displayName: String,
        installingDependency: Boolean,
        visiting: MutableSet<String>,
        onState: (InstallState) -> Unit
    ) {
        if (!visiting.add(packageName)) {
            throw IllegalStateException("Circular Google Play dependency detected for $packageName.")
        }

        val app = AppDetailsHelper.with(authData).getAppByPackageName(packageName)
        app.dependencies.dependentLibraries.forEach { dependency ->
            val installedVersion = runCatching {
                context.packageManager.getPackageInfo(dependency.packageName, 0).longVersionCode
            }.getOrDefault(0L)
            if (installedVersion < dependency.versionCode) {
                installPackage(
                    authData = authData,
                    packageName = dependency.packageName,
                    versionCode = dependency.versionCode,
                    displayName = dependency.displayName,
                    installingDependency = true,
                    visiting = visiting,
                    onState = onState
                )
            }
        }

        onState(InstallState.Downloading(displayName))
        val playFiles = PurchaseHelper.with(authData)
            .purchase(packageName, versionCode.toInt(), app.offerType)
            .filter { it.url.isNotBlank() && it.name.endsWith(".apk", ignoreCase = true) }

        if (playFiles.isEmpty()) {
            throw IllegalStateException("Google Play returned no APK files for $packageName.")
        }

        val apkFiles = playFiles.map { download(it, packageName, versionCode) }
        onState(InstallState.Installing(displayName, installingDependency))
        packageInstaller.install(packageName, apkFiles).getOrThrow()
        visiting.remove(packageName)
    }

    private fun download(playFile: PlayFile, packageName: String, versionCode: Long): File {
        val safeName = playFile.name.substringAfterLast('/').ifBlank { "base.apk" }
        val directory = File(context.cacheDir, "google-play/$packageName/$versionCode").apply { mkdirs() }
        val output = File(directory, safeName)
        val request = Request.Builder().url(playFile.url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Google Play download failed (${response.code}).")
            }
            val body = response.body ?: throw IllegalStateException("Google Play returned an empty download.")
            body.byteStream().use { input -> output.outputStream().use { input.copyTo(it) } }
        }
        return output
    }
}
