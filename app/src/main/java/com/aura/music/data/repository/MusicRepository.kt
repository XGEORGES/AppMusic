package com.aura.music.data.repository

import androidx.room.withTransaction
import com.aura.music.core.database.AuraDatabase
import com.aura.music.core.database.dao.PlaylistDao
import com.aura.music.core.database.dao.SongDao
import com.aura.music.core.database.entity.PlaylistEntity
import com.aura.music.core.database.entity.PlaylistSongCrossRef
import com.aura.music.core.database.entity.SongEntity
import com.aura.music.core.database.model.PlaylistWithSongs
import com.aura.music.data.extractor.YouTubeMusicSource
import com.aura.music.data.model.AudioStreamInfo
import com.aura.music.data.model.FilterType
import com.aura.music.data.model.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class MusicRepository @Inject constructor(
    private val database: AuraDatabase,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val youTubeMusicSource: YouTubeMusicSource
) {
    open fun search(query: String, filter: FilterType = FilterType.ALL): Flow<List<SongEntity>> = flow {
        val items = youTubeMusicSource.search(query, filter)
        val entities = items.map { it.toEntity() }
        emit(entities)
    }.flowOn(Dispatchers.IO)

    open suspend fun getStream(songId: String): Result<AudioStreamInfo> {
        return runCatching {
            youTubeMusicSource.getStreamUrl(songId)
        }
    }

    open fun importPlaylistFromUrl(url: String): Flow<Resource<PlaylistWithSongs>> = flow {
        emit(Resource.Loading)
        try {
            val extracted = youTubeMusicSource.extractPlaylist(url)

            val playlistWithSongs = database.withTransaction {
                val playlist = PlaylistEntity(
                    name = extracted.name.ifBlank { "Playlist Importada" },
                    description = "Importada desde YouTube",
                    isImported = true,
                    originalUrl = url
                )
                val playlistId = playlistDao.insertPlaylist(playlist)

                val songEntities = extracted.songs.map { it.toEntity() }
                if (songEntities.isNotEmpty()) {
                    songDao.insertOrUpdate(songEntities)
                }

                val crossRefs = extracted.songs.mapIndexed { index, song ->
                    PlaylistSongCrossRef(
                        playlistId = playlistId,
                        songId = song.id,
                        positionInPlaylist = index
                    )
                }
                if (crossRefs.isNotEmpty()) {
                    playlistDao.insertPlaylistSongCrossRefs(crossRefs)
                }

                playlistDao.getPlaylistWithSongs(playlistId)
            }

            if (playlistWithSongs != null) {
                emit(Resource.Success(playlistWithSongs))
            } else {
                emit(Resource.Error("No se pudo recuperar la playlist importada"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Error al importar la playlist", e))
        }
    }.flowOn(Dispatchers.IO)
}
