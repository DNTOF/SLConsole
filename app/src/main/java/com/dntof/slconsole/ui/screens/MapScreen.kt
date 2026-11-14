// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.microsoft.clarity.modifiers.clarityMask
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.data.model.MapSeedData
import com.dntof.slconsole.data.model.PlayerInfo
import com.dntof.slconsole.data.remote.AppJson
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.data.repo.MonitorEngine
import com.dntof.slconsole.mapgen.MapGenData
import com.dntof.slconsole.mapgen.MapGenerator
import com.dntof.slconsole.mapgen.MapRoomUi
import com.dntof.slconsole.ui.LocalSnackbarHost
import com.dntof.slconsole.ui.belowTopBar
import com.dntof.slconsole.ui.bottomChromePadding
import com.dntof.slconsole.ui.keepAboveIme
import com.dntof.slconsole.ui.scrollUnderChrome
import com.dntof.slconsole.ui.components.DropdownField
import com.dntof.slconsole.ui.components.EmptyState
import com.dntof.slconsole.ui.components.SectionCard
import com.dntof.slconsole.ui.components.showOutcome
import com.dntof.slconsole.ui.components.teamColor
import com.dntof.slconsole.ui.rememberActiveServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.res.stringResource
import com.dntof.slconsole.R
import com.dntof.slconsole.util.appString
import androidx.compose.ui.text.style.TextOverflow

private val ZONE_FILTERS = listOf(
    "All" to R.string.map_all,
    "LightContainment" to R.string.map_light_containment,
    "HeavyContainment" to R.string.map_heavy_containment,
    "Entrance" to R.string.map_entrance_zone,
)

@Composable
private fun rememberZoneColors(): Map<String, Color> = mapOf(
    "LightContainment" to MaterialTheme.colorScheme.secondary,
    "HeavyContainment" to MaterialTheme.colorScheme.primary,
    "Entrance" to MaterialTheme.colorScheme.tertiary,
    "Surface" to MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
fun MapScreen() {
    val server = rememberActiveServer()
    val monitor by MonitorEngine.state.collectAsState()
    val readyData = (monitor as? MonitorEngine.MonitorState.Ready)?.data

    if (server == null) {
        EmptyState(Icons.Outlined.Map, stringResource(R.string.common_no_server_selected), stringResource(R.string.common_add_select_server_top_bar))
        return
    }

    var seed by remember { mutableStateOf<Int?>(null) }
    var seedReady by remember { mutableStateOf(false) }
    var seedError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(server.id) {
        while (true) {
            val outcome = ControlRepository.call(server, "/control/map/seed")
            when (outcome) {
                is ControlRepository.ControlOutcome.Success -> {
                    val data = outcome.data?.let {
                        runCatching { AppJson.json.decodeFromJsonElement(MapSeedData.serializer(), it) }.getOrNull()
                    }
                    seed = data?.seed
                    seedReady = data?.ready ?: false
                    seedError = null
                }
                is ControlRepository.ControlOutcome.Failure -> seedError = outcome.message
            }
            delay(30_000)
        }
    }

    if (!server.hasControl) {
        Column(Modifier.fillMaxSize().belowTopBar().padding(16.dp).bottomChromePadding()) {
            SectionCard(stringResource(R.string.map_map_view), subtitle = stringResource(R.string.common_requires_control_plane_api_key)) {
                Text(
                    stringResource(R.string.map_map_seeds_facility_controls_go),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .scrollUnderChrome(),
    ) {
        SectionCard(
            stringResource(R.string.map_map_view),
            subtitle = when {
                seedError != null -> seedError
                seed == null -> stringResource(R.string.map_fetching_round_seed)
                !seedReady -> stringResource(R.string.map_round_seed_ready_server_layout)
                else -> stringResource(R.string.map_round_seed_refreshes_every_30, seed.toString())
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            actions = {
                seed?.let { TextButton(onClick = { /* 种子随轮询刷新 */ }) { Text(stringResource(R.string.map_round_progress)) } }
            },
        ) {
            if (seed == null && seedError == null) {
                Text(stringResource(R.string.map_waiting_control_map_seed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        seed?.let { currentSeed ->
            MapCanvasPanel(currentSeed, readyData?.players ?: emptyList())
        }
    }
}

@Composable
private fun MapCanvasPanel(seed: Int, players: List<PlayerInfo>) {
    val layout = remember(seed) {
        runCatching { MapGenerator.layoutFromGen(MapGenerator.generateLayout(seed)) }.getOrNull()
    }
    var zoneFilter by rememberSaveable { mutableStateOf("All") }
    var selected by remember { mutableStateOf<MapRoomUi?>(null) }
    val density = LocalDensity.current
    // 地图配色跟随应用主题:三区域对应 secondary / primary / tertiary
    data class ZoneStyle(val fill: Color, val label: Color, val stroke: Color)
    val darkTheme = isSystemInDarkTheme()
    val zoneColors: Map<String, ZoneStyle> = mapOf(
        "LightContainment" to ZoneStyle(
            fill = if (darkTheme) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
            label = if (darkTheme) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSecondaryContainer,
            stroke = if (darkTheme) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.35f),
        ),
        "HeavyContainment" to ZoneStyle(
            fill = if (darkTheme) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            label = if (darkTheme) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            stroke = if (darkTheme) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f),
        ),
        "Entrance" to ZoneStyle(
            fill = if (darkTheme) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.tertiaryContainer,
            label = if (darkTheme) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onTertiaryContainer,
            stroke = if (darkTheme) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.35f),
        ),
        "Surface" to ZoneStyle(
            fill = MaterialTheme.colorScheme.onSurfaceVariant,
            label = MaterialTheme.colorScheme.surface,
            stroke = MaterialTheme.colorScheme.surface,
        ),
    )
    val fallbackZoneStyle = ZoneStyle(
        fill = MaterialTheme.colorScheme.onSurfaceVariant,
        label = MaterialTheme.colorScheme.surface,
        stroke = MaterialTheme.colorScheme.surface,
    )
    val panelBg = MaterialTheme.colorScheme.surfaceContainerLow
    val panelStroke = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val onPanel = MaterialTheme.colorScheme.onBackground

    if (layout == null) {
        Text(stringResource(R.string.map_layout_generation_failed), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        return
    }

    val filteredRooms = remember(layout, zoneFilter) {
        if (zoneFilter == "All") layout.rooms else layout.rooms.filter { it.zone == zoneFilter }
    }
    val bounds = remember(filteredRooms) {
        if (filteredRooms.isEmpty()) null
        else arrayOf(
            filteredRooms.minOf { it.rx - it.w / 2 }, filteredRooms.maxOf { it.rx + it.w / 2 },
            filteredRooms.minOf { it.rz - it.d / 2 }, filteredRooms.maxOf { it.rz + it.d / 2 },
        )
    }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ZONE_FILTERS.forEach { (value, label) ->
                FilterChip(selected = zoneFilter == value, onClick = { zoneFilter = value; selected = null }, label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) })
            }
        }

        if (bounds == null) {
            EmptyState(Icons.Outlined.Map, stringResource(R.string.map_no_rooms_zone), stringResource(R.string.map_change_zone_filter_wait_new))
        } else {
            val (minX, maxX, minZ, maxZ) = bounds
            var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
            val projection = remember(canvasSize, minX, maxX, minZ, maxZ) {
                if (canvasSize.width == 0 || canvasSize.height == 0) null
                else {
                    val pad = 16f
                    val bw = max(1.0, maxX - minX).toFloat()
                    val bh = max(1.0, maxZ - minZ).toFloat()
                    val scale = min((canvasSize.width - 2 * pad) / bw, (canvasSize.height - 2 * pad) / bh)
                    Triple(
                        scale,
                        (canvasSize.width - bw * scale) / 2 - (minX * scale).toFloat(),
                        (canvasSize.height - bh * scale) / 2 - (minZ * scale).toFloat(),
                    )
                }
            }
            var userScale by remember { mutableFloatStateOf(1f) }
            var userPan by remember { mutableStateOf(Offset.Zero) }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.map_scroll_page_one_finger_drag),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    userScale = 1f
                    userPan = Offset.Zero
                }) { Text(stringResource(R.string.map_reset)) }
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clipToBounds()
                    .clarityMask()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(filteredRooms, projection, canvasSize) {
                        val camera = projection ?: return@pointerInput
                        detectMapCamera(
                            onTap = { tap ->
                                val (baseScale, ox, oy) = camera
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val bx = (tap.x - cx - userPan.x) / userScale + cx
                                val by = (tap.y - cy - userPan.y) / userScale + cy
                                val wx = ((bx - ox) / baseScale).toDouble()
                                val wz = ((by - oy) / baseScale).toDouble()
                                val hit = filteredRooms.firstOrNull {
                                    abs(wx - it.rx) <= it.w / 2 && abs(wz - it.rz) <= it.d / 2
                                }
                                selected = if (hit != null && hit.rawName == selected?.rawName && hit.zone == selected?.zone) null else hit
                            },
                            onTransform = { panAdd, zoomAdd ->
                                val (baseScale, ox, oy) = camera
                                val next = clampMapCamera(
                                    scale = userScale * zoomAdd,
                                    pan = userPan + panAdd,
                                    minX = minX,
                                    maxX = maxX,
                                    minZ = minZ,
                                    maxZ = maxZ,
                                    baseScale = baseScale,
                                    ox = ox,
                                    oy = oy,
                                    viewW = size.width.toFloat(),
                                    viewH = size.height.toFloat(),
                                )
                                userScale = next.first
                                userPan = next.second
                            },
                        )
                    },
            ) {
                val (baseScale, ox, oy) = projection ?: return@Canvas
                // 地图面板底:圆角面板承载地图,与主题融为一体
                drawRoundRect(
                    color = panelBg,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(28f),
                )
                drawRoundRect(
                    color = panelStroke,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(28f),
                    style = Stroke(1.5f),
                )
                val cx = size.width / 2f
                val cy = size.height / 2f
                fun px(x: Double) = cx + ((ox + (x * baseScale).toFloat()) - cx) * userScale + userPan.x
                fun py(z: Double) = cy + ((oy + (z * baseScale).toFloat()) - cy) * userScale + userPan.y
                val viewScale = baseScale * userScale

                val cell = max(15f * viewScale, 12f)
                val armW = max(0.44f * cell, 4f)
                val armLen = cell / 2f + 0.6f
                val bodyW = max(0.88f * cell, 10f)
                val bodyH = max(0.62f * cell, 8f)
                val selectedRaw = selected?.rawName

                fun drawRoomShape(room: MapRoomUi, sx: Float, sy: Float) {
                    val style = zoneColors[room.zone] ?: fallbackZoneStyle
                    val isSelected = room.rawName == selectedRaw && room.zone == selected?.zone
                    val fill = style.fill
                    val stroke = if (isSelected) onPanel else style.stroke
                    val strokeWidth = (if (isSelected) 2.5f else 1.2f) * density.density

                    fun strokePath(path: Path) {
                        drawPath(path, fill)
                        drawPath(path, stroke, style = Stroke(strokeWidth))
                    }

                    when (room.glyph) {
                        "corridor", "pass" -> {
                            drawRoundRect(
                                fill, Offset(sx - armW / 2, sy - armW / 2),
                                Size(armW, armW), CornerRadius(2f),
                            )
                            drawRoundRect(
                                stroke, Offset(sx - armW / 2, sy - armW / 2),
                                Size(armW, armW), CornerRadius(2f), style = Stroke(strokeWidth),
                            )
                        }
                        "circle" -> {
                            drawCircle(fill, radius = 0.42f * cell, center = Offset(sx, sy))
                            drawCircle(stroke, radius = 0.42f * cell, center = Offset(sx, sy), style = Stroke(strokeWidth))
                        }
                        "gate" -> {
                            drawRoundRect(
                                fill, Offset(sx - bodyW / 2, sy - bodyH / 2),
                                Size(bodyW, bodyH), CornerRadius(0.04f * cell),
                            )
                            drawRoundRect(
                                stroke, Offset(sx - bodyW / 2, sy - bodyH / 2),
                                Size(bodyW, bodyH), CornerRadius(0.04f * cell), style = Stroke(strokeWidth),
                            )
                        }
                        "octagon", "hex", "shield", "dead" -> {
                            val x0 = sx - bodyW / 2
                            val x1 = sx + bodyW / 2
                            val y0 = sy - bodyH / 2
                            val y1 = sy + bodyH / 2
                            val path = Path()
                            when (room.glyph) {
                                "octagon" -> {
                                    val k = min(0.16f * cell, min(bodyW, bodyH) / 3f)
                                    path.moveTo(x0 + k, y0); path.lineTo(x1 - k, y0)
                                    path.lineTo(x1, y0 + k); path.lineTo(x1, y1 - k)
                                    path.lineTo(x1 - k, y1); path.lineTo(x0 + k, y1)
                                    path.lineTo(x0, y1 - k); path.lineTo(x0, y0 + k)
                                }
                                "hex" -> {
                                    val k = bodyW * 0.2f
                                    path.moveTo(x0 + k, y0); path.lineTo(x1 - k, y0)
                                    path.lineTo(x1, sy); path.lineTo(x1 - k, y1)
                                    path.lineTo(x0 + k, y1); path.lineTo(x0, sy)
                                }
                                "shield" -> {
                                    val k = bodyH * 0.34f
                                    path.moveTo(x0, y0 + k); path.lineTo(x0 + k, y0)
                                    path.lineTo(x1 - k, y0); path.lineTo(x1, y0 + k)
                                    path.lineTo(x1, y1); path.lineTo(x0, y1)
                                }
                                else -> {
                                    val k = bodyW * 0.16f
                                    path.moveTo(x0 + k, y0); path.lineTo(x1 - k, y0)
                                    path.lineTo(x1, y1); path.lineTo(x0, y1)
                                }
                            }
                            path.close()
                            strokePath(path)
                        }
                        else -> {
                            drawRoundRect(
                                fill, Offset(sx - bodyW / 2, sy - bodyH / 2),
                                Size(bodyW, bodyH), CornerRadius(0.11f * cell),
                            )
                            drawRoundRect(
                                stroke, Offset(sx - bodyW / 2, sy - bodyH / 2),
                                Size(bodyW, bodyH), CornerRadius(0.11f * cell), style = Stroke(strokeWidth),
                            )
                        }
                    }
                }

                // 连接臂 + 房间体
                filteredRooms.forEach { room ->
                    val sx = px(room.rx)
                    val sy = py(room.rz)
                    room.conn.forEach { (dx, dz) ->
                        when {
                            dx != 0 -> drawRect(
                                (zoneColors[room.zone] ?: fallbackZoneStyle).fill,
                                Offset(if (dx > 0) sx else sx - armLen, sy - armW / 2),
                                Size(armLen, armW),
                            )
                            dz != 0 -> drawRect(
                                (zoneColors[room.zone] ?: fallbackZoneStyle).fill,
                                Offset(sx - armW / 2, if (dz > 0) sy else sy - armLen),
                                Size(armW, armLen),
                            )
                        }
                    }
                    drawRoomShape(room, sx, sy)
                }

                // 房间标签:统一字号;按 特定房间→走廊、大→小 排序并做碰撞剔除,
                // "全部"视角下相邻名字不再互相压盖;放大后房间散开,标签会逐渐全部显现
                val labelSize = with(density) { 9.dp.toPx() }
                val textPaint = Paint().apply {
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    textSize = labelSize
                }
                val drawnLabels = mutableListOf<Rect>()
                filteredRooms
                    .sortedWith(
                        compareBy(
                            { it.isCorridor },      // 特定房间优先于走廊
                            { -(it.w * it.d) },     // 大房间优先于小房间
                        )
                    )
                    .forEach { room ->
                        val roomPxW = (room.w * viewScale).toFloat()
                        val isSel = room.rawName == selected?.rawName && room.zone == selected?.zone
                        val sx = px(room.rx)
                        val sy = py(room.rz)
                        // 名称完整缩放适配房间宽度:放得下就全名显示;缩到最小仍放不下则不显示(无省略号)
                        val fullW = textPaint.measureText(room.label)
                        val targetW = min(fullW, roomPxW * 0.94f)
                        var sizePx = if (targetW < fullW) labelSize * (targetW / fullW) else labelSize
                        val minPx = with(density) { 5.5.dp.toPx() }
                        if (sizePx < minPx) {
                            if (!isSel) return@forEach
                            sizePx = minPx
                        }
                        textPaint.textSize = sizePx
                        textPaint.color = (zoneColors[room.zone] ?: fallbackZoneStyle).label.toArgb()
                        val rect = Rect(sx - targetW / 2, sy - sizePx, sx + targetW / 2, sy + sizePx * 0.3f)
                        if (!isSel && drawnLabels.any { it.overlaps(rect) }) return@forEach
                        drawContext.canvas.nativeCanvas.drawText(room.label, sx, sy + sizePx / 3f, textPaint)
                        drawnLabels += rect
                    }

                // 玩家定位(与房间同镜像规则:LCZ 加平移,其余取负)
                val zoneFiltered = zoneFilter != "All"
                players.forEach { player ->
                    val px0 = player.x ?: return@forEach
                    val pz0 = player.z ?: return@forEach
                    val py0 = player.y ?: 0.0
                    if (py0 > 200) return@forEach
                    val room = closestRoomTo(px0, py0, pz0, filteredRooms)
                    if (zoneFiltered && room == null) return@forEach
                    val (rx0, rz0) = if (room?.zone == "LightContainment") {
                        (px0 + layout.lightShiftDx) to (pz0 + layout.lightShiftDz)
                    } else {
                        -px0 to -pz0
                    }
                    val sx = px(rx0)
                    val sy = py(rz0)
                    drawCircle(Color.Black, radius = 8f, center = Offset(sx, sy))
                    drawCircle(teamColor(player.team), radius = 6.5f, center = Offset(sx, sy))
                    val namePaint = Paint().apply {
                        isAntiAlias = true
                        textAlign = Paint.Align.LEFT
                        textSize = 12f
                        color = onPanel.toArgb()
                    }
                    drawContext.canvas.nativeCanvas.drawText(player.nickname, sx + 9f, sy + 4f, namePaint)
                }
            }
        }

        // 图例
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                stringResource(R.string.map_light_containment) to "LightContainment",
                stringResource(R.string.map_heavy_containment) to "HeavyContainment",
                stringResource(R.string.map_entrance_zone) to "Entrance",
            ).forEach { (label, zone) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .width(14.dp)
                            .height(10.dp)
                            .background((zoneColors[zone] ?: fallbackZoneStyle).fill),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // 玩家位置列表
        SectionCard(stringResource(R.string.map_player_locations), subtitle = stringResource(R.string.map_by_nearest_room_y_200)) {
            val withPos = players.filter { it.x != null && it.z != null }
            if (withPos.isEmpty()) {
                Text(
                    stringResource(R.string.map_no_players_coordinates_yet_waiting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                withPos.forEach { player ->
                    val room = closestRoomTo(player.x ?: 0.0, player.y ?: 0.0, player.z ?: 0.0, layout.rooms)
                    val posText = "(${player.x?.toInt()}, ${player.y?.toInt()}, ${player.z?.toInt()})"
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            player.nickname,
                            Modifier.weight(1f).clarityMask(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                        Text(
                            room?.label ?: if ((player.y ?: 0.0) > 200) stringResource(R.string.map_surface) else stringResource(R.string.map_pocket_dimension),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(posText, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        FacilityControls(selected) { selected = it }
        Spacer(Modifier.height(24.dp))
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectMapCamera(
    onTap: (Offset) -> Unit,
    onTransform: (pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = down.position
        var twoFinger = false
        var moved = false
        var pastSlop = false
        var zoomAcc = 1f
        var panAcc = Offset.Zero
        val slop = viewConfiguration.touchSlop
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                twoFinger = true
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                if (!pastSlop) {
                    zoomAcc *= zoomChange
                    panAcc += panChange
                    val centroid = event.calculateCentroidSize(useCurrent = false)
                    if (abs(1f - zoomAcc) * centroid > slop || panAcc.getDistance() > slop) {
                        pastSlop = true
                    }
                }
                if (pastSlop) {
                    onTransform(panChange, zoomChange)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } else if (!twoFinger && pressed.size == 1) {
                if ((pressed[0].position - start).getDistance() > slop) moved = true
            }
        } while (event.changes.any { it.pressed })
        if (!twoFinger && !moved) onTap(start)
    }
}

/** 缩放限制在刚好看全图到 8 倍之间,平移不能把地图拖出视口(留一圈边)。 */
private fun clampMapCamera(
    scale: Float,
    pan: Offset,
    minX: Double,
    maxX: Double,
    minZ: Double,
    maxZ: Double,
    baseScale: Float,
    ox: Float,
    oy: Float,
    viewW: Float,
    viewH: Float,
): Pair<Float, Offset> {
    val nextScale = scale.coerceIn(1f, 8f)
    val cx = viewW / 2f
    val cy = viewH / 2f
    fun contentX(world: Double) = cx + ((ox + (world * baseScale).toFloat()) - cx) * nextScale
    fun contentY(world: Double) = cy + ((oy + (world * baseScale).toFloat()) - cy) * nextScale
    val margin = 24f
    return nextScale to Offset(
        clampMapAxis(pan.x, contentX(minX), contentX(maxX), viewW, margin),
        clampMapAxis(pan.y, contentY(minZ), contentY(maxZ), viewH, margin),
    )
}

private fun clampMapAxis(pan: Float, start: Float, end: Float, view: Float, margin: Float): Float {
    val size = end - start
    if (size <= view - 2 * margin) {
        return (view - size) / 2f - start
    }
    val minPan = view - margin - end
    val maxPan = margin - start
    return pan.coerceIn(minOf(minPan, maxPan), maxOf(minPan, maxPan))
}

private fun closestRoomTo(px: Double, py: Double, pz: Double, rooms: List<MapRoomUi>): MapRoomUi? {
    if (py > 200 || rooms.isEmpty()) return null
    return rooms.minByOrNull { hypot(it.worldX - px, it.worldZ - pz) + abs(it.worldY - py) }
}

@Composable
private fun FacilityControls(
    selected: MapRoomUi?,
    onSelectedChange: (MapRoomUi?) -> Unit,
) {
    val server = rememberActiveServer()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHost.current
    var lightsDuration by rememberSaveable { mutableStateOf("30") }
    var doorType by rememberSaveable { mutableStateOf("GateA") }
    var doorScope by rememberSaveable { mutableStateOf("type") }
    var elevatorType by rememberSaveable { mutableStateOf("GateA01") }
    var elevatorScope by rememberSaveable { mutableStateOf("type") }
    var sendLevel by rememberSaveable { mutableStateOf("0") }

    fun facility(body: kotlinx.serialization.json.JsonObject, okText: String) {
        val target = server ?: return
        scope.launch {
            snackbar.showOutcome(ControlRepository.call(target, "/control/map/facility", body), okText)
        }
    }

    SectionCard(stringResource(R.string.map_facility_control), subtitle = stringResource(R.string.map_lights_apply_selected_room_corridors)) {
        Text(
            if (selected == null) stringResource(R.string.map_no_room_selected_map_tap)
            else stringResource(R.string.map_selected, selected.label, selected.rawName),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (selected != null) {
                TextButton(onClick = { onSelectedChange(null) }) { Text(stringResource(R.string.map_deselect)) }
            }
        }
        OutlinedTextField(
            value = lightsDuration,
            onValueChange = { lightsDuration = it },
            label = { Text(stringResource(R.string.map_light_duration_seconds_1_300)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().keepAboveIme(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    facility(
                        buildJsonObject {
                            put("action", "lights")
                            put("room_type", selected!!.rawName)
                            put("lights_off", true)
                            put("duration", lightsDuration.toIntOrNull() ?: 30)
                        },
                        appString(R.string.map_turned_off_lights, selected?.label.orEmpty()),
                    )
                },
                enabled = selected != null && !selected.isCorridor,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.map_lights_off)) }
            OutlinedButton(
                onClick = {
                    facility(
                        buildJsonObject {
                            put("action", "lights")
                            put("room_type", selected!!.rawName)
                            put("lights_off", false)
                            put("duration", lightsDuration.toIntOrNull() ?: 30)
                        },
                        appString(R.string.map_turned_lights, selected?.label.orEmpty()),
                    )
                },
                enabled = selected != null && !selected.isCorridor,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.map_lights)) }
        }

        HorizontalDividerThin()

        DropdownField(stringResource(R.string.map_door_type), MapGenData.DOOR_TYPES, doorType) { doorType = it }
        DropdownField(stringResource(R.string.map_scope), listOf("type", "all", "all_not_list"), doorScope) { doorScope = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                facility(
                    buildJsonObject {
                        put("action", "doors"); put("scope", doorScope); put("door_type", doorType); put("lock_door", true)
                    }, appString(R.string.map_locked, doorType),
                )
            }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.map_lock)) }
            OutlinedButton(onClick = {
                facility(
                    buildJsonObject {
                        put("action", "doors"); put("scope", doorScope); put("door_type", doorType); put("lock_door", false)
                    }, appString(R.string.map_unlocked, doorType),
                )
            }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.common_unlock)) }
            OutlinedButton(onClick = {
                facility(
                    buildJsonObject {
                        put("action", "doors"); put("scope", doorScope); put("door_type", doorType); put("open_door", true)
                    }, appString(R.string.map_opened, doorType),
                )
            }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.map_open)) }
            OutlinedButton(onClick = {
                facility(
                    buildJsonObject {
                        put("action", "doors"); put("scope", doorScope); put("door_type", doorType); put("open_door", false)
                    }, appString(R.string.map_closed, doorType),
                )
            }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.map_close)) }
        }

        HorizontalDividerThin()

        DropdownField(stringResource(R.string.map_elevator_type), MapGenData.ELEVATOR_TYPES, elevatorType) { elevatorType = it }
        DropdownField(stringResource(R.string.map_scope), listOf("type", "all"), elevatorScope) { elevatorScope = it }
        OutlinedTextField(
            value = sendLevel,
            onValueChange = { sendLevel = it },
            label = { Text(stringResource(R.string.map_target_floor_0_20)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().keepAboveIme(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                facility(
                    buildJsonObject {
                        put("action", "elevators"); put("scope", elevatorScope); put("elevator_type", elevatorType); put("command", "up")
                    }, appString(R.string.map_up, elevatorType),
                )
            }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.map_up_2)) }
            OutlinedButton(onClick = {
                facility(
                    buildJsonObject {
                        put("action", "elevators"); put("scope", elevatorScope); put("elevator_type", elevatorType); put("command", "down")
                    }, appString(R.string.map_down, elevatorType),
                )
            }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.map_down_2)) }
            Button(onClick = {
                facility(
                    buildJsonObject {
                        put("action", "elevators"); put("scope", elevatorScope); put("elevator_type", elevatorType)
                        put("command", "send"); put("level", sendLevel.toIntOrNull() ?: 0)
                    }, appString(R.string.map_sent_floor, elevatorType),
                )
            }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.map_send)) }
        }
    }
}

@Composable
private fun HorizontalDividerThin() {
    androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
}
