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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.apkupdater.data.model.AppFilter
import com.android.apkupdater.data.model.AppUpdateInfo
import com.android.apkupdater.data.model.InstalledApp
import com.android.apkupdater.data.repository.ScanStatus

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
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val includeSystemApps by viewModel.includeSystemApps.collectAsStateWithLifecycle()
    val onlyStable by viewModel.onlyStable.collectAsStateWithLifecycle()

    var showSettingsSheet by remember { mutableStateOf(false) }
    val isScanning = scanStatus is ScanStatus.Scanning
    val userApps = installedApps.filterNot(InstalledApp::isSystemApp)
    val systemApps = installedApps.filter(InstalledApp::isSystemApp)
    val updatePackages = updates.asSequence().map(AppUpdateInfo::packageName).toSet()

    val filteredUpdates = updates.filter { update ->
        searchQuery.isBlank() ||
            update.appName.contains(searchQuery, ignoreCase = true) ||
            update.packageName.contains(searchQuery, ignoreCase = true)
    }

    val filteredApps = when (selectedFilter) {
        AppFilter.USER_APPS -> userApps
        AppFilter.SYSTEM_APPS -> systemApps
        AppFilter.ALL_APPS -> installedApps
        AppFilter.UPDATES_ONLY -> emptyList()
    }.filter { app ->
        searchQuery.isBlank() ||
            app.appName.contains(searchQuery, ignoreCase = true) ||
            app.packageName.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("APKUpdater") },
                actions = {
                    IconButton(
                        onClick = viewModel::scanForUpdates,
                        enabled = !isScanning
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Check for updates")
                        }
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScanStatusSection(
                scanStatus = scanStatus,
                updatesCount = updates.size,
                appsCount = if (includeSystemApps) installedApps.size else userApps.size,
                onScanClick = viewModel::scanForUpdates
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search apps") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == AppFilter.UPDATES_ONLY,
                        onClick = { viewModel.setFilter(AppFilter.UPDATES_ONLY) },
                        label = { Text("Updates (${updates.size})") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == AppFilter.USER_APPS,
                        onClick = { viewModel.setFilter(AppFilter.USER_APPS) },
                        label = { Text("User apps (${userApps.size})") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == AppFilter.ALL_APPS,
                        onClick = { viewModel.setFilter(AppFilter.ALL_APPS) },
                        label = { Text("All (${installedApps.size})") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == AppFilter.SYSTEM_APPS,
                        onClick = { viewModel.setFilter(AppFilter.SYSTEM_APPS) },
                        label = { Text("System (${systemApps.size})") }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (selectedFilter == AppFilter.UPDATES_ONLY) {
                if (filteredUpdates.isEmpty()) {
                    EmptyUpdatesView(isScanning = isScanning, onScanClick = viewModel::scanForUpdates)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredUpdates, key = { it.packageName }) { update ->
                            UpdateListItem(
                                update = update,
                                onOpenApkMirror = { openUrlInBrowser(context, update.apkMirrorUrl) }
                            )
                        }
                    }
                }
            } else {
                if (filteredApps.isEmpty()) {
                    EmptyAppsView(searchQuery)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            InstalledAppListItem(
                                app = app,
                                hasUpdate = app.packageName in updatePackages,
                                onOpenApkMirror = {
                                    val url = "https://www.apkmirror.com/?post_type=app_release&searchtype=app&s=${Uri.encode(app.packageName)}"
                                    openUrlInBrowser(context, url)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }) {
            SettingsContent(
                includeSystemApps = includeSystemApps,
                onlyStable = onlyStable,
                onIncludeSystemAppsChange = viewModel::setIncludeSystemApps,
                onOnlyStableChange = viewModel::setOnlyStable,
                onClose = { showSettingsSheet = false }
            )
        }
    }
}

@Composable
private fun ScanStatusSection(
    scanStatus: ScanStatus,
    updatesCount: Int,
    appsCount: Int,
    onScanClick: () -> Unit
) {
    when (scanStatus) {
        is ScanStatus.Scanning -> {
            ListItem(
                headlineContent = { Text("Checking for updates") },
                supportingContent = { Text("${scanStatus.processed} of ${scanStatus.total} · ${scanStatus.currentBatch}") },
                trailingContent = { CircularProgressIndicator() }
            )
            LinearProgressIndicator(
                progress = { if (scanStatus.total == 0) 0f else scanStatus.processed.toFloat() / scanStatus.total },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
        is ScanStatus.Success -> {
            ListItem(
                leadingContent = {
                    Icon(
                        if (updatesCount > 0) Icons.Outlined.Download else Icons.Default.CheckCircle,
                        contentDescription = null
                    )
                },
                headlineContent = {
                    Text(if (updatesCount > 0) "$updatesCount updates available" else "All apps are up to date")
                },
                supportingContent = { Text("Checked $appsCount installed apps") },
                trailingContent = {
                    TextButton(onClick = onScanClick) { Text("Check again") }
                }
            )
        }
        is ScanStatus.Error -> {
            ListItem(
                headlineContent = { Text("Update check failed") },
                supportingContent = { Text(scanStatus.message) },
                trailingContent = {
                    TextButton(onClick = onScanClick) { Text("Retry") }
                }
            )
        }
        ScanStatus.Idle -> {
            ListItem(
                headlineContent = { Text("Ready to check for updates") },
                supportingContent = { Text("APKMirror") },
                trailingContent = {
                    TextButton(onClick = onScanClick) { Text("Check now") }
                }
            )
        }
    }
}

@Composable
private fun UpdateListItem(
    update: AppUpdateInfo,
    onOpenApkMirror: () -> Unit
) {
    var showChangelog by remember(update.packageName, update.newVersionCode) { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            ListItem(
                leadingContent = {
                    AppIconImage(update.packageName, Modifier.size(48.dp))
                },
                headlineContent = {
                    Text(update.appName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(
                        "v${update.currentVersionName}  →  v${update.newVersionName}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingContent = {
                    FilledTonalUpdateButton(onClick = onOpenApkMirror)
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("APKMirror") },
                supportingContent = {
                    val details = buildList {
                        if (update.architectures.isNotEmpty()) add(update.architectures.joinToString(", "))
                        if (!update.publishDate.isNullOrBlank()) add(update.publishDate)
                    }
                    Text(details.joinToString(" · ").ifBlank { "Latest compatible release" })
                },
                trailingContent = if (!update.whatsNew.isNullOrBlank()) {
                    {
                        TextButton(onClick = { showChangelog = !showChangelog }) {
                            Text(if (showChangelog) "Hide changelog" else "Changelog")
                        }
                    }
                } else null
            )
            if (showChangelog && !update.whatsNew.isNullOrBlank()) {
                Text(
                    update.whatsNew.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun FilledTonalUpdateButton(onClick: () -> Unit) {
    androidx.compose.material3.FilledTonalButton(onClick = onClick) {
        Icon(Icons.Default.OpenInBrowser, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Open")
    }
}

@Composable
private fun InstalledAppListItem(
    app: InstalledApp,
    hasUpdate: Boolean,
    onOpenApkMirror: () -> Unit
) {
    ListItem(
        leadingContent = {
            AppIconImage(app.packageName, Modifier.size(48.dp))
        },
        headlineContent = {
            Text(app.appName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                buildString {
                    append("v${app.versionName} (${app.versionCode})")
                    if (app.isSystemApp) append(" · System")
                    if (hasUpdate) append(" · Update available")
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            OutlinedButton(onClick = onOpenApkMirror) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("APKMirror")
            }
        }
    )
}

@Composable
private fun AppIconImage(packageName: String, modifier: Modifier = Modifier) {
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
            Icon(Icons.Outlined.Android, contentDescription = null)
        }
    }
}

@Composable
private fun SettingsContent(
    includeSystemApps: Boolean,
    onlyStable: Boolean,
    onIncludeSystemAppsChange: (Boolean) -> Unit,
    onOnlyStableChange: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.navigationBarsPadding()) {
        ListItem(
            headlineContent = { Text("Settings") },
            supportingContent = { Text("APKMirror update checks") }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Stable releases only") },
            supportingContent = { Text("Exclude alpha and beta releases") },
            trailingContent = {
                Switch(checked = onlyStable, onCheckedChange = onOnlyStableChange)
            }
        )
        ListItem(
            headlineContent = { Text("System apps") },
            supportingContent = { Text("Include pre-installed system applications") },
            trailingContent = {
                Switch(checked = includeSystemApps, onCheckedChange = onIncludeSystemAppsChange)
            }
        )
        Button(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun EmptyUpdatesView(
    isScanning: Boolean,
    onScanClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("No updates found", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                if (isScanning) "Checking your installed apps…" else "Your selected apps have no available updates.",
                style = MaterialTheme.typography.bodyMedium
            )
            if (!isScanning) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onScanClick) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Check again")
                }
            }
        }
    }
}

@Composable
private fun EmptyAppsView(searchQuery: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                if (searchQuery.isBlank()) "No applications found" else "No apps match \"$searchQuery\"",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        Canvas(bitmap).also { canvas ->
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
    }
}

private fun openUrlInBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
