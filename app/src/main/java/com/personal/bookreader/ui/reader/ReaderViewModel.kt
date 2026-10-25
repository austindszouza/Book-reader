package com.personal.bookreader.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.personal.bookreader.data.local.AppDatabase
import com.personal.bookreader.data.repository.BookRepository
import com.personal.bookreader.data.settings.ReaderPreferencesRepository
import com.personal.bookreader.data.settings.ReaderSettings
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser

@OptIn(ExperimentalReadiumApi::class)
sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Success(
        val title: String,
        val navigatorFactory: EpubNavigatorFactory,
        val initialLocator: Locator?,
        val initialPreferences: EpubPreferences
    ) : ReaderUiState
    data class Error(val message: String) : ReaderUiState
}

@OptIn(ExperimentalReadiumApi::class, FlowPreview::class)
class ReaderViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val bookId: Long = savedStateHandle.get<Long>("bookId") ?: -1L

    private val repository: BookRepository
    private val preferencesRepository: ReaderPreferencesRepository

    val settings: StateFlow<ReaderSettings>

    private val _livePreferences = MutableSharedFlow<EpubPreferences>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val livePreferences: SharedFlow<EpubPreferences> = _livePreferences.asSharedFlow()

    private var openJob: Job? = null
    private var activePublication: Publication? = null
    private var activeAsset: Asset? = null

    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastSavedLocator: Locator? = null
    private var currentLocator: Locator? = null
    private val locatorUpdates = MutableSharedFlow<Locator>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BookRepository(database.bookDao())
        preferencesRepository = ReaderPreferencesRepository.getInstance(application)

        settings = preferencesRepository.settings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ReaderSettings()
        )

        // Whenever DataStore emits updated settings, push them to live preferences
        viewModelScope.launch {
            preferencesRepository.settings.collect { currentSettings ->
                _livePreferences.tryEmit(toEpubPreferences(currentSettings))
            }
        }

        // Debounce locator persistence so rapid page turns don't trigger database writes on every frame
        viewModelScope.launch(Dispatchers.IO) {
            locatorUpdates
                .debounce(1000L)
                .collectLatest { locator ->
                    saveLocator(locator, "debounce")
                }
        }

        loadBook()
    }

    fun toEpubPreferences(readerSettings: ReaderSettings): EpubPreferences {
        val isCustomSpacingOrFont = (readerSettings.lineHeight != null) || (readerSettings.fontFamily != null)
        val font = readerSettings.fontFamily?.let { FontFamily(it) }

        return EpubPreferences(
            theme = readerSettings.theme,
            fontSize = readerSettings.fontSize,
            lineHeight = readerSettings.lineHeight,
            fontFamily = font,
            publisherStyles = if (isCustomSpacingOrFont) false else null
        )
    }

    fun updateTheme(theme: Theme) {
        viewModelScope.launch {
            preferencesRepository.updateTheme(theme)
        }
    }

    fun updateFontFamily(fontFamily: String?) {
        viewModelScope.launch {
            preferencesRepository.updateFontFamily(fontFamily)
        }
    }

    fun updateLineHeight(lineHeight: Double?) {
        viewModelScope.launch {
            preferencesRepository.updateLineHeight(lineHeight)
        }
    }

    fun commitFontSize(fontSize: Double) {
        viewModelScope.launch {
            preferencesRepository.updateFontSize(fontSize)
        }
    }

    fun applyFontSizeLive(currentSettings: ReaderSettings, liveSize: Double) {
        val liveSettings = currentSettings.copy(fontSize = liveSize)
        _livePreferences.tryEmit(toEpubPreferences(liveSettings))
    }

    fun applyLineHeightLive(currentSettings: ReaderSettings, liveLineHeight: Double?) {
        val liveSettings = currentSettings.copy(lineHeight = liveLineHeight)
        _livePreferences.tryEmit(toEpubPreferences(liveSettings))
    }

    fun onLocatorChanged(locator: Locator) {
        currentLocator = locator
        locatorUpdates.tryEmit(locator)
    }

    fun saveProgressImmediately(source: String = "manual", freshLocator: Locator? = null) {
        var locator = freshLocator ?: currentLocator
        if (locator == null) return

        if (freshLocator != null && freshLocator.locations.totalProgression == null) {
            val totalProg = currentLocator?.locations?.totalProgression
            if (totalProg != null) {
                locator = locator.copy(
                    locations = locator.locations.copy(totalProgression = totalProg)
                )
            }
        }

        currentLocator = locator
        saveScope.launch {
            saveLocator(locator, source)
        }
    }

    private suspend fun saveLocator(locator: Locator, source: String) {
        if (locator == lastSavedLocator) {
            return
        }

        val rawProgression = locator.locations.totalProgression
        val progression = if (rawProgression != null && rawProgression >= 0.99) 1.0 else rawProgression
        val locatorJson = locator.toJSON().toString()

        repository.updateProgress(bookId, progression, locatorJson)
        lastSavedLocator = locator
    }

    fun loadBook() {
        if (bookId <= 0) {
            _uiState.value = ReaderUiState.Error("Invalid book ID.")
            return
        }

        openJob?.cancel()
        openJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = ReaderUiState.Loading

            var asset: Asset? = null
            var publication: Publication? = null

            try {
                val bookEntity = repository.getBookById(bookId)
                if (bookEntity == null) {
                    _uiState.value = ReaderUiState.Error("Book not found in library.")
                    return@launch
                }

                ensureActive()

                val file = File(bookEntity.filePath)
                if (!file.exists()) {
                    _uiState.value = ReaderUiState.Error("Book file is missing from storage.")
                    return@launch
                }

                ensureActive()

                val initialLocator = bookEntity.locatorJson?.let { json ->
                    try {
                        Locator.fromJSON(JSONObject(json))
                    } catch (e: Exception) {
                        null
                    }
                }
                lastSavedLocator = initialLocator
                currentLocator = initialLocator

                // Load saved preferences before creating the navigator factory to avoid theme flashing
                val initialSettings = preferencesRepository.settings.first()
                val initialPreferences = toEpubPreferences(initialSettings)

                val assetRetriever = AssetRetriever(
                    getApplication<Application>().contentResolver,
                    DefaultHttpClient()
                )
                val assetResult = assetRetriever.retrieve(file)
                asset = assetResult.getOrNull()
                if (asset == null) {
                    _uiState.value = ReaderUiState.Error("Failed to read book file format.")
                    return@launch
                }

                ensureActive()

                val parser = EpubParser()
                val opener = PublicationOpener(publicationParser = parser)
                val pubResult = opener.open(asset, allowUserInteraction = false)
                publication = pubResult.getOrNull()
                if (publication == null) {
                    asset.close()
                    _uiState.value = ReaderUiState.Error("Unable to open EPUB publication.")
                    return@launch
                }

                ensureActive()

                var validatedLocator = initialLocator
                if (validatedLocator != null) {
                    val link = publication.linkWithHref(validatedLocator.href)
                    if (link == null) {
                        validatedLocator = null
                        repository.updateProgress(bookId, null, null)
                    }
                }
                lastSavedLocator = validatedLocator
                currentLocator = validatedLocator

                activeAsset = asset
                activePublication = publication

                val navigatorFactory = EpubNavigatorFactory(publication)

                _uiState.value = ReaderUiState.Success(
                    title = bookEntity.title,
                    navigatorFactory = navigatorFactory,
                    initialLocator = validatedLocator,
                    initialPreferences = initialPreferences
                )
            } catch (e: CancellationException) {
                publication?.close()
                asset?.close()
                throw e
            } catch (e: Exception) {
                publication?.close()
                asset?.close()
                _uiState.value = ReaderUiState.Error(e.message ?: "Failed to open book.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        saveProgressImmediately("onCleared")
        openJob?.cancel()
        activePublication?.close()
        activeAsset?.close()
        activePublication = null
        activeAsset = null
    }
}
