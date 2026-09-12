package com.android.gupdater.data.installer

import android.content.Context
import com.android.gupdater.data.model.InstalledApp
import com.android.gupdater.data.model.InstallState
import com.aurora.gplayapi.data.models.PlayFile
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.Method
import okhttp3.OkHttpClient
import okhttp3.Request

class GooglePlayInstaller(private val context: Context) {
    private val packageInstaller = PackageInstallerManager(context)
    private val httpClient = OkHttpClient()

    suspend fun install(
        app: InstalledApp,
        versionCode: Long,
        email: String,
        aasToken: String,
        onState: (InstallState) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            require(email.isNotBlank()) { "Google Play email is required." }
            require(aasToken.isNotBlank()) { "Google Play AAS token is required." }

            onState(InstallState.Preparing(app.appName))
            val authData = AuthHelper.build(
                email = email.trim(),
                token = aasToken.trim(),
                tokenType = AuthHelper.Token.AAS
            )
            val details = AppDetailsHelper(authData).getAppByPackageName(app.packageName)

            if (versionCode <= app.versionCode) {
                throw IllegalArgumentException("The requested version must be newer than the installed version.")
            }

            val files = PurchaseHelper(authData).purchase(
                packageName = app.packageName,
                versionCode = versionCode,
                offerType = details.offerType
            )

            val apkFiles = downloadFiles(app.packageName, versionCode, files, onState)
            installDependencies(authData, details, app.appName, onState)

            onState(InstallState.Installing(app.appName, false))
            packageInstaller.install(apkFiles).getOrThrow()
            apkFiles.forEach(File::delete)
            onState(InstallState.Success(app.appName))
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            onState(InstallState.Error(app.appName, exception.message ?: "Installation failed"))
            Result.failure(exception)
        }
    }

    private fun downloadFiles(
        packageName: String,
        versionCode: Long,
        files: List<PlayFile>,
        onState: (InstallState) -> Unit
    ): List<File> {
        val apkFiles = files.filter { it.type == PlayFile.Type.BASE || it.type == PlayFile.Type.SPLIT }
        require(apkFiles.isNotEmpty()) { "Google Play returned no installable APK files." }

        val directory = File(context.cacheDir, "play_${packageName}_$versionCode").apply {
            deleteRecursively()
            mkdirs()
        }
        return apkFiles.mapIndexed { index, playFile ->
            onState(InstallState.Downloading(packageName))
            val target = File(directory, if (playFile.name.isBlank()) "apk_$index.apk" else playFile.name)
            val request = Request.Builder().url(playFile.url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Google Play download failed (${response.code}).")
                }
                val body = response.body ?: throw IllegalStateException("Google Play returned an empty file.")
                target.outputStream().use { output -> body.byteStream().copyTo(output) }
            }
            target
        }
    }

    private suspend fun installDependencies(
        authData: com.aurora.gplayapi.data.models.AuthData,
        appDetails: Any,
        appName: String,
        onState: (InstallState) -> Unit
    ) {
        val dependencyEntries = extractDependentLibraries(appDetails)
        for ((packageName, versionCode) in dependencyEntries) {
            if (packageName.isBlank() || versionCode <= 0L) continue

            val installedVersion = runCatching {
                context.packageManager.getPackageInfo(packageName, 0).longVersionCode
            }.getOrNull()
            if (installedVersion != null && installedVersion >= versionCode) continue

            onState(InstallState.Preparing(appName))
            val dependencyDetails = AppDetailsHelper(authData).getAppByPackageName(packageName)
            val dependencyFiles = PurchaseHelper(authData).purchase(
                packageName = packageName,
                versionCode = versionCode,
                offerType = dependencyDetails.offerType
            ).filter { it.type == PlayFile.Type.BASE || it.type == PlayFile.Type.SPLIT }

            val downloaded = downloadFiles(packageName, versionCode, dependencyFiles, onState)
            onState(InstallState.Installing(appName, true))
            packageInstaller.install(downloaded).getOrThrow()
            downloaded.forEach(File::delete)
        }
    }

    private fun extractDependentLibraries(appDetails: Any): List<Pair<String, Long>> {
        val dependencies = readProperty(appDetails, "dependencies") ?: return emptyList()
        val libraries = (readProperty(dependencies, "dependentLibraries") as? Iterable<*>)
            ?: return emptyList()
        return libraries.filterNotNull().mapNotNull { library ->
            val packageName = readProperty(library, "packageName") as? String
            val versionCode = when (val value = readProperty(library, "versionCode")) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            }
            if (packageName != null && versionCode != null) packageName to versionCode else null
        }
    }

    private fun readProperty(target: Any, name: String): Any? {
        val getterName = "get" + name.replaceFirstChar(Char::uppercaseChar)
        return runCatching {
            val getter = target.javaClass.methods.firstOrNull { method: Method ->
                method.name == getterName && method.parameterTypes.isEmpty()
            } ?: return@runCatching null
            getter.invoke(target)
        }.getOrNull()
    }
}
