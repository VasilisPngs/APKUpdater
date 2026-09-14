package com.android.apkupdater.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import com.android.apkupdater.ui.theme.Hairline
import com.android.apkupdater.ui.theme.IosPalette

private val BlurRadius = 32.dp

internal fun Modifier.captureBackdrop(backdrop: GraphicsLayer, background: Color): Modifier =
    drawWithContent {
        backdrop.record(this, layoutDirection, size.toIntSize()) {
            drawRect(background)
            this@drawWithContent.drawContent()
        }
        drawLayer(backdrop)
    }

@Composable
internal fun GlassSurface(
    backdrop: GraphicsLayer,
    shape: Shape,
    palette: IosPalette,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val layer = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clip(shape)
            .drawBehind {
                val radius = BlurRadius.toPx()
                layer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                layer.record(this, layoutDirection, size.toIntSize()) {
                    translate(-origin.x, -origin.y) { drawLayer(backdrop) }
                }
                drawLayer(layer)
                drawRect(palette.material)
            }
            .innerShadow(shape) {
                this.radius = 2.dp.toPx()
                color = palette.materialSheen
                offset = Offset(0f, 1.dp.toPx())
            }
            .innerShadow(shape) {
                this.radius = 4.dp.toPx()
                color = palette.materialRim
                alpha = 0.5f
                offset = Offset(0f, (-2).dp.toPx())
            }
            .border(Hairline, palette.materialRim, shape),
        content = content
    )
}
