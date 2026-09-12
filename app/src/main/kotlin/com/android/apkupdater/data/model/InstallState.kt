package com.android.apkupdater.data.model

sealed interface InstallState {
    val appName: String

    data class Preparing(override val appName: String) : InstallState
    data class Downloading(override val appName: String, val progress: Float) : InstallState
    data class Installing(override val appName: String) : InstallState
    data class Success(override val appName: String) : InstallState
    data class Error(override val appName: String, val message: String) : InstallState
}
