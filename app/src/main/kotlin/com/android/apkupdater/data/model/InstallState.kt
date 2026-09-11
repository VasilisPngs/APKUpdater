package com.android.apkupdater.data.model

sealed interface InstallState {
    data object Idle : InstallState
    data class Preparing(val appName: String) : InstallState
    data class Downloading(val appName: String) : InstallState
    data class Installing(val appName: String, val dependency: Boolean) : InstallState
    data class Success(val appName: String) : InstallState
    data class Error(val appName: String, val message: String) : InstallState
}
