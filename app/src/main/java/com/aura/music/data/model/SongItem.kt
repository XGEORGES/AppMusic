package com.aura.music.data.model

import com.aura.music.core.database.entity.SongEntity

data class SongItem(
    val id: String, // YouTube VideoId
    val title: String,
    val artistName: String,
    val artistId: String? = null,
    val albumName: String? = null,
    val durationSeconds: Long = 0,
    val thumbnailUrl: String,
    val localFilePath: String? = null,
    val isFavorite: Boolean = false,
    val playCount: Int = 0
) {
    fun toEntity(): SongEntity {
        return SongEntity(
            id = id,
            title = title,
            artistName = artistName,
            artistId = artistId,
            albumName = albumName,
            durationSeconds = durationSeconds,
            thumbnailUrl = thumbnailUrl,
            localFilePath = localFilePath,
            isFavorite = isFavorite,
            playCount = playCount
        )
    }

    companion object {
        fun fromEntity(entity: SongEntity): SongItem {
            return SongItem(
                id = entity.id,
                title = entity.title,
                artistName = entity.artistName,
                artistId = entity.artistId,
                albumName = entity.albumName,
                durationSeconds = entity.durationSeconds,
                thumbnailUrl = entity.thumbnailUrl,
                localFilePath = entity.localFilePath,
                isFavorite = entity.isFavorite,
                playCount = entity.playCount
            )
        }
    }
}
