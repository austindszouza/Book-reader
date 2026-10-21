package com.personal.bookreader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.personal.bookreader.ui.library.LibraryScreen
import com.personal.bookreader.ui.reader.ReaderScreen

object Routes {
    const val LIBRARY = "library"
    const val READER = "reader/{bookId}"

    fun reader(bookId: Long): String = "reader/$bookId"
}

@Composable
fun BookReaderNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY,
        modifier = modifier
    ) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onNavigateToReader = { bookId ->
                    navController.navigate(Routes.reader(bookId))
                }
            )
        }
        composable(
            route = Routes.READER,
            arguments = listOf(
                navArgument("bookId") {
                    type = NavType.LongType
                }
            )
        ) {
            ReaderScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
