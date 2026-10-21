package com.personal.bookreader.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.personal.bookreader.data.local.AppDatabase
import com.personal.bookreader.data.repository.BookRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.ExperimentalReadiumApi
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
        val navigatorFactory: EpubNavigatorFactory
    ) : ReaderUiState
    data class Error(val message: String) : ReaderUiState
}

@OptIn(ExperimentalReadiumApi::class)
class ReaderViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val bookId: Long = savedStateHandle.get<Long>("bookId") ?: -1L

    private val repository: BookRepository
    private var openJob: Job? = null
    private var activePublication: Publication? = null
    private var activeAsset: Asset? = null

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BookRepository(database.bookDao())
        loadBook()
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
                val book = repository.allBooks // or dao
                val bookEntity = AppDatabase.getDatabase(getApplication()).bookDao().getBookById(bookId)
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

                // Successfully opened and verified active
                activeAsset = asset
                activePublication = publication

                val navigatorFactory = EpubNavigatorFactory(publication)

                _uiState.value = ReaderUiState.Success(
                    title = bookEntity.title,
                    navigatorFactory = navigatorFactory
                )
            } catch (e: CancellationException) {
                // If cancelled while opening, immediately close opened resources
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
        openJob?.cancel()
        activePublication?.close()
        activeAsset?.close()
        activePublication = null
        activeAsset = null
    }
}
