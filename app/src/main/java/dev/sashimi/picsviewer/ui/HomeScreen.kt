package dev.sashimi.picsviewer.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // セグメント化リストは surfaceContainer の上に置く（M3E の既定）
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text("高度化PICSビューア", Modifier.semantics { heading() }) },
                actions = {
                    FilledTonalIconButton(onClick = onMap, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Filled.Place, contentDescription = "対応交差点マップ")
                    }
                    Spacer(Modifier.width(4.dp))
                    FilledTonalIconButton(onClick = onSettings, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Filled.Settings, contentDescription = "設定")
                    }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val main: @Composable () -> Unit = {
            StatusItem(
                state = state,
                permissionGranted = permissionGranted,
                onToggleScan = onToggleScan,
                onDemoToggle = onDemo,
                onRequestBluetooth = onRequestBluetooth,
            )

            if (state.message != null) MessageBanner(state.message)

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
        }
        val side: @Composable () -> Unit = {
            if (state.devices.size > 1) {
                OtherDevices(
                    devices = state.devices.filter { it.key != device?.key },
                    now = now,
                    onSelect = onSelectDevice,
                )
            }

            LogCard(logs = state.logs, onShare = onShareLogs)
        }

        BoxWithConstraints(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (maxWidth >= TwoPaneMinWidth) {
                // 広げたフォルダブル・タブレット: 信号を左、他の送信機とログを右に並べる
                Row(
                    Modifier.widthIn(max = 1200.dp).fillMaxSize().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    PaneColumn(Modifier.weight(1.1f)) { main() }
                    PaneColumn(Modifier.weight(0.9f)) { side() }
                }
            } else {
                PaneColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    main()
                    side()
                }
            }
        }
    }
}

/** 2 列に分ける幅。広げたフォルダブル（約 840dp）とタブレットが該当する。 */
private val TwoPaneMinWidth = 700.dp

@Composable
private fun PaneColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun StatusItem(
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
    val (action, onAction) = when {
        state.demo -> "終了" to onDemoToggle
        !permissionGranted -> "許可" to onRequestBluetooth
        !state.bluetoothOn -> "オンにする" to onRequestBluetooth
        else -> (if (state.scanning) "停止" else "開始") to onToggleScan
    }

    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        leadingContent = { PulsingDot(color = tone, pulsing = state.scanning && !state.demo) },
        supportingContent = { Text(detail) },
        trailingContent = {
            FilledTonalButton(onClick = onAction, shapes = ButtonDefaults.shapes()) {
                Text(action)
            }
        },
    ) {
        Text(label, style = MaterialTheme.typography.titleMediumEmphasized)
    }
}

@Composable
private fun MessageBanner(message: String) {
    Card(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Text(
            message,
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EmptyState(state: UiState, onDemo: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val reduceMotion = rememberReduceMotion()
        if (reduceMotion) {
            ContainedLoadingIndicator(progress = { 0.7f }, modifier = Modifier.size(96.dp).clearAndSetSemantics {})
        } else {
            ContainedLoadingIndicator(Modifier.size(96.dp).clearAndSetSemantics {})
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (state.scanning) "信号を探しています" else "受信を待っています",
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "高度化PICS対応の信号機の近くでお待ちください。\n見つかると信号の色と残り時間が表示されます。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onDemo, shapes = ButtonDefaults.shapes()) {
            Text(if (state.demo) "デモを終了" else "デモで表示を確認")
        }
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

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp).alpha(if (stale) 0.62f else 1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                    Text(
                        "交差点 ${device.intersectionId}",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        device.identifier?.ifEmpty { null } ?: "送信機ID 受信待ち",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Badge(
                    modifier = Modifier.semantics {
                        contentDescription = if (stale) "受信が途絶えています" else "電波強度 ${device.rssi} dBm"
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(if (stale) "受信待ち" else "${device.rssi} dBm", fontFamily = FontFamily.Monospace)
                }
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
                Spacer(Modifier.height(12.dp))
                CompactChannels(signals = signals, pinned = pin, onPin = onPin)
            } else {
                ChannelGrid(signals = signals, onPin = onPin)
                Spacer(Modifier.height(10.dp))
                Text(
                    "自分の向きのランプをタップすると注目できます",
                    style = MaterialTheme.typography.labelMedium,
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
    val reduceMotion = rememberReduceMotion()
    val pulsing = signal.state == SignalState.BLINK_GREEN && !reduceMotion
    val description = signal.accessibilityDescription(index) +
        if (signal.state == SignalState.BLINK_GREEN) "、点滅中" else ""
    val transition = rememberInfiniteTransition(label = "blink")
    // 点滅は実際の信号機の点滅周期に合わせた表現なので、motion scheme ではなく固定周期にする
    val blinkAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
        label = "blinkAlpha",
    )
    val labelColor = if (pulsing) color.copy(alpha = blinkAlpha) else color

    // 外側 extraLarge(28dp) − 内側余白 → 内側は large(16dp)
    Card(
        onClick = onPin,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 20.dp)
                // 中身はまとめて 1 つの説明にし、変化したら読み上げる
                .clearAndSetSemantics {
                    contentDescription = description
                    stateDescription = "注目中。タップで解除"
                    liveRegion = LiveRegionMode.Polite
                },
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
                        style = MaterialTheme.typography.displaySmallEmphasized,
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
                shape = MaterialTheme.shapes.medium,
                color = color,
                contentColor = onSignalColor(color),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("チャンネル ${index + 1}", style = MaterialTheme.typography.titleSmallEmphasized)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (signal.hasData) signal.state.label else "データなし",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "タップで注目を解除",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChannelGrid(signals: List<PedestrianSignal>, onPin: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        signals.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { colIndex, signal ->
                    val index = rowIndex * 2 + colIndex
                    ChannelTile(
                        signal = signal,
                        index = index,
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
    index: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color = if (signal.hasData) signal.state.color() else SignalIdle.copy(alpha = 0.6f)
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    Card(
        onClick = onClick,
        // 文字を大きくしても切れないよう高さは最小値で指定する
        modifier = modifier.heightIn(min = 118.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f).compositeOver(base)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 118.dp)
                .padding(10.dp)
                .clearAndSetSemantics {
                    contentDescription = signal.accessibilityDescription(index)
                    stateDescription = "タップで注目"
                },
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
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = color,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (signal.hasData) signal.state.label else "—",
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = if (signal.hasData) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
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
            Surface(
                selected = active,
                onClick = { onPin(index) },
                modifier = Modifier.weight(1f).semantics { role = Role.Tab },
                shape = MaterialTheme.shapes.medium,
                color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                contentColor = if (active) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Column(
                    Modifier
                        .padding(vertical = 6.dp)
                        .clearAndSetSemantics { contentDescription = signal.accessibilityDescription(index) },
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
                        style = if (active) {
                            MaterialTheme.typography.labelMediumEmphasized
                        } else {
                            MaterialTheme.typography.labelMedium
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OtherDevices(
    devices: List<DeviceState>,
    now: Long,
    onSelect: (String) -> Unit,
) {
    val shown = devices.take(4)
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        Text(
            "他の送信機",
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp).semantics { heading() },
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
        shown.forEachIndexed { index, device ->
            SegmentedListItem(
                onClick = { onSelect(device.key) },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = shown.size),
                // 文字を大きくしても見出しが潰れないよう、補足は下の行にまとめる
                supportingContent = {
                    Text("${device.coordinateLabel}\n${device.rssi} dBm ・ ${relativeTime(device.lastSeen, now)}")
                },
            ) {
                Text(device.intersectionId, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun LogCard(logs: List<LogItem>, onShare: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "受信ログ",
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${logs.size} 件",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onShare,
                    shapes = IconButtonDefaults.shapes(),
                    enabled = logs.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "受信ログを共有")
                }
            }

            if (logs.isEmpty()) {
                Text(
                    "まだ受信していません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                val formatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.JAPAN) }
                logs.take(6).forEach { item ->
                    Column(Modifier.padding(vertical = 5.dp).semantics(mergeDescendants = true) {}) {
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {}) {
        Text(
            label,
            Modifier.widthIn(min = 76.dp).padding(end = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 文字拡大時に切らずに折り返す
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

@Composable
private fun PulsingDot(color: Color, pulsing: Boolean) {
    val animate = pulsing && !rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "dot")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dotPulse",
    )
    Box(
        Modifier
            .size(12.dp)
            .alpha(if (animate) pulse else 1f)
            .background(color, CircleShape)
    )
}

private fun relativeTime(then: Long, now: Long): String {
    val seconds = ((now - then) / 1000).coerceAtLeast(0)
    return when {
        seconds < 1 -> "たった今"
        seconds < 60 -> "${seconds}秒前"
        else -> "${seconds / 60}分前"
    }
}

/** 信号色の上に乗せる文字色（明るい色なら濃色、暗い色なら白） */
private fun onSignalColor(color: Color): Color =
    if (color.luminance() > 0.55f) Color(0xFF0B1220) else Color.White
