package dev.sashimi.picsviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.sashimi.picsviewer.data.MapTiles
import dev.sashimi.picsviewer.ui.PicsApp
import dev.sashimi.picsviewer.ui.PicsTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 地図（MapLibre）は MapView を作る前に初期化する
        MapTiles.init(this)

        enableEdgeToEdge()
        setContent {
            PicsTheme {
                PicsApp(viewModel)
            }
        }
    }
}
