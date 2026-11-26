package dev.sashimi.picsviewer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import dev.sashimi.picsviewer.R
import dev.sashimi.picsviewer.data.IntersectionRepository
import dev.sashimi.picsviewer.data.PicsIntersection
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun requestLocation(context: Context, onResult: (Location?) -> Unit) {
    val manager = context.getSystemService(LocationManager::class.java)
    if (manager == null) {
        onResult(null)
        return
    }
    val provider = when {
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        else -> {
            onResult(null)
            return
        }
    }
    val lastKnown = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
            manager.getCurrentLocation(
                provider,
                null,
                ContextCompat.getMainExecutor(context),
            ) { location -> onResult(location ?: lastKnown) }
        }.onFailure { onResult(lastKnown) }
    } else {
        @Suppress("DEPRECATION")
        runCatching {
            manager.requestSingleUpdate(
                provider,
                object : LocationListener {
                    override fun onLocationChanged(location: Location) = onResult(location)
                    override fun onStatusChanged(p: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(p: String) = Unit
                    override fun onProviderDisabled(p: String) = Unit
                },
                Looper.getMainLooper(),
            )
        }.onFailure { onResult(lastKnown) }
        if (lastKnown != null) onResult(lastKnown)
    }
}

@Composable
fun MapScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val intersections = remember { IntersectionRepository.load(context) }
    val dark = isSystemInDarkTheme()

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var selected by remember { mutableStateOf<PicsIntersection?>(null) }
    var location by remember { mutableStateOf<Location?>(null) }
    var nearest by remember {
        mutableStateOf<List<Pair<PicsIntersection, Double>>>(emptyList())
    }
    var hasLocation by remember { mutableStateOf(hasLocationPermission(context)) }

    fun refreshLocation() {
        if (!hasLocationPermission(context)) return
        requestLocation(context) { result ->
            if (result != null) {
                location = result
                nearest = IntersectionRepository.nearest(
                    intersections,
                    result.latitude,
                    result.longitude,
                )
                mapView?.controller?.setZoom(14.0)
                mapView?.controller?.animateTo(GeoPoint(result.latitude, result.longitude))
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasLocation = hasLocationPermission(context)
        if (hasLocation) refreshLocation()
    }

    DisposableEffect(Unit) { onDispose { mapView?.onDetach() } }

    // 権限がある場合は開いた時点で現在地を取得する
    LaunchedEffect(mapView, hasLocation) {
        if (mapView != null && hasLocation && location == null) refreshLocation()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val map = MapView(ctx)
                map.setTileSource(TileSourceFactory.MAPNIK)
                map.setMultiTouchControls(true)
                map.setBuiltInZoomControls(false)
                // 日本全体が入る縮尺（zoomToBoundingBox は osmdroid 6.1.20 で
                // Projection.getCloserPixel が無限ループし ANR になるため使わない）
                map.controller.setZoom(5.7)
                map.controller.setCenter(GeoPoint(37.3, 136.8))
                map.overlays.add(CopyrightOverlay(ctx))

                val pin = ContextCompat.getDrawable(ctx, R.drawable.ic_pics_pin)
                val clusterCache = HashMap<Int, android.graphics.drawable.BitmapDrawable>()
                val active = mutableListOf<Marker>()
                var currentCell = -1.0

                fun clusterIcon(count: Int): android.graphics.drawable.BitmapDrawable =
                    clusterCache.getOrPut(count) {
                        android.graphics.drawable.BitmapDrawable(
                            ctx.resources,
                            renderClusterBitmap(ctx, count),
                        )
                    }

                fun rebuild(zoom: Double) {
                    active.forEach { map.overlays.remove(it) }
                    active.clear()

                    val cell = cellForZoom(zoom)
                    currentCell = cell

                    if (cell == 0.0) {
                        intersections.forEach { item ->
                            val marker = Marker(map)
                            marker.position = GeoPoint(item.latitude, item.longitude)
                            marker.title = item.name
                            marker.snippet = item.address
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            marker.icon = pin?.constantState?.newDrawable()
                            marker.relatedObject = item
                            marker.setOnMarkerClickListener { m, view ->
                                (m.relatedObject as? PicsIntersection)?.let { selected = it }
                                view.controller.animateTo(m.position)
                                true
                            }
                            active.add(marker)
                            map.overlays.add(marker)
                        }
                        return
                    }

                    val groups = intersections.groupBy {
                        (Math.floor(it.latitude / cell).toInt() to
                            Math.floor(it.longitude / cell).toInt())
                    }
                    groups.values.forEach { items ->
                        val marker = Marker(map)
                        val lat = items.sumOf { it.latitude } / items.size
                        val lon = items.sumOf { it.longitude } / items.size
                        marker.position = GeoPoint(lat, lon)
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        if (items.size == 1) {
                            val item = items.first()
                            marker.title = item.name
                            marker.snippet = item.address
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            marker.icon = pin?.constantState?.newDrawable()
                            marker.relatedObject = item
                            marker.setOnMarkerClickListener { m, view ->
                                (m.relatedObject as? PicsIntersection)?.let { selected = it }
                                view.controller.animateTo(m.position)
                                true
                            }
                        } else {
                            marker.title = "${items.size} 箇所"
                            marker.icon = clusterIcon(items.size)
                            marker.setOnMarkerClickListener { m, view ->
                                view.controller.setZoom(zoom + 2)
                                view.controller.animateTo(m.position)
                                true
                            }
                        }
                        active.add(marker)
                        map.overlays.add(marker)
                    }
                }

                rebuild(map.zoomLevelDouble)
                map.addMapListener(object : org.osmdroid.events.MapListener {
                    override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean = false
                    override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                        val z = event?.zoomLevel ?: return false
                        val cell = cellForZoom(z)
                        if (cell != currentCell) rebuild(z)
                        return false
                    }
                })

                if (hasLocationPermission(ctx)) {
                    val myLocation = MyLocationNewOverlay(map)
                    runCatching {
                        myLocation.enableMyLocation()
                        map.overlays.add(myLocation)
                    }
                }
                mapView = map
                map
            },
        )

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.82f else 0.94f),
                shadowElevation = 3.dp,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "戻る",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.82f else 0.94f),
                shadowElevation = 3.dp,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        "対応交差点マップ",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "全国 ${intersections.size} 箇所",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.24f else 0.14f),
                shadowElevation = 3.dp,
            ) {
                TextButton(
                    onClick = {
                        if (hasLocation) refreshLocation()
                        else permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    }
                ) {
                    Text("現在地")
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .fillMaxWidth(),
        ) {
            val current = selected
            if (current != null) {
                SelectedCard(
                    intersection = current,
                    distance = location?.let {
                        IntersectionRepository.distanceMeters(
                            it.latitude,
                            it.longitude,
                            current.latitude,
                            current.longitude,
                        )
                    },
                    onClose = { selected = null },
                )
            } else {
                NearestCard(
                    nearest = nearest,
                    total = intersections.size,
                    hasLocation = hasLocation,
                    onPick = { item ->
                        selected = item
                        mapView?.controller?.setZoom(16.0)
                        mapView?.controller?.animateTo(GeoPoint(item.latitude, item.longitude))
                    },
                    onRequestLocation = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SelectedCard(
    intersection: PicsIntersection,
    distance: Double?,
    onClose: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(
            alpha = if (isSystemInDarkTheme()) 0.9f else 0.97f
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    intersection.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    intersection.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (distance != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "現在地から ${IntersectionRepository.formatDistance(distance)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "閉じる",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NearestCard(
    nearest: List<Pair<PicsIntersection, Double>>,
    total: Int,
    hasLocation: Boolean,
    onPick: (PicsIntersection) -> Unit,
    onRequestLocation: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(
            alpha = if (isSystemInDarkTheme()) 0.9f else 0.97f
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (nearest.isEmpty()) {
                Text(
                    if (hasLocation) "現在地を取得しています…" else "近くの対応交差点を表示",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (hasLocation) {
                        "位置情報が取れるまでお待ちください"
                    } else {
                        "現在地を許可すると、近い順に対応交差点を出せます。地図はピンをタップして確認できます。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!hasLocation) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onRequestLocation) { Text("現在地を使う") }
                }
            } else {
                Text(
                    "近くの対応交差点",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                nearest.forEach { (item, distance) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(item) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                item.address,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            IntersectionRepository.formatDistance(distance),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "全国 $total 箇所（警察庁「高度化PICS整備交差点」）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** クラスタ用の丸バッジを描く（件数を表示）。 */
private fun renderClusterBitmap(context: Context, count: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (42 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = size / 2f - 2 * density

    paint.color = Color.parseColor("#3A8DFF")
    canvas.drawCircle(size / 2f, size / 2f, radius, paint)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f * density
    paint.color = Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, radius, paint)

    paint.style = Paint.Style.FILL
    paint.color = Color.WHITE
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textAlign = Paint.Align.CENTER
    val label = count.toString()
    paint.textSize = when {
        count < 100 -> 15f
        count < 1000 -> 12f
        else -> 10f
    } * density
    val baseline = size / 2f - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(label, size / 2f, baseline, paint)
    return bitmap
}

/** ズームに応じたクラスタの格子サイズ（度）。アイコンが重ならない間隔にする。 */
private fun cellForZoom(zoom: Double): Double = when {
    zoom >= 14.0 -> 0.0
    zoom >= 12.5 -> 0.02
    zoom >= 11.0 -> 0.06
    zoom >= 10.0 -> 0.2
    zoom >= 9.0 -> 0.4
    zoom >= 8.0 -> 1.0
    zoom >= 7.0 -> 2.0
    zoom >= 6.0 -> 4.0
    else -> 8.0
}
