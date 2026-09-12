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

    val queue: StateFlow<List<SongEntity>> = audioPlayerManager.queue
        .map { list -> list.map { it.toEntity() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
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

                // Si la canción no es local, siempre asegurar que ExoPlayer tenga el stream real
                if (songItem.localFilePath.isNullOrBlank()) {
                    val cachedUrl = streamUrlCache[songItem.id]
                    if (!cachedUrl.isNullOrBlank()) {
                        // Ya tenemos la URL: actualizar inmediatamente en el Main thread
                        audioPlayerManager.updateMediaItemStream(songItem.id, cachedUrl)
                    } else {
                        // Resolver el stream en background
                        scope.launch(Dispatchers.IO) {
                            try {
                                val streamInfo = youTubeMusicSource.getStreamUrl(songItem.id)
                                streamUrlCache[songItem.id] = streamInfo.url
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    audioPlayerManager.updateMediaItemStream(songItem.id, streamInfo.url)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }

                // Disparar precarga de las siguientes canciones de la cola activa
                val currentQueue = queue.value
                val currentIndex = currentQueue.indexOfFirst { it.id == songItem.id }
                if (currentIndex != -1) {
                    preloadStreams(currentQueue, priorityIndex = currentIndex + 1)
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
                                    audioPlayerManager.addToQueue(newSongs)
                                    if (audioPlayerManager.player.playbackState == Player.STATE_ENDED) {
                                        audioPlayerManager.player.prepare()
                                        audioPlayerManager.player.play()
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

    private var playJob: kotlinx.coroutines.Job? = null
    private var prefetchJob: kotlinx.coroutines.Job? = null
    private val streamUrlCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun preloadStreams(songs: List<SongEntity>, priorityIndex: Int = 0) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            if (songs.isEmpty()) return@launch
            // Primero pre-resolver canciones prioritarias (la seleccionada, la siguiente, la anterior)
            val prioritizedList = mutableListOf<SongEntity>()
            if (priorityIndex in songs.indices) prioritizedList.add(songs[priorityIndex])
            if (priorityIndex + 1 in songs.indices) prioritizedList.add(songs[priorityIndex + 1])
            if (priorityIndex - 1 in songs.indices) prioritizedList.add(songs[priorityIndex - 1])
            if (priorityIndex + 2 in songs.indices) prioritizedList.add(songs[priorityIndex + 2])

            // Añadir el resto de la lista (hasta un máximo prudente de 8 canciones por delante)
            val remaining = songs.filter { song -> !prioritizedList.any { it.id == song.id } }.take(6)
            prioritizedList.addAll(remaining)

            for (song in prioritizedList) {
                if (song.localFilePath.isNullOrBlank() && !streamUrlCache.containsKey(song.id)) {
                    try {
                        val stream = youTubeMusicSource.getStreamUrl(song.id)
                        streamUrlCache[song.id] = stream.url
                        // updateMediaItemStream llama métodos de ExoPlayer: debe ejecutarse en Main thread
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            audioPlayerManager.updateMediaItemStream(song.id, stream.url)
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

        // Si el stream ya está en memoria caché, reproducir de inmediato (0s de retardo)
        val cachedUrl = streamUrlCache[song.id]
        if (!cachedUrl.isNullOrBlank()) {
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

        // Cache-miss: Silenciar inmediatamente la canción previa y activar estado de carga en la UI
        audioPlayerManager.prepareForLoading(item)

        playJob = scope.launch {
            try {
                songDao.updatePlayCount(song.id)
            } catch (_: Exception) {}

            try {
                val streamInfo = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    youTubeMusicSource.getStreamUrl(song.id)
                }
                streamUrlCache[song.id] = streamInfo.url
                audioPlayerManager.playStream(item, streamInfo.url)

                // Disparar precarga de las siguientes canciones de la cola activa
                val currentQueue = queue.value
                val currentIndex = currentQueue.indexOfFirst { it.id == song.id }
                if (currentIndex != -1) {
                    preloadStreams(currentQueue, priorityIndex = currentIndex + 1)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                audioPlayerManager.cancelLoading()
            }
        }
    }

    open fun setQueue(songs: List<SongEntity>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val items = songs.map { song ->
            val cached = streamUrlCache[song.id]
            if (!cached.isNullOrBlank() && song.localFilePath.isNullOrBlank()) {
                SongItem.fromEntity(song) // audioPlayerManager can use cached stream
            } else {
                SongItem.fromEntity(song)
            }
        }
        audioPlayerManager.setQueue(items, startIndex, autoPlay = false)
        val initialSong = songs.getOrNull(startIndex) ?: songs.first()
        playSong(initialSong)
        // Precargar en segundo plano las siguientes de la cola
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
        audioPlayerManager.togglePlayPause()
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
            audioPlayerManager.seekToNext()
        }
    }

    open fun seekToPrevious() {
        val q = queue.value
        val current = currentPlayingSong.value
        val prevIndex = if (current != null) q.indexOfFirst { it.id == current.id } - 1 else 0
        if (prevIndex in q.indices) {
            playSong(q[prevIndex])
        } else {
            audioPlayerManager.seekToPrevious()
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
