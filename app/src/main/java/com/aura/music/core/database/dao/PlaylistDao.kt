package com.aura.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.aura.music.core.database.entity.PlaylistEntity
import com.aura.music.core.database.entity.PlaylistSongCrossRef
import com.aura.music.core.database.entity.SongEntity
import com.aura.music.core.database.model.PlaylistWithSongs
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun getPlaylistsSync(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    fun getPlaylistByIdFlow(playlistId: Long): Flow<PlaylistEntity?>

    @Query("UPDATE playlists SET name = :newName WHERE playlistId = :playlistId")
    suspend fun updatePlaylistName(playlistId: Long, newName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongCrossRef(crossRef: PlaylistSongCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongCrossRefs(crossRefs: List<PlaylistSongCrossRef>)

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_song_cross_ref xref ON s.id = xref.songId
        WHERE xref.playlistId = :playlistId
        ORDER BY xref.positionInPlaylist ASC
    """)
    suspend fun getSongsForPlaylist(playlistId: Long): List<SongEntity>

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_song_cross_ref xref ON s.id = xref.songId
        WHERE xref.playlistId = :playlistId
        ORDER BY xref.positionInPlaylist ASC
    """)
    fun getSongsForPlaylistFlow(playlistId: Long): Flow<List<SongEntity>>

    @Transaction
    suspend fun getPlaylistWithSongs(playlistId: Long): PlaylistWithSongs? {
        val playlist = getPlaylistById(playlistId) ?: return null
        val songs = getSongsForPlaylist(playlistId)
        return PlaylistWithSongs(playlist = playlist, songs = songs)
    }

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    suspend fun deleteCrossRefsForPlaylist(playlistId: Long)

    @Transaction
    suspend fun updatePlaylistSongsOrder(playlistId: Long, songs: List<SongEntity>) {
        deleteCrossRefsForPlaylist(playlistId)
        val refs = songs.mapIndexed { index, song ->
            PlaylistSongCrossRef(
                playlistId = playlistId,
                songId = song.id,
                positionInPlaylist = index
            )
        }
        insertPlaylistSongCrossRefs(refs)
    }
}
