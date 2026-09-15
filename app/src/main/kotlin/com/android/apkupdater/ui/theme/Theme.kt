package com.android.apkupdater.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.pow

@Immutable
data class IosPalette(
    val groupedBackground: Color,
    val cardFill: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val accent: Color,
    val positive: Color,
    val controlFill: Color,
    val material: Color,
    val materialRim: Color,
    val materialSheen: Color,
    val shadow: Color
)

private val LightPalette = IosPalette(
    groupedBackground = Color(0xFFF2F2F7),
    cardFill = Color(0xFFFFFFFF),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x5C3C3C43),
    accent = Color(0xFF007AFF),
    positive = Color(0xFF34C759),
    controlFill = Color(0x1F787880),
    material = Color(0xB8FFFFFF),
    materialRim = Color(0x1F000000),
    materialSheen = Color(0xCCFFFFFF),
    shadow = Color(0xFF000000)
)

private val DarkPalette = IosPalette(
    groupedBackground = Color(0xFF000000),
    cardFill = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0xA6545458),
    accent = Color(0xFF0A84FF),
    positive = Color(0xFF30D158),
    controlFill = Color(0x5C787880),
    material = Color(0xAD1C1C1E),
    materialRim = Color(0x66000000),
    materialSheen = Color(0x2EFFFFFF),
    shadow = Color(0xFF000000)
)

@Immutable
data class IosTextStyles(
    val largeTitle: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val subheadline: TextStyle,
    val footnote: TextStyle,
    val caption2: TextStyle
)

private val TextStyles = IosTextStyles(
    largeTitle = TextStyle(
        fontSize = 34.sp,
        lineHeight = 41.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.37.sp
    ),
    title3 = TextStyle(
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.38.sp
    ),
    headline = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.43).sp
    ),
    body = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.43).sp
    ),
    subheadline = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.23).sp
    ),
    footnote = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.08).sp
    ),
    caption2 = TextStyle(
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.07.sp
    )
)

private const val CornerEdgeExtent = 1.528665f
private const val CornerExponent = 2.479363f
private const val CornerSegments = 24

data class ContinuousCornerShape(val radius: Dp) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val limit = min(size.width, size.height) / (2f * CornerEdgeExtent)
        val corner = min(with(density) { radius.toPx() }, limit)
        if (corner <= 0f) return Outline.Rectangle(Rect(Offset.Zero, size))

        val extent = CornerEdgeExtent * corner
        val width = size.width
        val height = size.height
        val path = Path()

        path.moveTo(extent, 0f)
        path.lineTo(width - extent, 0f)
        path.corner(width, 0f, -1f, 1f, extent, reversed = false)
        path.lineTo(width, height - extent)
        path.corner(width, height, -1f, -1f, extent, reversed = true)
        path.lineTo(extent, height)
        path.corner(0f, height, 1f, -1f, extent, reversed = false)
        path.lineTo(0f, extent)
        path.corner(0f, 0f, 1f, 1f, extent, reversed = true)
        path.close()

        return Outline.Generic(path)
    }

    private fun Path.corner(
        originX: Float,
        originY: Float,
        signX: Float,
        signY: Float,
        extent: Float,
        reversed: Boolean
    ) {
        for (step in 0..CornerSegments) {
            val fraction = step.toFloat() / CornerSegments
            val along = if (reversed) 1f - fraction else fraction
            val inward = (1f - along.pow(CornerExponent)).coerceAtLeast(0f)
            lineTo(
                originX + signX * extent * (1f - along),
                originY + signY * extent * (1f - inward.pow(1f / CornerExponent))
            )
        }
    }
}

val CardCorner = ContinuousCornerShape(20.dp)
val IconCorner = ContinuousCornerShape(12.5.dp)
val MenuCorner = ContinuousCornerShape(14.dp)
val CapsuleCorner = RoundedCornerShape(percent = 50)
val Hairline = 0.5.dp

private val LocalPalette = staticCompositionLocalOf { LightPalette }
private val LocalTextStyles = staticCompositionLocalOf { TextStyles }

object Ios {
    val palette: IosPalette
        @Composable @ReadOnlyComposable get() = LocalPalette.current
    val text: IosTextStyles
        @Composable @ReadOnlyComposable get() = LocalTextStyles.current
}

@Composable
fun ApkUpdaterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent,
            background = palette.groupedBackground,
            surface = palette.cardFill,
            surfaceContainerHighest = palette.cardFill,
            onBackground = palette.label,
            onSurface = palette.label,
            onSurfaceVariant = palette.secondaryLabel,
            outline = palette.separator
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            background = palette.groupedBackground,
            surface = palette.cardFill,
            surfaceContainerHighest = palette.cardFill,
            onBackground = palette.label,
            onSurface = palette.label,
            onSurfaceVariant = palette.secondaryLabel,
            outline = palette.separator
        )
    }

    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalTextStyles provides TextStyles
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(bodyLarge = TextStyles.body),
            content = content
        )
    }
}
