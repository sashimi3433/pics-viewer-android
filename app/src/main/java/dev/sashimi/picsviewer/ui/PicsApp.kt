package dev.sashimi.picsviewer.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sashimi.picsviewer.MainViewModel
import dev.sashimi.picsviewer.model.SignalState

private val blePermissions: Array<String>
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private enum class Screen { Home, Map, Settings }

private fun hasBlePermissions(context: Context): Boolean =
    blePermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

@Composable
fun PicsApp(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    var granted by remember { mutableStateOf(hasBlePermissions(context)) }
    var screenName by rememberSaveable { mutableStateOf(Screen.Home.name) }
    val screen = Screen.valueOf(screenName)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = hasBlePermissions(context)
        vm.setPermission(granted)
    }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(blePermissions)
        vm.setPermission(granted)
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { vm.refreshBluetooth() }

    // Bluetooth の ON/OFF を監視
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    vm.refreshBluetooth()
                    vm.startScan()
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    // 画面が見えている間だけスキャンする
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, granted, state.demo) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    vm.refreshBluetooth()
                    if (granted && !state.demo) vm.startScan() else vm.stopScan()
                }
                Lifecycle.Event.ON_STOP -> vm.stopScan()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.stopScan()
        }
    }

    // 権限が下りた直後・デモ終了直後にスキャンを再開する
    LaunchedEffect(granted, state.demo) {
        if (state.demo) vm.stopScan() else if (granted) vm.startScan()
    }

    // 画面を消さない
    DisposableEffect(state.keepScreenOn) {
        view.keepScreenOn = state.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // 注目チャンネルの変化を音声と振動で知らせる
    val announcer = remember { Announcer(context) }
    DisposableEffect(Unit) { onDispose { announcer.shutdown() } }

    val target = state.pinnedSignal(state.selected)
    val screenReader = rememberTouchExplorationEnabled()
    var lastState by remember { mutableStateOf<SignalState?>(null) }

    LaunchedEffect(target?.state, state.ttsEnabled, screenReader) {
        val signal = target ?: return@LaunchedEffect
        // TalkBack 利用中は画面のライブリージョンが読み上げるので、独自 TTS は重ねない
        if (!state.ttsEnabled || screenReader || !signal.hasData) return@LaunchedEffect
        if (signal.state == lastState) return@LaunchedEffect
        lastState = signal.state
        announcer.speak(signal.speech)
    }

    LaunchedEffect(target?.state, state.hapticsEnabled) {
        val signal = target ?: return@LaunchedEffect
        if (!state.hapticsEnabled || !signal.hasData) return@LaunchedEffect
        val feedback = when (signal.state) {
            SignalState.GREEN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
            SignalState.BLINK_GREEN -> if (Build.VERSION.SDK_INT >= 34) {
                HapticFeedbackConstants.TOGGLE_ON
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
            SignalState.RED -> HapticFeedbackConstants.CLOCK_TICK
            else -> HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(feedback)
    }

    val shareLogs = {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "PICS ビューア 受信ログ")
            putExtra(Intent.EXTRA_TEXT, vm.logText())
        }
        context.startActivity(Intent.createChooser(intent, "受信ログを共有"))
    }

    BackHandler(enabled = screen != Screen.Home) { screenName = Screen.Home.name }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Crossfade(
            targetState = screen,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "screen",
        ) { current ->
            when (current) {
                Screen.Settings -> SettingsScreen(
                    state = state,
                    onBack = { screenName = Screen.Home.name },
                    onTts = vm::setTts,
                    onHaptics = vm::setHaptics,
                    onKeepScreenOn = vm::setKeepScreenOn,
                    onDemo = vm::setDemo,
                    onShareLogs = { shareLogs() },
                    onClearLogs = vm::clearLogs,
                )

                Screen.Map -> MapScreen(onBack = { screenName = Screen.Home.name })

                Screen.Home -> HomeScreen(
                    state = state,
                    permissionGranted = granted,
                    onSettings = { screenName = Screen.Settings.name },
                    onMap = { screenName = Screen.Map.name },
                    onToggleScan = {
                        if (state.scanning) vm.stopScan() else vm.startScan()
                    },
                    onSelectDevice = vm::selectDevice,
                    onPin = vm::togglePin,
                    onDemo = { vm.setDemo(!state.demo) },
                    onShareLogs = { shareLogs() },
                    onRequestBluetooth = {
                        if (granted) {
                            enableBluetoothLauncher.launch(
                                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            )
                        } else {
                            permissionLauncher.launch(blePermissions)
                        }
                    },
                )
            }
        }
    }
}
