package com.aura.music.core.database.model

import com.aura.music.core.database.entity.PlaylistEntity
import com.aura.music.core.database.entity.SongEntity

data class PlaylistWithSongs(
    val playlist: PlaylistEntity,
    val songs: List<SongEntity>
)
