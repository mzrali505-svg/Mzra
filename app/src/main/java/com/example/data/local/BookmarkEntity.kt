package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class BrowserHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val profileId: Long,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val profileId: Long,
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis()
)
