package dev.sashimi.picsviewer.ui

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.sashimi.picsviewer.model.PedestrianSignal

/**
 * 端末の「アニメーションを削除」（開発者向けオプションのアニメーション倍率 0 も含む）。
 * 有効なら点滅やパルスなどの繰り返しアニメーションを止める。
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return rememberOnResume { animationsDisabled(context) }
}

/**
 * TalkBack などのタッチ探索が有効か。
 * 有効なときはスクリーンリーダーがライブリージョンを読むので、アプリ独自の TTS は止めて二重読み上げを防ぐ。
 */
@Composable
fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(AccessibilityManager::class.java) }
    var enabled by remember { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

/** スクリーンリーダー向けの信号の説明（例: 「チャンネル 1、青、残り 5/8」）。 */
fun PedestrianSignal.accessibilityDescription(index: Int): String {
    val state = if (hasData) this.state.label else "データなし"
    val remaining = when {
        !hasData -> null
        level != null -> "残り ${level + 1}/8"
        else -> "残り時間 不明"
    }
    return listOfNotNull("チャンネル ${index + 1}", state, remaining).joinToString("、")
}

private fun animationsDisabled(context: Context): Boolean =
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f

/** 設定画面から戻ったときに反映されるよう、ON_RESUME ごとに読み直す。 */
@Composable
private fun rememberOnResume(read: () -> Boolean): Boolean {
    var value by remember { mutableStateOf(read()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) value = read()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return value
}
