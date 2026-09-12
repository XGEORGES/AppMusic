package com.aura.music.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val playlistId: Long = 0,
    val name: String,
    val description: String? = null,
    val isImported: Boolean = false,
    val originalUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
