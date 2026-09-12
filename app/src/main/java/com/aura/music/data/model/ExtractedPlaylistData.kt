package com.aura.music.data.model

data class ExtractedPlaylistData(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val songs: List<SongItem> = emptyList()
)
