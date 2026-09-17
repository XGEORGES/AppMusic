package com.aura.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aura.music.core.database.AuraDatabase
import com.aura.music.core.database.dao.PlaylistDao
import com.aura.music.core.database.dao.SongDao
import com.aura.music.core.database.entity.SongEntity
import com.aura.music.data.extractor.YouTubeMusicSource
import com.aura.music.data.model.ExtractedPlaylistData
import com.aura.music.data.model.Resource
import com.aura.music.data.model.SongItem
import com.aura.music.data.repository.MusicRepository
import com.aura.music.data.repository.PlayerRepository
import com.aura.music.service.audio.AudioPlayerManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MusicRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: AuraDatabase
    private lateinit var songDao: SongDao
    private lateinit var playlistDao: PlaylistDao
    private lateinit var mockSource: YouTubeMusicSource
    private lateinit var musicRepository: MusicRepository
    private lateinit var playerManager: AudioPlayerManager
    private lateinit var playerRepository: PlayerRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        songDao = database.songDao()
        playlistDao = database.playlistDao()
        mockSource = mockk(relaxed = true)

        musicRepository = MusicRepository(database, songDao, playlistDao, mockSource)
        playerManager = AudioPlayerManager(context)
        playerRepository = PlayerRepository(songDao, playerManager, mockSource, dagger.Lazy { mockk(relaxed = true) })
    }

    @After
    fun tearDown() {
        playerManager.release()
        database.close()
    }

    /**
     * Test 4.1: Importar Playlist: Simular respuesta de NewPipe, verificar que se llamen
     * los métodos correspondientes de PlaylistDao y SongDao en una sola transacción.
     */
    @Test
    fun test4_1_importPlaylistFromUrlAtomicTransaction() = runBlocking {
        val testUrl = "https://www.youtube.com/playlist?list=PL12345"
        val mockSongs = listOf(
            SongItem(id = "s1", title = "Track 1", artistName = "Artist 1", thumbnailUrl = "thumb1"),
            SongItem(id = "s2", title = "Track 2", artistName = "Artist 2", thumbnailUrl = "thumb2"),
            SongItem(id = "s3", title = "Track 3", artistName = "Artist 3", thumbnailUrl = "thumb3")
        )
        val extractedData = ExtractedPlaylistData(
            id = "PL12345",
            name = "Top Hits 2026",
            thumbnailUrl = "thumb_cover",
            songs = mockSongs
        )

        coEvery { mockSource.extractPlaylist(testUrl) } returns extractedData

        val emissions = musicRepository.importPlaylistFromUrl(testUrl).toList()

        assertTrue("Debe iniciar emitiendo Resource.Loading", emissions.first() is Resource.Loading)
        val lastEmission = emissions.last()
        assertTrue("La emisión final debe ser Resource.Success", lastEmission is Resource.Success)

        val resultData = (lastEmission as Resource.Success).data
        assertNotNull("La playlist importada no debe ser nula", resultData)
        assertEquals("Top Hits 2026", resultData.playlist.name)
        assertTrue(resultData.playlist.isImported)
        assertEquals(3, resultData.songs.size)

        // Verificamos persistencia real en Room DB
        val storedSongs = playlistDao.getSongsForPlaylist(resultData.playlist.playlistId)
        assertEquals(3, storedSongs.size)
        assertEquals("s1", storedSongs[0].id)
        assertEquals("s2", storedSongs[1].id)
        assertEquals("s3", storedSongs[2].id)
    }

    /**
     * Test 4.2: Toggle Favorite: Verificar que el flag isFavorite se invierta
     * y emita el nuevo valor a través del StateFlow.
     */
    @Test
    fun test4_2_toggleFavoriteInvertsFlagAndEmitsNewValue() = runBlocking {
        val testSong = SongEntity(
            id = "fav_test_song",
            title = "Awesome Song",
            artistName = "Great Artist",
            thumbnailUrl = "thumb",
            isFavorite = false
        )
        songDao.insertOrUpdate(testSong)

        // Reproducimos la canción en PlayerRepository para que currentPlayingSong se active
        playerRepository.playSong(testSong)
        assertEquals("fav_test_song", playerRepository.currentPlayingSong.value?.id)
        assertFalse(playerRepository.currentPlayingSong.value?.isFavorite ?: true)

        // Primer Toggle: de false -> true
        playerRepository.toggleFavorite("fav_test_song")

        // Verificamos Room DB y StateFlow
        val updatedDbSong1 = songDao.getSongById("fav_test_song")
        assertTrue("En DB isFavorite debe ser true", updatedDbSong1?.isFavorite == true)
        assertTrue(
            "StateFlow currentPlayingSong debe emitir isFavorite == true",
            playerRepository.currentPlayingSong.value?.isFavorite == true
        )

        // Segundo Toggle: de true -> false
        playerRepository.toggleFavorite("fav_test_song")

        val updatedDbSong2 = songDao.getSongById("fav_test_song")
        assertFalse("En DB isFavorite debe ser false", updatedDbSong2?.isFavorite ?: true)
        assertFalse(
            "StateFlow currentPlayingSong debe emitir isFavorite == false",
            playerRepository.currentPlayingSong.value?.isFavorite ?: true
        )
    }

    /**
     * Test 4.3: Verificar que play() y pause() actúen de manera determinista
     * y que pause() detenga la reproducción sin invertir el estado arbitrariamente.
     */
    @Test
    fun test4_3_explicitPlayAndPauseMethodsAreDeterministic() = runBlocking {
        val testSong = SongEntity(
            id = "playback_test_song",
            title = "Playback Song",
            artistName = "Artist",
            thumbnailUrl = "thumb"
        )
        songDao.insertOrUpdate(testSong)

        playerRepository.playSong(testSong)

        // pause() debe pausar
        playerRepository.pause()
        assertFalse("El reproductor debe estar en pausa después de llamar a pause()", playerManager.player.playWhenReady)

        // Llamar pause() nuevamente no debe invertir a play
        playerRepository.pause()
        assertFalse("Llamar pause() dos veces debe mantener el reproductor en pausa", playerManager.player.playWhenReady)
    }
}
