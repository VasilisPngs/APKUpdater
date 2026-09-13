package com.android.gupdater.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.gupdater.data.installer.BundleInstaller
import com.android.gupdater.data.installer.GooglePlayInstaller
import com.android.gupdater.data.model.AppUpdateInfo
import com.android.gupdater.data.model.InstallState
import com.android.gupdater.data.model.InstalledApp
import com.android.gupdater.data.play.PlayAuthProvider
import com.android.gupdater.data.play.PlayCatalog
import com.android.gupdater.data.preferences.AppPreferences
import com.android.gupdater.data.repository.AppUpdateRepository
import com.android.gupdater.data.repository.ScanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

sealed interface InstallEvent {
    data class Finished(val appName: String) : InstallEvent
    data class Failed(val message: String) : InstallEvent
}

data class UpdaterUiState(
    val scanStatus: ScanStatus = ScanStatus.Scanning,
    val installedApps: List<InstalledApp> = emptyList(),
    val updates: List<AppUpdateInfo> = emptyList(),
    val includeDisabledApps: Boolean = false,
    val installs: Map<String, InstallState> = emptyMap()
)

class GUpdaterViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = AppPreferences(application.applicationContext)
    private val authProvider = PlayAuthProvider(application.applicationContext)
    private val repository = AppUpdateRepository(
        application.applicationContext,
        PlayCatalog(authProvider)
    )
    private val googlePlayInstaller = GooglePlayInstaller(application.applicationContext, authProvider)
    private val bundleInstaller = BundleInstaller(application.applicationContext)
    private val _uiState = MutableStateFlow(UpdaterUiState())
    private val _events = MutableSharedFlow<InstallEvent>(extraBufferCapacity = 16)
    private val installJobs = ConcurrentHashMap<String, Job>()
    private var scanJob: Job? = null

    val uiState: StateFlow<UpdaterUiState> = _uiState.asStateFlow()
    val events: SharedFlow<InstallEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val includeDisabledApps = withContext(Dispatchers.IO) { preferences.includeDisabledApps }
            _uiState.update {
                it.copy(
                    installedApps = repository.getInstalledApps(),
                    includeDisabledApps = includeDisabledApps
                )
            }
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
                val updates = when (status) {
                    ScanStatus.Scanning -> emptyList()
                    is ScanStatus.Success -> status.updates
                    is ScanStatus.Error -> status.partialUpdates
                }
                _uiState.update { it.copy(scanStatus = status, updates = updates) }
            }
        }
    }

    fun installVersion(app: InstalledApp, versionCode: Long) =
        install(app.packageName, app.packageName) { onState ->
            googlePlayInstaller.install(app, versionCode, onState)
        }

    fun installBundle(uri: Uri) = install(uri.toString(), packageName = null) { onState ->
        bundleInstaller.install(uri, onState)
    }

    fun setIncludeDisabledApps(include: Boolean) {
        if (_uiState.value.includeDisabledApps == include) return
        _uiState.update { it.copy(includeDisabledApps = include) }
        viewModelScope.launch(Dispatchers.IO) { preferences.includeDisabledApps = include }
        scanForUpdates()
    }

    private fun install(
        key: String,
        packageName: String?,
        block: suspend (onState: (InstallState) -> Unit) -> Result<Unit>
    ) {
        if (installJobs.containsKey(key)) return

        installJobs[key] = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                block { state ->
                    when (state) {
                        is InstallState.Success -> _events.tryEmit(InstallEvent.Finished(state.appName))
                        is InstallState.Error -> _events.tryEmit(InstallEvent.Failed(state.message))
                        else -> Unit
                    }
                    _uiState.update { it.copy(installs = it.installs + (key to state)) }
                }
            }

            installJobs.remove(key)
            _uiState.update { it.copy(installs = it.installs - key) }

            if (result.isSuccess) {
                if (packageName != null) dropUpdate(packageName) else dropInstalledUpdates()
            }
        }
    }

    private suspend fun dropUpdate(packageName: String) {
        val installed = repository.getInstalledApp(packageName) ?: return
        _uiState.update { state ->
            state.copy(
                installedApps = state.installedApps.map {
                    if (it.packageName == packageName) installed else it
                },
                updates = state.updates.filterNot {
                    it.packageName == packageName && it.newVersionCode <= installed.versionCode
                }
            )
        }
    }

    private suspend fun dropInstalledUpdates() {
        val apps = repository.getInstalledApps()
        val versions = apps.associate { it.packageName to it.versionCode }
        _uiState.update { state ->
            state.copy(
                installedApps = apps,
                updates = state.updates.filter {
                    it.newVersionCode > (versions[it.packageName] ?: 0L)
                }
            )
        }
    }

    override fun onCleared() {
        scanJob?.cancel()
        installJobs.values.forEach(Job::cancel)
    }
}
