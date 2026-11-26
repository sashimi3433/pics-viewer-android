package dev.sashimi.picsviewer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sashimi.picsviewer.UiState

@Composable
fun SettingsScreen(
    state: UiState,
    onBack: () -> Unit,
    onTts: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onDemo: (Boolean) -> Unit,
    onShareLogs: () -> Unit,
    onClearLogs: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 6.dp, bottom = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .padding(end = 4.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "戻る",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "設定",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(18.dp))

        SectionLabel("表示")
        GlassCard {
            Column {
                SwitchRow(
                    title = "画面を消さない",
                    description = "信号を待つ間、スリープしません",
                    checked = state.keepScreenOn,
                    onCheckedChange = onKeepScreenOn,
                )
                RowDivider()
                SwitchRow(
                    title = "デモ表示",
                    description = "信号機がなくても動作を確認できます",
                    checked = state.demo,
                    onCheckedChange = onDemo,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        SectionLabel("フィードバック")
        GlassCard {
            Column {
                SwitchRow(
                    title = "音声で読み上げ",
                    description = "注目チャンネルの色が変わると読み上げます",
                    checked = state.ttsEnabled,
                    onCheckedChange = onTts,
                )
                RowDivider()
                SwitchRow(
                    title = "振動で知らせる",
                    description = "状態が変わったときだけ短く振動します",
                    checked = state.hapticsEnabled,
                    onCheckedChange = onHaptics,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        SectionLabel("データ")
        GlassCard {
            Column {
                ActionRow(
                    title = "受信ログを共有",
                    value = "${state.logs.size} 件",
                    enabled = state.logs.isNotEmpty(),
                    onClick = onShareLogs,
                )
                RowDivider()
                ActionRow(
                    title = "ログを消去",
                    value = null,
                    enabled = state.logs.isNotEmpty(),
                    onClick = onClearLogs,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        SectionLabel("このアプリ")
        GlassCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "バージョン",
                        Modifier.width(110.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("1.4.0", style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "受信方式",
                        Modifier.width(110.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "BLE アドバタイズ（Company ID 0x01CE）",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "接続",
                        Modifier.width(110.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("なし（受信専用）", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "信号機から送信される公開電波を受信するだけで、送信や制御は行いません。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        Modifier.padding(start = 6.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionRow(
    title: String,
    value: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = tint)
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
