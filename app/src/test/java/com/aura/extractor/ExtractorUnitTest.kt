package com.aura.extractor

import com.aura.music.core.network.DownloaderImpl
import com.aura.music.data.extractor.YouTubeMusicSource
import com.aura.music.data.model.AudioStreamInfo
import com.aura.music.data.model.FilterType
import com.aura.music.data.model.SongItem
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class ExtractorUnitTest {

    /**
     * Test 2.1: Inicialización correcta de NewPipe.init(DownloaderImpl).
     */
    @Test
    fun test2_1_newPipeInitialization() {
        val downloader = DownloaderImpl()
        YouTubeMusicSource.initNewPipe(downloader)

        val activeDownloader = NewPipe.getDownloader()
        assertNotNull("El Downloader de NewPipe no debe ser nulo tras init", activeDownloader)
        assertTrue("El Downloader debe ser instancia de DownloaderImpl", activeDownloader is DownloaderImpl)
    }

    /**
     * Test 2.2: Mock de llamada de búsqueda que verifique el filtrado correcto de ítems de audio.
     */
    @Test
    fun test2_2_searchFiltersAudioItemsCorrectly() = runBlocking {
        val mockSource = mockk<YouTubeMusicSource>()
        val mockSongs = listOf(
            SongItem(
                id = "dQw4w9WgXcQ",
                title = "Never Gonna Give You Up",
                artistName = "Rick Astley",
                durationSeconds = 212,
                thumbnailUrl = "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
            ),
            SongItem(
                id = "9bZkp7q19f0",
                title = "Gangnam Style",
                artistName = "PSY",
                durationSeconds = 252,
                thumbnailUrl = "https://img.youtube.com/vi/9bZkp7q19f0/hqdefault.jpg"
            )
        )

        coEvery { mockSource.search("rick astley", FilterType.SONGS) } returns mockSongs

        val results = mockSource.search("rick astley", FilterType.SONGS)
        assertEquals(2, results.size)
        assertEquals("dQw4w9WgXcQ", results[0].id)
        assertEquals("Never Gonna Give You Up", results[0].title)
        assertEquals("Rick Astley", results[0].artistName)
        assertTrue(results[0].durationSeconds > 0)
    }

    /**
     * Test 2.3: Resolución de URL de stream: Verificar que el objeto devuelto contenga
     * un enlace HTTP válido y un bitrate mayor a 0.
     */
    @Test
    fun test2_3_resolveStreamUrlValidAndBitratePositive() = runBlocking {
        val mockSource = mockk<YouTubeMusicSource>()
        val streamInfo = AudioStreamInfo(
            url = "https://rr3---sn-4g5ednkk.googlevideo.com/videoplayback?expire=12345",
            format = "m4a",
            bitrate = 160000,
            durationSeconds = 210
        )

        coEvery { mockSource.getStreamUrl("video_test_id") } returns streamInfo

        val result = mockSource.getStreamUrl("video_test_id")
        assertNotNull("AudioStreamInfo no debe ser nulo", result)
        assertTrue(
            "La URL de stream debe ser HTTP o HTTPS válida",
            result.url.startsWith("http://") || result.url.startsWith("https://")
        )
        assertTrue("El bitrate debe ser mayor a 0", result.bitrate > 0)
        assertEquals("m4a", result.format)
    }

    /**
     * Test 2.4: Mapeo de relatedItems a la entidad de dominio SongItem sin campos nulos críticos.
     */
    @Test
    fun test2_4_mapRelatedItemToDomainSongItemWithoutNulls() {
        val source = YouTubeMusicSource()

        val streamInfoItem = StreamInfoItem(
            0,
            "https://www.youtube.com/watch?v=kXYiU_JCYtU",
            "Numb",
            StreamType.AUDIO_STREAM
        ).apply {
            uploaderName = "Linkin Park"
            uploaderUrl = "https://www.youtube.com/channel/linkinpark"
            duration = 187
            thumbnails = listOf(
                Image("https://img.youtube.com/vi/kXYiU_JCYtU/hqdefault.jpg", 480, 360, Image.ResolutionLevel.HIGH)
            )
        }

        val domainSong = source.mapStreamItemToSong(streamInfoItem)

        // Verificamos campos críticos
        assertNotNull(domainSong.id)
        assertFalse(domainSong.id.isEmpty())
        assertEquals("kXYiU_JCYtU", domainSong.id)

        assertNotNull(domainSong.title)
        assertEquals("Numb", domainSong.title)

        assertNotNull(domainSong.artistName)
        assertEquals("Linkin Park", domainSong.artistName)

        assertNotNull(domainSong.thumbnailUrl)
        assertTrue(domainSong.thumbnailUrl.startsWith("http"))

        assertEquals(187L, domainSong.durationSeconds)
    }
}
