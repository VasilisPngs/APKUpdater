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
import kotlinx.coroutines.launch

class ApkUpdaterViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppUpdateRepository(application.applicationContext)
    private val preferences = AppPreferences(application.applicationContext)
    private val _scanStatus = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _updates = MutableStateFlow<List<AppUpdateInfo>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _includeSystemApps = MutableStateFlow(preferences.includeSystemApps)
    private val _includeDisabledApps = MutableStateFlow(preferences.includeDisabledApps)
    private var scanJob: Job? = null

    val scanStatus: StateFlow<ScanStatus> = _scanStatus.asStateFlow()
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()
    val updates: StateFlow<List<AppUpdateInfo>> = _updates.asStateFlow()
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val includeSystemApps: StateFlow<Boolean> = _includeSystemApps.asStateFlow()
    val includeDisabledApps: StateFlow<Boolean> = _includeDisabledApps.asStateFlow()

    init {
        loadInstalledApps(autoScan = true)
    }

    fun loadInstalledApps(autoScan: Boolean = false) {
        viewModelScope.launch {
            val apps = repository.getInstalledApps()
            _installedApps.value = apps
            if (autoScan) scanForUpdates()
        }
    }

    fun scanForUpdates() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val allApps = _installedApps.value.ifEmpty {
                repository.getInstalledApps().also { _installedApps.value = it }
            }
            val appsToCheck = allApps
                .let { apps ->
                    if (_includeSystemApps.value) apps else apps.filterNot(InstalledApp::isSystemApp)
                }
                .let { apps ->
                    if (_includeDisabledApps.value) apps else apps.filter(InstalledApp::isEnabled)
                }

            repository.scanForUpdates(appsToCheck).collect { status ->
                _scanStatus.value = status
                when (status) {
                    is ScanStatus.Success -> _updates.value = status.updates
                    is ScanStatus.Error -> _updates.value = status.partialUpdates
                    else -> Unit
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setIncludeSystemApps(include: Boolean) {
        if (_includeSystemApps.value == include) return
        _includeSystemApps.value = include
        preferences.includeSystemApps = include
        scanForUpdates()
    }

    fun setIncludeDisabledApps(include: Boolean) {
        if (_includeDisabledApps.value == include) return
        _includeDisabledApps.value = include
        preferences.includeDisabledApps = include
        scanForUpdates()
    }

    override fun onCleared() {
        scanJob?.cancel()
        super.onCleared()
    }
}
