package com.personal.bookreader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.personal.bookreader.ui.library.LibraryScreen
import com.personal.bookreader.ui.reader.ReaderScreen

object Routes {
    const val LIBRARY = "library"
    const val READER = "reader"
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
                onNavigateToReader = {
                    navController.navigate(Routes.READER)
                }
            )
        }
        composable(Routes.READER) {
            ReaderScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
