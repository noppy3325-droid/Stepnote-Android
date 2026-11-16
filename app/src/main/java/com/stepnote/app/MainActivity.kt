package com.stepnote.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.health.connect.client.PermissionController
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.stepnote.app.data.health.DeviceCapabilityChecker
import com.stepnote.app.data.preferences.UserPreferences
import com.stepnote.app.domain.model.StepSourceKind
import com.stepnote.app.domain.model.StepUiState
import com.stepnote.app.ui.calendar.CalendarScreen
import com.stepnote.app.ui.calendar.CalendarViewModel
import com.stepnote.app.ui.history.HistoryScreen
import com.stepnote.app.ui.history.HistoryViewModel
import com.stepnote.app.ui.home.HomeScreen
import com.stepnote.app.ui.home.HomeViewModel
import com.stepnote.app.ui.onboarding.OnboardingScreen
import com.stepnote.app.ui.settings.SettingsScreen
import com.stepnote.app.ui.settings.SettingsViewModel
import com.stepnote.app.ui.theme.LocalStepnoteColors
import com.stepnote.app.ui.theme.StepnoteTheme
import com.stepnote.app.widget.StepnoteWidget
import com.stepnote.app.worker.StepSyncWorker
import kotlinx.coroutines.launch

private object Routes {
    const val TODAY = "today"
    const val HISTORY = "history"
    const val CALENDAR = "calendar"
    const val SETTINGS = "settings"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as StepnoteApplication).container

        setContent {
            val loadedPreferences by container.preferencesRepository.preferencesFlow
                .collectAsState(initial = null)
            val prefs: UserPreferences = loadedPreferences ?: return@setContent
            val coroutineScope = rememberCoroutineScope()

            StepnoteTheme(themeMode = prefs.themeMode) {
                val background = LocalStepnoteColors.current.background
                SideEffect {
                    val lightBars = background.luminance() > 0.5f
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightBars
                        isAppearanceLightNavigationBars = lightBars
                    }
                }
                val homeVm = viewModel {
                    HomeViewModel(
                        container.getTodayStepsUseCase,
                        container.preferencesRepository,
                        container.statsUseCase
                    )
                }
                val historyVm = viewModel { HistoryViewModel(container.getStepHistoryUseCase) }
                val calendarVm = viewModel {
                    CalendarViewModel(
                        container.getStepHistoryUseCase,
                        container.preferencesRepository,
                        container.statsUseCase
                    )
                }
                val settingsVm = viewModel {
                    SettingsViewModel(container.preferencesRepository, container.stepRepository)
                }

                val hcPermissionLauncher = rememberLauncherForActivityResult(
                    contract = PermissionController.createRequestPermissionResultContract()
                ) {
                    homeVm.refresh()
                    settingsVm.refreshDiagnostics()
                    StepSyncWorker.enqueueImmediate(this@MainActivity)
                }

                val activityPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {
                    homeVm.refresh()
                    settingsVm.refreshDiagnostics()
                }

                val requestPermissions: (StepSourceKind) -> Unit = { kind ->
                    when (kind) {
                        StepSourceKind.HEALTH_CONNECT -> {
                            hcPermissionLauncher.launch(setOf(DeviceCapabilityChecker.READ_STEPS_PERMISSION))
                        }
                        StepSourceKind.LOCAL_RECORDING -> {
                            activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        }
                        StepSourceKind.NONE -> Unit
                    }
                }

                // Refresh when returning to foreground so permissions revoked in OS settings never crash
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            homeVm.refresh()
                            settingsVm.refreshDiagnostics()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(prefs.copy(lastSyncAt = null)) {
                    homeVm.refresh()
                    settingsVm.refreshDiagnostics()
                    StepnoteWidget().updateAll(this@MainActivity)
                }
                val homeState by homeVm.uiState.collectAsState()
                LaunchedEffect(homeState) {
                    if (homeState != StepUiState.Loading) {
                        StepnoteWidget().updateAll(this@MainActivity)
                    }
                }

                if (!prefs.onboardingCompleted) {
                    OnboardingScreen(
                        initialGoal = prefs.dailyGoal,
                        initialTheme = prefs.themeMode,
                        onRequestHealthPermission = {
                            coroutineScope.launch {
                                val source = container.stepRepository.resolveActiveSource(prefs)
                                if (source == null) {
                                    android.widget.Toast.makeText(
                                        this@MainActivity, "対応する記録方式が見つかりません。", android.widget.Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    requestPermissions(source.kind)
                                }
                            }
                        },
                        onComplete = { goal, theme ->
                            coroutineScope.launch {
                                container.preferencesRepository.setDailyGoal(goal)
                                container.preferencesRepository.setThemeMode(theme)
                                container.preferencesRepository.setOnboardingCompleted(true)
                                homeVm.refresh()
                            }
                        },
                        modifier = Modifier.background(background).safeDrawingPadding()
                    )
                } else {
                    StepnoteMainScaffold(
                        homeVm = homeVm,
                        historyVm = historyVm,
                        calendarVm = calendarVm,
                        settingsVm = settingsVm,
                        onRequestPermissions = requestPermissions,
                        onRequestBackground = {
                            hcPermissionLauncher.launch(
                                setOf(DeviceCapabilityChecker.READ_BACKGROUND_PERMISSION)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StepnoteMainScaffold(
    homeVm: HomeViewModel,
    historyVm: HistoryViewModel,
    calendarVm: CalendarViewModel,
    settingsVm: SettingsViewModel,
    onRequestPermissions: (StepSourceKind) -> Unit,
    onRequestBackground: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val colors = LocalStepnoteColors.current

    val showBottomBar = currentRoute in setOf(Routes.TODAY, Routes.HISTORY, Routes.CALENDAR)

    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = colors.card) {
                    NavigationBarItem(
                        selected = currentRoute == Routes.TODAY,
                        onClick = {
                            navController.navigate(Routes.TODAY) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, contentDescription = null) },
                        label = { Text("今日") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.HISTORY,
                        onClick = {
                            navController.navigate(Routes.HISTORY) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Outlined.BarChart, contentDescription = null) },
                        label = { Text("履歴") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.CALENDAR,
                        onClick = {
                            navController.navigate(Routes.CALENDAR) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                        label = { Text("カレンダー") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.TODAY) {
                val state by homeVm.uiState.collectAsState()
                HomeScreen(
                    uiState = state,
                    statsUseCase = homeVm.statsUseCase,
                    onRefresh = homeVm::refresh,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onRequestPermissions = onRequestPermissions,
                    onSwitchToRecordingFallback = homeVm::switchToRecordingFallback
                )
            }
            composable(Routes.HISTORY) {
                LaunchedEffect(Unit) { historyVm.load() }
                val state by historyVm.uiState.collectAsState()
                HistoryScreen(
                    uiState = state,
                    onGranularityChange = historyVm::setGranularity,
                    onMovePeriod = historyVm::movePeriod
                )
            }
            composable(Routes.CALENDAR) {
                LaunchedEffect(Unit) { calendarVm.refresh() }
                val state by calendarVm.uiState.collectAsState()
                CalendarScreen(
                    uiState = state,
                    statsUseCase = calendarVm.statsUseCase,
                    onMoveMonth = calendarVm::moveMonth,
                    onSelectDate = calendarVm::selectDate
                )
            }
            composable(Routes.SETTINGS) {
                LaunchedEffect(Unit) { settingsVm.refreshDiagnostics() }
                val prefs by settingsVm.preferences.collectAsState()
                val diag by settingsVm.diagnostic.collectAsState()
                SettingsScreen(
                    preferences = prefs,
                    diagnostic = diag,
                    onBack = { navController.popBackStack() },
                    onUpdateGoal = settingsVm::updateGoal,
                    onUpdateTheme = settingsVm::updateTheme,
                    onUpdateSourceScope = settingsVm::updateSourceScope,
                    onUpdateRecordingSource = settingsVm::updateRecordingSource,
                    onUpdateStrideCm = settingsVm::updateStrideCm,
                    onUpdateCalorieSettings = settingsVm::updateCalorieSettings,
                    onClearLocalData = settingsVm::clearLocalData,
                    onRequestBackground = onRequestBackground
                )
            }
        }
    }
}
