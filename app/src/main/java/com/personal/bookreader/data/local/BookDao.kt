package com.personal.bookreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY dateAdded DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE fileHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getBookById(id: Long): BookEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity): Long
}
