package com.android.gupdater.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.gupdater.R
import com.android.gupdater.data.model.AppUpdateInfo
import com.android.gupdater.data.model.InstallState
import com.android.gupdater.data.model.InstalledApp
import com.android.gupdater.data.repository.ScanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val APP_ICON_SIZE = 56.dp

private enum class AppTab(val labelRes: Int, val iconRes: Int, val selectedIconRes: Int) {
    Home(R.string.home, R.drawable.ic_home, R.drawable.ic_home_filled),
    Settings(R.string.settings, R.drawable.ic_settings, R.drawable.ic_settings_filled)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GUpdaterScreen(
    viewModel: GUpdaterViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Home) }
    var menuExpanded by remember { mutableStateOf(false) }
    val homeListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val bundlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::installBundle)
    }
    val bottomBarScrollBehavior = BottomAppBarDefaults.exitAlwaysScrollBehavior()

    val installedFormat = stringResource(R.string.update_installed)
    val failedFormat = stringResource(R.string.update_failed)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(
                message = when (event) {
                    is InstallEvent.Finished -> installedFormat.format(event.appName)
                    is InstallEvent.Failed -> failedFormat.format(event.message)
                },
                duration = when (event) {
                    is InstallEvent.Finished -> SnackbarDuration.Short
                    is InstallEvent.Failed -> SnackbarDuration.Long
                }
            )
        }
    }

    LaunchedEffect(uiState.scanStatus) {
        val status = uiState.scanStatus
        if (status is ScanStatus.Error) snackbarHostState.showSnackbar(status.message)
    }

    val updates = remember(uiState.installedApps, uiState.updates) {
        val installedByPackage = uiState.installedApps.associateBy(InstalledApp::packageName)
        uiState.updates.mapNotNull { update ->
            installedByPackage[update.packageName]?.let { it to update }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(bottomBarScrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = stringResource(R.string.more_options)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.install_bundle)) },
                            onClick = {
                                menuExpanded = false
                                bundlePicker.launch(arrayOf("*/*"))
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                scrollBehavior = bottomBarScrollBehavior,
                contentPadding = PaddingValues(0.dp)
            ) {
                AppTab.entries.forEach { tab ->
                    val selected = selectedTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            when {
                                !selected -> selectedTab = tab
                                tab != AppTab.Home -> Unit
                                homeListState.canScrollBackward ->
                                    coroutineScope.launch { homeListState.animateScrollToItem(0) }
                                else -> viewModel.scanForUpdates()
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(
                                    if (selected) tab.selectedIconRes else tab.iconRes
                                ),
                                contentDescription = stringResource(tab.labelRes)
                            )
                        },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data, shape = MaterialTheme.shapes.extraLarge)
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            AppTab.Home -> HomeContent(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding,
                isScanning = uiState.scanStatus == ScanStatus.Scanning,
                updates = updates,
                listState = homeListState,
                installs = uiState.installs,
                onScan = viewModel::scanForUpdates,
                onPlayStoreUpdate = { app, versionCode -> viewModel.installFromPlay(app, versionCode) }
            )
            AppTab.Settings -> SettingsContent(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                includeDisabledApps = uiState.includeDisabledApps,
                onIncludeDisabledAppsChange = viewModel::setIncludeDisabledApps
            )
        }
    }
}

@Composable
private fun HomeContent(
    modifier: Modifier,
    contentPadding: PaddingValues,
    isScanning: Boolean,
    updates: List<Pair<InstalledApp, AppUpdateInfo>>,
    listState: LazyListState,
    installs: Map<String, InstallState>,
    onScan: () -> Unit,
    onPlayStoreUpdate: (InstalledApp, Long) -> Unit
) {
    val fileInstalls = remember(installs, updates) {
        installs.filterKeys { key -> updates.none { (app, _) -> app.packageName == key } }.values.toList()
    }

    PullToRefreshBox(
        isRefreshing = isScanning,
        onRefresh = onScan,
        modifier = modifier.padding(top = contentPadding.calculateTopPadding())
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            fileInstalls.forEach { state ->
                RoundedSection {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = {
                            Text(state.appName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(updates, key = { (app, _) -> app.packageName }) { (app, update) ->
                    AppListItem(
                        modifier = Modifier.animateItem(),
                        app = app,
                        update = update,
                        installState = installs[app.packageName],
                        onPlayStoreUpdate = { versionCode -> onPlayStoreUpdate(app, versionCode) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundedSection(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        content()
    }
}

@Composable
private fun AppListItem(
    modifier: Modifier,
    app: InstalledApp,
    update: AppUpdateInfo,
    installState: InstallState?,
    onPlayStoreUpdate: (Long) -> Unit
) {
    val context = LocalContext.current
    val iconSizePx = with(LocalDensity.current) { APP_ICON_SIZE.roundToPx() }
    val iconBitmap by produceState<Bitmap?>(initialValue = null, key1 = app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(app.packageName).toBitmap(iconSizePx)
            }.getOrNull()
        }
    }

    Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap!!.asImageBitmap(),
                        contentDescription = update.appName,
                        modifier = Modifier.size(APP_ICON_SIZE),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(Modifier.size(APP_ICON_SIZE))
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = update.appName,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.version_current,
                            app.versionName,
                            app.versionCode
                        ),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.version_latest,
                            update.newVersionName,
                            update.newVersionCode
                        ),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (update.playAvailable) {
                    FilledTonalButton(
                        onClick = { onPlayStoreUpdate(update.playVersionCode ?: update.newVersionCode) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.play_store),
                                modifier = Modifier.alpha(if (installState == null) 1f else 0f)
                            )
                            when (installState) {
                                null -> Unit
                                is InstallState.Downloading -> CircularProgressIndicator(
                                    progress = { installState.progress },
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                else -> CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                    if (update.apkMirrorUrl != null) Spacer(Modifier.width(8.dp))
                }
                if (update.apkMirrorUrl != null) {
                    FilledTonalButton(onClick = { openUrlInBrowser(context, update.apkMirrorUrl) }) {
                        Text(stringResource(R.string.apkmirror))
                    }
                }
            }
        }
    }
}

private fun android.graphics.drawable.Drawable.toBitmap(sizePx: Int): Bitmap =
    createBitmap(sizePx, sizePx).also { bitmap ->
        Canvas(bitmap).also { canvas ->
            setBounds(0, 0, sizePx, sizePx)
            draw(canvas)
        }
    }

@Composable
private fun SettingsContent(
    modifier: Modifier,
    includeDisabledApps: Boolean,
    onIncludeDisabledAppsChange: (Boolean) -> Unit
) {
    Column(modifier) {
        RoundedSection {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.disabled_apps)) },
                supportingContent = { Text(stringResource(R.string.disabled_apps_description)) },
                trailingContent = {
                    Switch(checked = includeDisabledApps, onCheckedChange = onIncludeDisabledAppsChange)
                }
            )
        }
    }
}

private fun openUrlInBrowser(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}
