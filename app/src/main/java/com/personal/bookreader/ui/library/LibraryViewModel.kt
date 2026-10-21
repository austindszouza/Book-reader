package com.personal.bookreader.ui.library

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.bookreader.data.local.AppDatabase
import com.personal.bookreader.data.local.BookEntity
import com.personal.bookreader.data.repository.BookRepository
import com.personal.bookreader.reader.EpubImportService
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImportItemResult {
    data class Success(val title: String) : ImportItemResult
    data class Duplicate(val title: String) : ImportItemResult
    data class Failure(val reason: String) : ImportItemResult
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: BookRepository
    private val epubImportService = EpubImportService()

    val books: StateFlow<List<BookEntity>>

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BookRepository(database.bookDao())
        books = repository.allBooks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun importBooks(uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            _isImporting.value = true

            var importedCount = 0
            var duplicateCount = 0
            var failureCount = 0

            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    when (val result = importSingleBook(uri)) {
                        is ImportItemResult.Success -> importedCount++
                        is ImportItemResult.Duplicate -> duplicateCount++
                        is ImportItemResult.Failure -> failureCount++
                    }
                }
            }

            _isImporting.value = false

            val message = buildString {
                if (importedCount > 0) {
                    append("Imported $importedCount ${if (importedCount == 1) "book" else "books"}.")
                }
                if (duplicateCount > 0) {
                    if (isNotEmpty()) append(" ")
                    append("$duplicateCount already in library.")
                }
                if (failureCount > 0) {
                    if (isNotEmpty()) append(" ")
                    append("$failureCount failed (corrupt or not an EPUB).")
                }
            }

            if (message.isNotEmpty()) {
                _userMessage.emit(message)
            }
        }
    }

    private suspend fun importSingleBook(uri: Uri): ImportItemResult {
        val context = getApplication<Application>()
        val fallbackTitle = getDisplayName(uri) ?: "Untitled Book"
        val tempFile = try {
            File.createTempFile("import_", ".epub", context.cacheDir)
        } catch (e: Exception) {
            return ImportItemResult.Failure("Unable to create temporary file")
        }

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val copied = context.contentResolver.openInputStream(uri)?.use { rawIn ->
                DigestInputStream(rawIn, digest).use { digestIn ->
                    FileOutputStream(tempFile).use { fileOut ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (digestIn.read(buffer).also { bytesRead = it } != -1) {
                            fileOut.write(buffer, 0, bytesRead)
                        }
                    }
                }
                true
            } ?: false

            if (!copied || tempFile.length() == 0L) {
                tempFile.delete()
                return ImportItemResult.Failure("Failed to read file from storage")
            }

            val fileHash = digest.digest().joinToString("") { "%02x".format(it) }

            // Check duplicate in Room before further processing
            val existing = repository.findByHash(fileHash)
            if (existing != null) {
                tempFile.delete()
                return ImportItemResult.Duplicate(existing.title)
            }

            // Validate and parse with Readium
            val parseResult = epubImportService.parseAndExtract(
                context = context,
                epubFile = tempFile,
                fileHash = fileHash,
                fallbackTitle = fallbackTitle
            )

            if (parseResult.isFailure) {
                tempFile.delete()
                return ImportItemResult.Failure(parseResult.exceptionOrNull()?.message ?: "Invalid EPUB")
            }

            val metadata = parseResult.getOrThrow()

            // Move temp file into app's private permanent storage
            val booksDir = File(context.filesDir, "books").apply { mkdirs() }
            val permanentFile = File(booksDir, "$fileHash.epub")

            val moved = if (tempFile.renameTo(permanentFile)) {
                true
            } else {
                tempFile.copyTo(permanentFile, overwrite = true)
                tempFile.delete()
                true
            }

            if (!moved || !permanentFile.exists()) {
                tempFile.delete()
                return ImportItemResult.Failure("Failed to store EPUB permanently")
            }

            // Store in Room
            val bookEntity = BookEntity(
                title = metadata.title,
                author = metadata.author,
                filePath = permanentFile.absolutePath,
                coverPath = metadata.coverPath,
                fileHash = fileHash
            )

            repository.insertBook(bookEntity)
            return ImportItemResult.Success(metadata.title)
        } catch (e: Exception) {
            tempFile.delete()
            return ImportItemResult.Failure(e.message ?: "Import error")
        }
    }

    private fun getDisplayName(uri: Uri): String? {
        val context = getApplication<Application>()
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {
        }
        return name?.removeSuffix(".epub")
    }
}
