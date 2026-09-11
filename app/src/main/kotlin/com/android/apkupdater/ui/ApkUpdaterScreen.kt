package com.android.apkupdater.ui

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.launch

private enum class AppTab(val labelRes: Int) {
    Home(R.string.home),
    Search(R.string.search),
    Settings(R.string.settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkUpdaterScreen(viewModel: ApkUpdaterViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTabIndex by remember { mutableIntStateOf(AppTab.Home.ordinal) }
    var manualUpdateRequest by remember { mutableStateOf<Pair<InstalledApp, Long>?>(null) }
    val homeListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedTab = AppTab.entries[selectedTabIndex]
    val installActive = uiState.installState is InstallState.Preparing ||
        uiState.installState is InstallState.Downloading ||
        uiState.installState is InstallState.Installing
    val updateMap = remember(uiState.updates) { uiState.updates.associateBy(AppUpdateInfo::packageName) }
    val visibleApps = remember(
        uiState.installedApps,
        uiState.includeSystemApps,
        uiState.includeDisabledApps,
        uiState.searchQuery
    ) {
        uiState.installedApps
            .filter { uiState.includeSystemApps || !it.isSystemApp }
            .filter { uiState.includeDisabledApps || it.isEnabled }
            .filter {
                uiState.searchQuery.isBlank() ||
                    it.appName.contains(uiState.searchQuery, true) ||
                    it.packageName.contains(uiState.searchQuery, true)
            }
            .sortedBy { it.appName.lowercase() }
    }
    val appsWithUpdates = remember(visibleApps, updateMap) {
        visibleApps.filter { updateMap.containsKey(it.packageName) }
            .sortedWith(compareByDescending<InstalledApp> {
                updateMap[it.packageName]?.apkMirrorUploadedAt ?: Long.MIN_VALUE
            }.thenBy { it.appName.lowercase() })
    }
    val installMessage = when (val state = uiState.installState) {
        is InstallState.Success -> stringResource(R.string.update_installed, state.appName)
        is InstallState.Error -> stringResource(R.string.update_failed, state.message)
        else -> ""
    }

    val accountChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val request = manualUpdateRequest
        manualUpdateRequest = null
        if (result.resultCode == Activity.RESULT_OK && request != null) {
            result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.let { email ->
                viewModel.installManual(request.first, request.second, email)
            }
        }
    }

    LaunchedEffect(uiState.installState) {
        when (uiState.installState) {
            is InstallState.Success, is InstallState.Error -> snackbarHostState.showSnackbar(installMessage)
            else -> Unit
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (selectedTab == AppTab.Home) stringResource(R.string.app_name) else stringResource(selectedTab.labelRes)) }
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
            AppTab.Home -> UpdateListContent(
                Modifier.fillMaxSize().padding(innerPadding),
                appsWithUpdates,
                updateMap,
                homeListState,
                uiState.scanStatus,
                viewModel::scanForUpdates,
                onOpenApkMirror = { openUrlInBrowser(context, it.apkMirrorUrl) },
                onManualUpdate = { app -> startManualUpdate(app, updateMap, accountChooserLauncher) },
                installActive = installActive
            )
            AppTab.Search -> SearchContent(
                Modifier.fillMaxSize().padding(innerPadding),
                uiState.searchQuery,
                viewModel::setSearchQuery,
                visibleApps,
                updateMap,
                searchListState,
                onOpenApkMirror = { app ->
                    openUrlInBrowser(context, updateMap[app.packageName]?.apkMirrorUrl ?: buildApkMirrorSearchUrl(app.packageName))
                },
                onManualUpdate = { app -> startManualUpdate(app, updateMap, accountChooserLauncher) },
                installActive = installActive
            )
            AppTab.Settings -> SettingsContent(
                Modifier.fillMaxSize().padding(innerPadding).navigationBarsPadding(),
                uiState.includeSystemApps,
                viewModel::setIncludeSystemApps,
                uiState.includeDisabledApps,
                viewModel::setIncludeDisabledApps
            )
        }
    }
}

private fun startManualUpdate(
    app: InstalledApp,
    updateMap: Map<String, AppUpdateInfo>,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val targetVersion = updateMap[app.packageName]?.newVersionCode ?: return
    launcher.launch(googleAccountChooserIntent())
}

@Composable
private fun UpdateListContent(
    modifier: Modifier,
    apps: List<InstalledApp>,
    updateMap: Map<String, AppUpdateInfo>,
    listState: LazyListState,
    scanStatus: ScanStatus,
    onScanClick: () -> Unit,
    onOpenApkMirror: (AppUpdateInfo) -> Unit,
    onManualUpdate: (InstalledApp) -> Unit,
    installActive: Boolean
) {
    Column(modifier) {
        ScanStatusSection(scanStatus, apps.size, onScanClick)
        if (apps.isEmpty()) {
            EmptyAppsView(if (scanStatus is ScanStatus.Scanning) stringResource(R.string.checking_installed_apps) else stringResource(R.string.no_updates_available))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    updateMap[app.packageName]?.let { update ->
                        AppListItem(app, update, installActive, { onOpenApkMirror(update) }, { onManualUpdate(app) })
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
    onOpenApkMirror: (InstalledApp) -> Unit,
    onManualUpdate: (InstalledApp) -> Unit,
    installActive: Boolean
) {
    Column(modifier) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.search_apps)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.clear_search))
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large
        )
        if (apps.isEmpty()) {
            EmptyAppsView(if (searchQuery.isNotEmpty()) stringResource(R.string.no_apps_matching, searchQuery) else stringResource(R.string.no_applications_found))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppListItem(app, updateMap[app.packageName], installActive, { onOpenApkMirror(app) }, { onManualUpdate(app) })
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
        is ScanStatus.Success -> ListItem(
            headlineContent = { Text(if (updatesCount > 0) stringResource(R.string.updates_available, updatesCount) else stringResource(R.string.all_apps_up_to_date)) },
            trailingContent = { TextButton(onClick = onScanClick) { Text(stringResource(R.string.check_again)) } }
        )
        is ScanStatus.Error -> ListItem(
            headlineContent = { Text(stringResource(R.string.update_check_failed)) },
            supportingContent = { Text(status.message) },
            trailingContent = { TextButton(onClick = onScanClick) { Text(stringResource(R.string.retry)) } }
        )
        ScanStatus.Idle -> ListItem(
            headlineContent = { Text(stringResource(R.string.ready_to_check)) },
            trailingContent = { TextButton(onClick = onScanClick) { Text(stringResource(R.string.check_now)) } }
        )
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
    OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                AppIconImage(app.packageName, Modifier.size(64.dp))
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(app.appName, Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(16.dp))
                        Text(if (app.isSystemApp) stringResource(R.string.system) else stringResource(R.string.user), style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.version_display, app.versionName, app.versionCode), style = MaterialTheme.typography.bodyLarge)
                    if (update != null) {
                        Text("↓", Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.version_display, update.newVersionName, update.newVersionCode), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (update != null) {
                    FilledTonalButton(onClick = onOpenApkMirror, enabled = !installActive, shape = MaterialTheme.shapes.extraLarge) { Text(stringResource(R.string.update)) }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onManualUpdate, enabled = !installActive, shape = MaterialTheme.shapes.extraLarge) { Text(stringResource(R.string.manual)) }
                } else {
                    FilledTonalButton(onClick = onOpenApkMirror, shape = MaterialTheme.shapes.extraLarge) { Text(stringResource(R.string.apkmirror)) }
                }
            }
        }
    }
}

@Composable
private fun AppIconImage(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(packageName) { runCatching { drawableToBitmap(context.packageManager.getApplicationIcon(packageName)) }.getOrNull() }
    if (bitmap != null) Image(bitmap.asImageBitmap(), null, modifier)
    else Box(modifier, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Search, null) }
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
            trailingContent = { Switch(includeSystemApps, onIncludeSystemAppsChange) }
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.disabled_apps)) },
            supportingContent = { Text(stringResource(R.string.disabled_apps_description)) },
            trailingContent = { Switch(includeDisabledApps, onIncludeDisabledAppsChange) }
        )
    }
}

@Composable
private fun EmptyAppsView(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.titleMedium)
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) return drawable.bitmap
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
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun googleAccountChooserIntent(): Intent = AccountManager.newChooseAccountIntent(
    null,
    null,
    arrayOf("com.google"),
    null,
    null,
    null,
    null
)
