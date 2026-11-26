package dev.sashimi.picsviewer.model

import java.util.Locale

/** 高度化PICS の BLE アドバタイズに付く Bluetooth SIG Company Identifier。 */
const val PICS_COMPANY_ID = 0x01CE

/** 1つの送信機が持つ歩行者信号チャンネルの最大数。 */
const val PICS_CHANNEL_COUNT = 6

enum class SignalState(val label: String) {
    NO_SIGNAL("信号なし"),
    RED("赤"),
    BLINK_GREEN("青点滅"),
    GREEN("青"),
    OUT_OF_SCOPE("対象外"),
    UNKNOWN("—");

    val isActive: Boolean get() = this == RED || this == BLINK_GREEN || this == GREEN
}

/**
 * 1チャンネル分の歩行者信号。
 * [level] は残り時間の段階（0〜7 の8段階。位相の残りを8分割したもの）。
 * 実測ログでも 7→6→…→0 と段階的に減り、公式アプリ「信GO!」も秒数ではなく
 * バーで表示しているため、秒には変換しない。
 */
data class PedestrianSignal(
    val state: SignalState,
    val level: Int?,
) {
    val hasData: Boolean get() = state != SignalState.UNKNOWN

    /** 0.0〜1.0 の残り割合（不明なら null）。 */
    val remainingFraction: Float?
        get() = level?.let { ((it + 1) / 8f).coerceIn(0f, 1f) }

    val speech: String
        get() = when {
            !hasData -> "信号情報なし"
            level != null && level <= 1 -> "${state.label} まもなく"
            else -> state.label
        }
}

/** アドバタイズ1パケットの解析結果。 */
data class PicsFrame(
    val type: Int,
    val messageId: Int,
    val intersectionId: String,
    val identifier: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val signals: List<PedestrianSignal> = emptyList(),
    val raw: ByteArray = ByteArray(0),
) {
    val typeLabel: String
        get() = when (type) {
            0 -> "識別"
            1 -> "位置"
            2 -> "信号"
            else -> "不明"
        }
}

/**
 * 高度化PICS のアドバタイズ（Company ID 0x01CE 以降の 24 バイト）を解釈する。
 *
 * 構造:
 *   [0]     バージョン
 *   [1]     フラグ
 *   [2]     メッセージタイプ (0=識別 / 1=位置 / 2=信号)
 *   [3]     メッセージID
 *   [4..5]  予約
 *   [6..9]  交差点ID（ビッグエンディアン）
 *   [10..]  タイプ別ペイロード
 *     type 0: ASCII の送信機識別子（"UTMS_PICS0001" など）
 *     type 1: 緯度 int32 BE / 1e6, 経度 int32 BE / 1e6
 *     type 2: 6チャンネル分の 1 バイト（上位4bit=残り秒-1 / 下位4bit=状態）
 */
object PicsParser {

    fun parse(data: ByteArray): PicsFrame? {
        if (data.size < 10) return null
        val type = data[2].toInt() and 0xFF
        val messageId = data[3].toInt() and 0xFF
        val intersectionId = hex(data, 6, 4)

        return when (type) {
            0 -> PicsFrame(
                type = 0,
                messageId = messageId,
                intersectionId = intersectionId,
                identifier = parseIdentifier(data),
                raw = data,
            )

            1 -> {
                if (data.size < 18) return null
                val lat = readInt32(data, 10) / 1_000_000.0
                val lon = readInt32(data, 14) / 1_000_000.0
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
                PicsFrame(
                    type = 1,
                    messageId = messageId,
                    intersectionId = intersectionId,
                    latitude = lat,
                    longitude = lon,
                    raw = data,
                )
            }

            2 -> {
                if (data.size < 11) return null
                val end = minOf(data.size, 10 + PICS_CHANNEL_COUNT)
                PicsFrame(
                    type = 2,
                    messageId = messageId,
                    intersectionId = intersectionId,
                    signals = (10 until end).map { decodeSignal(data[it].toInt() and 0xFF) },
                    raw = data,
                )
            }

            else -> PicsFrame(
                type = type,
                messageId = messageId,
                intersectionId = intersectionId,
                raw = data,
            )
        }
    }

    /** 信号1バイトを状態＋残り秒に変換する。 */
    fun decodeSignal(byte: Int): PedestrianSignal {
        if (byte == 0xFF) return PedestrianSignal(SignalState.UNKNOWN, null)
        val state = when (byte and 0x0F) {
            0 -> SignalState.NO_SIGNAL
            1 -> SignalState.RED
            2 -> SignalState.BLINK_GREEN
            3 -> SignalState.GREEN
            4 -> SignalState.OUT_OF_SCOPE
            else -> SignalState.UNKNOWN
        }
        val high = (byte shr 4) and 0x0F
        // 上位ニブルは残り時間の段階（0〜7）。8 以上は不明値。
        val level = if (high >= 8) null else high
        return PedestrianSignal(state, level)
    }

    private fun parseIdentifier(data: ByteArray): String {
        val end = minOf(data.size, 24)
        val sb = StringBuilder()
        for (i in 10 until end) {
            val c = data[i].toInt() and 0xFF
            if (c in 32..126) sb.append(c.toChar())
        }
        return sb.toString().trim()
    }

    private fun readInt32(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)

    fun hex(data: ByteArray, offset: Int, length: Int): String {
        val sb = StringBuilder()
        for (i in offset until minOf(data.size, offset + length)) {
            sb.append(String.format(Locale.ROOT, "%02X", data[i].toInt() and 0xFF))
        }
        return sb.toString()
    }
}

fun ByteArray.toHexString(): String = PicsParser.hex(this, 0, size)


/** 受信した1台分の送信機の状態（タイプ0/1/2をマージしたもの）。 */
data class DeviceState(
    val key: String,
    val intersectionId: String,
    val identifier: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val signals: List<PedestrianSignal>? = null,
    val rssi: Int = 0,
    val lastSeen: Long = 0L,
    val packets: Int = 0,
    val lastType: Int = -1,
    val lastRaw: String = "",
) {
    val coordinateLabel: String
        get() = if (latitude != null && longitude != null) {
            String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude)
        } else {
            "未受信"
        }
}
