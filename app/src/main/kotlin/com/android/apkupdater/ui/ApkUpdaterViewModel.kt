package com.android.apkupdater.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.apkupdater.data.installer.GooglePlayInstaller
import com.android.apkupdater.data.model.AppUpdateInfo
import com.android.apkupdater.data.model.InstalledApp
import com.android.apkupdater.data.model.InstallState
import com.android.apkupdater.data.preferences.AppPreferences
import com.android.apkupdater.data.repository.AppUpdateRepository
import com.android.apkupdater.data.repository.ScanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UpdaterUiState(
    val scanStatus: ScanStatus = ScanStatus.Idle,
    val installedApps: List<InstalledApp> = emptyList(),
    val updates: List<AppUpdateInfo> = emptyList(),
    val includeDisabledApps: Boolean = false,
    val installState: InstallState = InstallState.Idle
)

class ApkUpdaterViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppUpdateRepository(application.applicationContext)
    private val preferences = AppPreferences(application.applicationContext)
    private val installer = GooglePlayInstaller(application.applicationContext)
    private val _uiState = MutableStateFlow(
        UpdaterUiState(includeDisabledApps = preferences.includeDisabledApps)
    )
    private var scanJob: Job? = null
    private var installJob: Job? = null

    val uiState: StateFlow<UpdaterUiState> = _uiState.asStateFlow()

    init {
        refreshInstalledAppsAndScan()
    }

    fun refreshInstalledAppsAndScan() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = repository.getInstalledApps()
            _uiState.update { it.copy(installedApps = apps) }
            scanForUpdates()
        }
    }

    fun scanForUpdates() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val state = _uiState.value
            val allApps = state.installedApps.ifEmpty {
                repository.getInstalledApps().also { apps ->
                    _uiState.update { it.copy(installedApps = apps) }
                }
            }
            val appsToCheck = allApps.filter { state.includeDisabledApps || it.isEnabled }

            repository.scanForUpdates(appsToCheck).collect { status ->
                _uiState.update { current ->
                    when (status) {
                        is ScanStatus.Scanning -> current.copy(scanStatus = status)
                        is ScanStatus.Success -> current.copy(
                            scanStatus = status,
                            updates = status.updates
                        )
                        is ScanStatus.Error -> current.copy(
                            scanStatus = status,
                            updates = status.partialUpdates
                        )
                        ScanStatus.Idle -> current.copy(scanStatus = status)
                    }
                }
            }
        }
    }

    fun installManual(app: InstalledApp, versionCode: Long, email: String, aasToken: String) {
        if (installJob?.isActive == true) return
        scanJob?.cancel()
        installJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                installer.install(app, versionCode, email, aasToken) { state ->
                    _uiState.update { it.copy(installState = state) }
                }
            }
            if (result.isSuccess) {
                val apps = withContext(Dispatchers.IO) { repository.getInstalledApps() }
                _uiState.update { it.copy(installedApps = apps) }
                scanForUpdates()
            }
            installJob = null
        }
    }

    fun setIncludeDisabledApps(include: Boolean) {
        if (_uiState.value.includeDisabledApps == include) return
        preferences.includeDisabledApps = include
        _uiState.update { it.copy(includeDisabledApps = include) }
        scanForUpdates()
    }

    override fun onCleared() {
        scanJob?.cancel()
        installJob?.cancel()
        super.onCleared()
    }
}
