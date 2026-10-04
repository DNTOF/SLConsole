// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.mapgen

/**
 * 地图生成静态数据:glyph 颜色对、区域候选表、区域元数据、连接模式、中文显示名。
 * 与 Web 端 mapgen/data.ts / roomNames.ts / Map.tsx 同源。
 */
object MapGenData {

    const val GRID_SCALE = 15
    const val GLYPH_SIZE = 3
    const val BG_R = 100
    const val BG_G = 100
    const val BG_B = 100
    const val TOLERANCE = 5

    /** 区域楼层高度(EZ 再加 y=0 偏移,仍为 -100)。 */
    val ZONE_HEIGHTS = mapOf(
        "LightContainment" to 100.0,
        "HeavyContainment" to -100.0,
        "Entrance" to -100.0,
    )

    /** EZ 硬位置/旋转偏移(与 HCZ 检查点质心对齐)。 */
    val EZ_HARD_POSITION_OFFSET_X = 15.0
    val EZ_HARD_POSITION_OFFSET_Z = 0.0
    const val EZ_HARD_ROTATION_OFFSET = 270

    /** 形状网格占用(格),1 格 = GRID_SCALE 世界单位。 */
    val SHAPE_GRID_SIZE = mapOf(
        "Endroom" to (1 to 1),
        "Straight" to (2 to 1),
        "Curve" to (2 to 1),
        "TShape" to (3 to 1),
        "XShape" to (3 to 1),
    )

    /** glyph 颜色对(RGB,±TOLERANCE 匹配;不看 alpha)。 */
    data class GlyphPair(
        val r: Int, val g: Int, val b: Int,
        val centerOffsetX: Int, val centerOffsetY: Int,
        val shape: String,
        val specificRooms: List<String>,
        val rotations: List<Int>,
    )

    val GLYPH_PAIRS = listOf(
        GlyphPair(255, 0, 255, 1, 0, "Straight", emptyList(), listOf(0, 180)),
        GlyphPair(255, 0, 128, 0, 1, "Straight", emptyList(), listOf(90, 270)),
        GlyphPair(255, 0, 0, 0, 1, "Curve", emptyList(), listOf(0)),
        GlyphPair(255, 192, 0, 0, 1, "Curve", emptyList(), listOf(90)),
        GlyphPair(255, 128, 0, 1, 0, "Curve", emptyList(), listOf(180)),
        GlyphPair(255, 64, 0, 0, 0, "Curve", emptyList(), listOf(270)),
        GlyphPair(0, 192, 255, 0, 1, "TShape", emptyList(), listOf(0)),
        GlyphPair(0, 128, 255, 1, 0, "TShape", emptyList(), listOf(90)),
        GlyphPair(0, 0, 255, 0, 1, "TShape", emptyList(), listOf(180)),
        GlyphPair(128, 0, 255, 0, 1, "TShape", emptyList(), listOf(270)),
        GlyphPair(0, 255, 255, 0, 1, "XShape", emptyList(), listOf(0, 90, 180, 270)),
        GlyphPair(0, 255, 0, 1, 1, "Endroom", emptyList(), listOf(0)),
        GlyphPair(0, 192, 0, 1, 1, "Endroom", emptyList(), listOf(90)),
        GlyphPair(0, 128, 0, 1, 1, "Endroom", emptyList(), listOf(180)),
        GlyphPair(0, 64, 0, 1, 1, "Endroom", emptyList(), listOf(270)),
        GlyphPair(255, 255, 0, 1, 1, "Endroom", listOf("LczClassDSpawn"), listOf(0)),
        GlyphPair(205, 255, 0, 0, 1, "Straight", listOf("HczCheckpointToEntranceZone"), listOf(0)),
    )

    data class ZoneCandidate(
        val name: String,
        val shape: String,
        val min: Int,
        val max: Int,
        val chance: Double,
        val adjChance: Double,
        val special: Boolean,
    )

    val ZONE_CANDIDATES: Map<String, List<ZoneCandidate>> = mapOf(
        "HeavyContainment" to listOf(
            ZoneCandidate("Hcz049", "Straight", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("Hcz079", "Endroom", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("Hcz096", "Endroom", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("Hcz106", "Endroom", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("Hcz939", "Curve", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("HczCheckpointA", "Endroom", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("HczCheckpointB", "Endroom", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("Unnamed", "Curve", 0, 10, 1.0, 0.1, false),
            ZoneCandidate("Unnamed", "XShape", 0, 10, 1.0, 0.1, false),
            ZoneCandidate("HczAcroamaticAbatement", "XShape", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("Unnamed", "Curve", 0, 10, 0.1, 0.1, false),
            ZoneCandidate("Hcz127", "TShape", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("HczServers", "Straight", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("Unnamed", "TShape", 0, 15, 1.0, 0.1, false),
            ZoneCandidate("Unnamed", "TShape", 0, 1, 100.0, 0.1, false),
            ZoneCandidate("HczMicroHID", "Straight", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("HczWarhead", "TShape", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("Unnamed", "Straight", 0, 10, 1.0, 0.1, false),
            ZoneCandidate("Unnamed", "Straight", 0, 1, 5.0, 0.1, false),
            ZoneCandidate("Unnamed", "Straight", 0, 1, 100.0, 0.1, false),
            ZoneCandidate("HczArmory", "TShape", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("HczTesla", "Straight", 1, 3, 2.0, 0.0, false),
            ZoneCandidate("HczTestroom", "Straight", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("HczCheckpointToEntranceZone", "Straight", 2, 2, 1.0, 0.1, true),
            ZoneCandidate("HczWaysideIncinerator", "Curve", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("HczRampTunnel", "TShape", 1, 1, 100.0, 0.1, false),
        ),
        "LightContainment" to listOf(
            ZoneCandidate("LczClassDSpawn", "Endroom", 1, 1, 1.0, 0.1, true),
            ZoneCandidate("LczComputerRoom", "Endroom", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("LczCheckpointA", "Endroom", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("LczCheckpointB", "Endroom", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("LczToilets", "Straight", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("LczArmory", "Endroom", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("Lcz173", "Endroom", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("LczGlassroom", "Endroom", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("Lcz914", "Endroom", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("Lcz330", "Endroom", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("LczGreenhouse", "Straight", 0, 1, 100.0, 0.1, false),
            ZoneCandidate("LczAirlock", "Straight", 1, 2, 100.0, 0.1, false),
            ZoneCandidate("Unnamed", "XShape", 0, 15, 1.0, 0.1, false),
            ZoneCandidate("Unnamed", "TShape", 0, 15, 1.0, 0.1, false),
            ZoneCandidate("Unnamed", "Straight", 0, 15, 0.1, 0.1, false),
            ZoneCandidate("Unnamed", "Curve", 0, 15, 1.0, 0.1, false),
        ),
        "Entrance" to listOf(
            ZoneCandidate("Unnamed", "Straight", 0, 1, 1.0, 0.1, false),
            ZoneCandidate("Unnamed", "Straight", 1, 1, 10.0, 0.1, false),
            ZoneCandidate("EzCollapsedTunnel", "Endroom", 0, 10, 1.0, 0.1, false),
            ZoneCandidate("Unnamed", "XShape", 0, 15, 1.0, 0.1, false),
            ZoneCandidate("Unnamed", "Curve", 0, 15, 1.0, 0.1, false),
            ZoneCandidate("EzRedroom", "Endroom", 0, 10, 1.0, 0.1, false),
            ZoneCandidate("EzGateA", "Endroom", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("EzGateB", "Endroom", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("HczCheckpointToEntranceZone", "Straight", 2, 2, 1.0, 0.1, true),
            ZoneCandidate("EzIntercom", "Curve", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("EzOfficeLarge", "Straight", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("EzOfficeSmall", "Straight", 1, 1, 100.0, 0.1, false),
            ZoneCandidate("EzEvacShelter", "Endroom", 1, 1, 1.0, 0.1, false),
            ZoneCandidate("Unnamed", "Straight", 0, 1, 100.0, 0.1, false),
            ZoneCandidate("Unnamed", "Straight", 0, 10, 0.05, 0.1, false),
            ZoneCandidate("Unnamed", "TShape", 0, 15, 1.0, 0.1, false),
            ZoneCandidate("EzOfficeStoried", "Straight", 1, 1, 1.0, 0.1, false),
        ),
    )

    /** 检查点:开口不与邻居求交。 */
    val CHECKPOINT_NO_INTERSECT = setOf(
        "LczCheckpointA", "LczCheckpointB", "HczCheckpointA", "HczCheckpointB",
    )

    /** 桥接检查点固定开口。 */
    val FIXED_CONN: Map<String, List<Pair<Int, Int>>> = mapOf(
        "HczCheckpointToEntranceZone" to listOf(-1 to 0, 1 to 0),
    )

    /** 具体房间开口模式(模型坐标系,按 rotY 旋转后与实际邻居求交)。 */
    val ROOM_CONN_PATTERN: Map<String, List<Pair<Int, Int>>> = mapOf(
        "EzEvacShelter" to listOf(0 to 1),
        "EzGateA" to listOf(0 to 1),
        "EzGateB" to listOf(0 to 1),
        "EzCollapsedTunnel" to listOf(0 to 1),
        "EzIntercom" to listOf(-1 to 0, 0 to -1),
        "EzOfficeLarge" to listOf(0 to -1, 0 to 1),
        "EzOfficeSmall" to listOf(0 to -1, 0 to 1),
        "EzOfficeStoried" to listOf(0 to -1, 0 to 1),
        "EzRedroom" to listOf(0 to 1),
        "Hcz049" to listOf(-1 to 0, 1 to 0),
        "Hcz079" to listOf(-1 to 0),
        "Hcz096" to listOf(-1 to 0),
        "Hcz106" to listOf(-1 to 0),
        "Hcz127" to listOf(-1 to 0, 0 to -1, 0 to 1),
        "Hcz939" to listOf(0 to -1, 1 to 0),
        "HczAcroamaticAbatement" to listOf(-1 to 0, 0 to -1, 0 to 1, 1 to 0),
        "HczArmory" to listOf(-1 to 0, 0 to -1, 0 to 1),
        "HczCheckpointA" to listOf(-1 to 0),
        "HczCheckpointB" to listOf(-1 to 0),
        "HczMicroHID" to listOf(-1 to 0, 1 to 0),
        "HczRampTunnel" to listOf(-1 to 0, 0 to -1, 0 to 1),
        "HczServers" to listOf(-1 to 0, 1 to 0),
        "HczTesla" to listOf(-1 to 0, 1 to 0),
        "HczTestroom" to listOf(-1 to 0, 1 to 0),
        "HczWarhead" to listOf(-1 to 0, 0 to -1, 0 to 1),
        "HczWaysideIncinerator" to listOf(0 to -1, 1 to 0),
        "Lcz173" to listOf(-1 to 0),
        "Lcz330" to listOf(-1 to 0),
        "Lcz914" to listOf(-1 to 0),
        "LczAirlock" to listOf(-1 to 0, 1 to 0),
        "LczArmory" to listOf(-1 to 0),
        "LczCheckpointA" to listOf(-1 to 0),
        "LczCheckpointB" to listOf(-1 to 0),
        "LczClassDSpawn" to listOf(1 to 0),
        "LczComputerRoom" to listOf(-1 to 0),
        "LczGlassroom" to listOf(-1 to 0),
        "LczGreenhouse" to listOf(-1 to 0, 1 to 0),
        "LczToilets" to listOf(-1 to 0, 1 to 0),
    )

    /** 走廊开口模式(按形状)。 */
    val CORRIDOR_CONN_PATTERN: Map<String, List<Pair<Int, Int>>> = mapOf(
        "Curve" to listOf(0 to -1, 1 to 0),
        "Straight" to listOf(-1 to 0, 1 to 0),
        "TShape" to listOf(-1 to 0, 0 to -1, 0 to 1),
        "XShape" to listOf(-1 to 0, 0 to -1, 0 to 1, 1 to 0),
    )

    /** 房间渲染形状分类(与 Web 端 GLYPH_BY_NAME 一致)。 */
    val GLYPH_BY_NAME: Map<String, String> = mapOf(
        "Lcz173" to "octagon", "Lcz330" to "octagon", "Lcz914" to "octagon",
        "Hcz049" to "octagon", "Hcz079" to "octagon", "Hcz096" to "octagon",
        "Hcz106" to "octagon", "Hcz127" to "octagon", "Hcz939" to "octagon",
        "LczAirlock" to "gate", "LczCheckpointA" to "gate", "LczCheckpointB" to "gate",
        "HczCheckpointA" to "gate", "HczCheckpointB" to "gate", "HczCheckpointToEntranceZone" to "gate",
        "EzGateA" to "gate", "EzGateB" to "gate",
        "LczArmory" to "shield", "HczArmory" to "shield",
        "HczMicroHID" to "hex", "HczWarhead" to "hex",
        "HczWaysideIncinerator" to "circle",
        "EzCollapsedTunnel" to "dead", "EzEvacShelter" to "dead", "EzRedroom" to "dead",
        "HczRampTunnel" to "pass", "HczTesla" to "pass",
    )

    /** 房间中文显示名(与 Web 端 roomNames.ts 一致)。 */
    val ROOM_NAMES: Map<String, String> = mapOf(
        "LczToilets" to "卫生间",
        "LczAirlock" to "气闸室",
        "LczComputerRoom" to "电脑房",
        "LczGreenhouse" to "温室",
        "LczCheckpointA" to "A 电梯(轻收容侧)",
        "LczCheckpointB" to "B 电梯(轻收容侧)",
        "LczClassDSpawn" to "D 级人员出生点",
        "LczArmory" to "轻型武器库",
        "Lcz173" to "旧 173 收容间",
        "LczGlassroom" to "玻璃房",
        "Lcz914" to "SCP-914 收容间",
        "Lcz330" to "糖果屋(SCP-330)",
        "HczAcroamaticAbatement" to "瀑布房",
        "Hcz127" to "SCP-127 收容间",
        "HczWarhead" to "核弹控制室",
        "HczArmory" to "重型武器库",
        "HczRampTunnel" to "斜坡",
        "Hcz049" to "049 / 173 收容间",
        "Hcz939" to "SCP-939 收容间",
        "HczWaysideIncinerator" to "路边焚化炉",
        "HczServers" to "机房",
        "HczMicroHID" to "微型 HID 室",
        "HczCheckpointToEntranceZone" to "检查点(通往办公区)",
        "HczTesla" to "电网",
        "HczTestroom" to "老狗家",
        "Hcz079" to "SCP-079 收容间",
        "Hcz096" to "SCP-096 收容间",
        "Hcz106" to "SCP-106 收容间",
        "HczCheckpointA" to "A 电梯(重收容侧)",
        "HczCheckpointB" to "B 电梯(重收容侧)",
        "EzGateA" to "A 大门(地表出口)",
        "EzGateB" to "B 大门(地表出口)",
        "EzEvacShelter" to "避难所",
        "EzIntercom" to "广播室",
        "EzRedroom" to "红房",
        "EzOfficeLarge" to "大型办公室",
        "EzOfficeSmall" to "小型办公室",
        "EzOfficeStoried" to "普通办公室",
        "EzCollapsedTunnel" to "坍塌隧道",
    )

    /** 走廊显示名(按形状)。 */
    fun corridorName(shape: String): String = when (shape) {
        "TShape" -> "T 形路口"
        "Straight" -> "直线走廊"
        "XShape" -> "十字路口"
        "Curve" -> "转角走廊"
        "Endroom" -> "死路"
        else -> "走廊"
    }

    /** 门类型(设施控制下拉)。 */
    val DOOR_TYPES = listOf(
        "GateA", "GateB", "CheckpointLczA", "CheckpointLczB", "CheckpointEzHcz",
        "HczEzCheckpoint", "EntranceCheckpoint", "HczArmory", "LczArmory",
        "Scp173Gate", "Scp914Gate", "Scp106Primary", "Scp106Secondary", "Scp096",
        "Scp049Gate", "Scp049Armory", "Scp330", "SurfaceGate",
        "EscapePrimary", "EscapeSecondary", "LczWc", "Scp330Chamber",
    )

    /** 电梯类型(设施控制下拉)。 */
    val ELEVATOR_TYPES = listOf(
        "Nuke01", "Nuke02", "Scp049", "GateA01", "GateA02", "GateB",
        "LczA01", "LczA02", "LczB01", "LczB02", "ServerRoom",
    )
}
