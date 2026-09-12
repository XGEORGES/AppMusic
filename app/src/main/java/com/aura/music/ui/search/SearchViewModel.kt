package com.aura.music.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.core.database.dao.PlaylistDao
import com.aura.music.core.database.dao.SongDao
import com.aura.music.core.database.entity.PlaylistEntity
import com.aura.music.core.database.entity.PlaylistSongCrossRef
import com.aura.music.core.database.entity.SongEntity
import com.aura.music.data.model.FilterType
import com.aura.music.data.repository.MusicRepository
import com.aura.music.data.repository.PlayerRepository
import com.aura.music.service.audio.MediaDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playerRepository: PlayerRepository,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val downloadManager: MediaDownloadManager
) : ViewModel() {

    val userPlaylists: StateFlow<List<PlaylistEntity>> = playlistDao.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedFilter = MutableStateFlow(FilterType.SONGS)
    val selectedFilter: StateFlow<FilterType> = _selectedFilter.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SongEntity>>(emptyList())
    val searchResults: StateFlow<List<SongEntity>> = _searchResults.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(listOf("Queen", "Coldplay", "Hans Zimmer", "Daft Punk"))
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            _query
                .debounce(300)
                .distinctUntilChanged()
                .filter { it.isNotBlank() }
                .collect { q ->
                    performSearch(q, _selectedFilter.value)
                }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isBlank()) {
            _searchResults.value = emptyList()
        }
    }

    fun setFilter(filter: FilterType) {
        _selectedFilter.value = filter
        if (_query.value.isNotBlank()) {
            performSearch(_query.value, filter)
        }
    }

    fun selectHistoryItem(item: String) {
        _query.value = item
        performSearch(item, _selectedFilter.value)
    }

    fun clearHistory() {
        _searchHistory.value = emptyList()
    }

    private fun performSearch(q: String, filter: FilterType) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Agregar al historial si no está
                val currentHistory = _searchHistory.value.toMutableList()
                if (!currentHistory.contains(q)) {
                    currentHistory.add(0, q)
                    _searchHistory.value = currentHistory.take(10)
                }

                musicRepository.search(q, filter).collect { songs ->
                    _searchResults.value = songs
                    if (filter == FilterType.SONGS && songs.isNotEmpty()) {
                        playerRepository.preloadStreams(songs.take(3))
                    }
                }
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveToLibrary(song: SongEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            songDao.insertOrUpdate(song.copy(isFavorite = true))
        }
    }

    fun addSongToPlaylist(song: SongEntity, playlistId: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            songDao.insertOrUpdate(song)
            val currentSongs = playlistDao.getSongsForPlaylist(playlistId)
            val nextPosition = currentSongs.size
            playlistDao.insertPlaylistSongCrossRef(
                PlaylistSongCrossRef(
                    playlistId = playlistId,
                    songId = song.id,
                    positionInPlaylist = nextPosition
                )
            )
            onDone()
        }
    }

    fun createPlaylistAndAddSong(playlistName: String, song: SongEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            songDao.insertOrUpdate(song)
            val playlistId = playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = playlistName.trim(),
                    isImported = false
                )
            )
            playlistDao.insertPlaylistSongCrossRef(
                PlaylistSongCrossRef(
                    playlistId = playlistId,
                    songId = song.id,
                    positionInPlaylist = 0
                )
            )
            onDone()
        }
    }

    fun downloadSong(song: SongEntity, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            songDao.insertOrUpdate(song)
            val result = downloadManager.downloadSong(song)
            result.onSuccess {
                onResult(true, "Descargado correctamente")
            }.onFailure { error ->
                onResult(false, error.message ?: "Error al descargar")
            }
        }
    }
}
