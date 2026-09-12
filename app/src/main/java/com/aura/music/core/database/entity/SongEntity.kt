package com.aura.music.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey
    val id: String, // YouTube VideoId
    val title: String,
    val artistName: String,
    val artistId: String? = null,
    val albumName: String? = null,
    val durationSeconds: Long = 0,
    val thumbnailUrl: String,
    val localFilePath: String? = null,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long? = null
)
