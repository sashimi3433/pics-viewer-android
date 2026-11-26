package dev.sashimi.picsviewer.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.graphics.PointF
import android.graphics.RectF
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.sashimi.picsviewer.R
import dev.sashimi.picsviewer.data.IntersectionRepository
import dev.sashimi.picsviewer.data.MapTiles
import dev.sashimi.picsviewer.data.PicsIntersection
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.utils.BitmapUtils
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

// 冒頭で権限を確認し、取り消し時の SecurityException も runCatching で受ける
@SuppressLint("MissingPermission")
private fun requestLocation(context: Context, onResult: (Location?) -> Unit) {
    val manager = context.getSystemService(LocationManager::class.java)
    if (manager == null || !hasLocationPermission(context)) {
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
                    @Deprecated("Deprecated in Java")
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

private const val SOURCE_ID = "pics"
private const val LAYER_CLUSTER = "pics-cluster"
private const val LAYER_COUNT = "pics-count"
private const val LAYER_PIN = "pics-pin"
private const val IMAGE_PIN = "pics-pin"
private const val PROP_INDEX = "index"

/** 日本全体が入る位置と縮尺。 */
private val JAPAN = CameraPosition.Builder().target(LatLng(37.3, 136.8)).zoom(4.4).build()

@Composable
fun MapScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val intersections = remember { IntersectionRepository.load(context) }
    val clusterColor = MaterialTheme.colorScheme.primary.toArgb()
    val onClusterColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val styleUri = if (isSystemInDarkTheme()) MapTiles.STYLE_DARK else MapTiles.STYLE_LIGHT

    val mapView = rememberMapViewWithLifecycle()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var selected by remember { mutableStateOf<PicsIntersection?>(null) }
    var location by remember { mutableStateOf<Location?>(null) }
    var nearest by remember {
        mutableStateOf<List<Pair<PicsIntersection, Double>>>(emptyList())
    }
    var hasLocation by remember { mutableStateOf(hasLocationPermission(context)) }

    fun moveTo(latitude: Double, longitude: Double, zoom: Double) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoom))
    }

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
                moveTo(result.latitude, result.longitude, 13.0)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasLocation = hasLocationPermission(context)
        if (hasLocation) refreshLocation()
    }

    // 地図の準備とスタイル（ダークテーマに合わせて切り替える）の読み込み
    LaunchedEffect(styleUri) {
        mapView.getMapAsync { m ->
            if (map == null) {
                m.cameraPosition = JAPAN
                m.uiSettings.isRotateGesturesEnabled = false
                m.uiSettings.isTiltGesturesEnabled = false
                m.addOnMapClickListener { point ->
                    val screen = m.projection.toScreenLocation(point)
                    handleMapTap(m, screen, intersections, context.resources.displayMetrics.density) {
                        selected = it
                    }
                }
                map = m
            }
            m.setStyle(Style.Builder().fromUri(styleUri)) { loaded ->
                showJapaneseLabels(loaded)
                addIntersections(context, loaded, intersections, clusterColor, onClusterColor)
                style = loaded
            }
        }
    }

    // 現在地の表示（権限があるときだけ）
    LaunchedEffect(style, hasLocation) {
        val m = map ?: return@LaunchedEffect
        val s = style ?: return@LaunchedEffect
        if (hasLocation) enableMyLocation(context, m, s)
    }

    // 権限がある場合は開いた時点で現在地を取得する
    LaunchedEffect(map, hasLocation) {
        if (map != null && hasLocation && location == null) refreshLocation()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize().semantics {
                contentDescription = "対応交差点の地図。近くの交差点は画面下の一覧から選べます"
            },
            factory = { mapView },
        )

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
            }
            Spacer(Modifier.width(8.dp))
            Card(
                modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        "対応交差点マップ",
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "全国 ${intersections.size} 箇所",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = {
                    if (hasLocation) refreshLocation()
                    else permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("現在地")
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
                        moveTo(item.latitude, item.longitude, 15.0)
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
    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(
                    intersection.name,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    intersection.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (distance != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "現在地から ${IntersectionRepository.formatDistance(distance)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Filled.Close, contentDescription = "閉じる")
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
    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            if (nearest.isEmpty()) {
                Column(Modifier.padding(8.dp)) {
                    Text(
                        if (hasLocation) "現在地を取得しています…" else "近くの対応交差点を表示",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (hasLocation) {
                            "位置情報が取れるまでお待ちください"
                        } else {
                            "現在地を許可すると、近い順に対応交差点を出せます。地図はピンをタップして確認できます。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!hasLocation) {
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = onRequestLocation, shapes = ButtonDefaults.shapes()) {
                            Text("現在地を使う")
                        }
                    }
                }
            } else {
                Text(
                    "近くの対応交差点",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp).semantics { heading() },
                )
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    nearest.forEachIndexed { index, (item, distance) ->
                        SegmentedListItem(
                            onClick = { onPick(item) },
                            shapes = ListItemDefaults.segmentedShapes(index = index, count = nearest.size),
                            supportingContent = {
                                Text(item.address, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = {
                                Text(
                                    IntersectionRepository.formatDistance(distance),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                        ) {
                            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Text(
                    "全国 $total 箇所（警察庁「全国高度化PICS整備交差点」を加工して作成）",
                    Modifier.padding(start = 8.dp, top = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** MapView を画面のライフサイクルに合わせて動かす。 */
@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            val state = lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

/** OpenFreeMap のスタイルは「ローマ字＋現地語」の 2 行表記なので、日本語（なければ現地語）だけにする。 */
private fun showJapaneseLabels(style: Style) {
    val label = Expression.coalesce(
        Expression.get("name:ja"),
        Expression.get("name"),
    )
    style.layers.filterIsInstance<SymbolLayer>().forEach { layer ->
        val field = layer.textField.expression?.toString() ?: return@forEach
        if ("name" in field) layer.setProperties(PropertyFactory.textField(label))
    }
}

/** 交差点をクラスタ付きのソースとして載せる。拡大すると個別のピンになる。 */
private fun addIntersections(
    context: Context,
    style: Style,
    intersections: List<PicsIntersection>,
    clusterColor: Int,
    onClusterColor: Int,
) {
    ContextCompat.getDrawable(context, R.drawable.ic_pics_pin)
        ?.let { BitmapUtils.getBitmapFromDrawable(it) }
        ?.let { style.addImage(IMAGE_PIN, it) }

    val features = intersections.mapIndexed { index, item ->
        Feature.fromGeometry(Point.fromLngLat(item.longitude, item.latitude)).apply {
            addNumberProperty(PROP_INDEX, index)
        }
    }
    style.addSource(
        GeoJsonSource(
            SOURCE_ID,
            FeatureCollection.fromFeatures(features),
            GeoJsonOptions().withCluster(true).withClusterMaxZoom(12).withClusterRadius(48),
        )
    )
    val isCluster = Expression.has("point_count")
    style.addLayer(
        CircleLayer(LAYER_CLUSTER, SOURCE_ID).withFilter(isCluster).withProperties(
            PropertyFactory.circleColor(clusterColor),
            PropertyFactory.circleRadius(
                Expression.step(
                    Expression.get("point_count"),
                    Expression.literal(16),
                    Expression.stop(10, 19),
                    Expression.stop(100, 23),
                )
            ),
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
            PropertyFactory.circleStrokeWidth(2f),
        )
    )
    style.addLayer(
        SymbolLayer(LAYER_COUNT, SOURCE_ID).withFilter(isCluster).withProperties(
            PropertyFactory.textField(Expression.toString(Expression.get("point_count"))),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
            PropertyFactory.textSize(14f),
            PropertyFactory.textColor(onClusterColor),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
        )
    )
    style.addLayer(
        SymbolLayer(LAYER_PIN, SOURCE_ID).withFilter(Expression.not(isCluster)).withProperties(
            PropertyFactory.iconImage(IMAGE_PIN),
            PropertyFactory.iconAnchor("bottom"),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        )
    )
}

/**
 * 地図のタップ。指の大きさを見込んで周り 24dp の範囲を探し、
 * ピンなら選択、クラスタなら拡大する。地図の他の場所なら何もしない。
 */
private fun handleMapTap(
    map: MapLibreMap,
    screen: PointF,
    intersections: List<PicsIntersection>,
    density: Float,
    onSelect: (PicsIntersection) -> Unit,
): Boolean {
    val r = 24 * density
    val area = RectF(screen.x - r, screen.y - r, screen.x + r, screen.y + r)
    val pin = map.queryRenderedFeatures(area, LAYER_PIN).firstOrNull()
    val index = pin?.getNumberProperty(PROP_INDEX)?.toInt()
    if (index != null) {
        val item = intersections.getOrNull(index) ?: return false
        onSelect(item)
        map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(item.latitude, item.longitude)))
        return true
    }
    val cluster = map.queryRenderedFeatures(area, LAYER_CLUSTER).firstOrNull() ?: return false
    val point = cluster.geometry() as? Point ?: return false
    map.animateCamera(
        CameraUpdateFactory.newLatLngZoom(
            LatLng(point.latitude(), point.longitude()),
            map.cameraPosition.zoom + 2,
        )
    )
    return true
}

@SuppressLint("MissingPermission")
private fun enableMyLocation(context: Context, map: MapLibreMap, style: Style) {
    if (!hasLocationPermission(context)) return
    runCatching {
        map.locationComponent.apply {
            activateLocationComponent(LocationComponentActivationOptions.builder(context, style).build())
            isLocationComponentEnabled = true
            renderMode = RenderMode.NORMAL
        }
    }
}
