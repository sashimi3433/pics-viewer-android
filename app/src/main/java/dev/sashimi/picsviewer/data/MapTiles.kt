package dev.sashimi.picsviewer.data

import android.content.Context
import org.maplibre.android.MapLibre
import org.maplibre.android.offline.OfflineManager

/**
 * 地図（MapLibre + OpenFreeMap）の設定とタイルのキャッシュ。
 *
 * 一度表示した地図のタイル・スタイル・フォントは、MapLibre のキャッシュ（端末内のデータベース）に残り、
 * 次に同じ場所を開いたときは通信せずに表示する。上限を超えると古いものから消える。
 */
object MapTiles {
    const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
    const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"

    /** キャッシュの上限。日本の主要都市を何か所か拡大して見ても収まる大きさ。 */
    private const val CACHE_BYTES = 256L * 1024 * 1024

    fun init(context: Context) {
        MapLibre.getInstance(context.applicationContext)
        OfflineManager.getInstance(context.applicationContext)
            .setMaximumAmbientCacheSize(CACHE_BYTES, null)
    }

    fun clearCache(context: Context, onDone: (Boolean) -> Unit) {
        OfflineManager.getInstance(context.applicationContext).clearAmbientCache(
            object : OfflineManager.FileSourceCallback {
                override fun onSuccess() = onDone(true)
                override fun onError(message: String) = onDone(false)
            },
        )
    }
}
