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
import kotlinx.coroutines.flow.first
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
    private val youTubeMusicSource: YouTubeMusicSource,
    private val djAuraRepositoryLazy: dagger.Lazy<DjAuraRepository>
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

    private val _isDjAuraMode = MutableStateFlow(false)
    val isDjAuraMode: StateFlow<Boolean> = _isDjAuraMode.asStateFlow()

    private val _quickSkipCount = MutableStateFlow(0)
    val quickSkipCount: StateFlow<Int> = _quickSkipCount.asStateFlow()

    private var isFetchingRadio = false
    private var lastErrorSongId: String? = null
    private var errorRetryCount = 0

    var onSongSkipped: ((SongEntity) -> Unit)? = null

    // ─── DJ Aura: extensión automática desde scope del Repository ─────────────
    // La extensión vive aquí (no en el ViewModel) para que funcione
    // incluso cuando MIUI destruye la Activity/ViewModel en background.
    private var isExtendingDj = false
    private var extendDjStartTimeMs = 0L

    sealed interface DjExtendEvent {
        data object Loading : DjExtendEvent
        data class SongsAdded(
            val songs: List<SongEntity>,
            val shoutout: String,
            val comment: String,
            val vibeTag: String
        ) : DjExtendEvent
        data class Error(val message: String) : DjExtendEvent
    }

    private val _djExtendEvent = kotlinx.coroutines.flow.MutableSharedFlow<DjExtendEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val djExtendEvent: kotlinx.coroutines.flow.SharedFlow<DjExtendEvent> = _djExtendEvent

    /**
     * Dispara la extensión automática de la sesión DJ Aura.
     * Ejecuta en el scope del Repository (independiente del ViewModel).
     * Si ya hay una extensión en curso y no ha excedido el timeout, no hace nada.
     */
    fun triggerDjExtend() {
        if (isExtendingDj) {
            // Guard: si la flag lleva más de 90 segundos en true, resetear
            val elapsed = System.currentTimeMillis() - extendDjStartTimeMs
            if (elapsed < 90_000L) return
            isExtendingDj = false
        }

        isExtendingDj = true
        extendDjStartTimeMs = System.currentTimeMillis()

        scope.launch {
            try {
                val djRepo = djAuraRepositoryLazy.get()
                _djExtendEvent.tryEmit(DjExtendEvent.Loading)

                djRepo.extendMix().collect { progress ->
                    when (progress) {
                        is DjProgress.Loading -> {
                            // Loading ya emitido
                        }
                        is DjProgress.FirstSongReady -> {
                            val currentQueueIds = queue.value.map { it.id }.toSet()
                            if (progress.firstSong.id !in currentQueueIds) {
                                addToQueue(progress.firstSong)
                            }
                            // Si el player terminó la cola, reproducir esta canción de inmediato
                            if (audioPlayerManager.player.playbackState == Player.STATE_ENDED) {
                                playSong(progress.firstSong)
                            }
                        }
                        is DjProgress.FullMixReady -> {
                            val currentQueueIds = queue.value.map { it.id }.toSet()
                            val newSongs = progress.songs.filter { it.id !in currentQueueIds }
                            if (newSongs.isNotEmpty()) {
                                addToQueue(newSongs)
                                // Si el player terminó antes de que llegaran las canciones, reproducir la primera nueva
                                if (audioPlayerManager.player.playbackState == Player.STATE_ENDED ||
                                    (!audioPlayerManager.isPlaying.value && !audioPlayerManager.userInitiatedPause)
                                ) {
                                    playSong(newSongs.first())
                                }
                            }
                            _djExtendEvent.tryEmit(
                                DjExtendEvent.SongsAdded(
                                    songs = newSongs,
                                    shoutout = progress.shoutout,
                                    comment = progress.comment,
                                    vibeTag = progress.vibeTag
                                )
                            )
                            isExtendingDj = false
                        }
                        is DjProgress.Error -> {
                            _djExtendEvent.tryEmit(DjExtendEvent.Error(progress.message))
                            isExtendingDj = false
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _djExtendEvent.tryEmit(DjExtendEvent.Error(e.message ?: "Error al extender sesión DJ"))
                isExtendingDj = false
            }
        }
    }

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

                    if (_isDjAuraMode.value && currentQueue.size > 1 && currentIndex >= currentQueue.size - 1) {
                        triggerDjExtend()
                    }
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

        // Configurar callback de Radio Infinita (desactivada en modo DJ Aura para mantener coherencia temática)
        audioPlayerManager.onInfiniteRadioTriggered = { lastSongId ->
            if (_isDjAuraMode.value) {
                if (queue.value.size > 1) {
                    triggerDjExtend()
                }
            } else if (!isFetchingRadio) {
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
    private var positionRestoreJob: kotlinx.coroutines.Job? = null
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
        positionRestoreJob?.cancel()
        playJob?.cancel()

        if (song.id.startsWith("PL") || song.id.startsWith("PL_") || song.id.startsWith("OLAK5uy_") || song.id.startsWith("VL")) {
            val rawId = if (song.id.startsWith("PL_")) song.id.removePrefix("PL_") else song.id
            val playlistId = if (rawId.startsWith("VL")) rawId.removePrefix("VL") else rawId
            playJob = scope.launch {
                try {
                    audioPlayerManager.prepareForLoading(SongItem.fromEntity(song), resetPosition = true)
                    val extracted = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        youTubeMusicSource.extractPlaylist(playlistId)
                    }
                    if (extracted.songs.isNotEmpty()) {
                        val songEntities = extracted.songs.map { it.toEntity() }
                        setQueue(songEntities, startIndex = 0)
                    } else {
                        audioPlayerManager.cancelLoading()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    audioPlayerManager.cancelLoading()
                }
            }
            return
        }

        val previousSong = _currentPlayingSong.value
        if (_isDjAuraMode.value && previousSong != null && previousSong.id != song.id) {
            val playedDuration = playbackPosition.value
            if (playedDuration < 60_000L) {
                _quickSkipCount.value += 1
                onSongSkipped?.invoke(previousSong)
            } else {
                _quickSkipCount.value = 0
            }
        }

        _currentPlayingSong.value = song

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

            // Si la canción ya fue descargada localmente, reproducir directo del archivo sin usar red
            try {
                val localSong = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    songDao.getSongById(song.id)
                }
                if (localSong != null && !localSong.localFilePath.isNullOrBlank()) {
                    val file = java.io.File(localSong.localFilePath)
                    if (file.exists()) {
                        _currentPlayingSong.value = localSong
                        audioPlayerManager.playStream(SongItem.fromEntity(localSong), localSong.localFilePath)
                        return@launch
                    }
                }
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

    open fun replaceUpcomingQueue(songs: List<SongEntity>) {
        val items = songs.map { SongItem.fromEntity(it) }
        audioPlayerManager.replaceUpcomingQueue(items)
        preloadStreams(songs, priorityIndex = 0)
    }

    open fun addToQueue(song: SongEntity) {
        audioPlayerManager.addToQueue(SongItem.fromEntity(song))
    }

    open fun addToQueue(songs: List<SongEntity>) {
        if (songs.isEmpty()) return
        val items = songs.map { SongItem.fromEntity(it) }
        audioPlayerManager.addToQueue(items)
        preloadStreams(songs, priorityIndex = 0)
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

    open fun play() {
        if (audioPlayerManager.isPlaying.value) {
            return
        }

        val currentSong = _currentPlayingSong.value
            ?: audioPlayerManager.currentSong.value?.toEntity()
            ?: queue.value.firstOrNull()
            ?: return

        val player = audioPlayerManager.player
        val currentMediaItem = player.currentMediaItem

        val isReadyToResume = player.playbackState == Player.STATE_READY &&
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
                positionRestoreJob?.cancel()
                positionRestoreJob = scope.launch {
                    audioPlayerManager.isPlaying.first { it }
                    audioPlayerManager.seekTo(currentPos)
                }
            }
        }
    }

    open fun pause() {
        positionRestoreJob?.cancel()
        audioPlayerManager.pause()
    }

    open fun togglePlayPause() {
        if (audioPlayerManager.isLoading.value) {
            return
        }

        if (audioPlayerManager.isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    open fun seekTo(positionMs: Long) {
        audioPlayerManager.seekTo(positionMs)
    }

    open fun seekToNext() {
        val q = queue.value
        val current = currentPlayingSong.value

        if (_isDjAuraMode.value && current != null) {
            val playedDuration = playbackPosition.value
            if (playedDuration < 60_000L) {
                _quickSkipCount.value += 1
                onSongSkipped?.invoke(current)
            } else {
                _quickSkipCount.value = 0
            }
        }

        val nextIndex = if (current != null) q.indexOfFirst { it.id == current.id } + 1 else 0
        if (nextIndex in q.indices) {
            playSong(q[nextIndex])
            if (_isDjAuraMode.value && q.size > 1 && nextIndex >= q.size - 1) {
                triggerDjExtend()
            }
        } else if (_isDjAuraMode.value) {
            triggerDjExtend()
        } else if (audioPlayerManager.repeatMode.value == androidx.media3.common.Player.REPEAT_MODE_ALL && q.isNotEmpty()) {
            // Repetir toda la lista desde el principio únicamente con REPEAT_MODE_ALL
            playSong(q.first())
        } else if (audioPlayerManager.isInfiniteRadioEnabled.value && !_isDjAuraMode.value && current != null) {
            // Fin de la cola con Radio Infinita: traer canciones recomendadas y continuar sin repetir
            audioPlayerManager.prepareForLoading(SongItem.fromEntity(current), resetPosition = false)
            scope.launch(Dispatchers.IO) {
                try {
                    val related = youTubeMusicSource.getSongRadio(current.id)
                    val currentIds = audioPlayerManager.queue.value.map { it.id }.toSet()
                    val newSongs = related.filter { it.id !in currentIds }
                    if (newSongs.isNotEmpty()) {
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            audioPlayerManager.addToQueue(newSongs)
                            val firstNewSong = newSongs.first().toEntity()
                            playSong(firstNewSong)
                        }
                    } else {
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            audioPlayerManager.cancelLoading()
                            audioPlayerManager.pause()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        audioPlayerManager.cancelLoading()
                        audioPlayerManager.pause()
                    }
                }
            }
        } else {
            // Fin de cola sin bucles
            audioPlayerManager.pause()
            audioPlayerManager.seekTo(0L)
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
        } else if (audioPlayerManager.repeatMode.value == androidx.media3.common.Player.REPEAT_MODE_ALL && q.isNotEmpty()) {
            // Si está en la primera canción y repite todo, envolver al final de la playlist
            playSong(q.last())
        } else {
            audioPlayerManager.seekTo(0L)
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

    open fun setDjAuraMode(enabled: Boolean) {
        _isDjAuraMode.value = enabled
        if (!enabled) {
            _quickSkipCount.value = 0
            isExtendingDj = false
        }
    }

    open fun resetQuickSkipCount() {
        _quickSkipCount.value = 0
    }
}
