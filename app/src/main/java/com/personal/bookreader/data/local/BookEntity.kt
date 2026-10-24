package com.personal.bookreader.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [Index(value = ["fileHash"], unique = true)]
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String?,
    val filePath: String,
    val coverPath: String?,
    val fileHash: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val progression: Double? = null,
    val locatorJson: String? = null
)
