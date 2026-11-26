package dev.sashimi.picsviewer.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.sashimi.picsviewer.model.SignalState

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

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC2FF),
    onPrimary = Color(0xFF07131F),
    secondary = Color(0xFF8FE3B0),
    background = Color(0xFF05070D),
    onBackground = Color(0xFFE9EEF8),
    surface = Color(0xFF0E1526),
    onSurface = Color(0xFFE9EEF8),
    surfaceVariant = Color(0xFF1A2338),
    onSurfaceVariant = Color(0xFF9AA6BC),
    outline = Color(0xFF2A3650),
    outlineVariant = Color(0xFF1C2740),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2C5FD6),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF1F8A4C),
    background = Color(0xFFF3F6FC),
    onBackground = Color(0xFF0E1420),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101522),
    surfaceVariant = Color(0xFFE7ECF6),
    onSurfaceVariant = Color(0xFF5A6478),
    outline = Color(0xFFC3CCDC),
    outlineVariant = Color(0xFFDCE3EF),
)

private val PicsShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/** M3E っぽさを出すための、太めの見出し */
private val PicsTypography: Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Black),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
fun PicsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, shapes = PicsShapes, typography = PicsTypography) {
        // 画面全体の既定文字色をテーマ側で決める（Text が黒固定になるのを防ぐ）
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
            contentColor = colors.onBackground,
            content = content,
        )
    }
}
