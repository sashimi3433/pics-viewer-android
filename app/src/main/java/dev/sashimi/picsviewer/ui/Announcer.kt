package dev.sashimi.picsviewer.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** 画面を見なくても状態が分かるようにする読み上げ。 */
class Announcer(context: Context) : TextToSpeech.OnInitListener {

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale.JAPANESE)
        ready = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
        tts.setSpeechRate(1.05f)
        tts.setPitch(1.0f)
    }

    fun speak(text: String) {
        if (!ready) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pics-signal")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
