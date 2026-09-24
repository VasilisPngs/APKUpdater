package com.android.apkupdater.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.max
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.apkupdater.R
import com.android.apkupdater.data.model.AppUpdateInfo
import com.android.apkupdater.data.model.InstallState
import com.android.apkupdater.data.model.InstalledApp
import com.android.apkupdater.data.repository.ScanStatus
import com.android.apkupdater.ui.theme.Design
import com.android.apkupdater.ui.theme.DisabledOpacity
import com.android.apkupdater.ui.theme.Motion
import com.android.apkupdater.ui.theme.PressedOpacity
import com.android.apkupdater.ui.theme.PressedScale
import com.android.apkupdater.ui.theme.Radius
import com.android.apkupdater.ui.theme.ShapeL
import com.android.apkupdater.ui.theme.ShapeM
import com.android.apkupdater.ui.theme.ShapePill
import com.android.apkupdater.ui.theme.ShapeS
import com.android.apkupdater.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BarPadding = 12.dp
private val BarContentHeight = 36.dp
private val TabRowHeight = 50.dp
private val TabBarPadding = 6.dp
private val AppIconSize = 44.dp
private val DotSize = 7.dp
private val SwitchWidth = 58.dp
private val SwitchHeight = 34.dp
private val SwitchKnob = 28.dp
private val SwitchInset = 3.dp
private val TabIconSize = 21.dp

private enum class AppTab(val labelRes: Int, val iconRes: Int) {
    Home(R.string.home, R.drawable.ic_home),
    Settings(R.string.settings, R.drawable.ic_settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkUpdaterScreen(
    viewModel: ApkUpdaterViewModel,
    modifier: Modifier = Modifier
) {
    val colors = Design.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Home) }
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

    val topBarHeight = insets.calculateTopPadding() + BarPadding * 2 + BarContentHeight
    val tabBarHeight = TabBarPadding + TabRowHeight +
        max(insets.calculateBottomPadding(), TabBarPadding)

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

    val scanning = uiState.scanStatus == ScanStatus.Scanning

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().captureBackdrop(backdrop, colors.background)) {
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = false,
                onRefresh = viewModel::scanForUpdates,
                state = pullState,
                indicator = {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topBarHeight + Space.l)
                            .alpha(pullState.distanceFraction.coerceIn(0f, 1f))
                            .size(DotSize)
                            .clip(CircleShape)
                            .background(colors.accent)
                    )
                }
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        (
                            fadeIn(tween(Motion.NORMAL, easing = Motion.ease)) +
                                slideInVertically(tween(Motion.NORMAL, easing = Motion.ease)) { it / 24 }
                            ) togetherWith fadeOut(tween(Motion.FAST, easing = Motion.ease))
                    },
                    label = "view"
                ) { tab ->
                    when (tab) {
                        AppTab.Home -> HomeView(
                            listState = homeListState,
                            topInset = topBarHeight,
                            bottomInset = tabBarHeight,
                            scanning = scanning,
                            updates = updates,
                            installs = uiState.installs
                        )
                        AppTab.Settings -> SettingsView(
                            listState = settingsListState,
                            topInset = topBarHeight,
                            bottomInset = tabBarHeight,
                            includeDisabledApps = uiState.includeDisabledApps,
                            onIncludeDisabledAppsChange = viewModel::setIncludeDisabledApps,
                            onPickBundle = { bundlePicker.launch(arrayOf("*/*")) }
                        )
                    }
                }
            }
        }

        TopBar(
            backdrop = backdrop,
            height = topBarHeight,
            topInset = insets.calculateTopPadding(),
            scanning = scanning,
            failed = uiState.scanStatus is ScanStatus.Error,
            count = updates.size
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = tabBarHeight + Space.xl)
        ) { data ->
            BackdropSurface(
                backdrop = backdrop,
                tint = colors.surface3.copy(alpha = 0.78f),
                shape = ShapePill,
                edgeHighlight = colors.glassEdge,
                modifier = Modifier
                    .padding(horizontal = Space.l)
                    .border(Space.hairline, colors.border, ShapePill)
            ) {
                Text(
                    text = data.visuals.message,
                    style = Design.type.muted,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Space.l, vertical = 9.dp)
                )
            }
        }

        TabBar(
            backdrop = backdrop,
            height = tabBarHeight,
            bottomInset = max(insets.calculateBottomPadding(), TabBarPadding),
            selectedTab = selectedTab,
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

@Composable
private fun HomeView(
    listState: LazyListState,
    topInset: Dp,
    bottomInset: Dp,
    scanning: Boolean,
    updates: List<Pair<InstalledApp, AppUpdateInfo>>,
    installs: Map<String, InstallState>
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.l,
            end = Space.l,
            top = topInset + Space.l,
            bottom = bottomInset + Space.xl
        ),
        verticalArrangement = Arrangement.spacedBy(Space.m)
    ) {
        items(installs.values.toList(), key = InstallState::appName) { state ->
            Card(tight = true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s)
                ) {
                    StatusDot(color = Design.colors.accent, pulsing = true)
                    Text(
                        text = state.appName,
                        style = Design.type.h3,
                        color = Design.colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (updates.isEmpty() && !scanning) {
            item(key = "empty") { EmptyState(stringResource(R.string.all_up_to_date)) }
        }

        items(items = updates, key = { it.first.packageName }) { pair ->
            UpdateCard(
                app = pair.first,
                update = pair.second,
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
private fun SettingsView(
    listState: LazyListState,
    topInset: Dp,
    bottomInset: Dp,
    includeDisabledApps: Boolean,
    onIncludeDisabledAppsChange: (Boolean) -> Unit,
    onPickBundle: () -> Unit
) {
    val colors = Design.colors
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.l,
            end = Space.l,
            top = topInset + Space.l,
            bottom = bottomInset + Space.xl
        ),
        verticalArrangement = Arrangement.spacedBy(Space.m)
    ) {
        item(key = "disabled-apps") {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeM)
                        .background(colors.surface2)
                        .border(Space.hairline, colors.border, ShapeM)
                        .padding(horizontal = Space.m, vertical = Space.s),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.disabled_apps),
                        style = Design.type.switchLabel,
                        color = colors.text,
                        modifier = Modifier.weight(1f)
                    )
                    SwitchTrack(
                        checked = includeDisabledApps,
                        onCheckedChange = onIncludeDisabledAppsChange
                    )
                }
                Text(
                    text = stringResource(R.string.disabled_apps_description),
                    style = Design.type.tiny,
                    color = colors.text,
                    modifier = Modifier.padding(horizontal = 13.dp)
                )
            }
        }

        item(key = "install-bundle") {
            Button(
                text = stringResource(R.string.install_bundle),
                onClick = onPickBundle,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Card(
    modifier: Modifier = Modifier,
    tight: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = Design.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeL)
            .background(colors.surface)
            .border(Space.hairline, colors.border, ShapeL)
            .padding(
                horizontal = if (tight) 14.dp else Space.l,
                vertical = if (tight) Space.m else Space.l
            ),
        verticalArrangement = Arrangement.spacedBy(if (tight) Space.s else Space.m)
    ) {
        content()
    }
}

@Composable
private fun UpdateCard(app: InstalledApp, update: AppUpdateInfo, modifier: Modifier) {
    val colors = Design.colors
    val context = LocalContext.current
    val iconSizePx = with(LocalDensity.current) { AppIconSize.roundToPx() }
    val iconBitmap by produceState(AppIconCache.peek(app.packageName), app.packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                AppIconCache.load(context, app.packageName, iconSizePx)
            }
        }
    }

    Card(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s + 2.dp)
        ) {
            Box(modifier = Modifier.size(AppIconSize).clip(ShapeS)) {
                iconBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = update.appName,
                    style = Design.type.h3,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.version_current,
                        app.versionName,
                        app.versionCode
                    ),
                    style = Design.type.tiny,
                    color = colors.text
                )
                Text(
                    text = stringResource(
                        R.string.version_latest,
                        update.newVersionName,
                        update.newVersionCode
                    ),
                    style = Design.type.tiny,
                    color = colors.text
                )
            }
            Button(
                text = stringResource(R.string.update),
                small = true,
                onClick = { openUrlInBrowser(context, update.apkMirrorUrl) }
            )
        }
    }
}

@Composable
private fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    enabled: Boolean = true
) {
    val colors = Design.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PressedScale else 1f,
        animationSpec = tween(Motion.FAST, easing = Motion.ease),
        label = "press"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = Space.touch)
            .scale(scale)
            .alpha(if (enabled) 1f else DisabledOpacity)
            .clip(if (small) ShapeS else ShapeM)
            .background(colors.surface2)
            .border(Space.hairline, colors.border, if (small) ShapeS else ShapeM)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(
                horizontal = if (small) 11.dp else 14.dp,
                vertical = if (small) 7.dp else 10.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = if (small) Design.type.buttonSmall else Design.type.button,
            color = colors.text,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyState(text: String) {
    val colors = Design.colors
    val density = LocalDensity.current
    val stroke = with(density) { Space.hairline.toPx() }
    val dash = remember(density) {
        with(density) { PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) }
    }
    val radius = with(density) { Radius.l.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = colors.border,
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = stroke, pathEffect = dash)
                )
            }
            .padding(horizontal = Space.l, vertical = 34.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = Design.type.muted,
            color = colors.text,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TopBar(
    backdrop: GraphicsLayer,
    height: Dp,
    topInset: Dp,
    scanning: Boolean,
    failed: Boolean,
    count: Int
) {
    val colors = Design.colors
    BackdropSurface(
        backdrop = backdrop,
        tint = colors.background.copy(alpha = 0.82f),
        shape = RectangleShape,
        edgeHighlight = colors.glassEdge,
        modifier = Modifier.fillMaxWidth().height(height)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topInset + BarPadding, bottom = BarPadding)
                .padding(horizontal = Space.l)
                .height(BarContentHeight),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = Design.type.brand,
                color = colors.text
            )
            StatusPill(scanning = scanning, failed = failed, count = count)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(Space.hairline)
                .background(colors.border)
        )
    }
}

@Composable
private fun StatusPill(scanning: Boolean, failed: Boolean, count: Int) {
    val colors = Design.colors
    val dotColor = when {
        failed -> colors.danger
        scanning -> colors.accent
        else -> colors.success
    }
    Row(
        modifier = Modifier
            .clip(ShapePill)
            .background(colors.surface)
            .border(Space.hairline, colors.border, ShapePill)
            .padding(horizontal = Space.m, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(color = dotColor, pulsing = scanning)
        Text(
            text = when {
                scanning -> stringResource(R.string.status_checking)
                count == 0 -> stringResource(R.string.status_up_to_date)
                else -> pluralStringResource(R.plurals.status_updates, count, count)
            },
            style = Design.type.pill,
            color = colors.text,
            maxLines = 1
        )
    }
}

@Composable
private fun StatusDot(color: Color, pulsing: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = Motion.ease),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val factor = if (pulsing) progress else 0f

    Box(
        modifier = Modifier
            .size(DotSize)
            .scale(1f - 0.14f * factor)
            .alpha(1f - 0.55f * factor)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun TabBar(
    backdrop: GraphicsLayer,
    height: Dp,
    bottomInset: Dp,
    selectedTab: AppTab,
    modifier: Modifier,
    onSelect: (AppTab) -> Unit
) {
    val colors = Design.colors
    BackdropSurface(
        backdrop = backdrop,
        tint = colors.background.copy(alpha = 0.88f),
        shape = RectangleShape,
        edgeHighlight = colors.glassEdge,
        modifier = modifier.fillMaxWidth().height(height)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Space.hairline)
                .background(colors.border)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TabBarPadding, bottom = bottomInset)
                .padding(horizontal = Space.s),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AppTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val fade by animateFloatAsState(
                    targetValue = if (pressed) PressedOpacity else 1f,
                    animationSpec = tween(Motion.FAST, easing = Motion.ease),
                    label = "tab"
                )
                val fill by animateColorAsState(
                    targetValue = if (selected) colors.surface2 else Color.Transparent,
                    animationSpec = tween(Motion.NORMAL, easing = Motion.ease),
                    label = "fill"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(TabRowHeight)
                        .alpha(fade)
                        .clip(ShapeM)
                        .background(fill)
                        .clickable(
                            interactionSource = interaction,
                            indication = null
                        ) { onSelect(tab) }
                        .padding(vertical = TabBarPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = null,
                        tint = colors.text,
                        modifier = Modifier.size(TabIconSize)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stringResource(tab.labelRes),
                        style = Design.type.tab,
                        color = colors.text
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchTrack(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = Design.colors
    val track by animateColorAsState(
        targetValue = if (checked) colors.accent else colors.surface3,
        animationSpec = tween(Motion.NORMAL, easing = Motion.ease),
        label = "track"
    )
    val travel by animateDpAsState(
        targetValue = if (checked) SwitchWidth - SwitchKnob - SwitchInset * 2 else 0.dp,
        animationSpec = tween(Motion.NORMAL, easing = Motion.ease),
        label = "knob"
    )

    Box(
        modifier = Modifier
            .size(width = SwitchWidth, height = SwitchHeight)
            .clip(ShapePill)
            .background(track)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(SwitchInset),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(travel.roundToPx(), 0) }
                .size(SwitchKnob)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

private fun openUrlInBrowser(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}
