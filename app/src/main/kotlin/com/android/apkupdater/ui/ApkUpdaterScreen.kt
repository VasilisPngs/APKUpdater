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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.apkupdater.data.model.AppUpdateInfo
import com.android.apkupdater.data.model.InstalledApp
import com.android.apkupdater.data.repository.ScanStatus

private enum class AppTab(val label: String) {
    Home("Home"),
    Search("Search"),
    Settings("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkUpdaterScreen(
    viewModel: ApkUpdaterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scanStatus by viewModel.scanStatus.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val includeSystemApps by viewModel.includeSystemApps.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(AppTab.Home.ordinal) }

    val updateMap = updates.associateBy(AppUpdateInfo::packageName)
    val selectedAppTab = AppTab.entries[selectedTab]
    val visibleApps = installedApps
        .filter { includeSystemApps || !it.isSystemApp }
        .filter { app ->
            searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
        }
    val appsWithUpdates = visibleApps.filter { updateMap.containsKey(it.packageName) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (selectedAppTab == AppTab.Home) "APKUpdater" else selectedAppTab.label) }
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedAppTab == tab,
                        onClick = { selectedTab = tab.ordinal },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    AppTab.Home -> Icons.Rounded.Home
                                    AppTab.Search -> Icons.Rounded.Search
                                    AppTab.Settings -> Icons.Rounded.Settings
                                },
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedAppTab) {
            AppTab.Home -> HomeContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                status = scanStatus,
                updates = updates,
                apps = appsWithUpdates,
                appsCount = visibleApps.size,
                onScanClick = viewModel::scanForUpdates,
                onOpenApkMirror = { update -> openUrlInBrowser(context, update.apkMirrorUrl) }
            )
            AppTab.Search -> SearchContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::setSearchQuery,
                apps = visibleApps,
                updateMap = updateMap,
                onOpenApkMirror = { app ->
                    val url = updateMap[app.packageName]?.apkMirrorUrl
                        ?: buildApkMirrorSearchUrl(app.packageName)
                    openUrlInBrowser(context, url)
                }
            )
            AppTab.Settings -> SettingsContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding(),
                includeSystemApps = includeSystemApps,
                onIncludeSystemAppsChange = viewModel::setIncludeSystemApps
            )
        }
    }
}

@Composable
private fun HomeContent(
    modifier: Modifier,
    status: ScanStatus,
    updates: List<AppUpdateInfo>,
    apps: List<InstalledApp>,
    appsCount: Int,
    onScanClick: () -> Unit,
    onOpenApkMirror: (AppUpdateInfo) -> Unit
) {
    Column(modifier = modifier) {
        ScanStatusSection(
            status = status,
            updatesCount = updates.size,
            onScanClick = onScanClick
        )

        if (apps.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    val update = updates.first { it.packageName == app.packageName }
                    AppListItem(
                        app = app,
                        update = update,
                        onOpenApkMirror = { onOpenApkMirror(update) }
                    )
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
    onOpenApkMirror: (InstalledApp) -> Unit
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large
        )

        if (apps.isEmpty()) {
            EmptyAppsView(
                if (searchQuery.isNotEmpty()) "No apps matching \"$searchQuery\"" else "No applications found"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppListItem(
                        app = app,
                        update = updateMap[app.packageName],
                        onOpenApkMirror = { onOpenApkMirror(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanStatusSection(
    status: ScanStatus,
    updatesCount: Int,
    onScanClick: () -> Unit
) {
    when (status) {
        is ScanStatus.Scanning -> {
            ListItem(
                headlineContent = { Text("Checking for updates") },
                supportingContent = { Text("${status.processed} of ${status.total} · ${status.currentBatch}") }
            )
            LinearProgressIndicator(
                progress = { if (status.total == 0) 0f else status.processed.toFloat() / status.total },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
        is ScanStatus.Success -> {
            ListItem(
                leadingContent = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
                headlineContent = {
                    Text(if (updatesCount > 0) "$updatesCount updates available" else "All apps are up to date")
                },
                trailingContent = { TextButton(onClick = onScanClick) { Text("Check again") } }
            )
        }
        is ScanStatus.Error -> {
            ListItem(
                headlineContent = { Text("Update check failed") },
                supportingContent = { Text(status.message) },
                trailingContent = { TextButton(onClick = onScanClick) { Text("Retry") } }
            )
        }
        ScanStatus.Idle -> {
            ListItem(
                headlineContent = { Text("Ready to check for updates") },
                trailingContent = { TextButton(onClick = onScanClick) { Text("Check now") } }
            )
        }
    }
}

@Composable
private fun AppListItem(
    app: InstalledApp,
    update: AppUpdateInfo?,
    onOpenApkMirror: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            AppIconImage(app.packageName, Modifier.size(56.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = app.appName,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = if (app.isSystemApp) "System" else "User",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Spacer(Modifier.size(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (update != null) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "v${app.versionName} (${app.versionCode})",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Rounded.ArrowForward,
                                contentDescription = "Update available",
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .size(20.dp)
                            )
                            Text(
                                text = "v${update.newVersionName} (${update.newVersionCode})",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            text = "v${app.versionName} (${app.versionCode})",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    FilledTonalButton(
                        onClick = onOpenApkMirror,
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Text(if (update != null) "Update" else "APKMirror")
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIconImage(
    packageName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching {
            drawableToBitmap(context.packageManager.getApplicationIcon(packageName))
        }.getOrNull()
    }

    if (bitmap != null) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = modifier)
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
    onIncludeSystemAppsChange: (Boolean) -> Unit
) {
    Column(modifier) {
        ListItem(
            headlineContent = { Text("System apps") },
            supportingContent = { Text("Include pre-installed system applications") },
            trailingContent = {
                Switch(checked = includeSystemApps, onCheckedChange = onIncludeSystemAppsChange)
            }
        )
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun EmptyAppsView(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
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
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
