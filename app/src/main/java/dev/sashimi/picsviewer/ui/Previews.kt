package dev.sashimi.picsviewer.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.sashimi.picsviewer.UiState
import dev.sashimi.picsviewer.model.DeviceState
import dev.sashimi.picsviewer.model.PedestrianSignal
import dev.sashimi.picsviewer.model.SignalState

private fun sampleState(pinned: Boolean): UiState {
    val device = DeviceState(
        key = "demo-1",
        intersectionId = "DEADBEEF",
        identifier = "PICS-0001",
        latitude = 35.280093,
        longitude = 139.669927,
        signals = listOf(
            PedestrianSignal(SignalState.GREEN, 5),
            PedestrianSignal(SignalState.RED, 2),
            PedestrianSignal(SignalState.BLINK_GREEN, 0),
            PedestrianSignal(SignalState.OUT_OF_SCOPE, null),
        ),
        rssi = -52,
        lastSeen = System.currentTimeMillis(),
        packets = 128,
        lastRaw = "58 01 02 2A 00 00 DE AD BE EF",
    )
    return UiState(
        scanning = true,
        permissionGranted = true,
        devices = listOf(device, device.copy(key = "demo-2", intersectionId = "00D5F5E3", rssi = -78)),
        selectedKey = "demo-1",
        pins = if (pinned) mapOf("DEADBEEF" to 0) else emptyMap(),
    )
}

@Composable
private fun HomePreviewContent(state: UiState) {
    PicsTheme(dynamicColor = false) {
        HomeScreen(
            state = state,
            permissionGranted = true,
            onSettings = {},
            onMap = {},
            onToggleScan = {},
            onSelectDevice = {},
            onPin = { _, _ -> },
            onDemo = {},
            onShareLogs = {},
            onRequestBluetooth = {},
        )
    }
}

@Preview(name = "Home 一覧", widthDp = 412, heightDp = 1400, showBackground = true)
@Composable
private fun HomeGridPreview() = HomePreviewContent(sampleState(pinned = false))

@Preview(
    name = "Home 注目・ダーク",
    widthDp = 412,
    heightDp = 1400,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HomePinnedDarkPreview() = HomePreviewContent(sampleState(pinned = true))

@Preview(name = "Home 文字 2 倍", widthDp = 412, heightDp = 2000, showBackground = true, fontScale = 2f)
@Composable
private fun HomeLargeFontPreview() = HomePreviewContent(sampleState(pinned = false))

@Preview(name = "Home 空", widthDp = 412, heightDp = 900, showBackground = true)
@Composable
private fun HomeEmptyPreview() = HomePreviewContent(UiState(scanning = true, permissionGranted = true))

@Preview(name = "設定", widthDp = 412, heightDp = 1200, showBackground = true)
@Composable
private fun SettingsPreview() {
    PicsTheme(dynamicColor = false) {
        SettingsScreen(
            state = UiState(keepScreenOn = true, demo = false),
            onBack = {},
            onTts = {},
            onHaptics = {},
            onKeepScreenOn = {},
            onDemo = {},
            onShareLogs = {},
            onClearLogs = {},
        )
    }
}
