package dev.sashimi.picsviewer.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.sashimi.picsviewer.model.SignalState

// 信号の色は実際の信号機に合わせた固定色（テーマ色にはしない）
val SignalRed = Color(0xFFFF453A)
val SignalGreen = Color(0xFF34D96B)
val SignalAmber = Color(0xFFFFB020)
val SignalIdle = Color(0xFF747F94)

fun SignalState.color(): Color = when (this) {
    SignalState.RED -> SignalRed
    SignalState.BLINK_GREEN -> SignalGreen
    SignalState.GREEN -> SignalGreen
    SignalState.NO_SIGNAL -> SignalIdle
    SignalState.OUT_OF_SCOPE -> SignalIdle
    SignalState.UNKNOWN -> SignalIdle
}

// Android 12 未満（ダイナミックカラーなし）向けの静的スキーム。
// M3E のコンポーネントは *Container / surfaceContainer* ロールを使うので全て埋める。
private val LightColors = lightColorScheme(
    primary = Color(0xFF2C5FD6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDAE2FF),
    onPrimaryContainer = Color(0xFF0E2C6B),
    secondary = Color(0xFF1F7A47),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB4F1C6),
    onSecondaryContainer = Color(0xFF00210E),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBC),
    onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF191C22),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF191C22),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E9),
    surfaceDim = Color(0xFFD9D9E0),
    surfaceBright = Color(0xFFF9F9FF),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB1C5FF),
    onPrimary = Color(0xFF002C71),
    primaryContainer = Color(0xFF1F4BB8),
    onPrimaryContainer = Color(0xFFDAE2FF),
    secondary = Color(0xFF98D5AB),
    onSecondary = Color(0xFF00391C),
    secondaryContainer = Color(0xFF005230),
    onSecondaryContainer = Color(0xFFB4F1C6),
    tertiary = Color(0xFFFFB86E),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDCBC),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    surfaceDim = Color(0xFF111318),
    surfaceBright = Color(0xFF37393E),
    outline = Color(0xFF8F9099),
    outlineVariant = Color(0xFF44474F),
)

@Composable
fun PicsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    // M3 Expressive の標準値（形・文字・動き）をそのまま使う
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(),
        typography = Typography(),
        content = content,
    )
}
