package com.android.gupdater.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.gupdater.R
import com.android.gupdater.data.model.AppUpdateInfo
import com.android.gupdater.data.model.InstalledApp
import com.android.gupdater.data.model.InstallState
import com.android.gupdater.data.repository.ScanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppTab(val labelRes: Int) {
    Home(R.string.home),
    Settings(R.string.settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GUpdaterScreen(
    viewModel: GUpdaterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTabIndex by remember { mutableIntStateOf(AppTab.Home.ordinal) }
    var manualUpdateApp by remember { mutableStateOf<InstalledApp?>(null) }
    val homeListState = rememberLazyListState()
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
    val appsWithUpdates = remember(uiState.installedApps, updateMap) {
        uiState.installedApps.filter { updateMap.containsKey(it.packageName) }
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
            CenterAlignedTopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = {
                            if (selectedTab == tab && tab == AppTab.Home) {
                                coroutineScope.launch { homeListState.animateScrollToItem(0) }
                            } else {
                                selectedTabIndex = tab.ordinal
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    AppTab.Home -> Icons.Rounded.Home
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
                onManualUpdate = { manualUpdateApp = it }
            )
            AppTab.Settings -> SettingsContent(
                modifier = Modifier.fillMaxSize().padding(innerPadding).navigationBarsPadding(),
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
                            onManualUpdate = { onManualUpdate(app) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanStatusSection(status: ScanStatus, updatesCount: Int, onScanClick: () -> Unit) {
    when (status) {
        is ScanStatus.Scanning -> {
            LinearProgressIndicator(
                progress = {
                    if (status.total > 0) status.processed.toFloat() / status.total else 0f
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        is ScanStatus.Success -> {
            ListItem(
                headlineContent = {
                    Text(
                        if (updatesCount > 0) {
                            stringResource(R.string.updates_available, updatesCount)
                        } else {
                            stringResource(R.string.all_apps_up_to_date)
                        }
                    )
                },
                trailingContent = {
                    TextButton(onClick = onScanClick) { Text(stringResource(R.string.check_again)) }
                }
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
    update: AppUpdateInfo,
    installActive: Boolean,
    onManualUpdate: () -> Unit
) {
    val context = LocalContext.current
    val iconBitmap by produceState<Bitmap?>(initialValue = null, key1 = app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(app.packageName).toBitmap()
            }.getOrNull()
        }
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap!!.asImageBitmap(),
                        contentDescription = app.appName,
                        modifier = Modifier.size(56.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(Modifier.size(56.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.version_display, app.versionName, app.versionCode),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.version_display, update.newVersionName, update.newVersionCode),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    onClick = onManualUpdate,
                    enabled = !installActive,
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text(stringResource(R.string.manual))
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = { openUrlInBrowser(context, update.apkMirrorUrl) },
                    enabled = !installActive,
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text(stringResource(R.string.update))
                }
            }
        }
    }
}

private fun android.graphics.drawable.Drawable.toBitmap(): Bitmap {
    val width = intrinsicWidth.takeIf { it > 0 } ?: 1
    val height = intrinsicHeight.takeIf { it > 0 } ?: 1
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        Canvas(bitmap).also { canvas ->
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
        }
    }
}

@Composable
private fun SettingsContent(
    modifier: Modifier,
    includeDisabledApps: Boolean,
    onIncludeDisabledAppsChange: (Boolean) -> Unit
) {
    Column(modifier) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.disabled_apps)) },
            supportingContent = { Text(stringResource(R.string.disabled_apps_description)) },
            trailingContent = {
                Switch(checked = includeDisabledApps, onCheckedChange = onIncludeDisabledAppsChange)
            }
        )
    }
}

@Composable
private fun EmptyAppsView(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.titleMedium)
    }
}

private fun openUrlInBrowser(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
