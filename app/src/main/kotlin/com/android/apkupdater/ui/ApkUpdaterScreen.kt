package com.android.apkupdater.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.apkupdater.R
import com.android.apkupdater.data.model.AppUpdateInfo
import com.android.apkupdater.data.model.InstalledApp
import com.android.apkupdater.data.model.InstallState
import com.android.apkupdater.data.repository.ScanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppTab(val labelRes: Int) {
    Home(R.string.home),
    Search(R.string.search),
    Settings(R.string.settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkUpdaterScreen(
    viewModel: ApkUpdaterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTabIndex by remember { mutableIntStateOf(AppTab.Home.ordinal) }
    var manualUpdateApp by remember { mutableStateOf<InstalledApp?>(null) }
    val homeListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedTab = AppTab.entries[selectedTabIndex]
    val installActive = uiState.installState is InstallState.Preparing ||
        uiState.installState is InstallState.Downloading ||
        uiState.installState is InstallState.Installing

    LaunchedEffect(uiState.installState) {
        when (val state = uiState.installState) {
            is InstallState.Success -> snackbarHostState.showSnackbar(
                context.getString(R.string.update_installed, state.appName)
            )
            is InstallState.Error -> snackbarHostState.showSnackbar(
                context.getString(R.string.update_failed, state.message)
            )
            else -> Unit
        }
    }

    val updateMap = remember(uiState.updates) {
        uiState.updates.associateBy(AppUpdateInfo::packageName)
    }
    val visibleApps = remember(
        uiState.installedApps,
        uiState.includeSystemApps,
        uiState.includeDisabledApps,
        uiState.searchQuery
    ) {
        uiState.installedApps
            .filter { uiState.includeSystemApps || !it.isSystemApp }
            .filter { uiState.includeDisabledApps || it.isEnabled }
            .filter { app ->
                uiState.searchQuery.isBlank() ||
                    app.appName.contains(uiState.searchQuery, ignoreCase = true) ||
                    app.packageName.contains(uiState.searchQuery, ignoreCase = true)
            }
            .sortedBy { it.appName.lowercase() }
    }
    val appsWithUpdates = remember(visibleApps, updateMap) {
        visibleApps.filter { updateMap.containsKey(it.packageName) }
    }

    manualUpdateApp?.let { app ->
        ManualUpdateDialog(
            app = app,
            onDismiss = { manualUpdateApp = null },
            onConfirm = { versionCode, email, aasToken ->
                manualUpdateApp = null
                viewModel.installManual(app, versionCode, email, aasToken)
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (selectedTab == AppTab.Home) stringResource(R.string.app_name)
                        else stringResource(selectedTab.labelRes)
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = {
                            if (selectedTab == tab) {
                                when (tab) {
                                    AppTab.Home -> coroutineScope.launch { homeListState.animateScrollToItem(0) }
                                    AppTab.Search -> coroutineScope.launch { searchListState.animateScrollToItem(0) }
                                    AppTab.Settings -> Unit
                                }
                            } else {
                                selectedTabIndex = tab.ordinal
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    AppTab.Home -> Icons.Rounded.Home
                                    AppTab.Search -> Icons.Rounded.Search
                                    AppTab.Settings -> Icons.Rounded.Settings
                                },
                                contentDescription = stringResource(tab.labelRes)
                            )
                        },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when (selectedTab) {
            AppTab.Home -> HomeContent(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                status = uiState.scanStatus,
                apps = appsWithUpdates,
                updateMap = updateMap,
                listState = homeListState,
                installActive = installActive,
                onScanClick = viewModel::scanForUpdates,
                onOpenApkMirror = { update -> openUrlInBrowser(context, update.apkMirrorUrl) },
                onManualUpdate = { manualUpdateApp = it }
            )
            AppTab.Search -> SearchContent(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = viewModel::setSearchQuery,
                apps = visibleApps,
                updateMap = updateMap,
                listState = searchListState,
                installActive = installActive,
                onOpenApkMirror = { app ->
                    val url = updateMap[app.packageName]?.apkMirrorUrl
                        ?: buildApkMirrorSearchUrl(app.packageName)
                    openUrlInBrowser(context, url)
                },
                onManualUpdate = { manualUpdateApp = it }
            )
            AppTab.Settings -> SettingsContent(
                modifier = Modifier.fillMaxSize().padding(innerPadding).navigationBarsPadding(),
                includeSystemApps = uiState.includeSystemApps,
                onIncludeSystemAppsChange = viewModel::setIncludeSystemApps,
                includeDisabledApps = uiState.includeDisabledApps,
                onIncludeDisabledAppsChange = viewModel::setIncludeDisabledApps
            )
        }
    }
}

@Composable
private fun HomeContent(
    modifier: Modifier,
    status: ScanStatus,
    apps: List<InstalledApp>,
    updateMap: Map<String, AppUpdateInfo>,
    listState: LazyListState,
    installActive: Boolean,
    onScanClick: () -> Unit,
    onOpenApkMirror: (AppUpdateInfo) -> Unit,
    onManualUpdate: (InstalledApp) -> Unit
) {
    Column(modifier = modifier) {
        ScanStatusSection(status, apps.size, onScanClick)
        if (apps.isEmpty()) {
            EmptyAppsView(
                when (status) {
                    is ScanStatus.Scanning -> stringResource(R.string.checking_installed_apps)
                    else -> stringResource(R.string.no_updates_available)
                }
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    updateMap[app.packageName]?.let { update ->
                        AppListItem(
                            app = app,
                            update = update,
                            installActive = installActive,
                            onOpenApkMirror = { onOpenApkMirror(update) },
                            onManualUpdate = { onManualUpdate(app) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchContent(
    modifier: Modifier,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    apps: List<InstalledApp>,
    updateMap: Map<String, AppUpdateInfo>,
    listState: LazyListState,
    installActive: Boolean,
    onOpenApkMirror: (InstalledApp) -> Unit,
    onManualUpdate: (InstalledApp) -> Unit
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.search_apps)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_search))
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large
        )
        if (apps.isEmpty()) {
            EmptyAppsView(
                if (searchQuery.isNotEmpty()) stringResource(R.string.no_apps_matching, searchQuery)
                else stringResource(R.string.no_applications_found)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppListItem(
                        app = app,
                        update = updateMap[app.packageName],
                        installActive = installActive,
                        onOpenApkMirror = { onOpenApkMirror(app) },
                        onManualUpdate = { onManualUpdate(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanStatusSection(status: ScanStatus, updatesCount: Int, onScanClick: () -> Unit) {
    when (status) {
        is ScanStatus.Scanning -> {
            ListItem(
                headlineContent = { Text(stringResource(R.string.checking_for_updates)) },
                supportingContent = { Text(stringResource(R.string.scan_progress, status.processed, status.total, status.currentBatch)) }
            )
            LinearProgressIndicator(
                progress = { if (status.total == 0) 0f else status.processed.toFloat() / status.total },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
        is ScanStatus.Success -> {
            ListItem(
                headlineContent = {
                    Text(if (updatesCount > 0) stringResource(R.string.updates_available, updatesCount) else stringResource(R.string.all_apps_up_to_date))
                },
                trailingContent = { TextButton(onClick = onScanClick) { Text(stringResource(R.string.check_again)) } }
            )
        }
        is ScanStatus.Error -> {
            ListItem(
                headlineContent = { Text(stringResource(R.string.update_check_failed)) },
                supportingContent = { Text(status.message) },
                trailingContent = { TextButton(onClick = onScanClick) { Text(stringResource(R.string.retry)) } }
            )
        }
        ScanStatus.Idle -> {
            ListItem(
                headlineContent = { Text(stringResource(R.string.ready_to_check)) },
                trailingContent = { TextButton(onClick = onScanClick) { Text(stringResource(R.string.check_now)) } }
            )
        }
    }
}

@Composable
private fun AppListItem(
    app: InstalledApp,
    update: AppUpdateInfo?,
    installActive: Boolean,
    onOpenApkMirror: () -> Unit,
    onManualUpdate: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                AppIconImage(app.packageName, Modifier.size(64.dp))
                Spacer(Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            text = app.appName,
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = if (app.isSystemApp) stringResource(R.string.system) else stringResource(R.string.user),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.version_display, app.versionName, app.versionCode),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (update != null) {
                        Text(text = "↓", modifier = Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = stringResource(R.string.version_display, update.newVersionName, update.newVersionCode),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (update != null) {
                    FilledTonalButton(onClick = onOpenApkMirror, enabled = !installActive, shape = MaterialTheme.shapes.extraLarge) {
                        Text(stringResource(R.string.update))
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onManualUpdate, enabled = !installActive, shape = MaterialTheme.shapes.extraLarge) {
                        Text(stringResource(R.string.manual))
                    }
                } else {
                    FilledTonalButton(onClick = onOpenApkMirror, shape = MaterialTheme.shapes.extraLarge) {
                        Text(stringResource(R.string.apkmirror))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIconImage(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { drawableToBitmap(context.packageManager.getApplicationIcon(packageName)) }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null, modifier = modifier)
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Search, contentDescription = null)
        }
    }
}

@Composable
private fun SettingsContent(
    modifier: Modifier,
    includeSystemApps: Boolean,
    onIncludeSystemAppsChange: (Boolean) -> Unit,
    includeDisabledApps: Boolean,
    onIncludeDisabledAppsChange: (Boolean) -> Unit
) {
    Column(modifier) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.system_apps)) },
            supportingContent = { Text(stringResource(R.string.system_apps_description)) },
            trailingContent = { Switch(checked = includeSystemApps, onCheckedChange = onIncludeSystemAppsChange) }
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.disabled_apps)) },
            supportingContent = { Text(stringResource(R.string.disabled_apps_description)) },
            trailingContent = { Switch(checked = includeDisabledApps, onCheckedChange = onIncludeDisabledAppsChange) }
        )
    }
}

@Composable
private fun EmptyAppsView(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.titleMedium)
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
    val width = drawable.intrinsicWidth.coerceAtLeast(96)
    val height = drawable.intrinsicHeight.coerceAtLeast(96)
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        Canvas(bitmap).apply {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(this)
        }
    }
}

private fun buildApkMirrorSearchUrl(packageName: String): String =
    "https://www.apkmirror.com/?post_type=app_release&searchtype=app&s=${Uri.encode(packageName)}"

private fun openUrlInBrowser(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
