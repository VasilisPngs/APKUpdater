package com.android.gupdater.data.installer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import kotlinx.coroutines.CompletableDeferred
import java.io.File

class PackageInstallerManager(private val context: Context) {

    suspend fun install(apkFiles: List<File>): Result<Unit> {
        if (apkFiles.isEmpty()) return Result.failure(IllegalArgumentException("No APK files to install."))

        val installer = context.getPackageManager().getPackageInstaller()
        val totalSize = apkFiles.sumOf { it.length() }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(totalSize)
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
                            ?: "Installation failed"
                        result.complete(Result.failure(IllegalStateException(message)))
                    }
                }
            }
        }

        context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        return try {
            apkFiles
                .sortedWith(compareByDescending<File> { it.name.equals("base.apk", ignoreCase = true) }.thenBy { it.name })
                .forEach { file ->
                    file.inputStream().use { input ->
                        session.openWrite(file.name, 0, file.length()).use { output ->
                            input.copyTo(output)
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
        } catch (exception: Exception) {
            runCatching { session.abandon() }
            Result.failure(exception)
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
}
