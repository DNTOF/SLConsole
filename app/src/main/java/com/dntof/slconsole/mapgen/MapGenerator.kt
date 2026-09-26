package com.dntof.slconsole.mapgen

import android.util.Base64
import kotlin.math.abs
import kotlin.math.floor

/** 图集解释结果(一个 glyph 槽位)。 */
data class GenInterp(
    val shape: String,
    val specificRooms: List<String>,
    val coordsX: Int,
    val coordsZ: Int,
    val rotY: Int,
)

/** 生成的房间(世界坐标)。 */
data class SpawnedRoom(
    val name: String,
    val shape: String,
    val zone: String,
    val coordsX: Int,
    val coordsZ: Int,
    val rotY: Int,
    val worldX: Double,
    val worldY: Double,
    val worldZ: Double,
    val conn: List<Pair<Int, Int>>,
)

/** 显示布局中的房间(镜像/平移后的渲染模型)。 */
data class MapRoomUi(
    val rawName: String,
    val label: String,
    val zone: String,
    val shape: String,
    val isCorridor: Boolean,
    val glyph: String,
    val rx: Double,
    val rz: Double,
    val w: Double,
    val d: Double,
    val worldX: Double,
    val worldY: Double,
    val worldZ: Double,
    val conn: List<Pair<Int, Int>>,
)

data class MapLayoutUi(
    val rooms: List<MapRoomUi>,
    val minX: Double,
    val maxX: Double,
    val minZ: Double,
    val maxZ: Double,
    /** LCZ 显示平移量:玩家坐标映射需使用同一平移。 */
    val lightShiftDx: Double,
    val lightShiftDz: Double,
)

/**
 * SCP:SL 地图布局生成器:seed → 确定性布局。
 * 与 Web 端 mapgen/generate.ts + Map.tsx 的 layoutFromGen 同源。
 */
object MapGenerator {

    fun generateLayout(seed: Int): List<SpawnedRoom> {
        val rng = DotNetRandom(seed)
        val all = mutableListOf<SpawnedRoom>()
        var hczCheckpointCentroid: DoubleArray? = null

        for (zone in listOf("LightContainment", "HeavyContainment", "Entrance")) {
            val prefix = when (zone) {
                "LightContainment" -> "LC_"
                "HeavyContainment" -> "HC_"
                else -> "EZ_"
            }
            val indexes = MapGenAtlas.NAMES.withIndex()
                .filter { it.value.startsWith(prefix) }
                .map { it.index }
            val atlasIndex = indexes[rng.nextInt(indexes.size)]
            val rgba = Base64.decode(MapGenAtlas.RGBA_BASE64[atlasIndex], Base64.NO_WRAP)
            val interps = interpretAtlas(rgba, 32, 32, rng)

            var offsetX = 0.0
            var offsetZ = 0.0
            var rotOffset = 0
            if (zone == "HeavyContainment") {
                hczCheckpointCentroid = centroidOfCheckpoints(interps)
            }
            if (zone == "Entrance") {
                val ezCentroid = centroidOfCheckpoints(interps)
                val hcz = hczCheckpointCentroid ?: doubleArrayOf(0.0, 0.0)
                offsetX = hcz[0] - ezCentroid[0] + MapGenData.EZ_HARD_POSITION_OFFSET_X
                offsetZ = hcz[1] - ezCentroid[1] + MapGenData.EZ_HARD_POSITION_OFFSET_Z
                rotOffset = MapGenData.EZ_HARD_ROTATION_OFFSET
            }

            // Fisher-Yates 洗牌(每次迭代消耗 1 次随机)
            val slots = interps.toMutableList()
            for (i in slots.size - 1 downTo 1) {
                val j = rng.nextInt(i + 1)
                val tmp = slots[i]
                slots[i] = slots[j]
                slots[j] = tmp
            }

            val candidates = MapGenData.ZONE_CANDIDATES[zone].orEmpty()
            val spawned = mutableListOf<Pair<IntArray, String>>()
            for (interp in slots) {
                var chosen: MapGenData.ZoneCandidate? = null
                val pool = mutableListOf<Pair<MapGenData.ZoneCandidate, Double>>()
                val hasSpecific = interp.specificRooms.isNotEmpty()
                for (cand in candidates) {
                    if (cand.shape != interp.shape) continue
                    if (cand.special != hasSpecific) continue
                    if (hasSpecific && interp.specificRooms.none { it == cand.name }) continue
                    val key = candidateKey(cand)
                    val prev = spawned.count { it.second == key }
                    if (prev >= cand.max) continue
                    if (prev < cand.min) {
                        chosen = cand
                        break
                    }
                    val adj = spawned.count {
                        it.second == key && isAdjacent(it.first, interp.coordsX, interp.coordsZ)
                    }
                    val weight = cand.chance * Math.pow(cand.adjChance, adj.toDouble())
                    if (weight > 0) pool += cand to weight
                }
                if (chosen == null && pool.isNotEmpty()) {
                    val total = pool.sumOf { it.second }
                    val roll = rng.nextDouble() * total
                    var acc = 0.0
                    for ((cand, weight) in pool) {
                        acc += weight
                        if (roll <= acc) {
                            chosen = cand
                            break
                        }
                    }
                }
                if (chosen != null) {
                    all += SpawnedRoom(
                        name = chosen.name,
                        shape = interp.shape,
                        zone = zone,
                        coordsX = interp.coordsX,
                        coordsZ = interp.coordsZ,
                        rotY = interp.rotY + rotOffset,
                        worldX = interp.coordsX * 15.0 + offsetX,
                        worldY = MapGenData.ZONE_HEIGHTS[zone] ?: 0.0,
                        worldZ = interp.coordsZ * 15.0 + offsetZ,
                        conn = emptyList(),
                    )
                    spawned += intArrayOf(interp.coordsX, interp.coordsZ) to candidateKey(chosen)
                }
            }
        }
        return attachConns(all)
    }

    /** 显示重排:非 LCZ 镜像,LCZ 平移到核心区上方;并推导渲染矩形。 */
    fun layoutFromGen(rooms: List<SpawnedRoom>): MapLayoutUi {
        data class Disp(
            val room: SpawnedRoom,
            var rx: Double,
            var rz: Double,
            val w: Double,
            val d: Double,
            val conn: List<Pair<Int, Int>>,
        )

        val disp = rooms.map { r ->
            val mirror = r.zone != "LightContainment"
            val rx = if (mirror) -r.worldX else r.worldX
            val rz = if (mirror) -r.worldZ else r.worldZ
            val conn = r.conn.map { if (mirror) -it.first to -it.second else it }
            val (gw, gd) = MapGenData.SHAPE_GRID_SIZE[r.shape] ?: (1 to 1)
            val rotNorm = ((r.rotY % 180) + 180) % 180
            val horizontal = rotNorm < 45 || rotNorm > 135
            val w = (if (horizontal) gw else gd) * 15.0
            val d = (if (horizontal) gd else gw) * 15.0
            Disp(r, rx, rz, w, d, conn)
        }

        var lightShiftDx = 0.0
        var lightShiftDz = 0.0
        val core = disp.filter { it.room.zone != "LightContainment" }
        val light = disp.filter { it.room.zone == "LightContainment" }
        if (core.isNotEmpty() && light.isNotEmpty()) {
            val coreCx = (core.minOf { it.rx } + core.maxOf { it.rx }) / 2
            val lightCx = (light.minOf { it.rx } + light.maxOf { it.rx }) / 2
            val coreMaxZ = core.maxOf { it.rz }
            val lightMinZ = light.minOf { it.rz }
            lightShiftDx = Math.round((coreCx - lightCx) / 15.0) * 15.0
            lightShiftDz = Math.round((coreMaxZ + 30 - lightMinZ) / 15.0) * 15.0
            light.forEach {
                it.rx += lightShiftDx
                it.rz += lightShiftDz
            }
        }

        val roomsUi = disp.map { item ->
            val r = item.room
            val isCorridor = r.name == "Unnamed"
            val glyph = if (isCorridor) "corridor" else MapGenData.GLYPH_BY_NAME[r.name] ?: "rect"
            val displayName = when {
                isCorridor -> MapGenData.corridorName(r.shape)
                MapGenData.ROOM_NAMES.containsKey(r.name) -> MapGenData.ROOM_NAMES[r.name]!!
                else -> r.name.replace(Regex("^\\w{3}_?"), "$1 ")
            }
            MapRoomUi(
                rawName = r.name,
                label = displayName,
                zone = r.zone,
                shape = r.shape,
                isCorridor = isCorridor,
                glyph = glyph,
                rx = item.rx,
                rz = item.rz,
                w = item.w,
                d = item.d,
                worldX = r.worldX,
                worldY = r.worldY,
                worldZ = r.worldZ,
                conn = item.conn,
            )
        }
        return MapLayoutUi(
            rooms = roomsUi,
            minX = roomsUi.minOf { it.rx - it.w / 2 },
            maxX = roomsUi.maxOf { it.rx + it.w / 2 },
            minZ = roomsUi.minOf { it.rz - it.d / 2 },
            maxZ = roomsUi.maxOf { it.rz + it.d / 2 },
            lightShiftDx = lightShiftDx,
            lightShiftDz = lightShiftDz,
        )
    }

    // ---------- 内部实现 ----------

    private fun candidateKey(cand: MapGenData.ZoneCandidate): String =
        "${cand.name}|${cand.shape}|${cand.chance}|${cand.min}|${cand.max}"

    private fun isAdjacent(a: IntArray, x: Int, z: Int): Boolean {
        val dx = abs(a[0] - x)
        val dz = abs(a[1] - z)
        return (dx == 1 && dz == 0) || (dx == 0 && dz == 1)
    }

    private fun centroidOfCheckpoints(interps: List<GenInterp>): DoubleArray {
        val checkpoints = interps.filter { it.specificRooms.contains("HczCheckpointToEntranceZone") }
        if (checkpoints.isEmpty()) return doubleArrayOf(0.0, 0.0)
        val cx = checkpoints.sumOf { it.coordsX * 15.0 } / checkpoints.size
        val cz = checkpoints.sumOf { it.coordsZ * 15.0 } / checkpoints.size
        return doubleArrayOf(cx, cz)
    }

    private fun withinTolerance(r: Int, g: Int, b: Int, pr: Int, pg: Int, pb: Int): Boolean =
        abs(r - pr) <= MapGenData.TOLERANCE && abs(g - pg) <= MapGenData.TOLERANCE && abs(b - pb) <= MapGenData.TOLERANCE

    private fun scanPixel(bytes: ByteArray, w: Int, x: Int, y: Int): MapGenData.GlyphPair? {
        val idx = (y * w + x) * 4
        val r = bytes[idx].toInt() and 0xFF
        val g = bytes[idx + 1].toInt() and 0xFF
        val b = bytes[idx + 2].toInt() and 0xFF
        if (withinTolerance(r, g, b, MapGenData.BG_R, MapGenData.BG_G, MapGenData.BG_B)) return null
        for (pair in MapGenData.GLYPH_PAIRS) {
            if (withinTolerance(r, g, b, pair.r, pair.g, pair.b)) return pair
        }
        return null
    }

    /** 图集扫描(复刻 MapAtlasInterpreter.Interpret):对齐前逐像素,首个 glyph 校正为 3 网格步长。 */
    private fun interpretAtlas(bytes: ByteArray, w: Int, h: Int, rng: DotNetRandom): List<GenInterp> {
        val results = mutableListOf<GenInterp>()
        var step = 1
        var xStart = 0
        var aligned = false
        var y = 0
        while (y < h) {
            var x = xStart
            while (x < w) {
                val pair = scanPixel(bytes, w, x, y)
                if (pair != null) {
                    if (!aligned) {
                        x += pair.centerOffsetX
                        y += pair.centerOffsetY
                        step = MapGenData.GLYPH_SIZE
                        xStart = x % 3
                        aligned = true
                    }
                    results += GenInterp(
                        shape = pair.shape,
                        specificRooms = pair.specificRooms,
                        coordsX = floor(x / 3.0).toInt(),
                        coordsZ = floor(y / 3.0).toInt(),
                        rotY = pair.rotations[rng.nextInt(pair.rotations.size)],
                    )
                }
                x += step
            }
            y += step
        }
        return results
    }

    /** Unity Y 轴顺时针旋转开口方向。 */
    private fun rotateConn(dx: Int, dz: Int, rotY: Int): Pair<Int, Int> {
        val t = ((rotY % 360) + 360) % 360
        return when (t) {
            90 -> dz to -dx
            180 -> -dx to -dz
            270 -> -dz to dx
            else -> dx to dz
        }
    }

    /** 槽位键:世界坐标归约到网格(HCZ/EZ 偏移后取整对齐)。 */
    private fun slotKey(room: SpawnedRoom): Pair<Int, Int> =
        Math.round(room.worldX / 15.0).toInt() to Math.round(room.worldZ / 15.0).toInt()

    /** 连接臂推导:LCZ 一组,HCZ+EZ 合并一组(同层,偏移后对齐)。 */
    private fun attachConns(rooms: List<SpawnedRoom>): List<SpawnedRoom> {
        val connByRoom = HashMap<SpawnedRoom, List<Pair<Int, Int>>>()
        val groups = listOf(
            rooms.filter { it.zone == "LightContainment" },
            rooms.filter { it.zone != "LightContainment" },
        )
        for (group in groups) {
            val bySlot = group.associateBy { slotKey(it) }
            for (room in group) {
                val (sx, sz) = slotKey(room)
                val neighbors = mutableListOf<Pair<Int, Int>>()
                for (dir in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                    if (bySlot.containsKey(sx + dir.first to sz + dir.second)) neighbors += dir
                }
                val isCorridor = room.name == "Unnamed"
                val pattern: List<Pair<Int, Int>> = when {
                    MapGenData.FIXED_CONN.containsKey(room.name) ->
                        MapGenData.FIXED_CONN[room.name]!!
                    isCorridor -> {
                        val base = MapGenData.CORRIDOR_CONN_PATTERN[room.shape]
                        val rot = if (room.zone == "Entrance") {
                            room.rotY - MapGenData.EZ_HARD_ROTATION_OFFSET
                        } else {
                            room.rotY
                        }
                        if (base != null) base.map { rotateConn(it.first, it.second, rot) }
                        else neighbors.take(1)
                    }
                    else ->
                        (MapGenData.ROOM_CONN_PATTERN[room.name] ?: emptyList())
                            .map { rotateConn(it.first, it.second, room.rotY) }
                }
                val conn = when {
                    MapGenData.FIXED_CONN.containsKey(room.name) -> pattern
                    !isCorridor && MapGenData.CHECKPOINT_NO_INTERSECT.contains(room.name) -> pattern
                    else -> pattern.filter { neighbors.contains(it) }
                }
                connByRoom[room] = conn
            }
        }
        return rooms.map { it.copy(conn = connByRoom[it].orEmpty()) }
    }
}
