package com.aura.music.data.repository

import com.aura.music.core.database.dao.SongDao
import com.aura.music.core.database.entity.SongEntity
import com.aura.music.data.extractor.YouTubeMusicSource
import com.aura.music.data.model.SongItem
import com.aura.music.service.audio.AudioPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.media3.common.Player
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class PlayerRepository @Inject constructor(
    private val songDao: SongDao,
    private val audioPlayerManager: AudioPlayerManager,
    private val youTubeMusicSource: YouTubeMusicSource
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _currentPlayingSong = MutableStateFlow<SongEntity?>(null)
    val currentPlayingSong: StateFlow<SongEntity?> = _currentPlayingSong.asStateFlow()

    val isPlaying: StateFlow<Boolean> = audioPlayerManager.isPlaying
    val isLoading: StateFlow<Boolean> = audioPlayerManager.isLoading
    val playbackPosition: StateFlow<Long> = audioPlayerManager.currentPosition
    val duration: StateFlow<Long> = audioPlayerManager.duration
    val repeatMode: StateFlow<Int> = audioPlayerManager.repeatMode
    val shuffleModeEnabled: StateFlow<Boolean> = audioPlayerManager.shuffleModeEnabled
    val isInfiniteRadioEnabled: StateFlow<Boolean> = audioPlayerManager.isInfiniteRadioEnabled

    private var isFetchingRadio = false
    private var lastErrorSongId: String? = null
    private var errorRetryCount = 0

    val queue: StateFlow<List<SongEntity>> = audioPlayerManager.queue
        .map { list -> list.map { it.toEntity() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        // Conectar ResolvingDataSource de AudioPlayerManager con extracción bajo demanda y caché
        audioPlayerManager.streamResolver = { songId ->
            getValidCachedStream(songId) ?: run {
                try {
                    val streamInfo = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                        youTubeMusicSource.getStreamUrl(songId)
                    }
                    putStreamUrlCache(songId, streamInfo.url)
                    streamInfo.url
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }

        // Conectar botones de búsqueda de la notificación o dispositivos externos
        audioPlayerManager.onSeekNextRequested = { seekToNext() }
        audioPlayerManager.onSeekPreviousRequested = { seekToPrevious() }

        // Sincronizar canción actual desde AudioPlayerManager
        scope.launch {
            audioPlayerManager.currentSong.collect { songItem ->
                if (songItem != null) {
                    val dbSong = songDao.getSongById(songItem.id)
                    _currentPlayingSong.value = dbSong ?: songItem.toEntity()
                } else {
                    _currentPlayingSong.value = null
                }
            }
        }

        // Configurar callback de transición automática de canción
        audioPlayerManager.onSongTransitionTriggered = { songItem ->
            scope.launch {
                try {
                    songDao.updatePlayCount(songItem.id)
                } catch (_: Exception) {}

                // Disparar precarga de la siguiente canción de la cola activa (1 pista adelante)
                val currentQueue = queue.value
                val currentIndex = currentQueue.indexOfFirst { it.id == songItem.id }
                if (currentIndex != -1) {
                    preloadStreams(currentQueue, priorityIndex = currentIndex + 1)
                }
            }
        }

        // Configurar fin de canción (si no había siguiente pista en ExoPlayer)
        audioPlayerManager.onSongEndedTriggered = {
            if (audioPlayerManager.repeatMode.value == Player.REPEAT_MODE_ONE) {
                audioPlayerManager.seekTo(0L)
                audioPlayerManager.play()
            } else {
                seekToNext()
            }
        }

        // Configurar callback de error de reproducción para reintentar con stream fresco
        audioPlayerManager.onPlayerErrorTriggered = { songItem, error ->
            scope.launch {
                streamUrlCache.remove(songItem.id)
                if (lastErrorSongId == songItem.id) {
                    errorRetryCount++
                } else {
                    lastErrorSongId = songItem.id
                    errorRetryCount = 1
                }

                if (errorRetryCount <= 1 && songItem.localFilePath.isNullOrBlank()) {
                    playSong(songItem.toEntity())
                } else {
                    errorRetryCount = 0
                    lastErrorSongId = null
                    audioPlayerManager.cancelLoading()
                    seekToNext()
                }
            }
        }

        // Configurar callback de Radio Infinita
        audioPlayerManager.onInfiniteRadioTriggered = { lastSongId ->
            if (!isFetchingRadio) {
                isFetchingRadio = true
                scope.launch(Dispatchers.IO) {
                    try {
                        val related = youTubeMusicSource.getSongRadio(lastSongId)
                        if (related.isNotEmpty()) {
                            val currentIds = audioPlayerManager.queue.value.map { it.id }.toSet()
                            val newSongs = related.filter { it.id !in currentIds }
                            if (newSongs.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    val previousItemCount = audioPlayerManager.player.mediaItemCount
                                    audioPlayerManager.addToQueue(newSongs)
                                    if (audioPlayerManager.player.playbackState == Player.STATE_ENDED) {
                                        val newFirstIndex = previousItemCount
                                        val currentQ = audioPlayerManager.queue.value
                                        if (newFirstIndex in currentQ.indices) {
                                            playSong(currentQ[newFirstIndex].toEntity())
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isFetchingRadio = false
                    }
                }
            }
        }
    }

    open suspend fun toggleFavorite(songId: String) {
        val current = songDao.getSongById(songId)
        val newFav = if (current != null) !current.isFavorite else true

        songDao.updateFavorite(songId, newFav)

        val updatedCurrent = _currentPlayingSong.value
        if (updatedCurrent != null && updatedCurrent.id == songId) {
            _currentPlayingSong.value = updatedCurrent.copy(isFavorite = newFav)
        }
    }

    data class CachedStream(
        val url: String,
        val fetchedAtElapsedMs: Long = try {
            android.os.SystemClock.elapsedRealtime()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    ) {
        fun isExpired(): Boolean {
            val now = try {
                android.os.SystemClock.elapsedRealtime()
            } catch (_: Throwable) {
                System.currentTimeMillis()
            }
            // Una URL de YouTube es válida por al menos 4 a 6 horas.
            // Consideramos seguro reutilizarla hasta por 3.5 horas de tiempo activo del dispositivo.
            return (now - fetchedAtElapsedMs) > (3.5 * 60 * 60 * 1000L).toLong()
        }
    }

    private var playJob: kotlinx.coroutines.Job? = null
    private var prefetchJob: kotlinx.coroutines.Job? = null
    private val streamUrlCache = java.util.concurrent.ConcurrentHashMap<String, CachedStream>()

    fun getValidCachedStream(songId: String): String? {
        val cached = streamUrlCache[songId] ?: return null
        if (cached.isExpired()) {
            streamUrlCache.remove(songId)
            return null
        }
        return cached.url
    }

    fun putStreamUrlCache(songId: String, url: String) {
        if (url.isNotBlank()) {
            streamUrlCache[songId] = CachedStream(url)
        }
    }

    fun preloadStreams(songs: List<SongEntity>, priorityIndex: Int = 0) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            if (songs.isEmpty()) return@launch
            // Precargar únicamente la siguiente canción inmediata (1 pista adelante)
            // Evita vencimiento anticipado de URLs y saturación/rate-limiting en YouTube
            val nextIndex = priorityIndex
            if (nextIndex in songs.indices) {
                val nextSong = songs[nextIndex]
                if (nextSong.localFilePath.isNullOrBlank() && getValidCachedStream(nextSong.id) == null) {
                    try {
                        val stream = youTubeMusicSource.getStreamUrl(nextSong.id)
                        putStreamUrlCache(nextSong.id, stream.url)
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            audioPlayerManager.updateMediaItemStream(nextSong.id, stream.url)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    open fun playSong(song: SongEntity) {
        _currentPlayingSong.value = song
        playJob?.cancel()

        if (song.id.startsWith("PL") || song.id.startsWith("PL_")) {
            val playlistId = if (song.id.startsWith("PL_")) song.id.removePrefix("PL_") else song.id
            playJob = scope.launch {
                try {
                    val extracted = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        youTubeMusicSource.extractPlaylist(playlistId)
                    }
                    if (extracted.songs.isNotEmpty()) {
                        val songEntities = extracted.songs.map { it.toEntity() }
                        setQueue(songEntities, startIndex = 0)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return
        }

        val item = SongItem.fromEntity(song)

        // Si es archivo local descargado, reproducir de inmediato sin peticiones de red
        if (!song.localFilePath.isNullOrBlank()) {
            audioPlayerManager.playStream(item, song.localFilePath)
            scope.launch {
                try { songDao.updatePlayCount(song.id) } catch (_: Exception) {}
            }
            return
        }

        // Si el stream ya está en memoria caché y es válido (no expirado), reproducir de inmediato
        val cachedUrl = getValidCachedStream(song.id)
        if (!cachedUrl.isNullOrBlank()) {
            audioPlayerManager.prepareForLoading(item, resetPosition = false)
            audioPlayerManager.playStream(item, cachedUrl)
            scope.launch {
                try { songDao.updatePlayCount(song.id) } catch (_: Exception) {}
                val currentQueue = queue.value
                val currentIndex = currentQueue.indexOfFirst { it.id == song.id }
                if (currentIndex != -1) {
                    preloadStreams(currentQueue, priorityIndex = currentIndex + 1)
                }
            }
            return
        }

        // Si la canción ya forma parte de la cola cargada en ExoPlayer, seek directo a su índice
        val q = audioPlayerManager.queue.value
        val queueIndex = q.indexOfFirst { it.id == song.id }
        if (queueIndex >= 0 && queueIndex < audioPlayerManager.player.mediaItemCount) {
            audioPlayerManager.prepareForLoading(item)
            if (audioPlayerManager.player.currentMediaItemIndex != queueIndex) {
                audioPlayerManager.player.seekToDefaultPosition(queueIndex)
            } else {
                audioPlayerManager.player.seekTo(queueIndex, 0L)
            }
            audioPlayerManager.player.prepare()
            audioPlayerManager.ensureServiceStarted()
            audioPlayerManager.player.play()
            scope.launch {
                try { songDao.updatePlayCount(song.id) } catch (_: Exception) {}
                preloadStreams(queue.value, priorityIndex = queueIndex + 1)
            }
            return
        }

        // Cache-miss y no en cola previa: Silenciar inmediatamente la canción previa y activar estado de carga en la UI
        audioPlayerManager.prepareForLoading(item)

        playJob = scope.launch {
            try {
                songDao.updatePlayCount(song.id)
            } catch (_: Exception) {}

            try {
                val streamInfo = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    youTubeMusicSource.getStreamUrl(song.id)
                }
                putStreamUrlCache(song.id, streamInfo.url)
                audioPlayerManager.playStream(item, streamInfo.url)

                // Disparar precarga de la siguiente canción de la cola activa (1 pista adelante)
                val currentQueue = queue.value
                val currentIndex = currentQueue.indexOfFirst { it.id == song.id }
                if (currentIndex != -1) {
                    preloadStreams(currentQueue, priorityIndex = currentIndex + 1)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                audioPlayerManager.cancelLoading()
                // Si la resolución falló (video restringido o caído), saltar automáticamente a la siguiente pista
                seekToNext()
            }
        }
    }

    open fun setQueue(songs: List<SongEntity>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val items = songs.map { SongItem.fromEntity(it) }
        audioPlayerManager.setQueue(items, startIndex, autoPlay = true)
        val initialSong = songs.getOrNull(startIndex) ?: songs.first()
        _currentPlayingSong.value = initialSong
        scope.launch {
            try { songDao.updatePlayCount(initialSong.id) } catch (_: Exception) {}
        }
        preloadStreams(songs, priorityIndex = startIndex + 1)
    }

    open fun playNext(song: SongEntity) {
        audioPlayerManager.addNextToQueue(SongItem.fromEntity(song))
    }

    open fun playNext(songs: List<SongEntity>) {
        val items = songs.map { SongItem.fromEntity(it) }
        audioPlayerManager.addNextToQueue(items)
    }

    open fun addToQueue(song: SongEntity) {
        audioPlayerManager.addToQueue(SongItem.fromEntity(song))
    }

    open fun startMix(song: SongEntity) {
        playSong(song)
        scope.launch(Dispatchers.IO) {
            try {
                val related = youTubeMusicSource.getSongRadio(song.id)
                if (related.isNotEmpty()) {
                    audioPlayerManager.addToQueue(related)
                }
            } catch (_: Exception) {}
        }
    }

    open fun togglePlayPause() {
        if (audioPlayerManager.isLoading.value) {
            return
        }

        if (audioPlayerManager.isPlaying.value) {
            audioPlayerManager.pause()
            return
        }

        val currentSong = _currentPlayingSong.value
            ?: audioPlayerManager.currentSong.value?.toEntity()
            ?: queue.value.firstOrNull()
            ?: return

        val player = audioPlayerManager.player
        val currentMediaItem = player.currentMediaItem
        val currentUri = currentMediaItem?.localConfiguration?.uri?.toString() ?: ""
        val isPlaceholder = AudioPlayerManager.isPlaceholderUrl(currentUri)

        val isReadyToResume = player.playbackState == Player.STATE_READY &&
                !isPlaceholder &&
                player.playerError == null &&
                (currentMediaItem?.mediaId == currentSong.id)

        if (isReadyToResume) {
            audioPlayerManager.ensureServiceStarted()
            player.play()
        } else {
            val currentPos = audioPlayerManager.currentPosition.value
            val totalDuration = audioPlayerManager.duration.value
            val shouldRestorePosition = currentPos > 1000L && (totalDuration <= 0L || currentPos < totalDuration - 2000L)

            playSong(currentSong)

            if (shouldRestorePosition) {
                scope.launch {
                    audioPlayerManager.isPlaying.collect { playing ->
                        if (playing) {
                            audioPlayerManager.seekTo(currentPos)
                            return@collect
                        }
                    }
                }
            }
        }
    }

    open fun seekTo(positionMs: Long) {
        audioPlayerManager.seekTo(positionMs)
    }

    open fun seekToNext() {
        val q = queue.value
        val current = currentPlayingSong.value
        val nextIndex = if (current != null) q.indexOfFirst { it.id == current.id } + 1 else 0
        if (nextIndex in q.indices) {
            playSong(q[nextIndex])
        } else {
            // Fin de la cola: volver al inicio de la playlist (loop continuo)
            if (q.isNotEmpty()) {
                playSong(q.first())
            } else {
                audioPlayerManager.seekToNext()
            }
        }
    }

    open fun seekToPrevious() {
        val currentPos = audioPlayerManager.currentPosition.value
        // Si han transcurrido más de 3 segundos, reiniciar la pista actual
        if (currentPos > 3000L) {
            audioPlayerManager.seekTo(0L)
            return
        }

        val q = queue.value
        val current = currentPlayingSong.value
        val prevIndex = if (current != null) q.indexOfFirst { it.id == current.id } - 1 else 0
        if (prevIndex in q.indices) {
            playSong(q[prevIndex])
        } else {
            // Si está en la primera canción, envolver al final de la playlist
            if (q.isNotEmpty()) {
                playSong(q.last())
            } else {
                audioPlayerManager.seekToPrevious()
            }
        }
    }

    open fun setRepeatMode(mode: Int) {
        audioPlayerManager.setRepeatMode(mode)
    }

    open fun setShuffleModeEnabled(enabled: Boolean) {
        audioPlayerManager.setShuffleModeEnabled(enabled)
    }

    open fun setInfiniteRadioEnabled(enabled: Boolean) {
        audioPlayerManager.setInfiniteRadioEnabled(enabled)
    }
}
