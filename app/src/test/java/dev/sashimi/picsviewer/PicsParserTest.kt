package dev.sashimi.picsviewer

import dev.sashimi.picsviewer.model.PicsParser
import dev.sashimi.picsviewer.model.SignalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 実測ログ（神奈川県内の高度化PICS交差点）から取った実パケットで解析を固定する。
 * ログ: Type/交差点ID/バイト列は受信そのまま。
 */
class PicsParserTest {

    private fun hex(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `type2 赤2秒を解析できる`() {
        val frame = PicsParser.parse(hex("580102C1000000D5F5B1FF11FFFFFFFF0000000000000000"))
            ?: error("parse failed")
        assertEquals(2, frame.type)
        assertEquals("00D5F5B1", frame.intersectionId)
        assertEquals(6, frame.signals.size)

        val red = frame.signals[1]
        assertEquals(SignalState.RED, red.state)
        assertEquals(1, red.level)

        // 0xFF は「データなし」
        assertEquals(SignalState.UNKNOWN, frame.signals[0].state)
        assertNull(frame.signals[0].level)
        assertTrue(!frame.signals[0].hasData)
    }

    @Test
    fun `type2 青2秒と青点滅を解析できる`() {
        val green = PicsParser.parse(hex("580102B1000000D5F5B1FF13FFFFFFFF0000000000000000"))!!
        assertEquals(SignalState.GREEN, green.signals[1].state)
        assertEquals(1, green.signals[1].level)

        val blink = PicsParser.parse(hex("580102CF000000D5F5B1FFF2FFFFFFFF0000000000000000"))!!
        assertEquals(SignalState.BLINK_GREEN, blink.signals[1].state)
        assertNull(blink.signals[1].level)
    }

    @Test
    fun `type1 緯度経度を解析できる`() {
        val frame = PicsParser.parse(hex("580101BE000000D5F5B1021A54DD085331A7160000000001"))!!
        assertEquals(1, frame.type)
        assertEquals(35.280093, frame.latitude!!, 0.0000005)
        assertEquals(139.669927, frame.longitude!!, 0.0000005)
    }

    @Test
    fun `type0 送信機IDを解析できる`() {
        val frame = PicsParser.parse(hex("580100CC000000D5F5B155544D535F504943533030303100"))!!
        assertEquals(0, frame.type)
        assertEquals("UTMS_PICS0001", frame.identifier)
    }

    @Test
    fun `残り時間の段階は8以上で不明になる`() {
        assertEquals(7, PicsParser.decodeSignal(0x71).level)
        assertNull(PicsParser.decodeSignal(0x81).level)
        assertEquals(SignalState.RED, PicsParser.decodeSignal(0x81).state)
        assertEquals(SignalState.NO_SIGNAL, PicsParser.decodeSignal(0x00).state)
        assertEquals(SignalState.OUT_OF_SCOPE, PicsParser.decodeSignal(0xF4).state)
    }

    @Test
    fun `短すぎるパケットは捨てる`() {
        assertNull(PicsParser.parse(hex("580102C10000")))
        assertNull(PicsParser.parse(hex("580101BE000000D5F5B1")))
        assertNull(PicsParser.parse(ByteArray(0)))
    }
}
