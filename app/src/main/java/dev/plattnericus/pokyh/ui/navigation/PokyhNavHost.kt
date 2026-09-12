package dev.plattnericus.pokyh.ui.navigation

import dev.plattnericus.pokyh.core.util.SchoolDates
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.plattnericus.pokyh.ui.theme.PokyhMotion

/** The 5 bottom-nav tab roots — a transition between two of these is a tab switch (crossfade),
 * not a stack push (slide), even though tab switching does go through a real `navigate()` call
 * (see [dev.plattnericus.pokyh.ui.PokyhApp]'s `AuthedShell`). */
private val TAB_ROUTES = setOf(
    PokyhDestinations.HOME,
    PokyhDestinations.TIMETABLE,
    PokyhDestinations.SCHOOL_HUB,
    PokyhDestinations.GRADES,
    PokyhDestinations.MENSA,
)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean {
    val from = initialState.destination.route
    val to = targetState.destination.route
    return from in TAB_ROUTES && to in TAB_ROUTES
}

private val NavEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (isTabSwitch()) {
        fadeIn(tween(PokyhMotion.durationStandard))
    } else {
        slideInHorizontally(tween(PokyhMotion.durationStandard)) { it / 4 } + fadeIn(tween(PokyhMotion.durationStandard))
    }
}
private val NavExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (isTabSwitch()) {
        fadeOut(tween(PokyhMotion.durationStandard))
    } else {
        slideOutHorizontally(tween(PokyhMotion.durationStandard)) { -it / 4 } + fadeOut(tween(PokyhMotion.durationFast))
    }
}
private val NavPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (isTabSwitch()) {
        fadeIn(tween(PokyhMotion.durationStandard))
    } else {
        slideInHorizontally(tween(PokyhMotion.durationStandard)) { -it / 4 } + fadeIn(tween(PokyhMotion.durationStandard))
    }
}
private val NavPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (isTabSwitch()) {
        fadeOut(tween(PokyhMotion.durationStandard))
    } else {
        slideOutHorizontally(tween(PokyhMotion.durationStandard)) { it / 4 } + fadeOut(tween(PokyhMotion.durationFast))
    }
}

/** Registers every route from [PokyhDestinations] to its real screen composable. Tab-root <->
 * tab-root switches crossfade; every other push/pop slides+fades — see [PokyhMotion]. */
@Composable
fun PokyhNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = NavEnter,
        exitTransition = NavExit,
        popEnterTransition = NavPopEnter,
        popExitTransition = NavPopExit,
    ) {
        composable(PokyhDestinations.HOME) {
            dev.plattnericus.pokyh.ui.home.HomeScreen(
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(PokyhDestinations.TIMETABLE) {
            dev.plattnericus.pokyh.ui.timetable.TimetableScreen(
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(PokyhDestinations.SCHOOL_HUB) {
            dev.plattnericus.pokyh.ui.school.SchoolHubScreen(
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(PokyhDestinations.GRADES) {
            dev.plattnericus.pokyh.ui.grades.GradesScreen(
                onSubjectClick = { lessonId, year -> navController.navigate(PokyhDestinations.gradeSubject(lessonId, year)) },
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(
            route = PokyhDestinations.GRADE_SUBJECT,
            arguments = listOf(
                navArgument("subjectId") { type = NavType.IntType },
                navArgument("year") {
                    type = NavType.IntType
                    defaultValue = SchoolDates.currentSchoolYear
                },
            ),
        ) {
            dev.plattnericus.pokyh.ui.grades.GradeSubjectScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(PokyhDestinations.MENSA) {
            dev.plattnericus.pokyh.ui.mensa.MensaScreen(
                onDishClick = { dishId -> navController.navigate(PokyhDestinations.dishDetail(dishId)) },
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(
            route = PokyhDestinations.MENSA_DISH,
            arguments = listOf(navArgument("dishId") { type = NavType.StringType }),
        ) {
            dev.plattnericus.pokyh.ui.mensa.DishDetailScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(PokyhDestinations.TODOS) {
            dev.plattnericus.pokyh.ui.todos.TodosScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(PokyhDestinations.REMINDERS) {
            dev.plattnericus.pokyh.ui.reminders.RemindersScreen(
                onReminderClick = { id -> navController.navigate(PokyhDestinations.reminderDetail(id)) },
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(
            route = PokyhDestinations.REMINDER_DETAIL,
            arguments = listOf(navArgument("reminderId") { type = NavType.StringType }),
        ) {
            dev.plattnericus.pokyh.ui.reminders.ReminderDetailScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(PokyhDestinations.ABSENCES) {
            dev.plattnericus.pokyh.ui.absences.AbsencesScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(PokyhDestinations.MESSAGES) {
            dev.plattnericus.pokyh.ui.messages.MessagesScreen(
                onMessageClick = { id -> navController.navigate(PokyhDestinations.messageDetail(id)) },
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(
            route = PokyhDestinations.MESSAGE_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.IntType }),
        ) { entry ->
            val id = entry.arguments?.getInt("id") ?: 0
            dev.plattnericus.pokyh.ui.messages.MessageDetailScreen(
                id = id,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(PokyhDestinations.CLASSREG_EVENTS) {
            dev.plattnericus.pokyh.ui.classreg.ClassregEventsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(PokyhDestinations.CLASSROOM) {
            dev.plattnericus.pokyh.ui.classroom.ClassScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(PokyhDestinations.PROFILE) {
            dev.plattnericus.pokyh.ui.profile.ProfileScreen(
                onNavigateUp = { navController.popBackStack() },
                onConnectionStatus = { navController.navigate(PokyhDestinations.CONNECTION_STATUS) },
            )
        }
        composable(PokyhDestinations.CONNECTION_STATUS) {
            dev.plattnericus.pokyh.ui.profile.ConnectionStatusScreen(
                onNavigateUp = { navController.popBackStack() },
            )
        }
        composable(PokyhDestinations.LOGIN) {
            // The primary (non-additional) Login/Lock phases are rendered directly by PokyhApp's
            // `AppState.phase` switch, not pushed here (ContentView.swift's phase switch isn't a
            // NavigationStack push). This route entry is what a pushed "Konto hinzufügen" flow
            // (e.g. from ProfileScreen) lands on, so it's always the `isAdditional = true` sheet.
            dev.plattnericus.pokyh.ui.login.LoginScreen(
                isAdditional = true,
                onCancel = { navController.popBackStack() },
            )
        }
        composable(PokyhDestinations.LOCK) {
            dev.plattnericus.pokyh.ui.lock.LockScreen()
        }
    }
}
