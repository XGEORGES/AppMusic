package com.aura.music.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.core.database.dao.PlaylistDao
import com.aura.music.core.database.dao.SongDao
import com.aura.music.core.database.entity.PlaylistEntity
import com.aura.music.core.database.entity.PlaylistSongCrossRef
import com.aura.music.core.database.entity.SongEntity
import com.aura.music.data.model.FilterType
import com.aura.music.data.model.Resource
import com.aura.music.data.repository.MusicRepository
import com.aura.music.service.audio.MediaDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLEncoder
import javax.inject.Inject

enum class LibraryTab {
    PLAYLISTS,
    SONGS,
    DOWNLOADS
}

enum class LibrarySortOrder(val displayName: String) {
    RECENT("Actividad reciente"),
    A_TO_Z("Título (A - Z)"),
    Z_TO_A("Título (Z - A)")
}

data class PlaylistUiItem(
    val playlist: PlaylistEntity,
    val songCount: Int = 0,
    val thumbnailUrl: String? = null,
    val isPinned: Boolean = false
)

@HiltViewModel
open class LibraryViewModel @Inject constructor(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val musicRepository: MusicRepository,
    private val mediaDownloadManager: MediaDownloadManager? = null
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(LibraryTab.PLAYLISTS)
    val selectedTab: StateFlow<LibraryTab> = _selectedTab.asStateFlow()

    fun selectTab(tab: LibraryTab) {
        _selectedTab.value = tab
    }

    private val _sortOrder = MutableStateFlow(LibrarySortOrder.RECENT)
    val sortOrder: StateFlow<LibrarySortOrder> = _sortOrder.asStateFlow()

    fun setSortOrder(order: LibrarySortOrder) {
        _sortOrder.value = order
    }

    val favorites: StateFlow<List<SongEntity>> = combine(
        songDao.getFavorites(),
        _sortOrder
    ) { list, order ->
        when (order) {
            LibrarySortOrder.RECENT -> list
            LibrarySortOrder.A_TO_Z -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            LibrarySortOrder.Z_TO_A -> list.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<SongEntity>> = combine(
        songDao.getDownloadedSongs(),
        _sortOrder
    ) { list, order ->
        when (order) {
            LibrarySortOrder.RECENT -> list
            LibrarySortOrder.A_TO_Z -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            LibrarySortOrder.Z_TO_A -> list.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = playlistDao.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _playlistDetails = MutableStateFlow<Map<Long, Pair<Int, String?>>>(emptyMap())
    private val _pinnedPlaylistIds = MutableStateFlow<Set<Long>>(emptySet())
    val pinnedPlaylistIds: StateFlow<Set<Long>> = _pinnedPlaylistIds.asStateFlow()

    val playlistUiItems: StateFlow<List<PlaylistUiItem>> = combine(
        playlists,
        _playlistDetails,
        _pinnedPlaylistIds,
        _sortOrder
    ) { list, details, pinned, order ->
        val mapped = list.map { p ->
            val (count, thumb) = details[p.playlistId] ?: Pair(0, null)
            PlaylistUiItem(
                playlist = p,
                songCount = count,
                thumbnailUrl = thumb,
                isPinned = pinned.contains(p.playlistId)
            )
        }
        when (order) {
            LibrarySortOrder.RECENT -> mapped.sortedWith(
                compareByDescending<PlaylistUiItem> { it.isPinned }
                    .thenByDescending { it.playlist.createdAt }
            )
            LibrarySortOrder.A_TO_Z -> mapped.sortedWith(
                compareByDescending<PlaylistUiItem> { it.isPinned }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.playlist.name }
            )
            LibrarySortOrder.Z_TO_A -> mapped.sortedWith(
                compareByDescending<PlaylistUiItem> { it.isPinned }
                    .thenByDescending(String.CASE_INSENSITIVE_ORDER) { it.playlist.name }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Trigger para forzar re-lectura de conteos cuando se modifican cross-refs
    private val _playlistDetailsRefreshTrigger = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            // Combinar los cambios de playlists con el trigger de refresh manual
            // para que el conteo se actualice tanto al crear/borrar playlists como al agregar/quitar canciones
            combine(playlists, _playlistDetailsRefreshTrigger) { list, _ -> list }
                .collect { list ->
                    val detailsMap = mutableMapOf<Long, Pair<Int, String?>>()
                    for (p in list) {
                        val songs = playlistDao.getSongsForPlaylist(p.playlistId)
                        detailsMap[p.playlistId] = Pair(songs.size, songs.firstOrNull()?.thumbnailUrl)
                    }
                    _playlistDetails.value = detailsMap
                }
        }
    }

    fun refreshPlaylistDetails() {
        _playlistDetailsRefreshTrigger.value++
    }

    fun togglePinPlaylist(playlistId: Long) {
        val current = _pinnedPlaylistIds.value.toMutableSet()
        if (current.contains(playlistId)) {
            current.remove(playlistId)
        } else {
            current.add(playlistId)
        }
        _pinnedPlaylistIds.value = current
    }

    fun getPlaylistShareUrl(playlist: PlaylistEntity): String {
        return if (!playlist.originalUrl.isNullOrBlank()) {
            playlist.originalUrl
        } else {
            "https://music.youtube.com/search?q=${URLEncoder.encode(playlist.name, "UTF-8")}"
        }
    }

    fun downloadPlaylist(
        playlistId: Long,
        onProgress: (downloaded: Int, total: Int) -> Unit = { _, _ -> },
        onComplete: (downloadedCount: Int) -> Unit = {}
    ) {
        if (playlistId == -1L) {
            downloadAllFavorites(onComplete)
            return
        }
        val downloadManager = mediaDownloadManager ?: return
        viewModelScope.launch {
            val songs = playlistDao.getSongsForPlaylist(playlistId)
            var count = 0
            for ((idx, song) in songs.withIndex()) {
                onProgress(idx, songs.size)
                if (!downloadManager.isDownloaded(song)) {
                    val res = downloadManager.downloadSong(song)
                    if (res.isSuccess) count++
                } else {
                    count++
                }
            }
            refreshPlaylistDetails()
            onComplete(count)
        }
    }

    fun downloadAllFavorites(
        onComplete: (Int) -> Unit = {}
    ) {
        val downloadManager = mediaDownloadManager ?: return
        viewModelScope.launch {
            val songs = favorites.value
            var count = 0
            for (song in songs) {
                if (!downloadManager.isDownloaded(song)) {
                    val res = downloadManager.downloadSong(song)
                    if (res.isSuccess) count++
                } else {
                    count++
                }
            }
            onComplete(count)
        }
    }

    fun deleteDownload(songId: String) {
        val downloadManager = mediaDownloadManager ?: return
        viewModelScope.launch {
            downloadManager.deleteDownload(songId)
        }
    }

    fun downloadSong(song: SongEntity, onResult: (Boolean, String) -> Unit) {
        val downloadManager = mediaDownloadManager ?: run {
            onResult(false, "Descargador no disponible")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            songDao.insertOrUpdate(song)
            val result = downloadManager.downloadSong(song)
            result.onSuccess {
                refreshPlaylistDetails()
                onResult(true, "Descargado correctamente")
            }.onFailure { error ->
                onResult(false, error.message ?: "Error al descargar")
            }
        }
    }

    private val _importDialogVisible = MutableStateFlow(false)
    val importDialogVisible: StateFlow<Boolean> = _importDialogVisible.asStateFlow()

    private val _importUrl = MutableStateFlow("")
    val importUrl: StateFlow<String> = _importUrl.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    private val _createPlaylistDialogVisible = MutableStateFlow(false)
    val createPlaylistDialogVisible: StateFlow<Boolean> = _createPlaylistDialogVisible.asStateFlow()

    private val _newPlaylistName = MutableStateFlow("")
    val newPlaylistName: StateFlow<String> = _newPlaylistName.asStateFlow()

    fun showCreatePlaylistDialog() {
        _newPlaylistName.value = ""
        _createPlaylistDialogVisible.value = true
    }

    fun dismissCreatePlaylistDialog() {
        _createPlaylistDialogVisible.value = false
        _newPlaylistName.value = ""
    }

    fun onPlaylistNameChanged(name: String) {
        _newPlaylistName.value = name
    }

    fun createPlaylist(name: String = _newPlaylistName.value) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = trimmed,
                    isImported = false
                )
            )
            _createPlaylistDialogVisible.value = false
            _newPlaylistName.value = ""
        }
    }

    fun loadPlaylistSongs(playlistId: Long, onLoaded: (List<SongEntity>) -> Unit) {
        viewModelScope.launch {
            val songs = playlistDao.getSongsForPlaylist(playlistId)
            onLoaded(songs)
        }
    }

    private val _editingPlaylist = MutableStateFlow<PlaylistEntity?>(null)
    val editingPlaylist: StateFlow<PlaylistEntity?> = _editingPlaylist.asStateFlow()

    private val _editingSongs = MutableStateFlow<List<SongEntity>>(emptyList())
    val editingSongs: StateFlow<List<SongEntity>> = _editingSongs.asStateFlow()

    fun startEditingPlaylist(playlist: PlaylistEntity) {
        _editingPlaylist.value = playlist
        viewModelScope.launch {
            val songs = playlistDao.getSongsForPlaylist(playlist.playlistId)
            _editingSongs.value = songs
        }
    }

    fun dismissEditingPlaylist() {
        val currentPlaylist = _editingPlaylist.value
        _editingPlaylist.value = null
        _editingSongs.value = emptyList()
        refreshPlaylistDetails()
        currentPlaylist?.let { loadPlaylistDetailSongs(it.playlistId) }
    }

    fun removeSongFromEditingPlaylist(songId: String) {
        val playlist = _editingPlaylist.value ?: return
        val currentList = _editingSongs.value.filter { it.id != songId }
        _editingSongs.value = currentList
        viewModelScope.launch {
            playlistDao.removeSongFromPlaylist(playlist.playlistId, songId)
        }
    }

    fun moveSongInEditingPlaylist(fromIndex: Int, toIndex: Int) {
        val playlist = _editingPlaylist.value ?: return
        val currentList = _editingSongs.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            val item = currentList.removeAt(fromIndex)
            currentList.add(toIndex, item)
            _editingSongs.value = currentList
            viewModelScope.launch {
                playlistDao.updatePlaylistSongsOrder(playlist.playlistId, currentList)
            }
        }
    }

    fun savePlaylistOrder(playlistId: Long, songs: List<SongEntity>) {
        _editingSongs.value = songs
        viewModelScope.launch {
            playlistDao.updatePlaylistSongsOrder(playlistId, songs)
            val currentDetail = _selectedPlaylistDetail.value
            if (currentDetail != null && currentDetail.playlistId == playlistId) {
                _detailSongs.value = songs
            }
            refreshPlaylistDetails()
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistDao.deletePlaylist(playlistId)
            refreshPlaylistDetails()
        }
    }

    fun showImportDialog() {
        _importUrl.value = ""
        _importError.value = null
        _importDialogVisible.value = true
    }

    fun dismissImportDialog() {
        _importDialogVisible.value = false
        _importError.value = null
    }

    fun onUrlChanged(newUrl: String) {
        _importUrl.value = newUrl
        _importError.value = null
    }

    fun isUrlValid(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return false
        return (trimmed.startsWith("http://") || trimmed.startsWith("https://")) &&
                (trimmed.contains("list=") || trimmed.contains("youtube.com") || trimmed.contains("youtu.be"))
    }

    fun importPlaylist() {
        val url = _importUrl.value.trim()
        if (!isUrlValid(url)) {
            _importError.value = "Por favor ingresa una URL válida de YouTube"
            return
        }

        viewModelScope.launch {
            _isImporting.value = true
            _importError.value = null
            musicRepository.importPlaylistFromUrl(url).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _isImporting.value = true
                    }
                    is Resource.Success -> {
                        _isImporting.value = false
                        _importDialogVisible.value = false
                    }
                    is Resource.Error -> {
                        _isImporting.value = false
                        _importError.value = resource.message
                    }
                }
            }
        }
    }

    // Playlist Detail View State
    private val _selectedPlaylistDetail = MutableStateFlow<PlaylistEntity?>(null)
    val selectedPlaylistDetail: StateFlow<PlaylistEntity?> = _selectedPlaylistDetail.asStateFlow()

    private val _detailSongs = MutableStateFlow<List<SongEntity>>(emptyList())
    val detailSongs: StateFlow<List<SongEntity>> = _detailSongs.asStateFlow()

    private val _isDownloadingDetail = MutableStateFlow(false)
    val isDownloadingDetail: StateFlow<Boolean> = _isDownloadingDetail.asStateFlow()

    fun openPlaylistDetail(playlist: PlaylistEntity) {
        _selectedPlaylistDetail.value = playlist
        loadPlaylistDetailSongs(playlist.playlistId)
    }

    fun openFavoritesDetail() {
        _selectedPlaylistDetail.value = PlaylistEntity(
            playlistId = -1L,
            name = "Música que te gustó",
            description = "Canciones guardadas en tu biblioteca",
            isImported = false
        )
        loadPlaylistDetailSongs(-1L)
    }

    fun closePlaylistDetail() {
        _selectedPlaylistDetail.value = null
        _detailSongs.value = emptyList()
        refreshPlaylistDetails()
    }

    fun loadPlaylistDetailSongs(playlistId: Long) {
        viewModelScope.launch {
            if (playlistId == -1L) {
                favorites.collect { favs ->
                    if (_selectedPlaylistDetail.value?.playlistId == -1L) {
                        _detailSongs.value = favs
                    }
                }
            } else {
                val songs = playlistDao.getSongsForPlaylist(playlistId)
                _detailSongs.value = songs
            }
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        if (playlistId == -1L) return
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            playlistDao.updatePlaylistName(playlistId, trimmed)
            val current = _selectedPlaylistDetail.value
            if (current != null && current.playlistId == playlistId) {
                _selectedPlaylistDetail.value = current.copy(name = trimmed)
            }
            refreshPlaylistDetails()
        }
    }

    fun addSongToDetailPlaylist(song: SongEntity, playlistId: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            songDao.insertOrUpdate(song)
            if (playlistId == -1L) {
                songDao.updateFavorite(song.id, true)
            } else {
                val currentSongs = playlistDao.getSongsForPlaylist(playlistId)
                val nextPos = currentSongs.size
                playlistDao.insertPlaylistSongCrossRef(
                    PlaylistSongCrossRef(
                        playlistId = playlistId,
                        songId = song.id,
                        positionInPlaylist = nextPos
                    )
                )
                val updated = playlistDao.getSongsForPlaylist(playlistId)
                _detailSongs.value = updated
                refreshPlaylistDetails()
            }
            onDone()
        }
    }

    fun removeSongFromDetailPlaylist(songId: String, playlistId: Long) {
        viewModelScope.launch {
            if (playlistId == -1L) {
                songDao.updateFavorite(songId, false)
            } else {
                playlistDao.removeSongFromPlaylist(playlistId, songId)
                val updated = playlistDao.getSongsForPlaylist(playlistId)
                _detailSongs.value = updated
                refreshPlaylistDetails()
            }
        }
    }

    // Add Song Dialog & Search
    private val _addSongDialogVisible = MutableStateFlow(false)
    val addSongDialogVisible: StateFlow<Boolean> = _addSongDialogVisible.asStateFlow()

    private val _searchToAddQuery = MutableStateFlow("")
    val searchToAddQuery: StateFlow<String> = _searchToAddQuery.asStateFlow()

    private val _searchToAddResults = MutableStateFlow<List<SongEntity>>(emptyList())
    val searchToAddResults: StateFlow<List<SongEntity>> = _searchToAddResults.asStateFlow()

    private val _isSearchingToAdd = MutableStateFlow(false)
    val isSearchingToAdd: StateFlow<Boolean> = _isSearchingToAdd.asStateFlow()

    fun openAddSongDialog() {
        _searchToAddQuery.value = ""
        _searchToAddResults.value = emptyList()
        _isSearchingToAdd.value = false
        _addSongDialogVisible.value = true
    }

    fun closeAddSongDialog() {
        _addSongDialogVisible.value = false
        _searchToAddQuery.value = ""
        _searchToAddResults.value = emptyList()
        _isSearchingToAdd.value = false
    }

    private var searchJob: Job? = null
    fun onSearchToAddQueryChanged(query: String) {
        _searchToAddQuery.value = query
        searchJob?.cancel()
        if (query.trim().length >= 2) {
            searchJob = viewModelScope.launch {
                _isSearchingToAdd.value = true
                try {
                    musicRepository.search(query.trim(), FilterType.SONGS).collect { results ->
                        _searchToAddResults.value = results
                        _isSearchingToAdd.value = false
                    }
                } catch (_: Exception) {
                    _isSearchingToAdd.value = false
                }
            }
        } else {
            _searchToAddResults.value = emptyList()
            _isSearchingToAdd.value = false
        }
    }
}
