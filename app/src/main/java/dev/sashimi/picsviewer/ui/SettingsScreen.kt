package dev.sashimi.picsviewer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.sashimi.picsviewer.UiState

private data class Toggle(
    val title: String,
    val description: String,
    val checked: Boolean,
    val onChange: (Boolean) -> Unit,
)

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
    onClearMapCache: () -> Unit,
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // セグメント化リストは surfaceContainer の上に置く（M3E の既定）
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("設定", Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        // 広い画面では読みやすい幅に収めて中央に置く
        LazyColumn(
            Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 720.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            toggleSection(
                "表示",
                listOf(
                    Toggle("画面を消さない", "信号を待つ間、スリープしません", state.keepScreenOn, onKeepScreenOn),
                    Toggle("デモ表示", "信号機がなくても動作を確認できます", state.demo, onDemo),
                ),
            )
            toggleSection(
                "フィードバック",
                listOf(
                    Toggle("音声で読み上げ", "注目チャンネルの色が変わると読み上げます", state.ttsEnabled, onTts),
                    Toggle("振動で知らせる", "状態が変わったときだけ短く振動します", state.hapticsEnabled, onHaptics),
                ),
            )

            item { SectionLabel("データ") }
            item {
                SegmentedListItem(
                    onClick = onShareLogs,
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
                    enabled = state.logs.isNotEmpty(),
                    leadingContent = { Icon(Icons.Filled.Share, contentDescription = null) },
                    trailingContent = {
                        Text("${state.logs.size} 件", fontFamily = FontFamily.Monospace)
                    },
                ) { Text("受信ログを共有") }
            }
            item {
                SegmentedListItem(
                    onClick = onClearLogs,
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                    enabled = state.logs.isNotEmpty(),
                    leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
                ) { Text("ログを消去") }
            }
            item {
                SegmentedListItem(
                    onClick = onClearMapCache,
                    shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
                    leadingContent = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                    supportingContent = { Text("一度見た地図は端末に残り、次から通信せずに表示します") },
                ) { Text("地図のキャッシュを消去") }
            }

            item { SectionLabel("このアプリ") }
            val info = listOf(
                "バージョン" to versionName,
                "受信方式" to "BLE アドバタイズ（Company ID 0x01CE）",
                "接続" to "なし（受信専用）",
            )
            info.forEachIndexed { index, (label, value) ->
                item {
                    SegmentedListItem(
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = info.size),
                        overlineContent = { Text(label) },
                    ) { Text(value) }
                }
            }

            item { SectionLabel("注意事項") }
            items(Disclaimer) { paragraph ->
                Text(
                    paragraph,
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** ストアの説明・サイトと同じ注意事項。 */
private val Disclaimer = listOf(
    "本アプリは、高度化PICSが発信する Bluetooth Low Energy（BLE）信号を受信・解析・可視化する非公式ツールです。",
    "本アプリは、警察庁、都道府県警察、日本信号株式会社その他の関係機関による提供、認定、監修を受けたものではありません。",
    "表示される情報には、通信遅延、受信障害、解析上の誤り等が含まれる可能性があります。本アプリの情報を道路横断、車両の運転その他の交通上の安全判断に使用しないでください。",
    "道路を通行する際は、実際の交通信号機の表示、警察官等の指示および周囲の交通状況に従ってください。",
    "本アプリは、交通信号機の操作、制御、信号の改変または再送信を行うものではありません。",
)

private fun LazyListScope.toggleSection(title: String, toggles: List<Toggle>) {
    item { SectionLabel(title) }
    toggles.forEachIndexed { index, toggle ->
        item {
            SegmentedListItem(
                checked = toggle.checked,
                onCheckedChange = toggle.onChange,
                shapes = ListItemDefaults.segmentedShapes(index = index, count = toggles.size),
                supportingContent = { Text(toggle.description) },
                trailingContent = { Switch(checked = toggle.checked, onCheckedChange = null) },
            ) { Text(toggle.title) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Column(Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp).semantics { heading() }) {
        Text(
            text,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
