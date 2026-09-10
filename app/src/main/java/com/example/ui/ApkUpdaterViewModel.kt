package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppFilter
import com.example.data.model.AppUpdateInfo
import com.example.data.model.InstalledApp
import com.example.data.repository.AppUpdateRepository
import com.example.data.repository.ScanStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _updates = MutableStateFlow<List<AppUpdateInfo>>(emptyList())
    private val _selectedFilter = MutableStateFlow(AppFilter.UPDATES_ONLY)
    private val _searchQuery = MutableStateFlow("")
    private val _includeSystemApps = MutableStateFlow(false)
    private val _onlyStable = MutableStateFlow(true)
    private val _lastScanTime = MutableStateFlow<Long?>(null)
    private var scanJob: Job? = null

    val scanStatus: StateFlow<ScanStatus> = _scanStatus.asStateFlow()
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()
    val updates: StateFlow<List<AppUpdateInfo>> = _updates.asStateFlow()
    val selectedFilter: StateFlow<AppFilter> = _selectedFilter.asStateFlow()
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val includeSystemApps: StateFlow<Boolean> = _includeSystemApps.asStateFlow()
    val onlyStable: StateFlow<Boolean> = _onlyStable.asStateFlow()
    val lastScanTime: StateFlow<Long?> = _lastScanTime.asStateFlow()

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
            val appsToCheck = if (_includeSystemApps.value) allApps else allApps.filterNot(InstalledApp::isSystemApp)

            repository.scanForUpdates(appsToCheck, _onlyStable.value).collect { status ->
                _scanStatus.value = status
                when (status) {
                    is ScanStatus.Success -> {
                        _updates.value = status.updates
                        _lastScanTime.value = System.currentTimeMillis()
                    }
                    is ScanStatus.Error -> {
                        _updates.value = status.partialUpdates
                    }
                    else -> Unit
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
        if (_includeSystemApps.value == include) return
        _includeSystemApps.value = include
        scanForUpdates()
    }

    fun setOnlyStable(onlyStable: Boolean) {
        if (_onlyStable.value == onlyStable) return
        _onlyStable.value = onlyStable
        scanForUpdates()
    }

    override fun onCleared() {
        scanJob?.cancel()
        super.onCleared()
    }
}
