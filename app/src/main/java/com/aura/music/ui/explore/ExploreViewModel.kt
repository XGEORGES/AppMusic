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
        observeUserDataAndPlaylists()
    }

    private fun observeUserDataAndPlaylists() {
        viewModelScope.launch {
            userPlaylists.collect { playlists ->
                updateSimilarSection(playlists)
            }
        }
    }

    fun onRefreshScreen() {
        loadShortcuts()
        rotateQuickPicks()
        updateSimilarSection()
        loadLongAudioMixes()
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
                loadShortcuts()
                rotateQuickPicks()
                updateSimilarSection()
                loadLongAudioMixes()
            } catch (_: Exception) {
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadShortcuts() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val topPlayed = songDao.getTopPlayedSync(27)
                val favorites = songDao.getFavoritesSync()
                // Prioridad absoluta al usuario: canciones que ha marcado o reproducido
                val userSongs = (favorites + topPlayed).distinctBy { it.id }.take(27)

                if (userSongs.isNotEmpty()) {
                    if (userSongs.size >= 9) {
                        _shortcutItems.value = userSongs
                    } else {
                        // Completar la cuadrícula si tiene menos de 9 canciones locales
                        var fallbackSongs = emptyList<SongEntity>()
                        try {
                            musicRepository.search("Top Hits Pop Latino y Global", FilterType.SONGS).collect { songs ->
                                fallbackSongs = songs.filter { fb -> !userSongs.any { it.id == fb.id } }
                            }
                        } catch (_: Exception) {}
                        _shortcutItems.value = (userSongs + fallbackSongs).take(9)
                    }
                } else {
                    // Instalación desde cero (0 historial): Cargar éxitos populares (Latino + Global)
                    musicRepository.search("Top Exitos Pop Latino y Global Hits", FilterType.SONGS).collect { songs ->
                        _shortcutItems.value = songs.take(27)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun updateSimilarSection(playlists: List<PlaylistEntity> = userPlaylists.value) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val topPlayed = songDao.getTopPlayedSync(15)

                if (playlists.isNotEmpty()) {
                    // Caso 1: El usuario tiene playlists creadas
                    val randomPlaylist = playlists.random()
                    _similarTitle.value = randomPlaylist.name
                    musicRepository.search("${randomPlaylist.name} Mix", FilterType.PLAYLISTS).collect { similar ->
                        _similarPlaylists.value = similar.take(8)
                    }
                } else if (topPlayed.isNotEmpty()) {
                    // Caso 2: El usuario ha escuchado canciones -> Basar en su artista o tema favorito
                    val sampleSong = topPlayed.random()
                    val artist = sampleSong.artistName.ifBlank { sampleSong.title }
                    _similarTitle.value = artist
                    musicRepository.search("$artist Mix", FilterType.PLAYLISTS).collect { similar ->
                        _similarPlaylists.value = similar.take(8)
                    }
                } else {
                    // Caso 3: Usuario nuevo -> Tendencias del momento
                    _similarTitle.value = "Tendencias de Hoy"
                    musicRepository.search("Top Playlists Exitos Pop Latino Global", FilterType.PLAYLISTS).collect { popular ->
                        _similarPlaylists.value = popular.take(8)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadLongAudioMixes() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val topPlayed = songDao.getTopPlayedSync(10)
                val playlists = userPlaylists.value

                val query = if (topPlayed.isNotEmpty()) {
                    val sampleSong = topPlayed.random()
                    "${sampleSong.artistName} Extended Mix"
                } else if (playlists.isNotEmpty()) {
                    "${playlists.random().name} Long Mix Extended"
                } else {
                    val fallbackMixes = listOf(
                        "MegaMix Exitos 80s 90s Pop Rock En Vivo",
                        "Mix Pop Latino Grandes Exitos Extended",
                        "Top Hits Global MegaMix Extended Session"
                    )
                    fallbackMixes.random()
                }

                musicRepository.search(query, FilterType.SONGS).collect { mixes ->
                    _longAudioMixes.value = mixes.take(8)
                }
            } catch (_: Exception) {}
        }
    }

    fun rotateQuickPicks() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val topPlayed = songDao.getTopPlayedSync(20)
                val picks = if (topPlayed.isNotEmpty()) {
                    topPlayed.shuffled().take(8)
                } else {
                    var loaded = emptyList<SongEntity>()
                    val queries = listOf(
                        "Top Canciones Exitos Pop Latino",
                        "Billboard Top Global Hits",
                        "Exitos Urbanos y Pop Viral"
                    )
                    try {
                        musicRepository.search(queries.random(), FilterType.SONGS).collect { songs ->
                            loaded = songs.shuffled().take(8)
                        }
                    } catch (_: Exception) {}
                    loaded
                }
                _quickPicks.value = picks
                if (picks.isNotEmpty()) {
                    playerRepository.preloadStreams(picks.take(4))
                }
            } catch (_: Exception) {}
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
