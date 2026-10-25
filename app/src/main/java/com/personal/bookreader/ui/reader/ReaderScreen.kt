package com.personal.bookreader.ui.reader

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.doOnAttach
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.bookreader.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.preferences.Theme
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
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    var navigatorFragment by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }

    // Edge-to-edge system bars: adapt icons to reader theme, restore normal system appearance on exit
    val systemInDark = isSystemInDarkTheme()
    val window = activity?.window

    LaunchedEffect(settings.theme, window) {
        window?.let { w ->
            val controller = WindowCompat.getInsetsController(w, w.decorView)
            val isDarkTheme = settings.theme == Theme.DARK
            controller.isAppearanceLightStatusBars = !isDarkTheme
            controller.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    DisposableEffect(window) {
        onDispose {
            window?.let { w ->
                val controller = WindowCompat.getInsetsController(w, w.decorView)
                controller.isAppearanceLightStatusBars = !systemInDark
                controller.isAppearanceLightNavigationBars = !systemInDark
            }
        }
    }

    // Observe page turns and position updates from Readium's currentLocator StateFlow
    LaunchedEffect(navigatorFragment) {
        val fragment = navigatorFragment ?: return@LaunchedEffect
        fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            fragment.currentLocator.collect { locator ->
                viewModel.onLocatorChanged(locator)
            }
        }
    }

    // Submit live preferences to the active navigator fragment (lifecycle-aware)
    LaunchedEffect(navigatorFragment) {
        val fragment = navigatorFragment ?: return@LaunchedEffect
        fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var reflowJob: Job? = null
            viewModel.livePreferences.collect { preferences ->
                fragment.submitPreferences(preferences)

                // After a preference change, wait ~500ms for reflow to settle,
                // then fetch and save the fresh locator so leaving immediately is safe.
                reflowJob?.cancel()
                reflowJob = launch {
                    delay(500L)
                    val freshLocator = try {
                        fragment.firstVisibleElementLocator()
                    } catch (e: Exception) {
                        null
                    }
                    if (freshLocator != null) {
                        viewModel.saveProgressImmediately("reflow", freshLocator)
                    }
                }
            }
        }
    }

    // Safely capture locator before back navigation
    val handleBack: () -> Unit = {
        val fragment = navigatorFragment
        if (fragment != null && fragment.view != null) {
            lifecycleOwner.lifecycleScope.launch {
                val freshLocator = try {
                    fragment.firstVisibleElementLocator()
                } catch (e: Exception) {
                    null
                }
                viewModel.saveProgressImmediately("back", freshLocator)
                onBackClick()
            }
        } else {
            onBackClick()
        }
    }

    // Immediately flush reading progress when pausing, stopping, or exiting
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                val fragment = navigatorFragment
                if (fragment != null && fragment.view != null) {
                    lifecycleOwner.lifecycleScope.launch {
                        val freshLocator = try {
                            fragment.firstVisibleElementLocator()
                        } catch (e: Exception) {
                            null
                        }
                        viewModel.saveProgressImmediately("pause", freshLocator)
                    }
                } else {
                    viewModel.saveProgressImmediately("pause")
                }
            } else if (event == Lifecycle.Event.ON_STOP) {
                viewModel.saveProgressImmediately("stop")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.saveProgressImmediately("dispose")
        }
    }

    BackHandler {
        handleBack()
    }

    // Track fragment view lifecycle to set navigatorFragment only once view exists
    DisposableEffect(activity, viewModel.bookId) {
        val fm = activity?.supportFragmentManager
        val callbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(
                fm: FragmentManager,
                f: Fragment,
                v: View,
                savedInstanceState: Bundle?
            ) {
                if (f is EpubNavigatorFragment && f.tag == "epub_navigator_${viewModel.bookId}") {
                    f.addInputListener(DirectionalNavigationAdapter(f))
                    navigatorFragment = f
                }
            }

            override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
                if (f is EpubNavigatorFragment && f.tag == "epub_navigator_${viewModel.bookId}") {
                    navigatorFragment = null
                }
            }
        }
        fm?.registerFragmentLifecycleCallbacks(callbacks, false)
        onDispose {
            fm?.unregisterFragmentLifecycleCallbacks(callbacks)
            navigatorFragment = null
            fm?.let { nonNullFm ->
                if (!nonNullFm.isStateSaved && !nonNullFm.isDestroyed) {
                    val fragment = nonNullFm.findFragmentById(R.id.epub_navigator_container)
                    if (fragment != null) {
                        nonNullFm.commit {
                            remove(fragment)
                        }
                    }
                }
            }
        }
    }

    val readerBgColor = Color(settings.theme.backgroundColor)
    val readerContentColor = Color(settings.theme.contentColor)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = readerBgColor,
        contentColor = readerContentColor,
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
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Library"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Reader Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = readerBgColor,
                    titleContentColor = readerContentColor,
                    navigationIconContentColor = readerContentColor,
                    actionIconContentColor = readerContentColor
                )
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
                    CircularProgressIndicator(color = readerContentColor)
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
                                            if (existing.view != null) {
                                                navigatorFragment = existing
                                            }
                                        } else {
                                            val factory = state.navigatorFactory.createFragmentFactory(
                                                initialLocator = state.initialLocator,
                                                initialPreferences = state.initialPreferences
                                            )
                                            fm.fragmentFactory = factory
                                            val fragment = factory.instantiate(
                                                container.context.classLoader,
                                                EpubNavigatorFragment::class.java.name
                                            ) as EpubNavigatorFragment

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

    if (showSettingsSheet) {
        ReaderSettingsBottomSheet(
            settings = settings,
            onDismissRequest = { showSettingsSheet = false },
            onThemeSelected = { viewModel.updateTheme(it) },
            onLiveFontSizeChange = { viewModel.applyFontSizeLive(settings, it) },
            onCommitFontSize = { viewModel.commitFontSize(it) },
            onLineHeightSelected = { viewModel.updateLineHeight(it) },
            onFontFamilySelected = { viewModel.updateFontFamily(it) }
        )
    }
}
