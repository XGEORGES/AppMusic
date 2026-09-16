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
import com.aura.music.data.extractor.YouTubeMusicSource
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playerRepository: PlayerRepository,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val downloadManager: MediaDownloadManager,
    private val youTubeMusicSource: YouTubeMusicSource
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

    fun saveToLibrary(song: SongEntity, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (song.id.startsWith("PL") || song.id.startsWith("PL_")) {
                    val cleanId = if (song.id.startsWith("PL_")) song.id.removePrefix("PL_") else song.id
                    val extracted = youTubeMusicSource.extractPlaylist(cleanId)
                    val playlistName = extracted.name.ifBlank { song.title.substringBefore(" (") }.ifBlank { "Playlist Importada" }
                    val playlist = PlaylistEntity(
                        name = playlistName,
                        description = "Importada desde YouTube",
                        isImported = true,
                        originalUrl = "https://www.youtube.com/playlist?list=$cleanId"
                    )
                    val playlistId = playlistDao.insertPlaylist(playlist)
                    val songEntities = extracted.songs.map { it.toEntity() }
                    if (songEntities.isNotEmpty()) {
                        songDao.insertOrUpdate(songEntities)
                        val crossRefs = songEntities.mapIndexed { index, s ->
                            PlaylistSongCrossRef(
                                playlistId = playlistId,
                                songId = s.id,
                                positionInPlaylist = index
                            )
                        }
                        playlistDao.insertPlaylistSongCrossRefs(crossRefs)
                    }
                    withContext(Dispatchers.Main) {
                        onResult(true, "Playlist '$playlistName' guardada en la biblioteca")
                    }
                } else {
                    songDao.insertOrUpdate(song.copy(isFavorite = true))
                    withContext(Dispatchers.Main) {
                        onResult(true, "Canción guardada en la biblioteca")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult(false, "Error al guardar en biblioteca: ${e.localizedMessage}")
                }
            }
        }
    }

    fun addSongToPlaylist(song: SongEntity, playlistId: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (song.id.startsWith("PL") || song.id.startsWith("PL_")) {
                    val cleanId = if (song.id.startsWith("PL_")) song.id.removePrefix("PL_") else song.id
                    val extracted = youTubeMusicSource.extractPlaylist(cleanId)
                    val songEntities = extracted.songs.map { it.toEntity() }
                    if (songEntities.isNotEmpty()) {
                        songDao.insertOrUpdate(songEntities)
                        val currentSongs = playlistDao.getSongsForPlaylist(playlistId)
                        val startPos = currentSongs.size
                        val crossRefs = songEntities.mapIndexed { index, s ->
                            PlaylistSongCrossRef(
                                playlistId = playlistId,
                                songId = s.id,
                                positionInPlaylist = startPos + index
                            )
                        }
                        playlistDao.insertPlaylistSongCrossRefs(crossRefs)
                    }
                } else {
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
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    onDone()
                }
            }
        }
    }

    fun createPlaylistAndAddSong(playlistName: String, song: SongEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val playlistId = playlistDao.insertPlaylist(
                    PlaylistEntity(
                        name = playlistName.trim(),
                        isImported = false
                    )
                )
                if (song.id.startsWith("PL") || song.id.startsWith("PL_")) {
                    val cleanId = if (song.id.startsWith("PL_")) song.id.removePrefix("PL_") else song.id
                    val extracted = youTubeMusicSource.extractPlaylist(cleanId)
                    val songEntities = extracted.songs.map { it.toEntity() }
                    if (songEntities.isNotEmpty()) {
                        songDao.insertOrUpdate(songEntities)
                        val crossRefs = songEntities.mapIndexed { index, s ->
                            PlaylistSongCrossRef(
                                playlistId = playlistId,
                                songId = s.id,
                                positionInPlaylist = index
                            )
                        }
                        playlistDao.insertPlaylistSongCrossRefs(crossRefs)
                    }
                } else {
                    songDao.insertOrUpdate(song)
                    playlistDao.insertPlaylistSongCrossRef(
                        PlaylistSongCrossRef(
                            playlistId = playlistId,
                            songId = song.id,
                            positionInPlaylist = 0
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    onDone()
                }
            }
        }
    }

    fun downloadSong(song: SongEntity, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (song.id.startsWith("PL") || song.id.startsWith("PL_") || song.id.startsWith("OLAK5uy_") || song.id.startsWith("VL")) {
                    withContext(Dispatchers.Main) {
                        onResult(true, "Descargando canciones de la playlist...")
                    }
                    val rawId = if (song.id.startsWith("PL_")) song.id.removePrefix("PL_") else song.id
                    val cleanId = if (rawId.startsWith("VL")) rawId.removePrefix("VL") else rawId
                    val extracted = youTubeMusicSource.extractPlaylist(cleanId)
                    val songEntities = extracted.songs.map { it.toEntity() }
                    if (songEntities.isNotEmpty()) {
                        songDao.insertOrUpdate(songEntities)
                        var downloaded = 0
                        for (s in songEntities) {
                            val res = downloadManager.downloadSong(s)
                            if (res.isSuccess) downloaded++
                        }
                        withContext(Dispatchers.Main) {
                            onResult(true, "$downloaded canciones descargadas de la playlist")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onResult(false, "No se encontraron canciones para descargar")
                        }
                    }
                } else {
                    songDao.insertOrUpdate(song)
                    val result = downloadManager.downloadSong(song)
                    result.onSuccess {
                        withContext(Dispatchers.Main) {
                            onResult(true, "Descargado correctamente")
                        }
                    }.onFailure { error ->
                        withContext(Dispatchers.Main) {
                            onResult(false, error.message ?: "Error al descargar")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, e.message ?: "Error al procesar la descarga")
                }
            }
        }
    }

    suspend fun getPlaylistSongs(playlistId: String): List<SongEntity> = withContext(Dispatchers.IO) {
        try {
            val cleanId = if (playlistId.startsWith("PL_")) playlistId.removePrefix("PL_") else playlistId
            val extracted = youTubeMusicSource.extractPlaylist(cleanId)
            extracted.songs.map { it.toEntity() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
