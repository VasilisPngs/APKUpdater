package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppFilter
import com.example.data.model.AppUpdateInfo
import com.example.data.model.InstalledApp
import com.example.data.repository.AppUpdateRepository
import com.example.data.repository.ScanStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val scanStatus: ScanStatus = ScanStatus.Idle,
    val installedApps: List<InstalledApp> = emptyList(),
    val updates: List<AppUpdateInfo> = emptyList(),
    val selectedFilter: AppFilter = AppFilter.UPDATES_ONLY,
    val searchQuery: String = "",
    val includeSystemApps: Boolean = false,
    val ignoreAlpha: Boolean = true,
    val ignoreBeta: Boolean = true,
    val lastScanTime: Long? = null
)

class ApkUpdaterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppUpdateRepository(application.applicationContext)

    private val _scanStatus = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val scanStatus: StateFlow<ScanStatus> = _scanStatus.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _updates = MutableStateFlow<List<AppUpdateInfo>>(emptyList())
    val updates: StateFlow<List<AppUpdateInfo>> = _updates.asStateFlow()

    private val _selectedFilter = MutableStateFlow(AppFilter.UPDATES_ONLY)
    val selectedFilter: StateFlow<AppFilter> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _includeSystemApps = MutableStateFlow(false)
    val includeSystemApps: StateFlow<Boolean> = _includeSystemApps.asStateFlow()

    private val _onlyStable = MutableStateFlow(true)
    val onlyStable: StateFlow<Boolean> = _onlyStable.asStateFlow()

    private val _lastScanTime = MutableStateFlow<Long?>(null)
    val lastScanTime: StateFlow<Long?> = _lastScanTime.asStateFlow()

    init {
        loadInstalledApps(autoScan = true)
    }

    fun loadInstalledApps(autoScan: Boolean = false) {
        viewModelScope.launch {
            val apps = repository.getInstalledApps(includeSystem = true)
            _installedApps.value = apps
            if (autoScan) {
                scanForUpdates()
            }
        }
    }

    fun scanForUpdates() {
        viewModelScope.launch {
            val allApps = _installedApps.value.ifEmpty {
                val loaded = repository.getInstalledApps(includeSystem = true)
                _installedApps.value = loaded
                loaded
            }

            val appsToCheck = if (_includeSystemApps.value) {
                allApps
            } else {
                allApps.filter { !it.isSystemApp }
            }

            repository.scanForUpdates(
                appsToCheck = appsToCheck,
                onlyStable = _onlyStable.value
            ).collect { status ->
                _scanStatus.value = status
                if (status is ScanStatus.Success) {
                    _updates.value = status.updates
                    _lastScanTime.value = System.currentTimeMillis()
                    if (status.updates.isEmpty() && _selectedFilter.value == AppFilter.UPDATES_ONLY) {
                        _selectedFilter.value = AppFilter.USER_APPS
                    }
                }
            }
        }
    }

    fun setFilter(filter: AppFilter) {
        _selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setIncludeSystemApps(include: Boolean) {
        _includeSystemApps.value = include
        scanForUpdates()
    }

    fun setOnlyStable(onlyStable: Boolean) {
        _onlyStable.value = onlyStable
        scanForUpdates()
    }
}
