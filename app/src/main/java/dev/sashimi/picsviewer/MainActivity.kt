package dev.sashimi.picsviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.sashimi.picsviewer.ui.PicsApp
import dev.sashimi.picsviewer.ui.PicsTheme
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // osmdroid（地図）は User-Agent の設定が必須
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance()
            .load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))

        enableEdgeToEdge()
        setContent {
            PicsTheme {
                PicsApp(viewModel)
            }
        }
    }
}
