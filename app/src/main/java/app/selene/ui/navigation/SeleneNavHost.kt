package app.selene.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.selene.R
import app.selene.ui.analysis.AnalysisScreen
import app.selene.ui.analysis.AnalysisViewModel
import app.selene.ui.calendar.CalendarScreen
import app.selene.ui.calendar.CalendarViewModel
import app.selene.ui.calendar.DayScreen
import app.selene.ui.calendar.DayViewModel
import app.selene.ui.components.BottomTab
import app.selene.ui.components.SeleneBottomBar
import app.selene.ui.components.SeleneIcons
import app.selene.ui.cycle.CycleScreen
import app.selene.ui.cycle.CycleViewModel
import app.selene.ui.entry.EntryKind
import app.selene.ui.entry.EntryScreen
import app.selene.ui.entry.EntryViewModel
import app.selene.ui.seleneViewModel
import app.selene.ui.settings.SettingsScreen
import app.selene.ui.settings.SettingsViewModel
import app.selene.ui.theme.SeleneColors
import java.time.LocalDate
import kotlin.reflect.KClass

private val tabRoutes: List<KClass<*>> = listOf(CycleRoute::class, CalendarRoute::class, AnalysisRoute::class, SettingsRoute::class)

private fun NavDestination?.isTab() = this != null && tabRoutes.any { hasRoute(it) }

@Composable
fun SeleneNavHost(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    fun isOn(route: KClass<*>) = destination?.hasRoute(route) == true
    fun goTab(route: Any) = navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    fun openEntry(route: EntryRoute) = navController.navigate(route) { launchSingleTop = true }
    fun openDay(date: LocalDate) = navController.navigate(DayRoute(date.toString())) { launchSingleTop = true }

    val tabs = listOf(
        BottomTab(stringResource(R.string.tab_cycle), SeleneIcons.Cycle, isOn(CycleRoute::class)) { goTab(CycleRoute) },
        BottomTab(stringResource(R.string.tab_calendar), SeleneIcons.Calendar, isOn(CalendarRoute::class)) { goTab(CalendarRoute) },
        BottomTab(stringResource(R.string.tab_analysis), SeleneIcons.Analysis, isOn(AnalysisRoute::class)) { goTab(AnalysisRoute) },
        BottomTab(stringResource(R.string.tab_settings), SeleneIcons.Settings, isOn(SettingsRoute::class)) { goTab(SettingsRoute) },
    )
    Scaffold(
        containerColor = SeleneColors.Background,
        bottomBar = {
            if (destination.isTab()) {
                SeleneBottomBar(tabs = tabs, addLabel = stringResource(R.string.action_add), onAdd = { openEntry(EntryRoute()) })
            }
        },
    ) { padding ->
        // Tabs get the bottom-bar padding; full-screen routes handle their own insets.
        NavHost(
            navController,
            startDestination = CycleRoute,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(180)) },
        ) {
            composable<CycleRoute> {
                Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
                    CycleScreen(
                        viewModel = seleneViewModel { c, _ -> CycleViewModel(c.prediction, c.today) },
                        onAddPeriod = { openEntry(EntryRoute(kind = EntryKind.PERIOD.name)) },
                    )
                }
            }
            composable<CalendarRoute> {
                Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
                    CalendarScreen(
                        viewModel = seleneViewModel { c, _ -> CalendarViewModel(c.logRepository, c.prediction, c.today) },
                        onDayClick = ::openDay,
                    )
                }
            }
            composable<AnalysisRoute> {
                Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
                    AnalysisScreen(viewModel = seleneViewModel { c, _ -> AnalysisViewModel(c.prediction) })
                }
            }
            composable<SettingsRoute> {
                Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
                    SettingsScreen(viewModel = seleneViewModel { c, _ -> SettingsViewModel(c) })
                }
            }
            composable<DayRoute> { entry ->
                val date = LocalDate.parse(entry.toRoute<DayRoute>().date)
                DayScreen(
                    viewModel = seleneViewModel(key = "day-$date") { c, _ -> DayViewModel(c.logRepository, c.prediction, c.today, date) },
                    onBack = { navController.popBackStack() },
                    onAdd = { kind -> openEntry(EntryRoute(date = date.toString(), kind = kind.name)) },
                    onEdit = { kind, eventId ->
                        openEntry(EntryRoute(date = date.toString(), kind = kind.name, eventId = eventId, edit = true))
                    },
                )
            }
            composable<EntryRoute>(
                enterTransition = { slideInVertically(tween(250)) { it / 3 } + fadeIn(tween(250)) },
                popExitTransition = { slideOutVertically(tween(200)) { it / 3 } + fadeOut(tween(200)) },
            ) { entry ->
                val route = entry.toRoute<EntryRoute>()
                EntryScreen(
                    viewModel = seleneViewModel { c, _ ->
                        EntryViewModel(
                            repository = c.logRepository,
                            today = c.today.value,
                            initialDate = route.date?.let(LocalDate::parse),
                            initialKind = route.kind?.let { runCatching { EntryKind.valueOf(it) }.getOrNull() },
                            eventId = route.eventId,
                            isEdit = route.edit,
                        )
                    },
                    onClose = { navController.popBackStack() },
                )
            }
        }
    }
}
