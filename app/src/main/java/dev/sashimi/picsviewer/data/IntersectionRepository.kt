package dev.sashimi.picsviewer.data

import android.content.Context
import org.json.JSONArray
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** 高度化PICS 整備交差点（警察庁の公開リスト由来）。 */
data class PicsIntersection(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

object IntersectionRepository {

    private const val ASSET = "pics_intersections.json"

    @Volatile
    private var cache: List<PicsIntersection>? = null

    fun load(context: Context): List<PicsIntersection> {
        cache?.let { return it }
        val list = runCatching {
            context.assets.open(ASSET).bufferedReader().use { reader ->
                val array = JSONArray(reader.readText())
                buildList(array.length()) {
                    for (i in 0 until array.length()) {
                        val row = array.getJSONArray(i)
                        add(
                            PicsIntersection(
                                name = row.getString(0),
                                address = row.getString(1),
                                latitude = row.getDouble(2),
                                longitude = row.getDouble(3),
                            )
                        )
                    }
                }
            }
        }.getOrElse { emptyList() }
        cache = list
        return list
    }

    fun nearest(
        intersections: List<PicsIntersection>,
        latitude: Double,
        longitude: Double,
        limit: Int = 3,
    ): List<Pair<PicsIntersection, Double>> = intersections
        .map { it to distanceMeters(latitude, longitude, it.latitude, it.longitude) }
        .sortedBy { it.second }
        .take(limit)

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val toRad = Math.PI / 180.0
        val dLat = (lat2 - lat1) * toRad
        val dLon = (lon2 - lon1) * toRad
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * toRad) * cos(lat2 * toRad) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun formatDistance(meters: Double): String = when {
        meters < 950 -> "${meters.roundToInt()} m"
        else -> String.format(java.util.Locale.ROOT, "%.1f km", meters / 1000)
    }
}
