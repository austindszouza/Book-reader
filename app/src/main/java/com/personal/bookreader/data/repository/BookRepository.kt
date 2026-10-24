package com.personal.bookreader.data.repository

import com.personal.bookreader.data.local.BookDao
import com.personal.bookreader.data.local.BookEntity
import kotlinx.coroutines.flow.Flow

class BookRepository(private val bookDao: BookDao) {
    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()

    suspend fun findByHash(hash: String): BookEntity? = bookDao.findByHash(hash)

    suspend fun getBookById(id: Long): BookEntity? = bookDao.getBookById(id)

    suspend fun insertBook(book: BookEntity): Long = bookDao.insert(book)

    suspend fun updateProgress(id: Long, progression: Double?, locatorJson: String?) =
        bookDao.updateProgress(id, progression, locatorJson)
}
