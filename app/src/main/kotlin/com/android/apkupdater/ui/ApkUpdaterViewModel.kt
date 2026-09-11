package com.android.apkupdater.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.apkupdater.data.model.AppUpdateInfo
import com.android.apkupdater.data.model.InstalledApp
import com.android.apkupdater.data.preferences.AppPreferences
import com.android.apkupdater.data.repository.AppUpdateRepository
import com.android.apkupdater.data.repository.ScanStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdaterUiState(
    val scanStatus: ScanStatus = ScanStatus.Idle,
    val installedApps: List<InstalledApp> = emptyList(),
    val updates: List<AppUpdateInfo> = emptyList(),
    val searchQuery: String = "",
    val includeSystemApps: Boolean = false,
    val includeDisabledApps: Boolean = false
)

class ApkUpdaterViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppUpdateRepository(application.applicationContext)
    private val preferences = AppPreferences(application.applicationContext)
    private val _uiState = MutableStateFlow(
        UpdaterUiState(
            includeSystemApps = preferences.includeSystemApps,
            includeDisabledApps = preferences.includeDisabledApps
        )
    )
    private var scanJob: Job? = null

    val uiState: StateFlow<UpdaterUiState> = _uiState.asStateFlow()

    init {
        refreshInstalledAppsAndScan()
    }

    fun refreshInstalledAppsAndScan() {
        viewModelScope.launch {
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
            val appsToCheck = allApps
                .filter { state.includeSystemApps || !it.isSystemApp }
                .filter { state.includeDisabledApps || it.isEnabled }

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

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setIncludeSystemApps(include: Boolean) {
        if (_uiState.value.includeSystemApps == include) return
        preferences.includeSystemApps = include
        _uiState.update { it.copy(includeSystemApps = include) }
        scanForUpdates()
    }

    fun setIncludeDisabledApps(include: Boolean) {
        if (_uiState.value.includeDisabledApps == include) return
        preferences.includeDisabledApps = include
        _uiState.update { it.copy(includeDisabledApps = include) }
        scanForUpdates()
    }

    override fun onCleared() {
        scanJob?.cancel()
        super.onCleared()
    }
}
