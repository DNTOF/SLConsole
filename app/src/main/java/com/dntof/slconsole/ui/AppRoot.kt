package com.dntof.slconsole.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dntof.slconsole.ServiceLocator
import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.data.repo.MonitorEngine
import com.dntof.slconsole.ui.components.StatusDot
import com.dntof.slconsole.ui.components.UiColors
import com.dntof.slconsole.ui.screens.AboutScreen
import com.dntof.slconsole.ui.screens.AuditScreen
import com.dntof.slconsole.ui.screens.BansScreen
import com.dntof.slconsole.ui.screens.ConsoleScreen
import com.dntof.slconsole.ui.screens.RemoteScreen
import com.dntof.slconsole.ui.screens.DashboardScreen
import com.dntof.slconsole.ui.screens.EventsScreen
import com.dntof.slconsole.ui.screens.FilesScreen
import com.dntof.slconsole.ui.screens.LogsScreen
import com.dntof.slconsole.ui.screens.MapScreen
import com.dntof.slconsole.ui.screens.MoreScreen
import com.dntof.slconsole.ui.screens.PlayersScreen
import com.dntof.slconsole.ui.screens.PluginsScreen
import com.dntof.slconsole.ui.screens.ReportsScreen
import com.dntof.slconsole.ui.screens.ServerEditScreen
import com.dntof.slconsole.ui.screens.ServersScreen
import com.dntof.slconsole.ui.screens.VoiceScreen
import kotlinx.coroutines.launch

val LocalSnackbarHost = staticCompositionLocalOf<SnackbarHostState> {
    error("SnackbarHostState not provided")
}

object Routes {
    const val DASHBOARD = "dashboard"
    const val PLAYERS = "players"
    const val CONSOLE = "console"
    const val REMOTE = "remote"
    const val EVENTS = "events"
    const val MORE = "more"
    const val BANS = "bans"
    const val LOGS = "logs"
    const val AUDIT = "audit"
    const val PLUGINS = "plugins"
    const val MAPS = "maps"
    const val VOICE = "voice"
    const val FILES = "files"
    const val REPORTS = "reports"
    const val SERVERS = "servers"
    const val ABOUT = "about"
    const val SERVER_EDIT = "serverEdit?serverId={serverId}"

    fun serverEdit(id: String?): String = "serverEdit?serverId=${id ?: ""}"

    val SUB_ROUTES = setOf(BANS, LOGS, AUDIT, PLUGINS, REMOTE, VOICE, FILES, REPORTS, SERVERS, ABOUT, SERVER_EDIT)

    val SUB_TITLES = mapOf(
        BANS to "封禁管理",
        LOGS to "服务器日志",
        AUDIT to "控制审计",
        PLUGINS to "插件管理",
        REMOTE to "远程控制",
        VOICE to "语音监听",
        FILES to "文件管理",
        REPORTS to "举报管理",
        SERVERS to "服务器管理",
        ABOUT to "关于",
        SERVER_EDIT to "编辑服务器",
    )
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector, val iconSelected: ImageVector)

private val TABS = listOf(
    TabItem(Routes.DASHBOARD, "概览", Icons.Outlined.SpaceDashboard, Icons.Filled.SpaceDashboard),
    TabItem(Routes.PLAYERS, "玩家", Icons.Outlined.Groups, Icons.Filled.Groups),
    TabItem(Routes.CONSOLE, "控制台", Icons.Outlined.Terminal, Icons.Filled.Terminal),
    TabItem(Routes.MAPS, "地图", Icons.Outlined.Map, Icons.Filled.Map),
    TabItem(Routes.EVENTS, "动态", Icons.Outlined.Bolt, Icons.Filled.Bolt),
    TabItem(Routes.MORE, "更多", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz),
)

/** 读取当前活动服务器配置(无激活时回退到第一个)。 */
@Composable
fun rememberActiveServer(): ServerConfig? {
    val servers by ServiceLocator.serverStore.serversFlow.collectAsState(initial = emptyList())
    val activeId by ServiceLocator.serverStore.activeIdFlow.collectAsState(initial = null)
    return remember(servers, activeId) { servers.find { it.id == activeId } ?: servers.firstOrNull() }
}

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val store = ServiceLocator.serverStore
    val scope = rememberCoroutineScope()

    val serversLoaded by store.serversFlow.collectAsState(initial = null)
    val activeId by store.activeIdFlow.collectAsState(initial = null)
    val servers = serversLoaded ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
    val activeServer = remember(servers, activeId) { servers.find { it.id == activeId } ?: servers.firstOrNull() }
    val monitorState by MonitorEngine.state.collectAsState()

    // 服务器配置或活动服务器变化时重启监控轮询
    LaunchedEffect(activeServer) {
        MonitorEngine.setActive(activeServer)
    }
    // 已删除的服务器:关闭其 WS 客户端
    LaunchedEffect(servers) {
        ControlRepository.retainOnly(servers.map { it.id }.toSet())
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isSubRoute = currentRoute in Routes.SUB_ROUTES

    CompositionLocalProvider(LocalSnackbarHost provides snackbarHostState) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (isSubRoute) {
                    SubRouteTopBar(currentRoute) { navController.popBackStack() }
                } else {
                    ServerTopBar(
                        active = activeServer,
                        servers = servers,
                        monitorState = monitorState,
                        onSelect = { id -> scope.launch { store.setActive(id) } },
                        onManage = { navController.navigate(Routes.SERVERS) },
                        onAdd = { navController.navigate(Routes.serverEdit(null)) },
                        onRefresh = { MonitorEngine.refreshNow() },
                    )
                }
            },
            bottomBar = {
                if (!isSubRoute) {
                    SlBottomNav(currentRoute) { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.DASHBOARD,
                modifier = Modifier.padding(padding),
            ) {
                composable(Routes.DASHBOARD) {
                    DashboardScreen(
                        onOpenPlayers = { navController.navigate(Routes.PLAYERS) },
                        onOpenControl = { navController.navigate(Routes.CONSOLE) },
                        onAddServer = { navController.navigate(Routes.serverEdit(null)) },
                    )
                }
                composable(Routes.PLAYERS) { PlayersScreen() }
                composable(Routes.CONSOLE) { ConsoleScreen() }
                composable(Routes.REMOTE) { RemoteScreen() }
                composable(Routes.EVENTS) {
                    EventsScreen(onOpenServerEdit = { navController.navigate(Routes.serverEdit(activeServer?.id)) })
                }
                composable(Routes.MORE) {
                    MoreScreen(onNavigate = { navController.navigate(it) })
                }
                composable(Routes.BANS) { BansScreen() }
                composable(Routes.LOGS) { LogsScreen() }
                composable(Routes.AUDIT) { AuditScreen() }
                composable(Routes.PLUGINS) { PluginsScreen() }
                composable(Routes.MAPS) { MapScreen() }
                composable(Routes.VOICE) { VoiceScreen() }
                composable(Routes.FILES) { FilesScreen() }
                composable(Routes.REPORTS) { ReportsScreen() }
                composable(Routes.SERVERS) {
                    ServersScreen(
                        onEdit = { navController.navigate(Routes.serverEdit(it)) },
                        onAdd = { navController.navigate(Routes.serverEdit(null)) },
                    )
                }
                composable(
                    Routes.SERVER_EDIT,
                    arguments = listOf(navArgument("serverId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }),
                ) { entry ->
                    ServerEditScreen(
                        serverId = entry.arguments?.getString("serverId")?.takeIf { it.isNotBlank() },
                        onDone = { navController.popBackStack() },
                    )
                }
                composable(Routes.ABOUT) { AboutScreen() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubRouteTopBar(currentRoute: String?, onBack: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = { Text(Routes.SUB_TITLES[currentRoute] ?: "") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerTopBar(
    active: ServerConfig?,
    servers: List<ServerConfig>,
    monitorState: MonitorEngine.MonitorState,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = {
            Row(
                Modifier.clickable { menuOpen = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(
                    color = when (monitorState) {
                        is MonitorEngine.MonitorState.Ready ->
                            if (monitorState.data.online) UiColors.Online else UiColors.Offline
                        is MonitorEngine.MonitorState.Loading -> UiColors.Pending
                        is MonitorEngine.MonitorState.Error -> UiColors.Offline
                        MonitorEngine.MonitorState.Idle -> UiColors.Unknown
                    },
                    size = 12.dp,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        active?.displayName ?: "未添加服务器",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        active?.addressText ?: "从菜单添加服务器",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Filled.ArrowDropDown, "切换服务器")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                servers.forEach { server ->
                    DropdownMenuItem(
                        text = { Text(server.displayName) },
                        leadingIcon = {
                            if (server.id == active?.id) {
                                Icon(Icons.Filled.Check, null, Modifier.width(20.dp))
                            }
                        },
                        trailingIcon = { Text(server.addressText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = {
                            onSelect(server.id)
                            menuOpen = false
                        },
                    )
                }
                if (servers.isNotEmpty()) HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("服务器管理") },
                    leadingIcon = { Icon(Icons.Filled.Dns, null) },
                    onClick = { onManage(); menuOpen = false },
                )
                DropdownMenuItem(
                    text = { Text("添加服务器") },
                    leadingIcon = { Icon(Icons.Filled.Add, null) },
                    onClick = { onAdd(); menuOpen = false },
                )
            }
        },
        actions = {
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "立即刷新") }
        },
    )
}


@Composable
private fun SlBottomNav(currentRoute: String?, onSelect: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TABS.forEach { tab ->
                    val selected = currentRoute == tab.route
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .clickable { onSelect(tab.route) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .width(46.dp)
                                .height(30.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(50),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (selected) tab.iconSelected else tab.icon,
                                tab.label,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}
