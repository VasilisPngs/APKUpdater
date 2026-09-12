package com.android.gupdater.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.Card
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    var menuExpanded by remember { mutableStateOf(false) }
    val homeListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedTab = AppTab.entries[selectedTabIndex]
    val bundlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::installBundle)
    }
    val bottomBarScrollBehavior = BottomAppBarDefaults.exitAlwaysScrollBehavior()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(
                message = when (event) {
                    is InstallEvent.Finished -> context.getString(R.string.update_installed, event.appName)
                    is InstallEvent.Failed -> context.getString(R.string.update_failed, event.message)
                },
                duration = when (event) {
                    is InstallEvent.Finished -> SnackbarDuration.Short
                    is InstallEvent.Failed -> SnackbarDuration.Long
                }
            )
        }
    }

    val updateMap = remember(uiState.updates) {
        uiState.updates.associateBy(AppUpdateInfo::packageName)
    }
    val appsWithUpdates = remember(uiState.installedApps, updateMap) {
        uiState.installedApps
            .filter { updateMap.containsKey(it.packageName) }
            .sortedBy { updateMap.getValue(it.packageName).appName.lowercase() }
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
                            imageVector = Icons.Rounded.MoreVert,
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
                status = uiState.scanStatus,
                apps = appsWithUpdates,
                updateMap = updateMap,
                listState = homeListState,
                installs = uiState.installs,
                onScanClick = viewModel::scanForUpdates,
                onPlayStoreUpdate = { app, update -> viewModel.installFromPlay(app, update.newVersionCode) }
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
    status: ScanStatus,
    apps: List<InstalledApp>,
    updateMap: Map<String, AppUpdateInfo>,
    listState: LazyListState,
    installs: Map<String, InstallState>,
    onScanClick: () -> Unit,
    onPlayStoreUpdate: (InstalledApp, AppUpdateInfo) -> Unit
) {
    val fileInstalls = remember(installs, apps) {
        installs.filterKeys { key -> apps.none { it.packageName == key } }.values.toList()
    }

    Column(modifier = modifier) {
        RoundedSection {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = {
                    Text(
                        when (status) {
                            ScanStatus.Scanning -> stringResource(R.string.searching_for_updates)
                            is ScanStatus.Success -> if (apps.isNotEmpty()) {
                                stringResource(R.string.updates_available, apps.size)
                            } else {
                                stringResource(R.string.all_apps_up_to_date)
                            }
                            is ScanStatus.Error -> stringResource(R.string.update_check_failed)
                        }
                    )
                },
                supportingContent = if (status is ScanStatus.Error) {
                    { Text(status.message) }
                } else {
                    null
                },
                trailingContent = {
                    if (status == ScanStatus.Scanning) {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else {
                        IconButton(onClick = onScanClick) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.check_again)
                            )
                        }
                    }
                }
            )
        }

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
            items(apps, key = { it.packageName }) { app ->
                updateMap[app.packageName]?.let { update ->
                    AppListItem(
                        app = app,
                        update = update,
                        installState = installs[app.packageName],
                        onPlayStoreUpdate = { onPlayStoreUpdate(app, update) }
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
    app: InstalledApp,
    update: AppUpdateInfo,
    installState: InstallState?,
    onPlayStoreUpdate: () -> Unit
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

    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
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
                FilledTonalButton(onClick = onPlayStoreUpdate) {
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
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { openUrlInBrowser(context, update.apkMirrorUrl) }) {
                    Text(stringResource(R.string.apkmirror))
                }
            }
        }
    }
}

private fun android.graphics.drawable.Drawable.toBitmap(sizePx: Int): Bitmap =
    Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bitmap ->
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
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
