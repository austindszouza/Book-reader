package com.personal.bookreader.ui.reader

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.bookreader.R
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalReadiumApi::class)
@Composable
fun ReaderScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    var navigatorFragment by remember { mutableStateOf<EpubNavigatorFragment?>(null) }

    // Observe page turns and position updates from Readium's currentLocator StateFlow
    LaunchedEffect(navigatorFragment) {
        navigatorFragment?.currentLocator?.collect { locator ->
            viewModel.onLocatorChanged(locator)
        }
    }

    // Immediately flush reading progress when pausing, stopping, or exiting
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.saveProgressImmediately()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.saveProgressImmediately()
        }
    }

    BackHandler {
        onBackClick()
    }

    DisposableEffect(viewModel.bookId) {
        onDispose {
            navigatorFragment = null
            activity?.supportFragmentManager?.let { fm ->
                if (!fm.isStateSaved && !fm.isDestroyed) {
                    val fragment = fm.findFragmentById(R.id.epub_navigator_container)
                    if (fragment != null) {
                        fm.commit {
                            remove(fragment)
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    val title = when (val state = uiState) {
                        is ReaderUiState.Success -> state.title
                        else -> "Reader"
                    }
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Library"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is ReaderUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is ReaderUiState.Error -> {
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
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBackClick) {
                            Text("Go Back")
                        }
                    }
                }
            }

            is ReaderUiState.Success -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            FragmentContainerView(ctx).apply {
                                id = R.id.epub_navigator_container
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { container ->
                            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                                val attachAction = {
                                    val fm = activity.supportFragmentManager
                                    if (!fm.isStateSaved && !fm.isDestroyed) {
                                        val existing = fm.findFragmentById(R.id.epub_navigator_container)
                                        if (existing is EpubNavigatorFragment && !existing.isRemoving) {
                                            existing.addInputListener(DirectionalNavigationAdapter(existing))
                                            navigatorFragment = existing
                                        } else {
                                            val factory = state.navigatorFactory.createFragmentFactory(
                                                initialLocator = state.initialLocator
                                            )
                                            fm.fragmentFactory = factory
                                            val fragment = factory.instantiate(
                                                container.context.classLoader,
                                                EpubNavigatorFragment::class.java.name
                                            ) as EpubNavigatorFragment

                                            fragment.addInputListener(DirectionalNavigationAdapter(fragment))
                                            navigatorFragment = fragment

                                            fm.commit {
                                                replace(
                                                    R.id.epub_navigator_container,
                                                    fragment,
                                                    "epub_navigator_${viewModel.bookId}"
                                                )
                                            }
                                        }
                                    }
                                }

                                if (container.isAttachedToWindow) {
                                    attachAction()
                                } else {
                                    container.doOnAttach {
                                        attachAction()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
