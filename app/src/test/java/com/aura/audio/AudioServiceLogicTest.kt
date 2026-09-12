package com.aura.audio

import android.content.Context
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.aura.music.data.model.SongItem
import com.aura.music.service.audio.AudioPlayerManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioServiceLogicTest {

    private lateinit var context: Context
    private lateinit var audioPlayerManager: AudioPlayerManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        audioPlayerManager = AudioPlayerManager(context)
    }

    @After
    fun tearDown() {
        audioPlayerManager.release()
    }

    /**
     * Test 3.1: Verificar que al encolar una lista de MediaItem,
     * ExoPlayer avance correctamente el índice al llamar a seekToNextMediaItem().
     */
    @Test
    fun test3_1_enqueueListAndSeekToNextAdvancesIndex() {
        val songs = listOf(
            SongItem(id = "song_1", title = "Track 1", artistName = "Artist 1", thumbnailUrl = "thumb1"),
            SongItem(id = "song_2", title = "Track 2", artistName = "Artist 2", thumbnailUrl = "thumb2"),
            SongItem(id = "song_3", title = "Track 3", artistName = "Artist 3", thumbnailUrl = "thumb3")
        )

        audioPlayerManager.setQueue(songs, startIndex = 0, autoPlay = false)
        assertEquals(0, audioPlayerManager.player.currentMediaItemIndex)
        assertEquals(3, audioPlayerManager.player.mediaItemCount)

        audioPlayerManager.player.seekToNextMediaItem()
        assertEquals(1, audioPlayerManager.player.currentMediaItemIndex)

        audioPlayerManager.player.seekToNextMediaItem()
        assertEquals(2, audioPlayerManager.player.currentMediaItemIndex)
    }

    /**
     * Test 3.2: Verificar que el modo repeatMode (OFF, ONE, ALL) actualice el estado del reproductor.
     */
    @Test
    fun test3_2_repeatModeUpdatesPlayerState() {
        // Inicial: REPEAT_MODE_OFF
        assertEquals(Player.REPEAT_MODE_OFF, audioPlayerManager.player.repeatMode)

        // Cambiar a REPEAT_MODE_ONE
        audioPlayerManager.setRepeatMode(Player.REPEAT_MODE_ONE)
        assertEquals(Player.REPEAT_MODE_ONE, audioPlayerManager.player.repeatMode)
        assertEquals(Player.REPEAT_MODE_ONE, audioPlayerManager.repeatMode.value)

        // Cambiar a REPEAT_MODE_ALL
        audioPlayerManager.setRepeatMode(Player.REPEAT_MODE_ALL)
        assertEquals(Player.REPEAT_MODE_ALL, audioPlayerManager.player.repeatMode)
        assertEquals(Player.REPEAT_MODE_ALL, audioPlayerManager.repeatMode.value)

        // Regresar a REPEAT_MODE_OFF
        audioPlayerManager.setRepeatMode(Player.REPEAT_MODE_OFF)
        assertEquals(Player.REPEAT_MODE_OFF, audioPlayerManager.player.repeatMode)
        assertEquals(Player.REPEAT_MODE_OFF, audioPlayerManager.repeatMode.value)
    }

    /**
     * Test 3.3: Verificar la lógica de disparo del callback de cola para invocar
     * la Radio Infinita cuando faltan menos de 2 canciones para terminar la cola.
     */
    @Test
    fun test3_3_infiniteRadioTriggersWhenQueueLow() {
        val songs = listOf(
            SongItem(id = "song_1", title = "Track 1", artistName = "Artist 1", thumbnailUrl = "thumb1"),
            SongItem(id = "song_2", title = "Track 2", artistName = "Artist 2", thumbnailUrl = "thumb2"),
            SongItem(id = "song_3", title = "Track 3", artistName = "Artist 3", thumbnailUrl = "thumb3")
        )

        var triggeredLastSongId: String? = null
        audioPlayerManager.onInfiniteRadioTriggered = { lastSongId ->
            triggeredLastSongId = lastSongId
        }

        audioPlayerManager.setQueue(songs, startIndex = 0, autoPlay = false)

        // En el índice 0: restan 3 - 1 - 0 = 2 canciones -> No dispara
        audioPlayerManager.checkInfiniteRadioTrigger(isEnd = false)
        assertNull("No debe disparar si restan 2 o más canciones", triggeredLastSongId)

        // Avanzamos al índice 1: restan 3 - 1 - 1 = 1 canción (< 2) -> Dispara Radio Infinita
        audioPlayerManager.player.seekToNextMediaItem()
        audioPlayerManager.checkInfiniteRadioTrigger(isEnd = false)

        assertNotNull("Debe disparar el callback de Radio Infinita cuando restan menos de 2 canciones", triggeredLastSongId)
        assertEquals("song_3", triggeredLastSongId)
    }

    /**
     * Test 3.4: Verificar que prepareForLoading pausa la reproducción previa,
     * actualiza la canción activa a la nueva, resetea posición y activa isLoading.
     */
    @Test
    fun test3_4_prepareForLoadingPausesPlayerAndSetsLoadingState() {
        val newSong = SongItem(
            id = "new_song",
            title = "New Song",
            artistName = "New Artist",
            durationSeconds = 180,
            thumbnailUrl = "thumb"
        )

        audioPlayerManager.prepareForLoading(newSong)

        assertEquals("New Song", audioPlayerManager.currentSong.value?.title)
        assertEquals(0L, audioPlayerManager.currentPosition.value)
        assertEquals(180_000L, audioPlayerManager.duration.value)
        org.junit.Assert.assertTrue("isLoading debe ser true al preparar carga", audioPlayerManager.isLoading.value)
        org.junit.Assert.assertFalse("El reproductor no debe estar en play", audioPlayerManager.player.playWhenReady)
    }

    /**
     * Test 3.5: Verificar que togglePlayPause se ignore mientras isLoading es true,
     * evitando que la canción anterior se reanude accidentalmente.
     */
    @Test
    fun test3_5_togglePlayPauseIgnoredWhileLoading() {
        val newSong = SongItem(id = "loading_song", title = "Loading", artistName = "Artist", thumbnailUrl = "thumb")
        audioPlayerManager.prepareForLoading(newSong)

        org.junit.Assert.assertTrue(audioPlayerManager.isLoading.value)
        // Intentar togglear play/pause mientras carga
        audioPlayerManager.togglePlayPause()

        // playWhenReady debe continuar en false
        org.junit.Assert.assertFalse("togglePlayPause no debe activar la reproducción mientras carga", audioPlayerManager.player.playWhenReady)
    }

    /**
     * Test 3.6: Verificar que al desactivar Radio Infinita (setInfiniteRadioEnabled(false)),
     * el callback no se dispare aunque falten menos de 2 canciones para terminar la cola.
     */
    @Test
    fun test3_6_infiniteRadioDisabledDoesNotTrigger() {
        val songs = listOf(
            SongItem(id = "song_1", title = "Track 1", artistName = "Artist 1", thumbnailUrl = "thumb1"),
            SongItem(id = "song_2", title = "Track 2", artistName = "Artist 2", thumbnailUrl = "thumb2"),
            SongItem(id = "song_3", title = "Track 3", artistName = "Artist 3", thumbnailUrl = "thumb3")
        )

        var triggeredLastSongId: String? = null
        audioPlayerManager.onInfiniteRadioTriggered = { lastSongId ->
            triggeredLastSongId = lastSongId
        }

        audioPlayerManager.setInfiniteRadioEnabled(false)
        audioPlayerManager.setQueue(songs, startIndex = 1, autoPlay = false)

        // En el índice 1: restan 3 - 1 - 1 = 1 canción (< 2), pero la radio está desactivada
        audioPlayerManager.checkInfiniteRadioTrigger(isEnd = false)
        assertNull("No debe disparar si la Radio Infinita está desactivada", triggeredLastSongId)

        // Si se vuelve a activar, debe disparar
        audioPlayerManager.setInfiniteRadioEnabled(true)
        audioPlayerManager.checkInfiniteRadioTrigger(isEnd = false)
        assertEquals("song_3", triggeredLastSongId)
    }
}
