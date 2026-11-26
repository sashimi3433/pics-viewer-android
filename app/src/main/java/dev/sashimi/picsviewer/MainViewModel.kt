package dev.sashimi.picsviewer

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.sashimi.picsviewer.ble.PicsScanner
import dev.sashimi.picsviewer.model.DeviceState
import dev.sashimi.picsviewer.model.PICS_CHANNEL_COUNT
import dev.sashimi.picsviewer.model.PedestrianSignal
import dev.sashimi.picsviewer.model.PicsFrame
import dev.sashimi.picsviewer.model.PicsParser
import dev.sashimi.picsviewer.model.SignalState
import dev.sashimi.picsviewer.model.toHexString
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

data class LogItem(
    val time: Long,
    val address: String,
    val rssi: Int,
    val frame: PicsFrame,
)

data class UiState(
    val scanning: Boolean = false,
    val bluetoothOn: Boolean = true,
    val permissionGranted: Boolean = false,
    val demo: Boolean = false,
    val devices: List<DeviceState> = emptyList(),
    val selectedKey: String? = null,
    val pins: Map<String, Int> = emptyMap(),
    val logs: List<LogItem> = emptyList(),
    val ttsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val message: String? = null,
) {
    /** 画面に出す送信機。選択がなければ最後に受信したもの。 */
    val selected: DeviceState?
        get() = devices.firstOrNull { it.key == selectedKey }
            ?: devices.maxByOrNull { it.lastSeen }

    fun pinOf(device: DeviceState?): Int? {
        if (device == null) return null
        return pins[device.intersectionId]
    }

    fun pinnedSignal(device: DeviceState?): PedestrianSignal? {
        val index = pinOf(device) ?: return null
        return device?.signals?.getOrNull(index)
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val MAX_LOG = 200
        private const val STALE_AFTER_MS = 5_000L
    }

    private val prefs = app.getSharedPreferences("pics_viewer", Context.MODE_PRIVATE)
    private val scanner = PicsScanner(app)

    private val _state = MutableStateFlow(
        UiState(
            ttsEnabled = prefs.getBoolean("tts", true),
            hapticsEnabled = prefs.getBoolean("haptics", true),
            keepScreenOn = prefs.getBoolean("keep_screen_on", true),
            pins = loadPins(),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var demoJob: Job? = null

    fun isStale(device: DeviceState, now: Long): Boolean = now - device.lastSeen > STALE_AFTER_MS

    fun setPermission(granted: Boolean) {
        _state.update { it.copy(permissionGranted = granted) }
        refreshBluetooth()
    }

    fun refreshBluetooth() {
        _state.update { it.copy(bluetoothOn = scanner.isBluetoothEnabled()) }
    }

    fun startScan() {
        if (_state.value.demo) return
        if (_state.value.scanning) return
        if (!_state.value.permissionGranted) return
        refreshBluetooth()
        val ok = scanner.start(
            onData = { address, rssi, data -> ingest(address, rssi, data) },
            onFailure = { message ->
                _state.update { it.copy(scanning = false, message = message) }
            },
        )
        _state.update {
            it.copy(
                scanning = ok,
                bluetoothOn = scanner.isBluetoothEnabled(),
                message = if (ok) null else it.message,
            )
        }
    }

    fun stopScan() {
        scanner.stop()
        _state.update { it.copy(scanning = false) }
    }

    fun selectDevice(key: String) {
        _state.update { it.copy(selectedKey = key) }
    }

    fun togglePin(intersectionId: String, index: Int) {
        val current = _state.value.pins[intersectionId]
        val next = if (current == index) null else index
        _state.update {
            it.copy(pins = if (next == null) it.pins - intersectionId else it.pins + (intersectionId to next))
        }
        prefs.edit().apply {
            if (next == null) remove("pin_$intersectionId") else putInt("pin_$intersectionId", next)
        }.apply()
    }

    fun setTts(enabled: Boolean) {
        prefs.edit().putBoolean("tts", enabled).apply()
        _state.update { it.copy(ttsEnabled = enabled) }
    }

    fun setHaptics(enabled: Boolean) {
        prefs.edit().putBoolean("haptics", enabled).apply()
        _state.update { it.copy(hapticsEnabled = enabled) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean("keep_screen_on", enabled).apply()
        _state.update { it.copy(keepScreenOn = enabled) }
    }

    fun clearLogs() {
        _state.update { it.copy(logs = emptyList()) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    fun setDemo(enabled: Boolean) {
        if (enabled == _state.value.demo) return
        if (enabled) {
            stopScan()
            _state.update { it.copy(demo = true, selectedKey = "demo-1", devices = emptyList()) }
            startDemo()
        } else {
            stopDemo()
            _state.update { it.copy(demo = false, devices = emptyList(), selectedKey = null) }
        }
    }

    fun logText(): String {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.JAPAN)
        val logs = _state.value.logs
        return buildString {
            appendLine("# PICS ビューア 受信ログ")
            appendLine("# ${logs.size} 件 / ${df.format(Date(System.currentTimeMillis()))} 時点の最新 ${logs.size} 件")
            logs.asReversed().forEach { item ->
                val f = item.frame
                appendLine(
                    "${df.format(Date(item.time))}  ${item.address}  RSSI ${item.rssi}dBm  " +
                        "交差点 ${f.intersectionId}  Type ${f.type}(${f.typeLabel})  ID ${f.messageId}"
                )
                appendLine("  ${f.raw.toHexString()}")
            }
        }
    }

    // ---- 内部処理 ----

    private fun loadPins(): Map<String, Int> = prefs.all
        .filterKeys { it.startsWith("pin_") }
        .mapNotNull { (key, value) ->
            val id = key.removePrefix("pin_")
            val index = value as? Int ?: return@mapNotNull null
            id to index
        }
        .toMap()

    private fun ingest(key: String, rssi: Int, data: ByteArray) {
        val frame = PicsParser.parse(data) ?: return
        val now = System.currentTimeMillis()
        _state.update { state ->
            val prev = state.devices.firstOrNull { it.key == key }
            val base = prev ?: DeviceState(key = key, intersectionId = frame.intersectionId)
            val merged = base.copy(
                intersectionId = frame.intersectionId.ifEmpty { base.intersectionId },
                identifier = frame.identifier?.takeIf { it.isNotEmpty() } ?: base.identifier,
                latitude = frame.latitude ?: base.latitude,
                longitude = frame.longitude ?: base.longitude,
                signals = if (frame.signals.isNotEmpty()) frame.signals else base.signals,
                rssi = rssi,
                lastSeen = now,
                packets = base.packets + 1,
                lastType = frame.type,
                lastRaw = data.toHexString(),
            )
            val devices = (state.devices.filterNot { it.key == key } + merged)
                .sortedByDescending { it.lastSeen }
            val logs = (listOf(LogItem(now, key, rssi, frame)) + state.logs).take(MAX_LOG)
            state.copy(devices = devices, logs = logs)
        }
    }

    private fun startDemo() {
        demoJob?.cancel()
        demoJob = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                emitDemo("demo-1", "DEADBEEF", tick, phaseOffset = 0, rssiBase = -52, lat = 35_280_093, lon = 139_669_927)
                emitDemo("demo-2", "00D5F5E3", tick, phaseOffset = 14, rssiBase = -78, lat = 35_280_734, lon = 139_669_405)
                tick++
                delay(500)
            }
        }
    }

    private fun stopDemo() {
        demoJob?.cancel()
        demoJob = null
    }

    private fun emitDemo(
        key: String,
        intersectionId: String,
        tick: Int,
        phaseOffset: Int,
        rssiBase: Int,
        lat: Int,
        lon: Int,
    ) {
        val rssi = rssiBase - Random.nextInt(0, 6)
        val seconds = tick / 2

        if (tick % 8 == 0) {
            ingest(key, rssi, demoIdentifier(intersectionId, tick))
        }
        if (tick % 6 == 0) {
            ingest(key, rssi, demoLocation(intersectionId, tick, lat, lon))
        }
        ingest(key, rssi, demoSignals(intersectionId, tick, seconds + phaseOffset))
    }

    private fun demoSignals(intersectionId: String, tick: Int, phase: Int): ByteArray {
        val data = ByteArray(16)
        data[0] = 0x58
        data[1] = 0x01
        data[2] = 0x02
        data[3] = (tick and 0xFF).toByte()
        writeIntersection(data, intersectionId)

        val signals = (0 until PICS_CHANNEL_COUNT).map { channel ->
            val p = (phase + channel * 7) % 28
            when {
                p < 10 -> PedestrianSignal(SignalState.RED, (9 - p).coerceIn(0, 7))
                p < 14 -> PedestrianSignal(SignalState.GREEN, (13 - p).coerceIn(0, 7))
                p < 18 -> PedestrianSignal(SignalState.BLINK_GREEN, null)
                else -> PedestrianSignal(SignalState.RED, (27 - p).coerceIn(0, 7))
            }
        }
        signals.forEachIndexed { index, signal ->
            data[10 + index] = encodeSignal(signal).toByte()
        }
        return data
    }

    private fun encodeSignal(signal: PedestrianSignal): Int {
        val code = when (signal.state) {
            SignalState.NO_SIGNAL -> 0
            SignalState.RED -> 1
            SignalState.BLINK_GREEN -> 2
            SignalState.GREEN -> 3
            SignalState.OUT_OF_SCOPE -> 4
            SignalState.UNKNOWN -> 0x0F
        }
        val high = signal.level ?: 0x0F
        return (high shl 4) or code
    }

    private fun demoIdentifier(intersectionId: String, tick: Int): ByteArray {
        val data = ByteArray(24)
        data[0] = 0x58
        data[1] = 0x01
        data[2] = 0x00
        data[3] = (tick and 0xFF).toByte()
        writeIntersection(data, intersectionId)
        val name = "UTMS_PICS0001".toByteArray(Charsets.US_ASCII)
        name.copyInto(data, 10, 0, minOf(name.size, 14))
        return data
    }

    private fun demoLocation(intersectionId: String, tick: Int, lat: Int, lon: Int): ByteArray {
        val data = ByteArray(18)
        data[0] = 0x58
        data[1] = 0x01
        data[2] = 0x01
        data[3] = (tick and 0xFF).toByte()
        writeIntersection(data, intersectionId)
        data[10] = (lat ushr 24).toByte()
        data[11] = (lat ushr 16).toByte()
        data[12] = (lat ushr 8).toByte()
        data[13] = lat.toByte()
        data[14] = (lon ushr 24).toByte()
        data[15] = (lon ushr 16).toByte()
        data[16] = (lon ushr 8).toByte()
        data[17] = lon.toByte()
        return data
    }

    private fun writeIntersection(data: ByteArray, intersectionId: String) {
        val clean = intersectionId.padStart(8, '0').takeLast(8)
        for (i in 0 until 4) {
            data[6 + i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
