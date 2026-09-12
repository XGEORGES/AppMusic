package com.aura.music.ui.explore

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val downloadManager: MediaDownloadManager,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    val chips = listOf("Podcasts", "Relajación", "Sueño", "Triste", "Actívate", "Energía", "Rock", "Pop", "Electrónica")

    private val _selectedChip = MutableStateFlow<String?>(null)
    val selectedChip: StateFlow<String?> = _selectedChip.asStateFlow()

    // Playlists obtenidas por el chip seleccionado
    private val _chipPlaylists = MutableStateFlow<List<SongEntity>>(emptyList())
    val chipPlaylists: StateFlow<List<SongEntity>> = _chipPlaylists.asStateFlow()

    // Playlists del usuario para los Accesos directos y para basar "SIMILARES A"
    val userPlaylists: StateFlow<List<PlaylistEntity>> = playlistDao.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Título dinámico para "SIMILARES A" según las playlists del usuario
    private val _similarTitle = MutableStateFlow<String?>(null)
    val similarTitle: StateFlow<String?> = _similarTitle.asStateFlow()

    // Accesos directos predeterminados o del usuario
    private val _shortcutItems = MutableStateFlow<List<SongEntity>>(emptyList())
    val shortcutItems: StateFlow<List<SongEntity>> = _shortcutItems.asStateFlow()

    // Selección rápida: canciones más escuchadas por el usuario que rotan aleatoriamente
    private val _quickPicks = MutableStateFlow<List<SongEntity>>(emptyList())
    val quickPicks: StateFlow<List<SongEntity>> = _quickPicks.asStateFlow()

    // Similares a: playlists recomendadas según gustos
    private val _similarPlaylists = MutableStateFlow<List<SongEntity>>(emptyList())
    val similarPlaylists: StateFlow<List<SongEntity>> = _similarPlaylists.asStateFlow()

    // Audios de larga duración (Mixes / Remixes de más de 30-40 minutos)
    private val _longAudioMixes = MutableStateFlow<List<SongEntity>>(emptyList())
    val longAudioMixes: StateFlow<List<SongEntity>> = _longAudioMixes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadHomeScreenData()
        observeUserPlaylists()
    }

    private fun observeUserPlaylists() {
        viewModelScope.launch {
            userPlaylists.collect { playlists ->
                if (playlists.isNotEmpty()) {
                    // Seleccionar una playlist aleatoria del usuario cada vez que cambien o roten
                    val randomPlaylist = playlists.random()
                    _similarTitle.value = randomPlaylist.name
                    // Cargar recomendaciones basadas en esa playlist rotada del usuario
                    launch(Dispatchers.IO) {
                        try {
                            musicRepository.search("${randomPlaylist.name} Mix", FilterType.PLAYLISTS).collect { similar ->
                                _similarPlaylists.value = similar.take(8)
                            }
                        } catch (_: Exception) {}
                    }
                } else {
                    // Si el usuario no tiene ninguna playlist guardada, ocultar "SIMILARES A"
                    _similarTitle.value = null
                    _similarPlaylists.value = emptyList()
                }
            }
        }
    }

    fun onRefreshScreen() {
        rotateQuickPicks()
        rotateSimilarSection()
        loadLongAudioMixes()
    }

    private fun rotateSimilarSection() {
        val currentPlaylists = userPlaylists.value
        if (currentPlaylists.isNotEmpty()) {
            val randomPlaylist = currentPlaylists.random()
            _similarTitle.value = randomPlaylist.name
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    musicRepository.search("${randomPlaylist.name} Mix", FilterType.PLAYLISTS).collect { similar ->
                        _similarPlaylists.value = similar.take(8)
                    }
                } catch (_: Exception) {}
            }
        } else {
            _similarTitle.value = null
            _similarPlaylists.value = emptyList()
        }
    }

    fun selectChip(chip: String) {
        if (_selectedChip.value == chip) {
            // Deseleccionar chip si se vuelve a presionar
            _selectedChip.value = null
            _chipPlaylists.value = emptyList()
            return
        }
        _selectedChip.value = chip
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                musicRepository.search("$chip Playlist", FilterType.PLAYLISTS).collect { results ->
                    _chipPlaylists.value = results.take(15)
                }
            } catch (_: Exception) {
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadHomeScreenData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                // 1. Cargar Accesos Directos (hasta 27 canciones para 3 páginas de 9 cada una)
                musicRepository.search("Top Hits Hits Mix", FilterType.PLAYLISTS).collect { playlists ->
                    _shortcutItems.value = playlists.take(27)
                }
            } catch (_: Exception) {}

            try {
                // 2. Cargar Selección rápida rotativa
                rotateQuickPicks()
            } catch (_: Exception) {}

            try {
                // 3. Cargar Audios de larga duración basados en canciones o playlists escuchadas
                loadLongAudioMixes()
            } catch (_: Exception) {}

            _isLoading.value = false
        }
    }

    private fun loadLongAudioMixes() {
        viewModelScope.launch(Dispatchers.IO) {
            val topPlayed = songDao.getTopPlayedSync(10)
            val query = if (topPlayed.isNotEmpty()) {
                val sampleSong = topPlayed.random()
                "${sampleSong.artistName} Extended Mix"
            } else if (userPlaylists.value.isNotEmpty()) {
                "${userPlaylists.value.random().name} Long Mix Extended"
            } else {
                "Full Album 80s 90s Disco MegaMix Extended"
            }

            try {
                musicRepository.search(query, FilterType.SONGS).collect { mixes ->
                    _longAudioMixes.value = mixes.take(8)
                }
            } catch (_: Exception) {}
        }
    }

    fun rotateQuickPicks() {
        viewModelScope.launch(Dispatchers.IO) {
            val topPlayed = songDao.getTopPlayedSync(20)
            val picks = if (topPlayed.isNotEmpty()) {
                topPlayed.shuffled().take(8)
            } else {
                var loaded = emptyList<SongEntity>()
                try {
                    musicRepository.search("Viral Hits Pop Rock", FilterType.SONGS).collect { songs ->
                        loaded = songs.shuffled().take(8)
                    }
                } catch (_: Exception) {}
                loaded
            }
            _quickPicks.value = picks
            if (picks.isNotEmpty()) {
                playerRepository.preloadStreams(picks.take(4))
            }
        }
    }

    fun toggleFavorite(song: SongEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            songDao.insertOrUpdate(song)
            songDao.updateFavorite(song.id, !song.isFavorite)
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

    fun pinToShortcuts(song: SongEntity) {
        val current = _shortcutItems.value.toMutableList()
        if (!current.any { it.id == song.id }) {
            current.add(0, song)
            _shortcutItems.value = current.take(9)
        }
    }

    fun dismissSong(songId: String) {
        _quickPicks.value = _quickPicks.value.filter { it.id != songId }
        _similarPlaylists.value = _similarPlaylists.value.filter { it.id != songId }
        _longAudioMixes.value = _longAudioMixes.value.filter { it.id != songId }
    }

    fun downloadSong(song: SongEntity, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            songDao.insertOrUpdate(song)
            val result = downloadManager.downloadSong(song)
            result.onSuccess { path ->
                onResult(true, "Descargado correctamente")
            }.onFailure { error ->
                onResult(false, error.message ?: "Error al descargar")
            }
        }
    }
}
