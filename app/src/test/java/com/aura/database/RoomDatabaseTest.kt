package com.aura.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aura.music.core.database.AuraDatabase
import com.aura.music.core.database.dao.PlaylistDao
import com.aura.music.core.database.dao.SongDao
import com.aura.music.core.database.entity.PlaylistEntity
import com.aura.music.core.database.entity.PlaylistSongCrossRef
import com.aura.music.core.database.entity.SongEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class RoomDatabaseTest {

    private lateinit var database: AuraDatabase
    private lateinit var songDao: SongDao
    private lateinit var playlistDao: PlaylistDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        songDao = database.songDao()
        playlistDao = database.playlistDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    /**
     * Test 1.1: Insertar una cancion y recuperarla por ID.
     */
    @Test
    fun test1_1_insertAndRetrieveSongById() = runBlocking {
        val song = SongEntity(
            id = "test_video_123",
            title = "Bohemian Rhapsody",
            artistName = "Queen",
            artistId = "artist_queen",
            albumName = "A Night at the Opera",
            durationSeconds = 354,
            thumbnailUrl = "https://example.com/thumb.jpg",
            localFilePath = null,
            isFavorite = false,
            playCount = 0,
            lastPlayedTimestamp = null
        )

        songDao.insertOrUpdate(song)

        val retrieved = songDao.getSongById("test_video_123")
        assertNotNull("La canción recuperada no debe ser nula", retrieved)
        assertEquals("test_video_123", retrieved?.id)
        assertEquals("Bohemian Rhapsody", retrieved?.title)
        assertEquals("Queen", retrieved?.artistName)
    }

    /**
     * Test 1.2: Alternar el estado isFavorite de false a true y filtrar getFavorites().
     */
    @Test
    fun test1_2_toggleFavoriteAndFilterFavorites() = runBlocking {
        val song1 = SongEntity(
            id = "song_fav_1",
            title = "Song One",
            artistName = "Artist A",
            thumbnailUrl = "https://example.com/1.jpg",
            isFavorite = false
        )
        val song2 = SongEntity(
            id = "song_fav_2",
            title = "Song Two",
            artistName = "Artist B",
            thumbnailUrl = "https://example.com/2.jpg",
            isFavorite = false
        )

        songDao.insertOrUpdate(listOf(song1, song2))

        // Al inicio, ninguna es favorita
        val initialFavorites = songDao.getFavoritesSync()
        assertTrue("Al inicio no debe haber favoritos", initialFavorites.isEmpty())

        // Alternamos song1 a favorita
        songDao.updateFavorite("song_fav_1", isFavorite = true)

        val updatedFavorites = songDao.getFavoritesSync()
        assertEquals("Debe haber exactamente 1 favorito", 1, updatedFavorites.size)
        assertEquals("song_fav_1", updatedFavorites.first().id)
        assertTrue(updatedFavorites.first().isFavorite)
    }

    /**
     * Test 1.3: Insertar una playlist, asociar 3 canciones con PlaylistSongCrossRef y
     * verificar que getPlaylistWithSongs() devuelva las canciones ordenadas por positionInPlaylist.
     */
    @Test
    fun test1_3_insertPlaylistWithSongsAndVerifyOrder() = runBlocking {
        // Insertamos 3 canciones
        val songA = SongEntity(id = "song_A", title = "Track Alpha", artistName = "Artist", thumbnailUrl = "thumb")
        val songB = SongEntity(id = "song_B", title = "Track Beta", artistName = "Artist", thumbnailUrl = "thumb")
        val songC = SongEntity(id = "song_C", title = "Track Gamma", artistName = "Artist", thumbnailUrl = "thumb")
        songDao.insertOrUpdate(listOf(songA, songB, songC))

        // Insertamos Playlist
        val playlistId = playlistDao.insertPlaylist(
            PlaylistEntity(
                name = "Rock Classics",
                description = "Best tracks",
                isImported = false
            )
        )

        // Asociamos las canciones con posiciones deliberadamente no consecutivas o invertidas
        // songC -> posición 0
        // songA -> posición 1
        // songB -> posición 2
        val crossRefs = listOf(
            PlaylistSongCrossRef(playlistId = playlistId, songId = "song_C", positionInPlaylist = 0),
            PlaylistSongCrossRef(playlistId = playlistId, songId = "song_A", positionInPlaylist = 1),
            PlaylistSongCrossRef(playlistId = playlistId, songId = "song_B", positionInPlaylist = 2)
        )
        playlistDao.insertPlaylistSongCrossRefs(crossRefs)

        val playlistWithSongs = playlistDao.getPlaylistWithSongs(playlistId)
        assertNotNull("PlaylistWithSongs no debe ser nulo", playlistWithSongs)
        assertEquals("Rock Classics", playlistWithSongs?.playlist?.name)
        assertEquals(3, playlistWithSongs?.songs?.size)

        // Verificamos el orden estricto por positionInPlaylist: [song_C, song_A, song_B]
        assertEquals("song_C", playlistWithSongs?.songs?.get(0)?.id)
        assertEquals("song_A", playlistWithSongs?.songs?.get(1)?.id)
        assertEquals("song_B", playlistWithSongs?.songs?.get(2)?.id)
    }

    /**
     * Test 1.4: Incrementar playCount y comprobar que getTopPlayed() ordene de mayor a menor.
     */
    @Test
    fun test1_4_incrementPlayCountAndVerifyTopPlayedOrder() = runBlocking {
        val song1 = SongEntity(id = "p_song_1", title = "Low Played", artistName = "Artist", thumbnailUrl = "thumb", playCount = 0)
        val song2 = SongEntity(id = "p_song_2", title = "Mid Played", artistName = "Artist", thumbnailUrl = "thumb", playCount = 0)
        val song3 = SongEntity(id = "p_song_3", title = "Top Played", artistName = "Artist", thumbnailUrl = "thumb", playCount = 0)
        songDao.insertOrUpdate(listOf(song1, song2, song3))

        // Incrementamos playCount: song1 -> 1 vez, song2 -> 3 veces, song3 -> 5 veces
        songDao.updatePlayCount("p_song_1") // 1

        repeat(3) { songDao.updatePlayCount("p_song_2") } // 3
        repeat(5) { songDao.updatePlayCount("p_song_3") } // 5

        val topPlayed = songDao.getTopPlayedSync(limit = 10)
        assertEquals(3, topPlayed.size)

        // Orden esperado de mayor a menor: song3 (5), song2 (3), song1 (1)
        assertEquals("p_song_3", topPlayed[0].id)
        assertEquals(5, topPlayed[0].playCount)

        assertEquals("p_song_2", topPlayed[1].id)
        assertEquals(3, topPlayed[1].playCount)

        assertEquals("p_song_1", topPlayed[2].id)
        assertEquals(1, topPlayed[2].playCount)
    }

    /**
     * Test 1.5: Actualizar nombre de playlist con updatePlaylistName.
     */
    @Test
    fun test1_5_updatePlaylistName() = runBlocking {
        val playlistId = playlistDao.insertPlaylist(
            PlaylistEntity(
                name = "Original Name",
                isImported = false
            )
        )

        playlistDao.updatePlaylistName(playlistId, "Renamed Playlist")

        val retrieved = playlistDao.getPlaylistById(playlistId)
        assertNotNull("La playlist debe existir", retrieved)
        assertEquals("Renamed Playlist", retrieved?.name)
    }
}
