package com.aura.music.service.audio

import android.content.Context
import com.aura.music.core.database.dao.SongDao
import com.aura.music.core.database.entity.SongEntity
import com.aura.music.data.extractor.YouTubeMusicSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class MediaDownloadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val youTubeMusicSource: YouTubeMusicSource,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        const val DEFAULT_MAX_SIZE_BYTES = 50L * 1024L * 1024L // 50 MB
    }

    open suspend fun downloadSong(
        song: SongEntity,
        maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val streamInfo = youTubeMusicSource.getStreamUrl(song.id)

            val request = Request.Builder()
                .url(streamInfo.url)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Error al descargar stream: código ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("Cuerpo de respuesta vacío")
            val contentLength = response.header("Content-Length")?.toLongOrNull() ?: body.contentLength()

            if (contentLength > maxSizeBytes) {
                throw IllegalStateException("El tamaño del archivo ($contentLength bytes) supera el límite permitido de $maxSizeBytes bytes")
            }

            val downloadDir = File(context.filesDir, "downloads").apply {
                if (!exists()) mkdirs()
            }
            val targetFile = File(downloadDir, "${song.id}.${streamInfo.format}")

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalDownloaded = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        totalDownloaded += bytesRead
                        if (totalDownloaded > maxSizeBytes) {
                            targetFile.delete()
                            throw IllegalStateException("El archivo descargado excedió el límite máximo de $maxSizeBytes bytes")
                        }
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            val absolutePath = targetFile.absolutePath
            songDao.updateLocalFilePath(song.id, absolutePath)
            absolutePath
        }
    }

    open suspend fun deleteDownload(songId: String): Boolean = withContext(Dispatchers.IO) {
        val song = songDao.getSongById(songId)
        if (song?.localFilePath != null) {
            val file = File(song.localFilePath)
            if (file.exists()) {
                file.delete()
            }
            songDao.updateLocalFilePath(songId, null)
            true
        } else {
            false
        }
    }

    open fun isDownloaded(song: SongEntity): Boolean {
        return !song.localFilePath.isNullOrBlank() && File(song.localFilePath).exists()
    }
}
