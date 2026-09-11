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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val includeSystemApps by viewModel.includeSystemApps.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    val isScanning = scanStatus is ScanStatus.Scanning
    val updateMap = updates.associateBy(AppUpdateInfo::packageName)
    val visibleApps = installedApps
        .filter { includeSystemApps || !it.isSystemApp }
        .filter { app ->
            searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("APKUpdater") },
                actions = {
                    IconButton(onClick = viewModel::scanForUpdates, enabled = !isScanning) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Check for updates")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
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
                status = scanStatus,
                updatesCount = updates.size,
                appsCount = if (includeSystemApps) installedApps.size else installedApps.count { !it.isSystemApp },
                onScanClick = viewModel::scanForUpdates
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )

            if (visibleApps.isEmpty()) {
                EmptyAppsView(searchQuery)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleApps, key = { it.packageName }) { app ->
                        val update = updateMap[app.packageName]
                        AppListItem(
                            app = app,
                            update = update,
                            onOpenApkMirror = {
                                val url = update?.apkMirrorUrl ?: buildApkMirrorSearchUrl(app.packageName)
                                openUrlInBrowser(context, url)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            SettingsContent(
                includeSystemApps = includeSystemApps,
                onIncludeSystemAppsChange = viewModel::setIncludeSystemApps,
                onClose = { showSettings = false }
            )
        }
    }
}

@Composable
private fun ScanStatusSection(
    status: ScanStatus,
    updatesCount: Int,
    appsCount: Int,
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
                supportingContent = { Text("Checked $appsCount installed apps · Stable releases only") },
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
                supportingContent = { Text("Stable releases only · APKMirror") },
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        ListItem(
            leadingContent = { AppIconImage(app.packageName, Modifier.size(48.dp)) },
            headlineContent = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        app.appName,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (app.isSystemApp) "System" else "User",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            },
            supportingContent = {
                if (update != null) {
                    Text("v${app.versionName} (${app.versionCode}) → v${update.newVersionName} (${update.newVersionCode})")
                } else {
                    Text("v${app.versionName} (${app.versionCode})")
                }
            },
            trailingContent = {
                FilledTonalButton(onClick = onOpenApkMirror) {
                    Icon(Icons.Rounded.Update, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Update")
                }
            }
        )
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
    includeSystemApps: Boolean,
    onIncludeSystemAppsChange: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.navigationBarsPadding()) {
        ListItem(
            headlineContent = { Text("Settings") },
            supportingContent = { Text("APKMirror update checks · Stable releases only") }
        )
        androidx.compose.material3.HorizontalDivider()
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
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun EmptyAppsView(searchQuery: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (searchQuery.isNotEmpty()) "No apps matching \"$searchQuery\"" else "No applications found",
            style = MaterialTheme.typography.titleMedium
        )
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
