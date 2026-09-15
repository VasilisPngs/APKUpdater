package com.android.apkupdater.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.apkupdater.R
import com.android.apkupdater.data.model.AppUpdateInfo
import com.android.apkupdater.data.model.InstallState
import com.android.apkupdater.data.model.InstalledApp
import com.android.apkupdater.data.repository.ScanStatus
import com.android.apkupdater.ui.theme.CapsuleCorner
import com.android.apkupdater.ui.theme.CardCorner
import com.android.apkupdater.ui.theme.Hairline
import com.android.apkupdater.ui.theme.IconCorner
import com.android.apkupdater.ui.theme.Ios
import com.android.apkupdater.ui.theme.MenuCorner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val ScreenInset = 20.dp
private val RowInset = 16.dp
private val CardGap = 12.dp
private val AppIconSize = 56.dp
private val TabBarHeight = 56.dp
private val ToolbarHeight = 44.dp
private val CollapseThreshold = 28.dp

private enum class AppTab(val labelRes: Int, val iconRes: Int, val selectedIconRes: Int) {
    Home(R.string.home, R.drawable.ic_home, R.drawable.ic_home_filled),
    Settings(R.string.settings, R.drawable.ic_settings, R.drawable.ic_settings_filled)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkUpdaterScreen(
    viewModel: ApkUpdaterViewModel,
    modifier: Modifier = Modifier
) {
    val palette = Ios.palette
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Home) }
    var menuExpanded by remember { mutableStateOf(false) }
    val homeListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val insets = WindowInsets.systemBars.asPaddingValues()
    val backdrop = rememberGraphicsLayer()
    val bundlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::installBundle)
    }
    BackHandler(enabled = selectedTab != AppTab.Home) { selectedTab = AppTab.Home }

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

    val threshold = with(LocalDensity.current) { CollapseThreshold.roundToPx() }
    val homeCollapsed by remember(threshold) { derivedStateOf { homeListState.isCollapsed(threshold) } }
    val settingsCollapsed by remember(threshold) {
        derivedStateOf { settingsListState.isCollapsed(threshold) }
    }
    val collapsed = if (selectedTab == AppTab.Home) homeCollapsed else settingsCollapsed

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().captureBackdrop(backdrop, palette.groupedBackground)) {
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = false,
                onRefresh = viewModel::scanForUpdates,
                state = pullState,
                indicator = {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = insets.calculateTopPadding() + ToolbarHeight + 8.dp)
                            .alpha(pullState.distanceFraction.coerceIn(0f, 1f))
                    ) {
                        ActivityIndicator(size = 22.dp, color = palette.tertiaryLabel)
                    }
                }
            ) {
                when (selectedTab) {
                    AppTab.Home -> HomeList(
                        listState = homeListState,
                        insets = insets,
                        isScanning = uiState.scanStatus == ScanStatus.Scanning,
                        updates = updates,
                        installs = uiState.installs
                    )
                    AppTab.Settings -> SettingsList(
                        listState = settingsListState,
                        insets = insets,
                        includeDisabledApps = uiState.includeDisabledApps,
                        onIncludeDisabledAppsChange = viewModel::setIncludeDisabledApps
                    )
                }
            }
        }

        Toolbar(
            backdrop = backdrop,
            title = stringResource(
                if (selectedTab == AppTab.Home) R.string.app_name else R.string.settings
            ),
            collapsed = collapsed,
            topInset = insets.calculateTopPadding(),
            menuExpanded = menuExpanded,
            onMenuExpandedChange = { menuExpanded = it },
            onPickBundle = { bundlePicker.launch(arrayOf("*/*")) }
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = insets.calculateBottomPadding() + TabBarHeight + 24.dp)
        ) { data ->
            Text(
                text = data.visuals.message,
                style = Ios.text.subheadline,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = ScreenInset)
                    .clip(CapsuleCorner)
                    .background(Color(0xE61C1C1E))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }

        TabBar(
            backdrop = backdrop,
            selectedTab = selectedTab,
            bottomInset = insets.calculateBottomPadding(),
            modifier = Modifier.align(Alignment.BottomCenter),
            onSelect = { tab ->
                when {
                    tab != selectedTab -> selectedTab = tab
                    tab == AppTab.Home && homeListState.canScrollBackward ->
                        coroutineScope.launch { homeListState.animateScrollToItem(0) }
                    else -> Unit
                }
            }
        )
    }
}

private fun LazyListState.isCollapsed(threshold: Int): Boolean =
    firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > threshold

@Composable
private fun HomeList(
    listState: LazyListState,
    insets: PaddingValues,
    isScanning: Boolean,
    updates: List<Pair<InstalledApp, AppUpdateInfo>>,
    installs: Map<String, InstallState>
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = insets.calculateTopPadding() + ToolbarHeight,
            bottom = insets.calculateBottomPadding() + TabBarHeight + 32.dp
        )
    ) {
        item(key = "title") { LargeTitle(stringResource(R.string.app_name)) }

        items(installs.values.toList(), key = InstallState::appName) { state ->
            IosCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(RowInset),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.appName,
                        style = Ios.text.body,
                        color = Ios.palette.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    ActivityIndicator(size = 20.dp, color = Ios.palette.tertiaryLabel)
                }
            }
        }

        item(key = "status") {
            SectionHeader(
                text = when {
                    isScanning -> stringResource(R.string.checking_for_updates)
                    updates.isEmpty() -> stringResource(R.string.all_up_to_date)
                    else -> pluralStringResource(R.plurals.updates_found, updates.size, updates.size)
                },
                busy = isScanning
            )
        }

        items(items = updates, key = { it.first.packageName }) { pair ->
            IosCard(modifier = Modifier.animateItem()) {
                UpdateRow(app = pair.first, update = pair.second)
            }
        }
    }
}

@Composable
private fun SettingsList(
    listState: LazyListState,
    insets: PaddingValues,
    includeDisabledApps: Boolean,
    onIncludeDisabledAppsChange: (Boolean) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = insets.calculateTopPadding() + ToolbarHeight,
            bottom = insets.calculateBottomPadding() + TabBarHeight + 32.dp
        )
    ) {
        item(key = "title") { LargeTitle(stringResource(R.string.settings)) }
        item(key = "spacer") { Spacer(Modifier.height(8.dp)) }
        item(key = "disabled-apps") {
            IosCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RowInset, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.disabled_apps),
                        style = Ios.text.body,
                        color = Ios.palette.label,
                        modifier = Modifier.weight(1f)
                    )
                    IosSwitch(
                        checked = includeDisabledApps,
                        onCheckedChange = onIncludeDisabledAppsChange
                    )
                }
            }
        }
        item(key = "disabled-apps-footer") {
            Text(
                text = stringResource(R.string.disabled_apps_description),
                style = Ios.text.footnote,
                color = Ios.palette.secondaryLabel,
                modifier = Modifier.padding(
                    start = ScreenInset + RowInset,
                    end = ScreenInset
                )
            )
        }
    }
}

@Composable
private fun IosCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenInset)
            .padding(bottom = CardGap)
            .clip(CardCorner)
            .background(Ios.palette.cardFill)
    ) {
        content()
    }
}

@Composable
private fun LargeTitle(text: String) {
    Text(
        text = text,
        style = Ios.text.largeTitle,
        color = Ios.palette.label,
        modifier = Modifier.padding(horizontal = ScreenInset, vertical = 8.dp)
    )
}

@Composable
private fun SectionHeader(text: String, busy: Boolean) {
    Row(
        modifier = Modifier.padding(
            start = ScreenInset + RowInset,
            end = ScreenInset,
            top = 12.dp,
            bottom = 7.dp
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = text.uppercase(Locale.ROOT),
            style = Ios.text.footnote,
            color = Ios.palette.secondaryLabel
        )
        if (busy) ActivityIndicator(size = 14.dp, color = Ios.palette.tertiaryLabel)
    }
}

@Composable
private fun UpdateRow(app: InstalledApp, update: AppUpdateInfo) {
    val palette = Ios.palette
    val context = LocalContext.current
    val iconSizePx = with(LocalDensity.current) { AppIconSize.roundToPx() }
    val iconBitmap by produceState(AppIconCache.peek(app.packageName), app.packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                AppIconCache.load(context, app.packageName, iconSizePx)
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(RowInset),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(AppIconSize).clip(IconCorner)) {
            iconBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = update.appName,
                style = Ios.text.headline,
                color = palette.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.version_current, app.versionName, app.versionCode),
                style = Ios.text.footnote,
                color = palette.secondaryLabel
            )
            Text(
                text = stringResource(
                    R.string.version_latest,
                    update.newVersionName,
                    update.newVersionCode
                ),
                style = Ios.text.footnote,
                color = palette.secondaryLabel
            )
        }
        Spacer(Modifier.width(12.dp))
        CapsuleButton(
            text = stringResource(R.string.update),
            onClick = { openUrlInBrowser(context, update.apkMirrorUrl) }
        )
    }
}

@Composable
private fun CapsuleButton(text: String, onClick: () -> Unit) {
    val palette = Ios.palette
    Text(
        text = text,
        style = Ios.text.headline,
        color = palette.accent,
        maxLines = 1,
        modifier = Modifier
            .clip(CapsuleCorner)
            .background(palette.controlFill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun Toolbar(
    backdrop: GraphicsLayer,
    title: String,
    collapsed: Boolean,
    topInset: Dp,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onPickBundle: () -> Unit
) {
    val palette = Ios.palette
    val barAlpha by animateFloatAsState(if (collapsed) 1f else 0f, tween(220), label = "bar")

    Box(modifier = Modifier.fillMaxWidth().height(topInset + ToolbarHeight)) {
        if (barAlpha > 0f) {
            GlassSurface(
                backdrop = backdrop,
                shape = RectangleShape,
                palette = palette,
                modifier = Modifier.fillMaxSize().alpha(barAlpha)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(Hairline)
                    .alpha(barAlpha)
                    .background(palette.separator)
            )
        }

        Box(modifier = Modifier.fillMaxWidth().padding(top = topInset).height(ToolbarHeight)) {
            Text(
                text = title,
                style = Ios.text.headline,
                color = palette.label,
                modifier = Modifier.align(Alignment.Center).alpha(barAlpha)
            )
            Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = ScreenInset)) {
                GlassSurface(
                    backdrop = backdrop,
                    shape = CircleShape,
                    palette = palette,
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onMenuExpandedChange(true) }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ellipsis),
                        contentDescription = stringResource(R.string.more_options),
                        tint = palette.label,
                        modifier = Modifier.align(Alignment.Center).size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) },
                    shape = MenuCorner,
                    containerColor = palette.cardFill,
                    shadowElevation = 12.dp
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.install_bundle),
                                style = Ios.text.body,
                                color = palette.label
                            )
                        },
                        colors = MenuDefaults.itemColors(textColor = palette.label),
                        onClick = {
                            onMenuExpandedChange(false)
                            onPickBundle()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TabBar(
    backdrop: GraphicsLayer,
    selectedTab: AppTab,
    bottomInset: Dp,
    modifier: Modifier,
    onSelect: (AppTab) -> Unit
) {
    val palette = Ios.palette
    GlassSurface(
        backdrop = backdrop,
        shape = CapsuleCorner,
        palette = palette,
        modifier = modifier
            .padding(bottom = bottomInset + 8.dp)
            .dropShadow(CapsuleCorner) {
                radius = 18.dp.toPx()
                color = palette.shadow
                alpha = 0.16f
                offset = Offset(0f, 6.dp.toPx())
            }
            .height(TabBarHeight)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxHeight()) {
            AppTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                val tint = if (selected) palette.accent else palette.secondaryLabel
                Column(
                    modifier = Modifier
                        .widthIn(min = 88.dp)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(if (selected) tab.selectedIconRes else tab.iconRes),
                        contentDescription = stringResource(tab.labelRes),
                        tint = tint,
                        modifier = Modifier.size(25.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stringResource(tab.labelRes),
                        style = Ios.text.caption2,
                        color = tint
                    )
                }
            }
        }
    }
}

@Composable
private fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = Ios.palette
    val track by animateColorAsState(
        targetValue = if (checked) palette.positive else palette.controlFill,
        animationSpec = tween(180),
        label = "track"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = tween(180),
        label = "thumb"
    )

    Box(
        modifier = Modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(CapsuleCorner)
            .background(track)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .size(27.dp)
                .dropShadow(CircleShape) {
                    radius = 3.dp.toPx()
                    color = Color.Black
                    alpha = 0.22f
                    offset = Offset(0f, 1.dp.toPx())
                }
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun ActivityIndicator(size: Dp, color: Color) {
    val spokes = 12
    val transition = rememberInfiniteTransition(label = "activity")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = spokes.toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spoke"
    )

    Canvas(modifier = Modifier.size(size)) {
        val outer = this.size.minDimension / 2f
        val inner = outer * 0.46f
        val stroke = outer * 0.22f
        repeat(spokes) { index ->
            val position = (index + progress.toInt()) % spokes
            rotate(degrees = index * 360f / spokes) {
                drawLine(
                    color = color.copy(alpha = 0.15f + 0.85f * position / (spokes - 1f)),
                    start = Offset(center.x, center.y - inner),
                    end = Offset(center.x, center.y - outer),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private fun openUrlInBrowser(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}
