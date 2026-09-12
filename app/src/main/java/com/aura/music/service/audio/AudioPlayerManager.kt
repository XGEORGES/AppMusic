package com.aura.music.service.audio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.aura.music.core.cache.MediaCacheManager
import com.aura.music.data.model.SongItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(UnstableApi::class)
class AudioPlayerManager(
    val context: Context,
    val player: Player = run {
        val cacheFactory = MediaCacheManager.createCacheDataSourceFactory(context)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(cacheFactory)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1500, // minBufferMs (>= bufferForPlaybackAfterRebufferMs)
                25000, // maxBufferMs
                500,  // bufferForPlaybackMs (empieza en 0.5s de buffer)
                1000  // bufferForPlaybackAfterRebufferMs (1.0s)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .setLooper(Looper.getMainLooper())
            .build()
    }
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _currentSong = MutableStateFlow<SongItem?>(null)
    val currentSong: StateFlow<SongItem?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _queue = MutableStateFlow<List<SongItem>>(emptyList())
    val queue: StateFlow<List<SongItem>> = _queue.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _isInfiniteRadioEnabled = MutableStateFlow(true)
    val isInfiniteRadioEnabled: StateFlow<Boolean> = _isInfiniteRadioEnabled.asStateFlow()

    fun setInfiniteRadioEnabled(enabled: Boolean) {
        _isInfiniteRadioEnabled.value = enabled
    }

    var onInfiniteRadioTriggered: ((lastSongId: String) -> Unit)? = null
    var onSongTransitionTriggered: ((song: SongItem) -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                _isLoading.value = false
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _isLoading.value = true
                }
                Player.STATE_READY -> {
                    _duration.value = player.duration.coerceAtLeast(0L)
                    _isLoading.value = false
                }
                Player.STATE_ENDED -> {
                    _isLoading.value = false
                    checkInfiniteRadioTrigger(isEnd = true)
                }
                Player.STATE_IDLE -> {
                    _isLoading.value = false
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                return
            }
            val index = player.currentMediaItemIndex
            val currentList = _queue.value
            if (index in currentList.indices) {
                val song = currentList[index]
                _currentSong.value = song
                // Si el item actual tiene una URL placeholder (no un stream real),
                // pausar (NO stop) antes de que ExoPlayer intente cargarla y falle.
                // pause() mantiene el foreground y los locks de red activos (stop() los destruye).
                val uri = mediaItem?.localConfiguration?.uri?.toString() ?: ""
                val isPlaceholder = uri.startsWith("https://youtube.com/watch?v=") ||
                        uri.startsWith("http://youtube.com/watch?v=")
                if (isPlaceholder && song.localFilePath.isNullOrBlank()) {
                    player.pause()
                }
                onSongTransitionTriggered?.invoke(song)
            }
            checkInfiniteRadioTrigger(isEnd = false)
        }

        override fun onPlayerError(error: PlaybackException) {
            // Error de reproducción: el stream placeholder falló.
            // Usar pause() (NO stop) para mantener el foreground y los locks de red
            // activos mientras onSongTransitionTriggered resuelve el stream real.
            _isLoading.value = false
            val index = player.currentMediaItemIndex
            val currentList = _queue.value
            if (index in currentList.indices) {
                val song = currentList[index]
                player.pause()
                onSongTransitionTriggered?.invoke(song)
            }
        }

        override fun onRepeatModeChanged(newRepeatMode: Int) {
            _repeatMode.value = newRepeatMode
        }

        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            _shuffleModeEnabled.value = enabled
        }
    }

    fun prepareForLoading(song: SongItem) {
        // Pausar inmediatamente la canción anterior para evitar desincronización de audio
        player.pause()
        _currentSong.value = song
        _currentPosition.value = 0L
        _duration.value = if (song.durationSeconds > 0) song.durationSeconds * 1000L else 0L
        _isLoading.value = true
    }

    fun cancelLoading() {
        _isLoading.value = false
    }

    init {
        player.addListener(playerListener)
        startPositionTracking()
    }

    fun checkInfiniteRadioTrigger(isEnd: Boolean) {
        if (!_isInfiniteRadioEnabled.value) return
        val currentList = _queue.value
        if (currentList.isEmpty()) return

        val currentIndex = player.currentMediaItemIndex
        val remainingSongs = currentList.size - 1 - currentIndex

        // Dispara la Radio Infinita si faltan menos de 2 canciones o al terminar la lista
        if (remainingSongs < 2 || (isEnd && currentIndex >= currentList.size - 1)) {
            val lastSong = currentList.lastOrNull()
            if (lastSong != null) {
                onInfiniteRadioTriggered?.invoke(lastSong.id)
            }
        }
    }

    fun setQueue(songs: List<SongItem>, startIndex: Int = 0, autoPlay: Boolean = true) {
        _queue.value = songs
        val mediaItems = songs.map { songToMediaItem(it) }
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        if (startIndex in songs.indices) {
            _currentSong.value = songs[startIndex]
        }
        if (autoPlay) {
            ensureServiceStarted()
            player.play()
        }
    }

    fun addToQueue(song: SongItem) {
        addToQueue(listOf(song))
    }

    fun addNextToQueue(song: SongItem) {
        addNextToQueue(listOf(song))
    }

    fun addNextToQueue(songs: List<SongItem>) {
        if (songs.isEmpty()) return
        val currentList = _queue.value.toMutableList()
        if (currentList.isEmpty()) {
            setQueue(songs, 0, autoPlay = true)
            return
        }
        val currentIndex = player.currentMediaItemIndex
        val insertIndex = if (currentIndex in currentList.indices) currentIndex + 1 else currentList.size
        currentList.addAll(insertIndex, songs)
        _queue.value = currentList
        val mediaItems = songs.map { songToMediaItem(it) }
        player.addMediaItems(insertIndex, mediaItems)
    }

    fun addToQueue(songs: List<SongItem>) {
        val updated = _queue.value.toMutableList()
        updated.addAll(songs)
        _queue.value = updated
        val mediaItems = songs.map { songToMediaItem(it) }
        player.addMediaItems(mediaItems)
    }

    fun removeQueueItem(index: Int) {
        val currentList = _queue.value.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            _queue.value = currentList
            player.removeMediaItem(index)
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val currentList = _queue.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            val item = currentList.removeAt(fromIndex)
            currentList.add(toIndex, item)
            _queue.value = currentList
            player.moveMediaItem(fromIndex, toIndex)
        }
    }

    fun ensureServiceStarted() {
        try {
            val intent = android.content.Intent(context, PlaybackService::class.java)
            context.startService(intent)
        } catch (_: Exception) {
            // Android gestiona el enlace mediante MediaSession automáticamente
        }
    }

    fun play() {
        ensureServiceStarted()
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun togglePlayPause() {
        if (_isLoading.value) {
            // Si está cargando un nuevo stream, ignorar para no reanudar la pista anterior
            return
        }
        if (player.isPlaying) {
            player.pause()
        } else {
            ensureServiceStarted()
            player.play()
        }
    }

    fun seekToNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }

    fun seekToPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun setRepeatMode(mode: Int) {
        player.repeatMode = mode
        _repeatMode.value = mode
    }

    fun setShuffleModeEnabled(enabled: Boolean) {
        player.shuffleModeEnabled = enabled
        _shuffleModeEnabled.value = enabled
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        player.removeListener(playerListener)
        player.release()
        _isLoading.value = false
    }

    private fun startPositionTracking() {
        mainHandler.post(object : Runnable {
            override fun run() {
                if (player.isPlaying) {
                    _currentPosition.value = player.currentPosition
                }
                mainHandler.postDelayed(this, 500)
            }
        })
    }

    fun songToMediaItem(song: SongItem, streamUrl: String? = null): MediaItem {
        val uri = when {
            !song.localFilePath.isNullOrBlank() -> Uri.parse(song.localFilePath)
            !streamUrl.isNullOrBlank() -> Uri.parse(streamUrl)
            else -> Uri.parse("https://youtube.com/watch?v=${song.id}")
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artistName)
            .setArtworkUri(Uri.parse(song.thumbnailUrl))
            .build()

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun updateMediaItemStream(songId: String, streamUrl: String) {
        val currentQueue = _queue.value
        val index = currentQueue.indexOfFirst { it.id == songId }
        if (index < 0 || index >= player.mediaItemCount) return

        // Si el item ya tiene exactamente esta URL, no hacer nada.
        // Esto evita: 1) la mini-pausa cuando playStream() ya cargó la URL real,
        //             2) loops infinitos (replaceMediaItem puede disparar onMediaItemTransition).
        val existingUri = player.getMediaItemAt(index).localConfiguration?.uri?.toString() ?: ""
        if (existingUri == streamUrl) return

        val song = currentQueue[index]
        val updatedMediaItem = songToMediaItem(song, streamUrl)
        val isCurrent = player.currentMediaItemIndex == index

        val isCurrentPlaceholder = existingUri.startsWith("https://youtube.com/watch?v=") ||
                existingUri.startsWith("http://youtube.com/watch?v=")

        if (isCurrent) {
            if (isCurrentPlaceholder) {
                // El item tiene URL placeholder → reemplazar y arrancar desde 0
                player.replaceMediaItem(index, updatedMediaItem)
                player.seekTo(index, 0L)
                if (player.playbackState == Player.STATE_IDLE) {
                    player.prepare()
                }
                ensureServiceStarted()
                player.play()
            } else {
                // Ya tiene URL real → actualizar silenciosamente sin interrumpir reproducción
                player.replaceMediaItem(index, updatedMediaItem)
            }
        } else {
            // Item futuro en la cola → solo actualizar la URL
            player.replaceMediaItem(index, updatedMediaItem)
        }
    }

    fun playStream(song: SongItem, streamUrl: String) {
        ensureServiceStarted()
        _currentSong.value = song
        val mediaItem = songToMediaItem(song, streamUrl)
        val currentQueue = _queue.value
        val existingIndex = currentQueue.indexOfFirst { it.id == song.id }

        if (existingIndex >= 0 && existingIndex < player.mediaItemCount) {
            // Si la canción ya forma parte de la lista/cola cargada en ExoPlayer
            player.replaceMediaItem(existingIndex, mediaItem)
            if (player.currentMediaItemIndex != existingIndex) {
                player.seekToDefaultPosition(existingIndex)
            }
            player.prepare()
            player.play()
        } else {
            // Si se reproduce una canción suelta fuera de cola previa
            _queue.value = listOf(song)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }
    }
}
