package com.android.apkupdater.data.installer

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
        require(apkFiles.isNotEmpty())

        val packageInstaller = context.packageManager.packageInstaller
        val totalSize = apkFiles.sumOf(File::length)
        val sessionParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(totalSize)
        }
        val sessionId = packageInstaller.createSession(sessionParams)
        val session = packageInstaller.openSession(sessionId)
        val result = CompletableDeferred<Result<Unit>>()
        val action = "${context.packageName}.PACKAGE_INSTALL_$sessionId"

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                    PackageInstaller.STATUS_SUCCESS -> result.complete(Result.success(Unit))
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)?.let {
                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            receiverContext.startActivity(it)
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
        try {
            apkFiles.sortedWith(compareByDescending<File> { it.name.equals("base.apk", true) }.thenBy(File::name))
                .forEach { file ->
                    file.inputStream().use { input ->
                        session.openWrite(file.name, 0, file.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                }

            val intent = Intent(action).setPackage(context.packageName)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pendingIntent.intentSender)
            session.close()
            return result.await()
        } catch (exception: Exception) {
            runCatching { session.abandon() }
            Result.failure(exception)
        } finally {
            context.unregisterReceiver(receiver)
        }
    }
}
