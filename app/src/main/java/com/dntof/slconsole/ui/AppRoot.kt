package com.dntof.slconsole.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Map
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dntof.slconsole.ServiceLocator
import com.dntof.slconsole.data.local.GlassGuard
import com.dntof.slconsole.data.model.ServerConfig
import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.data.repo.MonitorEngine
import com.dntof.slconsole.ui.components.AppBackdrop
import com.dntof.slconsole.ui.components.AppLayout
import com.dntof.slconsole.ui.components.AppSurface
import com.dntof.slconsole.ui.components.GlassRole
import com.dntof.slconsole.ui.components.LiveBlurClock
import com.dntof.slconsole.ui.components.LiveBlurState
import com.dntof.slconsole.ui.components.LocalAppLayout
import com.dntof.slconsole.ui.components.LocalGlassPlate
import com.dntof.slconsole.ui.components.LocalLiquidGlass
import com.dntof.slconsole.ui.components.LocalLiveBlur
import com.dntof.slconsole.ui.components.StatusDot
import com.dntof.slconsole.ui.components.captureBackdrop
import com.dntof.slconsole.ui.components.UiColors
import com.dntof.slconsole.ui.components.liquidGlass
import com.dntof.slconsole.ui.components.rememberGlassPlateState
import com.dntof.slconsole.ui.screens.AdaptedPluginDetailScreen
import com.dntof.slconsole.ui.screens.AdaptedPluginsScreen
import com.dntof.slconsole.ui.screens.AboutScreen
import com.dntof.slconsole.ui.screens.AuditScreen
import com.dntof.slconsole.ui.screens.BansScreen
import com.dntof.slconsole.ui.screens.ConsoleScreen
import com.dntof.slconsole.ui.screens.DashboardScreen
import com.dntof.slconsole.ui.screens.EventsScreen
import com.dntof.slconsole.ui.screens.FilesScreen
import com.dntof.slconsole.ui.screens.LogsScreen
import com.dntof.slconsole.ui.screens.MapScreen
import com.dntof.slconsole.ui.screens.MoreScreen
import com.dntof.slconsole.ui.screens.PlayersScreen
import com.dntof.slconsole.ui.screens.PluginsScreen
import com.dntof.slconsole.ui.screens.RemoteScreen
import com.dntof.slconsole.ui.screens.ReportsScreen
import com.dntof.slconsole.ui.screens.ServerEditScreen
import com.dntof.slconsole.ui.screens.ServersScreen
import com.dntof.slconsole.ui.screens.SettingsScreen
import com.dntof.slconsole.ui.screens.VoiceScreen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

val LocalSnackbarHost = staticCompositionLocalOf<SnackbarHostState> {
    error("SnackbarHostState not provided")
}

/** 底栏/系统导航区高度。列表用它加 contentPadding,视口本身仍延伸到栏下。 */
val LocalBottomChrome = staticCompositionLocalOf { 0.dp }

@Composable
fun PaddingValues.withBottomChrome(): PaddingValues {
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding() + LocalBottomChrome.current,
    )
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
    const val ADAPTED = "adapted"
    const val ADAPTED_DETAIL = "adaptedDetail/{pluginId}"

    fun adaptedDetail(id: String): String = "adaptedDetail/${android.net.Uri.encode(id)}"
    const val MAPS = "maps"
    const val VOICE = "voice"
    const val FILES = "files"
    const val REPORTS = "reports"
    const val SERVERS = "servers"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val SERVER_EDIT = "serverEdit?serverId={serverId}"

    fun serverEdit(id: String?): String = "serverEdit?serverId=${id ?: ""}"

    val SUB_ROUTES = setOf(
        BANS, LOGS, AUDIT, PLUGINS, ADAPTED, ADAPTED_DETAIL, REMOTE, EVENTS, VOICE, FILES, REPORTS, SERVERS, SETTINGS, ABOUT, SERVER_EDIT,
    )

    val SUB_TITLES = mapOf(
        BANS to "封禁管理",
        LOGS to "服务器日志",
        AUDIT to "控制审计",
        PLUGINS to "插件管理",
        ADAPTED to "适配插件",
        ADAPTED_DETAIL to "插件详情",
        REMOTE to "远程控制",
        EVENTS to "实时动态",
        VOICE to "语音监听",
        FILES to "文件管理",
        REPORTS to "举报管理",
        SERVERS to "服务器管理",
        SETTINGS to "外观",
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
    TabItem(Routes.MORE, "中心", Icons.Outlined.Apps, Icons.Filled.Apps),
)

/** 读取当前活动服务器配置(无激活时回退到第一个)。 */
@Composable
fun rememberActiveServer(): ServerConfig? {
    val servers by ServiceLocator.serverStore.serversFlow.collectAsState(initial = emptyList())
    val activeId by ServiceLocator.serverStore.activeIdFlow.collectAsState(initial = null)
    return remember(servers, activeId) { servers.find { it.id == activeId } ?: servers.firstOrNull() }
}

@Composable
fun AppRoot(startupNotice: String? = null) {
    val navController = rememberNavController()
    val store = ServiceLocator.serverStore
    val scope = rememberCoroutineScope()

    val serversLoaded by store.serversFlow.collectAsState(initial = null)
    val activeId by store.activeIdFlow.collectAsState(initial = null)
    val glassPref by ServiceLocator.settingsStore.liquidGlassFlow.collectAsState(initial = null as Boolean?)
    val servers = serversLoaded
    if (servers == null || glassPref == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val glassEnabled = glassPref == true
    val activeServer = remember(servers, activeId) { servers.find { it.id == activeId } ?: servers.firstOrNull() }
    val monitorState by MonitorEngine.state.collectAsState()
    val layout = when {
        LocalConfiguration.current.screenWidthDp >= 1000 -> AppLayout.Expanded
        LocalConfiguration.current.screenWidthDp >= 600 -> AppLayout.Medium
        else -> AppLayout.Compact
    }
    val plate = rememberGlassPlateState()
    val liveBlur = remember { LiveBlurState() }
    val context = LocalContext.current
    // 必须在第一帧绘制前把「绘制未完成」写进磁盘。进程如果死在这一帧,下次启动会关掉玻璃。
    SideEffect {
        if (glassEnabled && liveBlur.safeFrames < 2 && !liveBlur.failed) {
            GlassGuard.markRenderStart(context)
        }
    }

    LaunchedEffect(activeServer) {
        MonitorEngine.setActive(activeServer)
    }
    LaunchedEffect(servers) {
        ControlRepository.retainOnly(servers.map { it.id }.toSet())
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(startupNotice) {
        if (!startupNotice.isNullOrBlank()) snackbarHostState.showSnackbar(startupNotice)
    }
    LaunchedEffect(glassEnabled) {
        if (!glassEnabled) {
            liveBlur.safeFrames = 0
            liveBlur.failed = false
            GlassGuard.markRenderOk(context)
            return@LaunchedEffect
        }
        while (isActive) {
            withFrameNanos { }
            if (liveBlur.failed) break
            if (liveBlur.safeFrames >= 2) {
                GlassGuard.markRenderOk(context)
                break
            }
        }
    }
    SideEffect {
        liveBlur.onFailure = {
            scope.launch {
                GlassGuard.markRenderOk(context)
                ServiceLocator.settingsStore.setLiquidGlass(false)
                snackbarHostState.showSnackbar(GlassGuard.FAILURE_NOTICE)
            }
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isSubRoute = currentRoute in Routes.SUB_ROUTES
    val selectedTab = when {
        TABS.any { it.route == currentRoute } -> currentRoute
        isSubRoute -> Routes.MORE
        else -> currentRoute
    }

    CompositionLocalProvider(
        LocalSnackbarHost provides snackbarHostState,
        LocalLiquidGlass provides glassEnabled,
        LocalGlassPlate provides plate,
        LocalLiveBlur provides liveBlur,
        LocalAppLayout provides layout,
    ) {
        LiveBlurClock(liveBlur, glassEnabled)
        Box(Modifier.fillMaxSize()) {
            if (layout == AppLayout.Compact) {
                CompactShell(
                    navController = navController,
                    currentRoute = currentRoute,
                    isSubRoute = isSubRoute,
                    selectedTab = selectedTab,
                    activeServer = activeServer,
                    servers = servers,
                    monitorState = monitorState,
                    snackbarHostState = snackbarHostState,
                    onSelectServer = { id -> scope.launch { store.setActive(id) } },
                )
            } else {
                WideShell(
                    navController = navController,
                    currentRoute = currentRoute,
                    isSubRoute = isSubRoute,
                    selectedTab = selectedTab,
                    expanded = layout == AppLayout.Expanded,
                    activeServer = activeServer,
                    servers = servers,
                    monitorState = monitorState,
                    snackbarHostState = snackbarHostState,
                    onSelectServer = { id -> scope.launch { store.setActive(id) } },
                )
            }
        }
    }
}

@Composable
private fun CompactShell(
    navController: NavHostController,
    currentRoute: String?,
    isSubRoute: Boolean,
    selectedTab: String?,
    activeServer: ServerConfig?,
    servers: List<ServerConfig>,
    monitorState: MonitorEngine.MonitorState,
    snackbarHostState: SnackbarHostState,
    onSelectServer: (String) -> Unit,
) {
    val density = LocalDensity.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var topBarHeight by remember { mutableStateOf(64.dp) }
    var bottomOverlay by remember { mutableStateOf(0.dp) }
    val showTabs = !isSubRoute
    val bottomChrome = if (showTabs) bottomOverlay else navInset
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().captureBackdrop()) {
            AppBackdrop(LocalLiquidGlass.current)
            CompositionLocalProvider(LocalBottomChrome provides bottomChrome) {
                AppNavHost(
                    navController = navController,
                    activeServer = activeServer,
                    modifier = Modifier.padding(top = topBarHeight).fillMaxSize(),
                )
            }
        }
        Box(Modifier.align(Alignment.TopCenter).onSizeChanged {
            topBarHeight = with(density) { it.height.toDp() }
        }) {
            if (isSubRoute) {
                BarSurface { SubRouteTopBar(currentRoute, activeServer) { navController.popBackStack() } }
            } else {
                BarSurface {
                    ServerTopBar(
                        active = activeServer,
                        servers = servers,
                        monitorState = monitorState,
                        onSelect = onSelectServer,
                        onManage = { navController.navigate(Routes.SERVERS) },
                        onAdd = { navController.navigate(Routes.serverEdit(null)) },
                        onRefresh = { MonitorEngine.refreshNow() },
                    )
                }
            }
        }
        if (showTabs) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { bottomOverlay = with(density) { it.height.toDp() } },
            ) {
                SlBottomNav(selectedTab) { navController.navigateTab(it) }
            }
        }
        SnackbarHost(
            snackbarHostState,
            Modifier.align(Alignment.BottomCenter).padding(bottom = bottomChrome + 8.dp),
        )
    }
}

@Composable
private fun WideShell(
    navController: NavHostController,
    currentRoute: String?,
    isSubRoute: Boolean,
    selectedTab: String?,
    expanded: Boolean,
    activeServer: ServerConfig?,
    servers: List<ServerConfig>,
    monitorState: MonitorEngine.MonitorState,
    snackbarHostState: SnackbarHostState,
    onSelectServer: (String) -> Unit,
) {
    val density = LocalDensity.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val railWidth = if (expanded) 232.dp else 104.dp
    var topBarHeight by remember { mutableStateOf(64.dp) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().captureBackdrop()) {
            AppBackdrop(LocalLiquidGlass.current)
            CompositionLocalProvider(LocalBottomChrome provides navInset) {
                Column(
                    Modifier
                        .padding(start = railWidth, top = topBarHeight)
                        .fillMaxSize(),
                ) {
                    if (isSubRoute) {
                        SubRouteHeader(currentRoute) { navController.popBackStack() }
                    }
                    AppNavHost(
                        navController = navController,
                        activeServer = activeServer,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(start = railWidth)
                .fillMaxWidth()
                .onSizeChanged { topBarHeight = with(density) { it.height.toDp() } },
        ) {
            BarSurface {
                ServerTopBar(
                    active = activeServer,
                    servers = servers,
                    monitorState = monitorState,
                    onSelect = onSelectServer,
                    onManage = { navController.navigate(Routes.SERVERS) },
                    onAdd = { navController.navigate(Routes.serverEdit(null)) },
                    onRefresh = { MonitorEngine.refreshNow() },
                )
            }
        }
        Box(Modifier.align(Alignment.CenterStart).width(railWidth).fillMaxHeight()) {
            SlNavigationRail(
                selectedTab = selectedTab,
                expanded = expanded,
                onSelect = { navController.navigateTab(it) },
            )
        }
        SnackbarHost(
            snackbarHostState,
            Modifier.align(Alignment.BottomCenter).padding(start = railWidth, bottom = navInset + 8.dp),
        )
    }
}

@Composable
private fun BarSurface(content: @Composable () -> Unit) {
    val glass = LocalLiquidGlass.current
    Column(
        Modifier.liquidGlass(
            shape = RectangleShape,
            role = GlassRole.Chrome,
            dark = isSystemInDarkTheme(),
            framed = false,
        ),
    ) {
        content()
        HorizontalDivider(
            color = if (glass) Color.White.copy(alpha = 0.28f) else MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    activeServer: ServerConfig?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.widthIn(max = 1100.dp).fillMaxHeight(),
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenPlayers = { navController.navigateTab(Routes.PLAYERS) },
                    onOpenControl = { navController.navigateTab(Routes.CONSOLE) },
                    onAddServer = { navController.navigate(Routes.serverEdit(null)) },
                    onNavigate = { route ->
                        val tab = route == Routes.DASHBOARD || route == Routes.PLAYERS ||
                            route == Routes.CONSOLE || route == Routes.MAPS || route == Routes.MORE
                        if (tab) navController.navigateTab(route) else navController.navigate(route)
                    },
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
            composable(Routes.PLUGINS) {
                PluginsScreen(onOpenAdapted = { navController.navigate(Routes.ADAPTED) })
            }
            composable(Routes.ADAPTED) {
                AdaptedPluginsScreen(onOpen = { navController.navigate(Routes.adaptedDetail(it)) })
            }
            composable(
                Routes.ADAPTED_DETAIL,
                arguments = listOf(navArgument("pluginId") { type = NavType.StringType }),
            ) { entry ->
                AdaptedPluginDetailScreen(
                    pluginId = android.net.Uri.decode(entry.arguments?.getString("pluginId").orEmpty()),
                )
            }
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
            composable(Routes.SETTINGS) { SettingsScreen() }
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

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubRouteTopBar(currentRoute: String?, active: ServerConfig?, onBack: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = {
            Column {
                Text(Routes.SUB_TITLES[currentRoute] ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    active?.displayName ?: "未添加服务器",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
        },
    )
}

@Composable
private fun SubRouteHeader(currentRoute: String?, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
        }
        Text(
            Routes.SUB_TITLES[currentRoute] ?: "",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
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
                Column(Modifier.weight(1f, fill = false)) {
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
                        trailingIcon = {
                            Text(
                                server.addressText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
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
private fun SlBottomNav(selectedTab: String?, onSelect: (String) -> Unit) {
    // 外层保持透明,系统导航区露出背后正在滚动的内容,而不是一条实色带
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        AppSurface(
            modifier = Modifier.fillMaxWidth(),
            role = GlassRole.Chrome,
            shape = RoundedCornerShape(32.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 4.dp, vertical = 6.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TABS.forEach { tab ->
                    NavTab(
                        tab = tab,
                        selected = selectedTab == tab.route,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(tab.route) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SlNavigationRail(
    selectedTab: String?,
    expanded: Boolean,
    onSelect: (String) -> Unit,
) {
    val width = if (expanded) 232.dp else 104.dp
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(10.dp),
    ) {
        AppSurface(
            modifier = Modifier.fillMaxSize(),
            role = GlassRole.Chrome,
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                Modifier.fillMaxSize().padding(vertical = 12.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TABS.forEach { tab ->
                    RailTab(
                        tab = tab,
                        selected = selectedTab == tab.route,
                        expanded = expanded,
                        onClick = { onSelect(tab.route) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavTab(
    tab: TabItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val pill by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "tabPill",
    )
    val scale by animateFloatAsState(
        if (selected) 1f else 0.94f,
        spring(dampingRatio = 0.75f),
        label = "tabScale",
    )
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(40.dp)
                .height(28.dp)
                .background(pill, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (selected) tab.iconSelected else tab.icon,
                tab.label,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            tab.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun RailTab(
    tab: TabItem,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
    if (expanded) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (selected) tab.iconSelected else tab.icon,
                tab.label,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                tab.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                if (selected) tab.iconSelected else tab.icon,
                tab.label,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                tab.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
