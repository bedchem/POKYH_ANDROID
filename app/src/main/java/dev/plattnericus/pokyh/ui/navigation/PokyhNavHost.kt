package dev.plattnericus.pokyh.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/** Registers every route from [PokyhDestinations] to its real screen composable. */
@Composable
fun PokyhNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable(PokyhDestinations.HOME) {
            dev.plattnericus.pokyh.ui.home.HomeScreen(
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(PokyhDestinations.TIMETABLE) {
            dev.plattnericus.pokyh.ui.timetable.TimetableScreen()
        }
        composable(PokyhDestinations.SCHOOL_HUB) {
            dev.plattnericus.pokyh.ui.school.SchoolHubScreen(
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(PokyhDestinations.GRADES) {
            dev.plattnericus.pokyh.ui.grades.GradesScreen(
                onSubjectClick = { lessonId -> navController.navigate(PokyhDestinations.gradeSubject(lessonId)) },
            )
        }
        composable(
            route = PokyhDestinations.GRADE_SUBJECT,
            arguments = listOf(navArgument("subjectId") { type = NavType.IntType }),
        ) {
            dev.plattnericus.pokyh.ui.grades.GradeSubjectScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(PokyhDestinations.MENSA) {
            dev.plattnericus.pokyh.ui.mensa.MensaScreen(
                onDishClick = { dishId -> navController.navigate(PokyhDestinations.dishDetail(dishId)) },
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
            dev.plattnericus.pokyh.ui.todos.TodosScreen()
        }
        composable(PokyhDestinations.REMINDERS) {
            dev.plattnericus.pokyh.ui.reminders.RemindersScreen(
                onReminderClick = { id -> navController.navigate(PokyhDestinations.reminderDetail(id)) },
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
            dev.plattnericus.pokyh.ui.absences.AbsencesScreen()
        }
        composable(PokyhDestinations.MESSAGES) {
            dev.plattnericus.pokyh.ui.messages.MessagesScreen(
                onMessageClick = { id -> navController.navigate(PokyhDestinations.messageDetail(id)) },
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
            dev.plattnericus.pokyh.ui.classreg.ClassregEventsScreen()
        }
        composable(PokyhDestinations.CLASSROOM) {
            dev.plattnericus.pokyh.ui.classroom.ClassScreen()
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
