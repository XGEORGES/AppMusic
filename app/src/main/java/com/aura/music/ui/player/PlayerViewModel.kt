package com.aura.music.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.aura.music.core.database.entity.SongEntity
import com.aura.music.data.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
open class PlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository
) : ViewModel() {

    val currentPlayingSong: StateFlow<SongEntity?> = playerRepository.currentPlayingSong
    val isPlaying: StateFlow<Boolean> = playerRepository.isPlaying
    val isLoading: StateFlow<Boolean> = playerRepository.isLoading
    val playbackPosition: StateFlow<Long> = playerRepository.playbackPosition
    val duration: StateFlow<Long> = playerRepository.duration
    val queue: StateFlow<List<SongEntity>> = playerRepository.queue
    val repeatMode: StateFlow<Int> = playerRepository.repeatMode
    val shuffleModeEnabled: StateFlow<Boolean> = playerRepository.shuffleModeEnabled
    val isInfiniteRadioEnabled: StateFlow<Boolean> = playerRepository.isInfiniteRadioEnabled
    val isDjAuraMode: StateFlow<Boolean> = playerRepository.isDjAuraMode
    val quickSkipCount: StateFlow<Int> = playerRepository.quickSkipCount

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded.asStateFlow()

    fun setExpanded(expanded: Boolean) {
        _isExpanded.value = expanded
    }

    fun play() {
        playerRepository.play()
    }

    fun pause() {
        playerRepository.pause()
    }

    fun togglePlayPause() {
        playerRepository.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerRepository.seekTo(positionMs)
    }

    fun seekToNext() {
        playerRepository.seekToNext()
    }

    fun seekToPrevious() {
        playerRepository.seekToPrevious()
    }

    fun toggleFavorite(songId: String) {
        viewModelScope.launch {
            playerRepository.toggleFavorite(songId)
        }
    }

    fun playSong(song: SongEntity) {
        playerRepository.playSong(song)
    }

    fun preloadStreams(songs: List<SongEntity>, priorityIndex: Int = 0) {
        playerRepository.preloadStreams(songs, priorityIndex)
    }

    fun setQueue(songs: List<SongEntity>, startIndex: Int = 0) {
        playerRepository.setQueue(songs, startIndex)
    }

    fun cycleRepeatMode() {
        val nextMode = when (repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        playerRepository.setRepeatMode(nextMode)
    }

    fun toggleShuffle() {
        playerRepository.setShuffleModeEnabled(!shuffleModeEnabled.value)
    }

    fun playNext(song: SongEntity) {
        playerRepository.playNext(song)
    }

    fun playNext(songs: List<SongEntity>) {
        if (songs.isNotEmpty()) {
            playerRepository.playNext(songs)
        }
    }

    fun shufflePlay(songs: List<SongEntity>) {
        if (songs.isNotEmpty()) {
            val shuffled = songs.shuffled()
            playerRepository.setShuffleModeEnabled(false)
            playerRepository.setQueue(shuffled, 0)
        }
    }

    fun addToQueue(song: SongEntity) {
        playerRepository.addToQueue(song)
    }

    fun startMix(song: SongEntity) {
        playerRepository.startMix(song)
    }

    fun setInfiniteRadioEnabled(enabled: Boolean) {
        playerRepository.setInfiniteRadioEnabled(enabled)
    }

    fun setDjAuraMode(enabled: Boolean) {
        playerRepository.setDjAuraMode(enabled)
    }

    fun resetQuickSkipCount() {
        playerRepository.resetQuickSkipCount()
    }
}
