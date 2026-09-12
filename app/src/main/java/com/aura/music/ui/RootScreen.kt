package com.aura.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aura.music.ui.explore.ExploreScreen
import com.aura.music.ui.explore.ExploreViewModel
import com.aura.music.ui.library.LibraryScreen
import com.aura.music.ui.library.LibraryViewModel
import com.aura.music.ui.navigation.NavigationDestination
import com.aura.music.ui.player.ExpandedPlayerView
import com.aura.music.ui.player.MiniPlayerBar
import com.aura.music.ui.player.PlayerViewModel
import com.aura.music.ui.search.SearchScreen
import com.aura.music.ui.search.SearchViewModel
import com.aura.music.ui.theme.DarkSurface
import com.aura.music.ui.theme.OledBlack
import com.aura.music.ui.theme.TextMuted
import com.aura.music.ui.theme.TextPrimary

@Composable
fun RootScreen(
    playerViewModel: PlayerViewModel,
    exploreViewModel: ExploreViewModel,
    searchViewModel: SearchViewModel,
    libraryViewModel: LibraryViewModel,
    modifier: Modifier = Modifier
) {
    var currentDestination by remember { mutableStateOf(NavigationDestination.HOME) }

    val currentSong by playerViewModel.currentPlayingSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val isLoading by playerViewModel.isLoading.collectAsState()
    val playbackPosition by playerViewModel.playbackPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    val queue by playerViewModel.queue.collectAsState()
    val repeatMode by playerViewModel.repeatMode.collectAsState()
    val shuffleEnabled by playerViewModel.shuffleModeEnabled.collectAsState()
    val isInfiniteRadioEnabled by playerViewModel.isInfiniteRadioEnabled.collectAsState()
    val isExpanded by playerViewModel.isExpanded.collectAsState()

    val progress = if (duration > 0) playbackPosition.toFloat() / duration.toFloat() else 0f

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OledBlack)
                    .navigationBarsPadding()
            ) {
                // MiniPlayer animado encima de la barra de navegación
                AnimatedVisibility(
                    visible = currentSong != null,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    MiniPlayerBar(
                        song = currentSong,
                        isPlaying = isPlaying,
                        progress = progress,
                        isLoading = isLoading,
                        onPlayPauseClick = { playerViewModel.togglePlayPause() },
                        onNextClick = { playerViewModel.seekToNext() },
                        onPreviousClick = { playerViewModel.seekToPrevious() },
                        onClick = { playerViewModel.setExpanded(true) }
                    )
                }

                // Barra de navegación con 3 destinos (Principal, Buscar, Biblioteca)
                NavigationBar(
                    containerColor = OledBlack,
                    tonalElevation = 0.dp
                ) {
                    NavigationDestination.entries.forEach { destination ->
                        val selected = destination == currentDestination
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentDestination = destination },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.icon else destination.unselectedIcon,
                                    contentDescription = destination.title
                                )
                            },
                            label = {
                                Text(
                                    text = destination.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                unselectedIconColor = Color.White,
                                unselectedTextColor = Color.White.copy(alpha = 0.7f),
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(OledBlack)
        ) {
            when (currentDestination) {
                NavigationDestination.HOME -> {
                    ExploreScreen(
                        viewModel = exploreViewModel,
                        onSongClick = { song -> playerViewModel.playSong(song) },
                        onPlayNext = { song -> playerViewModel.playNext(song) },
                        onAddToQueue = { song -> playerViewModel.addToQueue(song) },
                        onStartMix = { song -> playerViewModel.startMix(song) }
                    )
                }
                NavigationDestination.SEARCH -> {
                    SearchScreen(
                        viewModel = searchViewModel,
                        onSongClick = { song -> playerViewModel.playSong(song) },
                        onPlayNext = { song -> playerViewModel.playNext(song) },
                        onStartMix = { song -> playerViewModel.startMix(song) },
                        onPinToShortcuts = { song -> exploreViewModel.pinToShortcuts(song) }
                    )
                }
                NavigationDestination.LIBRARY -> {
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        onSongListClick = { _, songs ->
                            if (songs.isNotEmpty()) {
                                playerViewModel.setQueue(songs, 0)
                            }
                        },
                        onPlayPlaylistIndex = { songs, index ->
                            if (songs.isNotEmpty()) {
                                playerViewModel.setQueue(songs, index)
                            }
                        },
                        onPlaySong = { song -> playerViewModel.playSong(song) },
                        onPlayNext = { songs -> playerViewModel.playNext(songs) },
                        onShufflePlay = { songs -> playerViewModel.shufflePlay(songs) },
                        onAddToQueue = { song -> playerViewModel.addToQueue(song) },
                        onStartMix = { song -> playerViewModel.startMix(song) },
                        onPinToShortcuts = { song -> exploreViewModel.pinToShortcuts(song) },
                        onSearchClick = { currentDestination = NavigationDestination.SEARCH }
                    )
                }
            }
        }
    }

    // Full Player expandido
    if (isExpanded && currentSong != null) {
        Dialog(
            onDismissRequest = { playerViewModel.setExpanded(false) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ExpandedPlayerView(
                song = currentSong,
                isPlaying = isPlaying,
                currentPosition = playbackPosition,
                duration = duration,
                queue = queue,
                repeatMode = repeatMode,
                shuffleEnabled = shuffleEnabled,
                isLoading = isLoading,
                onDismiss = { playerViewModel.setExpanded(false) },
                onPlayPauseClick = { playerViewModel.togglePlayPause() },
                onNextClick = { playerViewModel.seekToNext() },
                onPreviousClick = { playerViewModel.seekToPrevious() },
                onSeekTo = { playerViewModel.seekTo(it) },
                onToggleFavorite = { playerViewModel.toggleFavorite(it) },
                onCycleRepeat = { playerViewModel.cycleRepeatMode() },
                onToggleShuffle = { playerViewModel.toggleShuffle() },
                onSongClick = { song -> playerViewModel.playSong(song) },
                isInfiniteRadioEnabled = isInfiniteRadioEnabled,
                onToggleInfiniteRadio = { playerViewModel.setInfiniteRadioEnabled(it) }
            )
        }
    }
}
