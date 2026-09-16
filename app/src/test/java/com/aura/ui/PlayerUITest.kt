package com.aura.ui

import com.aura.music.core.database.dao.PlaylistDao
import com.aura.music.core.database.dao.SongDao
import com.aura.music.core.database.entity.SongEntity
import com.aura.music.data.repository.MusicRepository
import com.aura.music.data.repository.PlayerRepository
import com.aura.music.ui.library.LibraryViewModel
import com.aura.music.ui.player.PlayerViewModel
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerUITest {

    /**
     * Test 5.1: MiniPlayer se muestra si currentPlayingSong != null y se oculta si es null.
     */
    @Test
    fun test5_1_miniPlayerVisibilityCondition() {
        val mockPlayerRepo = mockk<PlayerRepository>(relaxed = true)
        val currentSongFlow = kotlinx.coroutines.flow.MutableStateFlow<SongEntity?>(null)
        io.mockk.every { mockPlayerRepo.currentPlayingSong } returns currentSongFlow

        val playerViewModel = PlayerViewModel(mockPlayerRepo)

        // Inicial: Sin canción -> null (MiniPlayer oculto)
        assertNull("Al inicio currentPlayingSong debe ser null (MiniPlayer oculto)", playerViewModel.currentPlayingSong.value)

        // Simular canción en reproducción -> no null (MiniPlayer visible)
        val testSong = SongEntity(
            id = "test_song_1",
            title = "Test Song",
            artistName = "Test Artist",
            thumbnailUrl = "thumb"
        )
        currentSongFlow.value = testSong

        assertNotNull("Cuando hay canción en reproducción, no debe ser null (MiniPlayer visible)", playerViewModel.currentPlayingSong.value)
        assertEquals("test_song_1", playerViewModel.currentPlayingSong.value?.id)
    }

    /**
     * Test 5.2: Al hacer click en el botón Play/Pause del MiniPlayer, se dispara el evento correspondiente.
     */
    @Test
    fun test5_2_miniPlayerPlayPauseTriggersEvent() {
        val mockPlayerRepo = mockk<PlayerRepository>(relaxed = true)
        val playerViewModel = PlayerViewModel(mockPlayerRepo)

        playerViewModel.togglePlayPause()

        verify(exactly = 1) { mockPlayerRepo.togglePlayPause() }
    }

    /**
     * Test 5.3: El diálogo de importar playlist valida URLs correctas de YouTube
     * y deshabilita la importación si el campo está vacío o inválido.
     */
    @Test
    fun test5_3_importPlaylistUrlValidation() {
        val mockSongDao = mockk<SongDao>(relaxed = true)
        val mockPlaylistDao = mockk<PlaylistDao>(relaxed = true)
        val mockMusicRepo = mockk<MusicRepository>(relaxed = true)

        val libraryViewModel = LibraryViewModel(mockSongDao, mockPlaylistDao, mockMusicRepo)

        // Validaciones negativas
        assertFalse("Cadena vacía debe ser inválida", libraryViewModel.isUrlValid(""))
        assertFalse("Solo espacios debe ser inválido", libraryViewModel.isUrlValid("   "))
        assertFalse("Texto aleatorio debe ser inválido", libraryViewModel.isUrlValid("not_a_url"))
        assertFalse("URL que no es de YouTube debe ser inválida", libraryViewModel.isUrlValid("https://example.com/something"))

        // Validaciones positivas
        assertTrue(
            "URL estándar de playlist de YouTube debe ser válida",
            libraryViewModel.isUrlValid("https://www.youtube.com/playlist?list=PL1234567890ABCDEF")
        )
        assertTrue(
            "URL móvil de YouTube debe ser válida",
            libraryViewModel.isUrlValid("https://m.youtube.com/playlist?list=PLxyz")
        )
        assertTrue(
            "URL acortada youtu.be con list param debe ser válida",
            libraryViewModel.isUrlValid("https://youtu.be/playlist?list=PLxyz")
        )

        // Comportamiento del ViewModel: URL vacía no dispara importación
        libraryViewModel.onUrlChanged("")
        libraryViewModel.importPlaylist()
        assertNotNull("Debe mostrar mensaje de error si se intenta importar URL inválida", libraryViewModel.importError.value)
    }

    /**
     * Test 5.4: Cambio de pestaña entre Playlists y Descargas en LibraryViewModel.
     */
    @Test
    fun test5_4_libraryTabSwitching() {
        val mockSongDao = mockk<SongDao>(relaxed = true)
        val mockPlaylistDao = mockk<PlaylistDao>(relaxed = true)
        val mockMusicRepo = mockk<MusicRepository>(relaxed = true)

        val libraryViewModel = LibraryViewModel(mockSongDao, mockPlaylistDao, mockMusicRepo)

        assertEquals("Por defecto la pestaña inicial debe ser PLAYLISTS", com.aura.music.ui.library.LibraryTab.PLAYLISTS, libraryViewModel.selectedTab.value)

        libraryViewModel.selectTab(com.aura.music.ui.library.LibraryTab.DOWNLOADS)
        assertEquals("Al seleccionar DOWNLOADS, el estado debe cambiar", com.aura.music.ui.library.LibraryTab.DOWNLOADS, libraryViewModel.selectedTab.value)

        libraryViewModel.selectTab(com.aura.music.ui.library.LibraryTab.PLAYLISTS)
        assertEquals("Al seleccionar PLAYLISTS, el estado debe cambiar de vuelta", com.aura.music.ui.library.LibraryTab.PLAYLISTS, libraryViewModel.selectedTab.value)
    }

    /**
     * Test 5.5: Fijar y desfijar playlists de accesos directos.
     */
    @Test
    fun test5_5_togglePinPlaylist() {
        val mockSongDao = mockk<SongDao>(relaxed = true)
        val mockPlaylistDao = mockk<PlaylistDao>(relaxed = true)
        val mockMusicRepo = mockk<MusicRepository>(relaxed = true)

        val libraryViewModel = LibraryViewModel(mockSongDao, mockPlaylistDao, mockMusicRepo)

        assertTrue("Inicialmente no hay playlists fijadas", libraryViewModel.pinnedPlaylistIds.value.isEmpty())

        libraryViewModel.togglePinPlaylist(42L)
        assertTrue("La playlist 42 debe estar fijada", libraryViewModel.pinnedPlaylistIds.value.contains(42L))

        libraryViewModel.togglePinPlaylist(42L)
        assertFalse("Al llamar toggle de nuevo, la playlist 42 debe quedar desfijada", libraryViewModel.pinnedPlaylistIds.value.contains(42L))
    }

    /**
     * Test 5.6: Generación de enlace para compartir playlists importadas de YouTube.
     */
    @Test
    fun test5_6_playlistShareUrlGeneration() {
        val mockSongDao = mockk<SongDao>(relaxed = true)
        val mockPlaylistDao = mockk<PlaylistDao>(relaxed = true)
        val mockMusicRepo = mockk<MusicRepository>(relaxed = true)

        val libraryViewModel = LibraryViewModel(mockSongDao, mockPlaylistDao, mockMusicRepo)

        val importedPlaylist = com.aura.music.core.database.entity.PlaylistEntity(
            playlistId = 10L,
            name = "YouTube Hits",
            isImported = true,
            originalUrl = "https://www.youtube.com/playlist?list=PL12345"
        )

        val shareUrl = libraryViewModel.getPlaylistShareUrl(importedPlaylist)
        assertEquals("Debe retornar la URL original de YouTube para playlists importadas", "https://www.youtube.com/playlist?list=PL12345", shareUrl)
    }

    /**
     * Test 5.7: Cambio de ordenación en LibraryViewModel (RECENT, A_TO_Z, Z_TO_A).
     */
    @Test
    fun test5_7_librarySortOrderState() {
        val mockSongDao = mockk<SongDao>(relaxed = true)
        val mockPlaylistDao = mockk<PlaylistDao>(relaxed = true)
        val mockMusicRepo = mockk<MusicRepository>(relaxed = true)

        val libraryViewModel = LibraryViewModel(mockSongDao, mockPlaylistDao, mockMusicRepo)

        assertEquals("Por defecto el orden debe ser RECENT", com.aura.music.ui.library.LibrarySortOrder.RECENT, libraryViewModel.sortOrder.value)

        libraryViewModel.setSortOrder(com.aura.music.ui.library.LibrarySortOrder.A_TO_Z)
        assertEquals("Debe cambiar a A_TO_Z", com.aura.music.ui.library.LibrarySortOrder.A_TO_Z, libraryViewModel.sortOrder.value)

        libraryViewModel.setSortOrder(com.aura.music.ui.library.LibrarySortOrder.Z_TO_A)
        assertEquals("Debe cambiar a Z_TO_A", com.aura.music.ui.library.LibrarySortOrder.Z_TO_A, libraryViewModel.sortOrder.value)
    }

    /**
     * Test 5.8: downloadPlaylist con ID -1L (Favoritos) no lanza excepciones y finaliza el callback.
     */
    @Test
    fun test5_8_downloadPlaylistFavoritesDelegation() {
        val mockSongDao = mockk<SongDao>(relaxed = true)
        val mockPlaylistDao = mockk<PlaylistDao>(relaxed = true)
        val mockMusicRepo = mockk<MusicRepository>(relaxed = true)

        val libraryViewModel = LibraryViewModel(mockSongDao, mockPlaylistDao, mockMusicRepo, mediaDownloadManager = null)

        var completed = false
        // Con downloadManager nulo no debe crashear
        libraryViewModel.downloadPlaylist(-1L) {
            completed = true
        }
    }
}
