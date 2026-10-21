package com.personal.bookreader.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToReader: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel()
) {
    val books by viewModel.books.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        viewModel.importBooks(uris)
    }

    fun openPicker() {
        filePickerLauncher.launch(
            arrayOf("application/epub+zip", "application/octet-stream", "*/*")
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Library") }
                )
                if (isImporting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = ::openPicker) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Import EPUBs"
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        if (books.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(text = "No books yet")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = ::openPicker) {
                        Text("Import EPUB")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onNavigateToReader) {
                        Text("Open Reader (Temporary)")
                    }
                }
            }
        } else {
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val bottomContentPadding = maxOf(innerPadding.calculateBottomPadding(), navBarBottom) + 88.dp

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = bottomContentPadding
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = books,
                    key = { it.id }
                ) { book ->
                    BookItem(
                        book = book,
                        onClick = onNavigateToReader
                    )
                }
            }
        }
    }
}
