package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.browser.ProfileWebViewManager
import com.example.ui.MainViewModel
import com.example.ui.navigation.AutomationRoute
import com.example.ui.navigation.BrowserRoute
import com.example.ui.navigation.MassRunnerRoute
import com.example.ui.navigation.ProfileEditorRoute
import com.example.ui.navigation.ProfilesRoute
import com.example.ui.navigation.ProxiesRoute
import com.example.ui.navigation.ProxyEditorRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.StealthLabRoute
import com.example.ui.screens.AutomationScreen
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.MassRunnerScreen
import com.example.ui.screens.ProfileEditorScreen
import com.example.ui.screens.ProfilesScreen
import com.example.ui.screens.ProxiesScreen
import com.example.ui.screens.ProxyEditorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StealthLabScreen
import com.example.ui.theme.CloakDeckTheme
import com.example.ui.theme.CyberCyan
import kotlin.reflect.KClass

private data class TopLevelTab(
    val label: String,
    val route: Any,
    val routeClass: KClass<*>,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topLevelTabs = listOf(
    TopLevelTab(
        label = "Profiles",
        route = ProfilesRoute,
        routeClass = ProfilesRoute::class,
        selectedIcon = Icons.Rounded.Layers,
        unselectedIcon = Icons.Outlined.Layers,
        testTag = "nav_profiles"
    ),
    TopLevelTab(
        label = "Proxies",
        route = ProxiesRoute,
        routeClass = ProxiesRoute::class,
        selectedIcon = Icons.Rounded.Public,
        unselectedIcon = Icons.Outlined.Public,
        testTag = "nav_proxies"
    ),
    TopLevelTab(
        label = "RPA",
        route = AutomationRoute,
        routeClass = AutomationRoute::class,
        selectedIcon = Icons.Rounded.SmartToy,
        unselectedIcon = Icons.Outlined.SmartToy,
        testTag = "nav_rpa"
    ),
    TopLevelTab(
        label = "Lab",
        route = StealthLabRoute,
        routeClass = StealthLabRoute::class,
        selectedIcon = Icons.Rounded.Fingerprint,
        unselectedIcon = Icons.Outlined.Fingerprint,
        testTag = "nav_lab"
    ),
    TopLevelTab(
        label = "Settings",
        route = SettingsRoute,
        routeClass = SettingsRoute::class,
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ProfileWebViewManager.warmUpChromium(applicationContext)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            CloakDeckTheme(darkTheme = settings.darkMode) {
                CloakDeckAppShell(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun CloakDeckAppShell(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val isTopLevel = currentDest != null && topLevelTabs.any { currentDest.hasRoute(it.routeClass) }

    fun navigateToTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp && isTopLevel

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = if (isTopLevel) WindowInsets.navigationBars else WindowInsets(0, 0, 0, 0),
            topBar = {
                if (isTopLevel) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = null,
                                        tint = CyberCyan,
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .size(18.dp)
                                    )
                                }
                                Text(
                                    text = "CloakDeck",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "ANTI-DETECT BROWSER",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            bottomBar = {
                if (isTopLevel && !useRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topLevelTabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.routeClass) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(modifier = Modifier.weight(0.1f))
                        topLevelTabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.routeClass) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = ProfilesRoute
                    ) {
                        composable<ProfilesRoute> {
                            ProfilesScreen(
                                viewModel = viewModel,
                                onOpenBrowser = { profId, url ->
                                    navController.navigate(BrowserRoute(profId, url))
                                },
                                onEditProfile = { profId ->
                                    navController.navigate(ProfileEditorRoute(profId))
                                },
                                onGoToProxies = { navigateToTab(ProxiesRoute) },
                                onOpenMassRunner = { navController.navigate(MassRunnerRoute) }
                            )
                        }
                        composable<ProxiesRoute> {
                            ProxiesScreen(
                                viewModel = viewModel,
                                onOpenProxyEditor = { proxyId ->
                                    navController.navigate(ProxyEditorRoute(proxyId))
                                },
                                onLaunchProxyProfile = { profId, isBatch ->
                                    navController.navigate(BrowserRoute(profId, null, null, isBatch))
                                },
                                onOpenMassRunner = { navController.navigate(MassRunnerRoute) }
                            )
                        }
                        composable<AutomationRoute> {
                            AutomationScreen(
                                viewModel = viewModel,
                                onRunFlowInProfile = { profId, flowId ->
                                    navController.navigate(BrowserRoute(profId, null, flowId))
                                },
                                onOpenMassRunner = { navController.navigate(MassRunnerRoute) }
                            )
                        }
                        composable<StealthLabRoute> {
                            StealthLabScreen(
                                viewModel = viewModel,
                                onLaunchProfile = { profId, url ->
                                    navController.navigate(BrowserRoute(profId, url))
                                }
                            )
                        }
                        composable<SettingsRoute> {
                            SettingsScreen(viewModel = viewModel)
                        }
                        composable<MassRunnerRoute> {
                            MassRunnerScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onGoToCaptchaSettings = {
                                    navController.popBackStack()
                                    navigateToTab(SettingsRoute)
                                }
                            )
                        }
                        composable<ProfileEditorRoute> { entry ->
                            val args = entry.toRoute<ProfileEditorRoute>()
                            ProfileEditorScreen(
                                profileId = args.profileId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onSaved = { navController.popBackStack() }
                            )
                        }
                        composable<ProxyEditorRoute> { entry ->
                            val args = entry.toRoute<ProxyEditorRoute>()
                            ProxyEditorScreen(
                                proxyId = args.proxyId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onSaved = { navController.popBackStack() },
                                onSaveAndLaunch = { profId ->
                                    navController.popBackStack()
                                    navController.navigate(BrowserRoute(profId))
                                }
                            )
                        }
                        composable<BrowserRoute> { entry ->
                            val args = entry.toRoute<BrowserRoute>()
                            BrowserScreen(
                                profileId = args.profileId,
                                initialUrl = args.initialUrl,
                                initialFlowId = args.initialFlowId,
                                batchMode = args.batchMode,
                                viewModel = viewModel,
                                onCloseBrowser = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
