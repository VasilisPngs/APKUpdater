package com.android.apkupdater.data.installer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PackageInstallerManager(private val context: Context) {

    suspend fun install(sources: List<ApkSource>): Result<Unit> = sessionLock.withLock {
        if (sources.isEmpty()) return Result.failure(IllegalArgumentException("No APK files to install."))

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(sources.sumOf { it.size.coerceAtLeast(0) })
        }
        val sessionId = try {
            installer.createSession(params)
        } catch (exception: Exception) {
            return Result.failure(exception)
        }
        val session = try {
            installer.openSession(sessionId)
        } catch (exception: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            return Result.failure(exception)
        }

        val result = CompletableDeferred<Result<Unit>>()
        val action = "${context.packageName}.PACKAGE_INSTALL_$sessionId"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                    PackageInstaller.STATUS_SUCCESS -> result.complete(Result.success(Unit))
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        if (confirmation != null) {
                            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            receiverContext.startActivity(confirmation)
                        }
                    }
                    else -> {
                        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        result.complete(Result.failure(IllegalStateException(failureMessage(message))))
                    }
                }
            }
        }

        context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        return try {
            sources.forEach { source ->
                source.openStream().use { input ->
                    session.openWrite(source.name, 0, source.size).use { output ->
                        input.copyTo(output, COPY_BUFFER_SIZE)
                        session.fsync(output)
                    }
                }
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(action).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pendingIntent.intentSender)
            session.close()
            result.await()
        } catch (exception: CancellationException) {
            runCatching { session.abandon() }
            throw exception
        } catch (exception: Exception) {
            runCatching { session.abandon() }
            Result.failure(exception)
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private fun failureMessage(message: String?): String {
        val library = message?.let { MISSING_LIBRARY_PATTERN.find(it)?.groupValues?.get(1) }
        return when {
            library != null -> "Requires the shared library $library, install it first."
            !message.isNullOrBlank() -> message
            else -> "Installation failed"
        }
    }

    private companion object {
        val sessionLock = Mutex()
        val MISSING_LIBRARY_PATTERN = Regex("shared library ([^\\s;]+)")
    }
}
