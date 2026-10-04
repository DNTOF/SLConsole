package com.dntof.slconsole.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * GET /get_sl_data 响应(SLDataAPI 2.6.0 契约,字段名 snake_case)。
 * 2.6.1 预发布在打开 beta_adapted_plugin_actions 时,适配插件会多一个 actions 列表;没打开时这个字段不会出现。
 */
@Serializable
data class ServerData(
    @SerialName("server_name") val serverName: String? = null,
    val online: Boolean = false,
    @SerialName("players_count") val playersCount: Int = 0,
    @SerialName("max_players") val maxPlayers: Int = 0,
    @SerialName("round_started") val roundStarted: Boolean = false,
    @SerialName("round_duration") val roundDuration: Int = 0,
    @SerialName("current_phase") val currentPhase: String? = null,
    @SerialName("nuke_status") val nukeStatus: String? = null,
    @SerialName("nuke_countdown") val nukeCountdown: Int? = null,
    @SerialName("voice_port") val voicePort: Int = 0,
    @SerialName("d_count") val dCount: Int = 0,
    @SerialName("foundation_count") val foundationCount: Int = 0,
    @SerialName("scp_count") val scpCount: Int = 0,
    @SerialName("spectator_count") val spectatorCount: Int = 0,
    val ping: Int = 0,
    val players: List<PlayerInfo> = emptyList(),
    @SerialName("dntof_plugins") val dntofPlugins: DntofPlugins = DntofPlugins(),
    @SerialName("adapted_plugins") val adaptedPlugins: List<AdaptedPlugin> = emptyList(),
)

@Serializable
data class PlayerInfo(
    val nickname: String = "",
    @SerialName("steam_id") val steamId: String = "",
    val role: String = "",
    val team: String = "",
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
)

@Serializable
data class AdaptedPlugin(
    val id: String = "",
    val name: String? = null,
    val version: String? = null,
    val capabilities: List<String> = emptyList(),
    val routes: List<String> = emptyList(),
    /**
     * 2.6.1 内测写操作的路径名,例如 echo、bump。
     * 服务器只在 beta_adapted_plugin_actions 打开时才带这个字段,旧版没有它。
     */
    val actions: List<String> = emptyList(),
    /** 插件自己的 status 回调,形状不固定。 */
    val status: JsonElement? = null,
)

@Serializable
data class DntofPlugins(
    @SerialName("sl_player") val slPlayer: SlPlayerSnapshot? = null,
    @SerialName("omega_warhead") val omegaWarhead: OmegaWarheadSnapshot? = null,
)

@Serializable
data class SlPlayerSnapshot(
    val present: Boolean = false,
    @SerialName("source_mode") val sourceMode: String? = null,
    @SerialName("remote_url") val remoteUrl: String? = null,
    @SerialName("now_playing") val nowPlaying: String? = null,
)

@Serializable
data class CoinHolder(
    val nickname: String = "",
    val count: Int = 0,
    val position: String = "",
)

@Serializable
data class OmegaWarheadSnapshot(
    val present: Boolean = false,
    val phase: String = "none",
    @SerialName("coin_holders") val coinHolders: List<CoinHolder> = emptyList(),
    @SerialName("controller_holder") val controllerHolder: String? = null,
    val countdown: Int? = null,
)

/** POST /control/plugins/slplayer action=status 的 data。 */
@Serializable
data class SlPlayerStatus(
    val playing: Boolean = false,
    val index: Int = -1,
    val song: String? = null,
    @SerialName("elapsed_seconds") val elapsedSeconds: Int = 0,
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
    val volume: Int = 0,
    val shuffle: Boolean = false,
    val source: String? = null,
    @SerialName("remote_url") val remoteUrl: String? = null,
    @SerialName("playlist_count") val playlistCount: Int = 0,
    val playlist: List<SlPlayerSong> = emptyList(),
)

@Serializable
data class SlPlayerSong(
    val index: Int = 0,
    val display: String = "",
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
    val current: Boolean = false,
)

@Serializable
data class BanEntry(
    @SerialName("user_id") val userId: String = "",
    @SerialName("original_name") val originalName: String? = null,
    val reason: String? = null,
    val issuer: String? = null,
    @SerialName("ban_type") val banType: String = "steam",
    val expires: Long = 0,
    @SerialName("issuance_time") val issuanceTime: Long = 0,
)

@Serializable
data class BanListData(
    val count: Int = 0,
    val bans: List<BanEntry> = emptyList(),
)

@Serializable
data class LogFile(
    val path: String = "",
    val name: String? = null,
    val size: Long = 0,
    val modified: String? = null,
)

@Serializable
data class LogListData(
    val count: Int = 0,
    val files: List<LogFile> = emptyList(),
)

@Serializable
data class LogTailData(
    val file: String? = null,
    val path: String? = null,
    val total: Int = 0,
    val lines: List<String> = emptyList(),
)

@Serializable
data class PluginInfo(
    val name: String = "",
    val author: String? = null,
    val version: String? = null,
    val prefix: String? = null,
    /** SLDataAPI 对 LabAPI 插件返回字符串(p.Priority.ToString()),EXILED 可能为数字,统一按文本。 */
    val priority: String? = null,
    val enabled: Boolean = false,
    val self: Boolean? = null,
    val staged: Boolean? = null,
    val source: String? = null,
)

@Serializable
data class PluginListData(
    val count: Int = 0,
    val plugins: List<PluginInfo> = emptyList(),
)

@Serializable
data class AuditEntry(
    val time: String? = null,
    val actor: String? = null,
    val endpoint: String? = null,
    val body: String? = null,
    val success: Boolean = false,
    val message: String? = null,
)

@Serializable
data class AuditListData(
    val count: Int = 0,
    val entries: List<AuditEntry> = emptyList(),
)

@Serializable
data class PlayerDetailData(
    val nickname: String? = null,
    val userid: String? = null,
    @SerialName("player_id") val playerId: Int? = null,
    val role: String? = null,
    val health: Double? = null,
    val position: Vec3? = null,
    val room: String? = null,
)

@Serializable
data class Vec3(
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
)

@Serializable
data class ConsoleOutputData(
    val output: String? = null,
    val console: String? = null,
)

/** WS 控制通道推送的实时事件(event 帧)。 */
@Serializable
data class SlEvent(
    val event: String,
    val utc: String? = null,
    val data: JsonObject = JsonObject(emptyMap()),
    val receivedAt: Long = System.currentTimeMillis(),
)

/** GET /control/map/seed 响应。seed 为 32 位 int,可为本地图形算法重建布局。 */
@Serializable
data class MapSeedData(
    val ready: Boolean = false,
    val seed: Int? = null,
)

/** POST /control/files/list 响应条目。protected 是 Kotlin 关键字,重命名。 */
@Serializable
data class FileEntry(
    val name: String = "",
    val type: String = "file",
    val size: Long = 0,
    val modified: String? = null,
    @SerialName("protected") val isProtected: Boolean = false,
)

@Serializable
data class FileListData(
    val path: String = "",
    val count: Int = 0,
    val entries: List<FileEntry> = emptyList(),
)

@Serializable
data class FileReadData(
    val path: String? = null,
    val size: Long = 0,
    val modified: String? = null,
    val content: String = "",
)

/** POST /control/files/stat。sha256 只在可读的配置文件上出现。 */
@Serializable
data class FileStatData(
    val path: String = "",
    val type: String = "file",
    val size: Long = 0,
    val sha256: String? = null,
)

/** POST /control/files/read_chunk。offset 为 0 时带整文件 sha256。 */
@Serializable
data class FileChunkReadData(
    val path: String? = null,
    val size: Long = 0,
    val offset: Long = 0,
    val length: Int = 0,
    @SerialName("next_offset") val nextOffset: Long = 0,
    val eof: Boolean = false,
    val data: String = "",
    val sha256: String? = null,
)

/** POST /control/files/write_chunk 成功时 data 里的会话进度。 */
@Serializable
data class FileChunkWriteData(
    @SerialName("upload_id") val uploadId: String = "",
    val received: Long = 0,
    val committed: Boolean = false,
)

/** POST /control/reports (action=list) 的 data 是 ReportRecord 裸数组。 */
@Serializable
data class ReportRecord(
    val id: String = "",
    @SerialName("reporter_steam64") val reporterSteam64: String? = null,
    @SerialName("reporter_name") val reporterName: String? = null,
    @SerialName("reporter_ip") val reporterIp: String? = null,
    @SerialName("target_steam64") val targetSteam64: String? = null,
    @SerialName("target_name") val targetName: String? = null,
    val reason: String? = null,
    @SerialName("reported_at") val reportedAt: String? = null,
    val status: String = "pending",
)
