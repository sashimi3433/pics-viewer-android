package dev.sashimi.picsviewer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sashimi.picsviewer.LogItem
import dev.sashimi.picsviewer.UiState
import dev.sashimi.picsviewer.model.DeviceState
import dev.sashimi.picsviewer.model.PedestrianSignal
import dev.sashimi.picsviewer.model.SignalState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    state: UiState,
    permissionGranted: Boolean,
    onSettings: () -> Unit,
    onMap: () -> Unit,
    onToggleScan: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onPin: (String, Int) -> Unit,
    onDemo: () -> Unit,
    onShareLogs: () -> Unit,
    onRequestBluetooth: () -> Unit,
) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(500)
        }
    }
    val device = state.selected

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 6.dp, bottom = 28.dp)
    ) {
        Header(onSettings = onSettings, onMap = onMap)

        Spacer(Modifier.height(14.dp))
        StatusCard(
            state = state,
            permissionGranted = permissionGranted,
            onToggleScan = onToggleScan,
            onDemoToggle = onDemo,
            onRequestBluetooth = onRequestBluetooth,
        )

        if (state.message != null) {
            Spacer(Modifier.height(10.dp))
            MessageBanner(state.message)
        }

        Spacer(Modifier.height(14.dp))

        if (device == null) {
            EmptyState(state = state, onDemo = onDemo)
        } else {
            SignalPanel(
                device = device,
                pin = state.pinOf(device),
                stale = now - device.lastSeen > 5_000L,
                now = now,
                onPin = { index -> onPin(device.intersectionId, index) },
            )
        }

        if (state.devices.size > 1) {
            Spacer(Modifier.height(14.dp))
            OtherDevices(
                devices = state.devices.filter { it.key != device?.key },
                now = now,
                onSelect = onSelectDevice,
            )
        }

        Spacer(Modifier.height(14.dp))
        LogCard(logs = state.logs, onShare = onShareLogs)
    }
}

@Composable
private fun Header(onSettings: () -> Unit, onMap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "ADVANCED PICS",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "信号ビューア",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        IconButton(
            onClick = onMap,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Icon(
                Icons.Filled.Place,
                contentDescription = "対応交差点マップ",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "設定",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusCard(
    state: UiState,
    permissionGranted: Boolean,
    onToggleScan: () -> Unit,
    onDemoToggle: () -> Unit,
    onRequestBluetooth: () -> Unit,
) {
    val label: String
    val detail: String
    val tone: Color
    when {
        !permissionGranted -> {
            label = "Bluetooth の権限が必要"
            detail = "許可すると周囲の信号を探します"
            tone = SignalAmber
        }
        !state.bluetoothOn -> {
            label = "Bluetooth がオフ"
            detail = "オンにすると受信を開始します"
            tone = SignalAmber
        }
        state.demo -> {
            label = "デモ表示中"
            detail = "サンプルデータで表示を確認しています"
            tone = MaterialTheme.colorScheme.primary
        }
        state.scanning -> {
            label = "スキャン中"
            detail = "高度化PICSのアドバタイズを待っています"
            tone = SignalGreen
        }
        else -> {
            label = "停止中"
            detail = "再開すると受信します"
            tone = SignalIdle
        }
    }

    GlassCard {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulsingDot(color = tone, pulsing = state.scanning && !state.demo)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                state.demo -> TextButton(onClick = onDemoToggle) { Text("終了") }
                !permissionGranted -> TextButton(onClick = onRequestBluetooth) { Text("許可") }
                !state.bluetoothOn -> TextButton(onClick = onRequestBluetooth) { Text("オンにする") }
                else -> TextButton(onClick = onToggleScan) {
                    Text(if (state.scanning) "停止" else "開始")
                }
            }
        }
    }
}

@Composable
private fun MessageBanner(message: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = SignalAmber.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, SignalAmber.copy(alpha = 0.35f)),
    ) {
        Text(
            message,
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EmptyState(state: UiState, onDemo: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RadarPulse()
        Spacer(Modifier.height(18.dp))
        Text(
            if (state.scanning) "信号を探しています" else "受信を待っています",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "高度化PICS対応の信号機の近くでお待ちください。\n見つかると信号の色と残り時間が表示されます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        TextButton(onClick = onDemo) {
            Text(if (state.demo) "デモを終了" else "デモで表示を確認")
        }
    }
}

@Composable
private fun RadarPulse() {
    val transition = rememberInfiniteTransition(label = "radar")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "radarProgress",
    )
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(148.dp)) {
        repeat(3) { index ->
            val t = (progress + index / 3f) % 1f
            drawCircle(
                color = accent.copy(alpha = (1f - t) * 0.35f),
                radius = size.minDimension / 2f * t,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        drawCircle(color = accent.copy(alpha = 0.18f), radius = size.minDimension / 2f)
        drawCircle(color = accent, radius = 6.dp.toPx())
    }
}

@Composable
private fun SignalPanel(
    device: DeviceState,
    pin: Int?,
    stale: Boolean,
    now: Long,
    onPin: (Int) -> Unit,
) {
    val signals = device.signals.orEmpty()

    GlassCard {
        Column(Modifier.fillMaxWidth().padding(18.dp).alpha(if (stale) 0.62f else 1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "交差点 ${device.intersectionId}",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        device.identifier?.ifEmpty { null } ?: "送信機ID 受信待ち",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RssiBadge(device.rssi, stale)
            }

            Spacer(Modifier.height(16.dp))

            if (signals.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().height(110.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "信号データを待っています",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (pin != null && signals.getOrNull(pin) != null) {
                HeroChannel(signal = signals[pin], index = pin, onPin = { onPin(pin) })
                Spacer(Modifier.height(14.dp))
                CompactChannels(signals = signals, pinned = pin, onPin = onPin)
            } else {
                ChannelGrid(signals = signals, onPin = onPin)
                Spacer(Modifier.height(10.dp))
                Text(
                    "自分の向きのランプをタップすると注目できます",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))

            InfoLine("送信機", device.identifier?.ifEmpty { null } ?: "—", mono = true)
            InfoLine("座標", device.coordinateLabel, mono = true)
            InfoLine("最終受信", "${relativeTime(device.lastSeen, now)} ・ ${device.packets} パケット")
            InfoLine("生データ", device.lastRaw, mono = true)
        }
    }
}

@Composable
private fun HeroChannel(signal: PedestrianSignal, index: Int, onPin: () -> Unit) {
    val color = if (signal.hasData) signal.state.color() else SignalIdle
    val pulsing = signal.state == SignalState.BLINK_GREEN
    val transition = rememberInfiniteTransition(label = "blink")
    val blinkAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
        label = "blinkAlpha",
    )
    val labelColor = if (pulsing) color.copy(alpha = blinkAlpha) else color

    Surface(
        onClick = onPin,
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SignalRing(
                level = signal.level,
                signalColor = color,
                size = 196.dp,
                strokeFraction = 0.075f,
                indeterminate = signal.hasData && signal.level == null,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (signal.hasData) signal.state.label else "—",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = labelColor,
                    )
                    Text(
                        "残り ${signal.level?.let { it + 1 } ?: "–"}/8",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // 真下は常に信号色（青／赤）を維持する帯
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = color,
                contentColor = onSignalColor(color),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "チャンネル ${index + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (signal.hasData) signal.state.label else "データなし",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "タップで注目を解除",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChannelGrid(signals: List<PedestrianSignal>, onPin: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        signals.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { colIndex, signal ->
                    val index = rowIndex * 2 + colIndex
                    ChannelTile(
                        signal = signal,
                        modifier = Modifier.weight(1f),
                        onClick = { onPin(index) },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChannelTile(
    signal: PedestrianSignal,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color = if (signal.hasData) signal.state.color() else SignalIdle.copy(alpha = 0.6f)
    Surface(
        onClick = onClick,
        modifier = modifier.height(118.dp),
        shape = RoundedCornerShape(26.dp),
        color = color.copy(alpha = if (isSystemInDarkTheme()) 0.10f else 0.07f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f)),
    ) {
        Column(
            Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SignalRing(
                level = signal.level,
                signalColor = color,
                size = 64.dp,
                strokeFraction = 0.13f,
                indeterminate = signal.hasData && signal.level == null,
            ) {
                Text(
                    if (signal.level != null) "${signal.level + 1}" else "–",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (signal.hasData) signal.state.label else "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (signal.hasData) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactChannels(
    signals: List<PedestrianSignal>,
    pinned: Int,
    onPin: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        signals.forEachIndexed { index, signal ->
            val color = if (signal.hasData) signal.state.color() else SignalIdle.copy(alpha = 0.5f)
            val active = index == pinned
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onPin(index) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SignalRing(
                    level = signal.level,
                    signalColor = if (active) color else color.copy(alpha = 0.45f),
                    size = 30.dp,
                    strokeFraction = 0.16f,
                    indeterminate = signal.hasData && signal.level == null,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SignalLamp(
    color: Color,
    size: androidx.compose.ui.unit.Dp,
    pulsing: Boolean = false,
    dim: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "lamp")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
        label = "lampPulse",
    )
    val animatedColor by animateColorAsState(color, tween(220), label = "lampColor")
    val a = (if (pulsing) pulse else 1f) * (if (dim) 0.45f else 1f)

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(animatedColor.copy(alpha = 0.42f * a), Color.Transparent)
                    ),
                    CircleShape,
                )
        )
        Box(
            Modifier
                .size(size * 0.52f)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            animatedColor.copy(alpha = 0.95f * a),
                            animatedColor.copy(alpha = 0.68f * a),
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.3f * a), CircleShape)
        )
    }
}

@Composable
private fun RssiBadge(rssi: Int, stale: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Text(
            if (stale) "受信待ち" else "$rssi dBm",
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OtherDevices(
    devices: List<DeviceState>,
    now: Long,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "他の送信機",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        devices.take(4).forEach { device ->
            Surface(
                onClick = { onSelect(device.key) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            device.intersectionId,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            device.coordinateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "${device.rssi} dBm ・ ${relativeTime(device.lastSeen, now)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LogCard(logs: List<LogItem>, onShare: () -> Unit) {
    GlassCard {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "受信ログ",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${logs.size} 件",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onShare, enabled = logs.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "受信ログを共有",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (logs.isEmpty()) {
                Text(
                    "まだ受信していません",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                val formatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.JAPAN) }
                logs.take(6).forEach { item ->
                    Column(Modifier.padding(vertical = 5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatter.format(Date(item.time)),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${item.frame.typeLabel} ・ ${item.frame.intersectionId} ・ ${item.rssi}dBm",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            item.frame.raw.joinToString(" ") { String.format(Locale.ROOT, "%02X", it) },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            Modifier.width(76.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun GlassCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(
            alpha = if (isSystemInDarkTheme()) 0.55f else 0.92f
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        content()
    }
}

@Composable
private fun PulsingDot(color: Color, pulsing: Boolean) {
    val transition = rememberInfiniteTransition(label = "dot")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dotPulse",
    )
    Box(
        Modifier
            .size(11.dp)
            .alpha(if (pulsing) pulse else 1f)
            .background(color, CircleShape)
    )
}

private fun remainingLabel(signal: PedestrianSignal): String = when {
    !signal.hasData -> "データなし"
    signal.level != null -> "残り時間 ${signal.level + 1}/8"
    else -> "残り時間 不明"
}

private fun relativeTime(then: Long, now: Long): String {
    val seconds = ((now - then) / 1000).coerceAtLeast(0)
    return when {
        seconds < 1 -> "たった今"
        seconds < 60 -> "${seconds}秒前"
        else -> "${seconds / 60}分前"
    }
}

/**
 * 残り時間の8段階バー。公式アプリ「信GO!」も秒数ではなくバーで表示している。
 */
@Composable
private fun LevelBar(
    level: Int?,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    segments: Int = 8,
) {
    val filled = (level ?: -1) + 1
    Row(
        modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(segments) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index < filled) color.copy(alpha = 0.92f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
            )
        }
    }
}

/** 信号色の上に乗せる文字色（明るい色なら濃色、暗い色なら白） */
private fun onSignalColor(color: Color): Color =
    if (color.luminance() > 0.55f) Color(0xFF0B1220) else Color.White
