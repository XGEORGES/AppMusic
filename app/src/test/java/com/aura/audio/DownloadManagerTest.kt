package com.aura.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aura.music.core.database.dao.SongDao
import com.aura.music.core.database.entity.SongEntity
import com.aura.music.data.extractor.YouTubeMusicSource
import com.aura.music.data.model.AudioStreamInfo
import com.aura.music.data.model.SongItem
import com.aura.music.service.audio.AudioPlayerManager
import com.aura.music.service.audio.MediaDownloadManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadManagerTest {

    private lateinit var context: Context
    private lateinit var audioPlayerManager: AudioPlayerManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        audioPlayerManager = AudioPlayerManager(context)
    }

    /**
     * Test 6.1: Validar que si una canción tiene localFilePath no nulo,
     * el MediaSource/MediaItem apunte al archivo local y no a la red.
     */
    @Test
    fun test6_1_localFilePathRoutesToLocalSourceNotNetwork() {
        val localPath = "/data/user/0/com.aura.music/files/downloads/test_song.m4a"

        val downloadedSong = SongItem(
            id = "offline_123",
            title = "Offline Song",
            artistName = "Local Artist",
            thumbnailUrl = "thumb",
            localFilePath = localPath
        )

        val localMediaItem = audioPlayerManager.songToMediaItem(downloadedSong)
        assertNotNull("Local MediaItem no debe ser nulo", localMediaItem)
        val resolvedUri = localMediaItem.localConfiguration?.uri.toString()

        // Debe apuntar al archivo local
        assertEquals(localPath, resolvedUri)
        assertFalse("El stream no debe apuntar a la red si hay archivo local", resolvedUri.startsWith("http"))

        // Canción sin descarga: debe apuntar a la red
        val onlineSong = SongItem(
            id = "online_456",
            title = "Online Song",
            artistName = "Remote Artist",
            thumbnailUrl = "thumb",
            localFilePath = null
        )

        val onlineMediaItem = audioPlayerManager.songToMediaItem(onlineSong)
        val onlineUri = onlineMediaItem.localConfiguration?.uri.toString()
        assertTrue("Canción no descargada debe apuntar a la red", onlineUri.startsWith("http"))
        assertTrue(onlineUri.contains("online_456"))
    }

    /**
     * Test 6.2: Verificación del límite de tamaño del archivo descargado.
     */
    @Test
    fun test6_2_downloadSizeLimitEnforcement() = runBlocking {
        val mockSongDao = mockk<SongDao>(relaxed = true)
        val mockSource = mockk<YouTubeMusicSource>(relaxed = true)
        val mockHttpClient = mockk<OkHttpClient>()

        val downloadManager = MediaDownloadManager(
            context = context,
            songDao = mockSongDao,
            youTubeMusicSource = mockSource,
            okHttpClient = mockHttpClient
        )

        val song = SongEntity(
            id = "huge_audio",
            title = "Huge Podcast",
            artistName = "Host",
            thumbnailUrl = "thumb"
        )

        coEvery { mockSource.getStreamUrl("huge_audio") } returns AudioStreamInfo(
            url = "https://example.com/huge.m4a",
            format = "m4a",
            bitrate = 320000,
            durationSeconds = 7200
        )

        // Simulamos respuesta HTTP con Content-Length de 100 MB
        val hundredMbInBytes = 100L * 1024L * 1024L
        val fakeBody = "data".toByteArray().toResponseBody(null)

        val mockCall = mockk<Call>()
        val mockResponse = Response.Builder()
            .request(Request.Builder().url("https://example.com/huge.m4a").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Content-Length", hundredMbInBytes.toString())
            .body(fakeBody)
            .build()

        every { mockHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns mockResponse

        // Definimos un límite estricto de 20 MB
        val limit20Mb = 20L * 1024L * 1024L
        val result = downloadManager.downloadSong(song, maxSizeBytes = limit20Mb)

        assertTrue("La descarga debe fallar si excede el límite máximo de tamaño", result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(
            "El mensaje de error debe indicar la violación del límite de tamaño",
            exception?.message?.contains("límite") == true
        )
    }
}
