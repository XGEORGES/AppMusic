package com.aura.music.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.core.database.entity.SongEntity
import com.aura.music.data.model.FilterType
import com.aura.music.data.repository.MusicRepository
import com.aura.music.data.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playerRepository: PlayerRepository
) : ViewModel() {

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
}
