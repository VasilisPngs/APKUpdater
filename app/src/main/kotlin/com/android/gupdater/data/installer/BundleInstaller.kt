package com.android.gupdater.data.installer

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.android.gupdater.data.model.InstallState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

class BundleInstaller(private val context: Context) {

    private val packageInstaller = PackageInstallerManager(context)

    suspend fun install(uri: Uri, onState: (InstallState) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            val label = displayName(uri)
            val archive = File(context.cacheDir, "bundle_archive")
            try {
                onState(InstallState.Preparing(label))
                copyToCache(uri, archive)

                onState(InstallState.Installing(label, false))
                ZipFile(archive).use { zip ->
                    packageInstaller.install(sources(zip, archive)).getOrThrow()
                }
                onState(InstallState.Success(label))
                Result.success(Unit)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: ZipException) {
                val message = "This file is not an APK or an APK bundle."
                onState(InstallState.Error(label, message))
                Result.failure(IllegalArgumentException(message, exception))
            } catch (exception: Exception) {
                onState(InstallState.Error(label, exception.message ?: "Installation failed"))
                Result.failure(exception)
            } finally {
                archive.delete()
            }
        }

    private fun sources(zip: ZipFile, archive: File): List<ApkSource> {
        val apkEntries = zip.entries()
            .asSequence()
            .filter { !it.isDirectory && it.name.endsWith(APK_SUFFIX, ignoreCase = true) }
            .toList()

        if (apkEntries.isEmpty()) return listOf(ApkSource(BASE_APK, archive.length()) { archive.inputStream() })

        return selectSplits(apkEntries).map { entry ->
            ApkSource(entry.name.substringAfterLast('/'), entry.size) { zip.getInputStream(entry) }
        }
    }

    private fun selectSplits(entries: List<ZipEntry>): List<ZipEntry> {
        val required = mutableListOf<ZipEntry>()
        val abis = mutableMapOf<String, MutableList<ZipEntry>>()
        val densities = mutableMapOf<String, MutableList<ZipEntry>>()
        val languages = mutableMapOf<String, MutableList<ZipEntry>>()

        entries.forEach { entry ->
            val qualifier = configQualifier(entry.name)
            when {
                qualifier == null -> required += entry
                qualifier in ABIS -> abis.getOrPut(qualifier, ::mutableListOf) += entry
                qualifier in DENSITIES -> densities.getOrPut(qualifier, ::mutableListOf) += entry
                LANGUAGE_PATTERN.matches(qualifier) ->
                    languages.getOrPut(qualifier.substringBefore('_'), ::mutableListOf) += entry
                else -> required += entry
            }
        }

        return required + pickAbi(abis) + pickDensity(densities) + pickLanguages(languages)
    }

    private fun configQualifier(name: String): String? {
        val fileName = name.substringAfterLast('/').dropLast(APK_SUFFIX.length)
        val qualifier = when {
            fileName.startsWith(SPLIT_CONFIG_PREFIX) -> fileName.removePrefix(SPLIT_CONFIG_PREFIX)
            fileName.startsWith(CONFIG_PREFIX) -> fileName.removePrefix(CONFIG_PREFIX)
            fileName.contains('-') -> fileName.substringAfterLast('-')
            else -> return null
        }
        return qualifier.lowercase().replace('-', '_').takeIf { it != MASTER_SPLIT && it != NO_DENSITY }
    }

    private fun pickAbi(abis: Map<String, List<ZipEntry>>): List<ZipEntry> {
        if (abis.isEmpty()) return emptyList()
        val supported = Build.SUPPORTED_ABIS.map { it.lowercase().replace('-', '_') }
        return supported.firstNotNullOfOrNull { abis[it] } ?: emptyList()
    }

    private fun pickDensity(densities: Map<String, List<ZipEntry>>): List<ZipEntry> {
        if (densities.isEmpty()) return emptyList()
        val deviceDensity = context.resources.displayMetrics.densityDpi
        val available = densities.keys.mapNotNull { key -> DENSITIES[key]?.let { key to it } }
        val best = available.filter { it.second >= deviceDensity }.minByOrNull { it.second }
            ?: available.maxByOrNull { it.second }
            ?: return emptyList()
        return densities.getValue(best.first)
    }

    private fun pickLanguages(languages: Map<String, List<ZipEntry>>): List<ZipEntry> {
        if (languages.isEmpty()) return emptyList()
        val locales = context.resources.configuration.locales
        val deviceLanguages = (0 until locales.size())
            .map { locales.get(it).language.lowercase() }
            .toSet()
        val matching = languages.filterKeys { it in deviceLanguages }.values.flatten()
        return matching.ifEmpty { languages[FALLBACK_LANGUAGE] ?: languages.values.flatten() }
    }

    private fun copyToCache(uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("This file could not be opened.")
        input.use { source -> target.outputStream().use { output -> source.copyTo(output, COPY_BUFFER_SIZE) } }
    }

    private fun displayName(uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        val name = context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        return name ?: uri.lastPathSegment ?: "Package"
    }

    private companion object {
        const val APK_SUFFIX = ".apk"
        const val BASE_APK = "base.apk"
        const val SPLIT_CONFIG_PREFIX = "split_config."
        const val CONFIG_PREFIX = "config."
        const val MASTER_SPLIT = "master"
        const val NO_DENSITY = "nodpi"
        const val FALLBACK_LANGUAGE = "en"
        const val COPY_BUFFER_SIZE = 64 * 1024
        val ABIS = setOf("armeabi", "armeabi_v7a", "arm64_v8a", "x86", "x86_64", "riscv64")
        val DENSITIES = mapOf(
            "ldpi" to 120,
            "mdpi" to 160,
            "tvdpi" to 213,
            "hdpi" to 240,
            "xhdpi" to 320,
            "xxhdpi" to 480,
            "xxxhdpi" to 640
        )
        val LANGUAGE_PATTERN = Regex("[a-z]{2,3}(_[a-z0-9]{2,8})*")
    }
}
