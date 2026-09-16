package com.aura.music.ui.library

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.aura.music.core.database.entity.PlaylistEntity
import com.aura.music.core.database.entity.SongEntity
import com.aura.music.ui.theme.DarkCard
import com.aura.music.ui.theme.DarkSurface
import com.aura.music.ui.theme.DarkSurfaceVariant
import com.aura.music.ui.theme.OledBlack
import com.aura.music.ui.theme.TextMuted
import com.aura.music.ui.theme.TextPrimary
import com.aura.music.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onSongListClick: (title: String, songs: List<SongEntity>) -> Unit,
    onPlayPlaylistIndex: (songs: List<SongEntity>, index: Int) -> Unit = { songs, index ->
        if (songs.isNotEmpty() && index in songs.indices) {
            onSongListClick(songs[index].title, songs.drop(index) + songs.take(index))
        }
    },
    onPlaySong: (SongEntity) -> Unit = {},
    onPlayNext: (List<SongEntity>) -> Unit = {},
    onShufflePlay: (List<SongEntity>) -> Unit = {},
    onAddToQueue: (SongEntity) -> Unit = {},
    onStartMix: (SongEntity) -> Unit = {},
    onPinToShortcuts: (SongEntity) -> Unit = {},
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val selectedTab by viewModel.selectedTab.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val playlistItems by viewModel.playlistUiItems.collectAsState()

    val selectedPlaylistDetail by viewModel.selectedPlaylistDetail.collectAsState()
    val detailSongs by viewModel.detailSongs.collectAsState()

    val dialogVisible by viewModel.importDialogVisible.collectAsState()
    val importUrl by viewModel.importUrl.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importError by viewModel.importError.collectAsState()

    val createPlaylistDialogVisible by viewModel.createPlaylistDialogVisible.collectAsState()
    val newPlaylistName by viewModel.newPlaylistName.collectAsState()

    val editingPlaylist by viewModel.editingPlaylist.collectAsState()
    val editingSongs by viewModel.editingSongs.collectAsState()

    var showFabMenu by remember { mutableStateOf(false) }
    var selectedPlaylistForMenu by remember { mutableStateOf<PlaylistUiItem?>(null) }
    var selectedFavoriteSongForMenu by remember { mutableStateOf<SongEntity?>(null) }
    var sharingPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    var isGridView by remember { mutableStateOf(false) }

    if (selectedPlaylistDetail != null) {
        PlaylistDetailView(
            playlist = selectedPlaylistDetail!!,
            songs = detailSongs,
            viewModel = viewModel,
            onBackClick = { viewModel.closePlaylistDetail() },
            onPlayPlaylist = onSongListClick,
            onPlayPlaylistIndex = onPlayPlaylistIndex,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onStartMix = onStartMix,
            onPinToShortcuts = onPinToShortcuts,
            onSearchClick = onSearchClick,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(OledBlack)
        ) {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // 1. Barra superior: Título "Biblioteca" e iconos a la derecha (Historial, Buscar, Perfil)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Biblioteca",
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 2. Fila superior de chips: "Playlists", "Canciones", "Descargas"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == LibraryTab.PLAYLISTS,
                    onClick = { viewModel.selectTab(LibraryTab.PLAYLISTS) },
                    label = {
                        Text(
                            text = "Playlists",
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == LibraryTab.PLAYLISTS) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.White,
                        selectedLabelColor = Color.Black,
                        containerColor = DarkSurfaceVariant,
                        labelColor = TextPrimary
                    ),
                    border = null
                )

                FilterChip(
                    selected = selectedTab == LibraryTab.SONGS,
                    onClick = { viewModel.selectTab(LibraryTab.SONGS) },
                    label = {
                        Text(
                            text = "Canciones",
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == LibraryTab.SONGS) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.White,
                        selectedLabelColor = Color.Black,
                        containerColor = DarkSurfaceVariant,
                        labelColor = TextPrimary
                    ),
                    border = null
                )

                FilterChip(
                    selected = selectedTab == LibraryTab.DOWNLOADS,
                    onClick = { viewModel.selectTab(LibraryTab.DOWNLOADS) },
                    label = {
                        Text(
                            text = "Descargas",
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == LibraryTab.DOWNLOADS) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.White,
                        selectedLabelColor = Color.Black,
                        containerColor = DarkSurfaceVariant,
                        labelColor = TextPrimary
                    ),
                    border = null
                )
            }

            // 3. Fila de ordenación: "Actividad reciente v" y selector de vista (Foto 1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var showSortMenu by remember { mutableStateOf(false) }
                val currentSortOrder by viewModel.sortOrder.collectAsState()

                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showSortMenu = true }
                    ) {
                        Text(
                            text = currentSortOrder.displayName,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(DarkSurface)
                    ) {
                        LibrarySortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = order.displayName,
                                        color = if (order == currentSortOrder) MaterialTheme.colorScheme.primary else TextPrimary,
                                        fontWeight = if (order == currentSortOrder) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                },
                                onClick = {
                                    viewModel.setSortOrder(order)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = { isGridView = !isGridView }) {
                    Icon(
                        imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Cambiar vista",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 4. Contenido principal según pestaña seleccionada
            when (selectedTab) {
                LibraryTab.PLAYLISTS -> {
                    if (isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 120.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // "Música que te gustó" (sin menú de 3 puntos)
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { viewModel.openFavoritesDetail() }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(Color(0xFF8B5CF6), Color(0xFFD946EF))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ThumbUp,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(44.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Música que te gustó",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "📌 Playlist • ${favorites.size} canciones",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            // Playlists del usuario
                            items(playlistItems) { item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            viewModel.openPlaylistDetail(item.playlist)
                                        }
                                ) {
                                    if (!item.thumbnailUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = item.thumbnailUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(150.dp)
                                                .clip(RoundedCornerShape(10.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(150.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(DarkSurfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(44.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (item.isPinned) {
                                                    Icon(
                                                        imageVector = Icons.Default.PushPin,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier
                                                            .size(12.dp)
                                                            .padding(end = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    text = item.playlist.name,
                                                    color = TextPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = "${item.songCount} pistas",
                                                color = TextSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        }
                                        IconButton(
                                            onClick = { selectedPlaylistForMenu = item },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // "Música que te gustó" (Foto 1, sin menú 3 puntos)
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.openFavoritesDetail() }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(Color(0xFF8B5CF6), Color(0xFFD946EF))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ThumbUp,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Música que te gustó",
                                            color = TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PushPin,
                                                contentDescription = null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Playlist • ${favorites.size} canciones",
                                                color = TextSecondary,
                                                fontSize = 13.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            // Playlists del usuario
                            if (playlistItems.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Aún no tienes playlists creadas.\nPulsa el botón (+) para crear una o importar desde YouTube.",
                                            color = TextMuted,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                items(playlistItems) { item ->
                                    val playlist = item.playlist
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.openPlaylistDetail(playlist)
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (!item.thumbnailUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = item.thumbnailUrl,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(DarkSurfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.QueueMusic,
                                                    contentDescription = null,
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (item.isPinned) {
                                                    Icon(
                                                        imageVector = Icons.Default.PushPin,
                                                        contentDescription = "Fijada",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier
                                                            .size(13.dp)
                                                            .padding(end = 4.dp)
                                                    )
                                                }
                                                Text(
                                                    text = playlist.name,
                                                    color = TextPrimary,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = "Playlist • ${item.songCount} pistas",
                                                color = TextSecondary,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        IconButton(onClick = { selectedPlaylistForMenu = item }) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Opciones",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                LibraryTab.SONGS -> {
                    // Vista de canciones favoritas guardadas en la biblioteca
                    if (favorites.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No tienes canciones en tu biblioteca",
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Guarda canciones desde el menú de opciones (⋮) de una canción o dales Me gusta para encontrarlas aquí.",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${favorites.size} canciones",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )

                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(DarkSurfaceVariant)
                                        .clickable { onShufflePlay(favorites) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = null,
                                        tint = TextPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Aleatorio", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(favorites) { song ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onPlaySong(song) }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = song.thumbnailUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = song.title,
                                                color = TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${song.artistName} • ${formatDuration(song.durationSeconds)}",
                                                color = TextSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        IconButton(onClick = { selectedFavoriteSongForMenu = song }) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Opciones",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                LibraryTab.DOWNLOADS -> {
                    // Vista de canciones descargadas
                    if (downloads.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No tienes canciones descargadas aún",
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Descarga canciones o playlists completas desde el menú de opciones (⋮) para escucharlas sin conexión a internet.",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${downloads.size} canciones descargadas",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )

                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(DarkSurfaceVariant)
                                        .clickable { onShufflePlay(downloads) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = null,
                                        tint = TextPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Aleatorio", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(downloads) { index, song ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onPlayPlaylistIndex(downloads, index) }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = song.thumbnailUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = song.title,
                                                color = TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Descargada",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = song.artistName,
                                                    color = TextSecondary,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                viewModel.deleteDownload(song.id)
                                                Toast.makeText(context, "Descarga eliminada", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Eliminar descarga",
                                                tint = TextMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Botón flotante (+) blanco con cruz negra (Foto 1)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
        ) {
            FloatingActionButton(
                onClick = { showFabMenu = true },
                containerColor = Color.White,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier
                    .size(56.dp)
                    .testTag("ImportPlaylistButton")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Crear o importar playlist",
                    tint = Color.Black,
                    modifier = Modifier.size(30.dp)
                )
            }

            DropdownMenu(
                expanded = showFabMenu,
                onDismissRequest = { showFabMenu = false },
                modifier = Modifier.background(DarkSurface)
            ) {
                DropdownMenuItem(
                    text = { Text("Crear nueva playlist", color = TextPrimary) },
                    onClick = {
                        showFabMenu = false
                        viewModel.showCreatePlaylistDialog()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Importar de YouTube por URL", color = TextPrimary) },
                    onClick = {
                        showFabMenu = false
                        viewModel.showImportDialog()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.QueueMusic, contentDescription = null, tint = TextSecondary)
                    }
                )
            }
        }
    }

    // 6. Bottom Sheet de Opciones de Playlist (Foto 2)
    if (selectedPlaylistForMenu != null) {
        val playlistItem = selectedPlaylistForMenu!!
        val playlist = playlistItem.playlist
        val canShare = playlist.isImported || !playlist.originalUrl.isNullOrBlank()

        ModalBottomSheet(
            onDismissRequest = { selectedPlaylistForMenu = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = DarkSurface,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 32.dp, start = 16.dp, end = 16.dp)
            ) {
                // Encabezado: Título y conteo de pistas (sin nombre de autor)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            color = TextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${playlistItem.songCount} pistas",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }

                    IconButton(onClick = { selectedPlaylistForMenu = null }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Fila de botones destacados: "Reproducir a continuación" y "Compartir" (si es importada)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Botón 1: Reproducir a continuación
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedPlaylistForMenu = null
                                viewModel.loadPlaylistSongs(playlist.playlistId) { songs ->
                                    if (songs.isNotEmpty()) {
                                        onPlayNext(songs)
                                        Toast.makeText(context, "Se reproducirá a continuación", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "La playlist está vacía", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistPlay,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Reproducir a\ncontinuación",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 13.sp
                            )
                        }
                    }

                    // Botón 2: Compartir (disponible solo si es importada de YouTube)
                    if (canShare) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val current = playlist
                                    selectedPlaylistForMenu = null
                                    sharingPlaylist = current
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Compartir",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Lista vertical de opciones según Foto 2
                BottomSheetActionRow(
                    icon = Icons.Default.Shuffle,
                    title = "Reproducir aleatoriamente",
                    onClick = {
                        selectedPlaylistForMenu = null
                        viewModel.loadPlaylistSongs(playlist.playlistId) { songs ->
                            if (songs.isNotEmpty()) {
                                onShufflePlay(songs)
                            } else {
                                Toast.makeText(context, "La playlist está vacía", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                BottomSheetActionRow(
                    icon = Icons.Default.Radio,
                    title = "Comenzar mix",
                    onClick = {
                        selectedPlaylistForMenu = null
                        viewModel.loadPlaylistSongs(playlist.playlistId) { songs ->
                            if (songs.isNotEmpty()) {
                                onStartMix(songs.random())
                            } else {
                                Toast.makeText(context, "La playlist está vacía", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                BottomSheetActionRow(
                    icon = Icons.Default.Edit,
                    title = "Editar playlist",
                    onClick = {
                        selectedPlaylistForMenu = null
                        viewModel.startEditingPlaylist(playlist)
                    }
                )

                BottomSheetActionRow(
                    icon = Icons.Default.FileDownload,
                    title = "Descargar",
                    onClick = {
                        selectedPlaylistForMenu = null
                        Toast.makeText(context, "Iniciando descarga de '${playlist.name}'...", Toast.LENGTH_SHORT).show()
                        viewModel.downloadPlaylist(playlist.playlistId) { count ->
                            Toast.makeText(context, "$count canciones listas para escuchar offline", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                BottomSheetActionRow(
                    icon = Icons.Default.Delete,
                    title = "Borrar playlist",
                    onClick = {
                        selectedPlaylistForMenu = null
                        playlistToDelete = playlist
                    }
                )

                BottomSheetActionRow(
                    icon = Icons.Default.PushPin,
                    title = if (playlistItem.isPinned) "Desfijar de Accesos directos" else "Fijar en Accesos directos",
                    onClick = {
                        selectedPlaylistForMenu = null
                        viewModel.togglePinPlaylist(playlist.playlistId)
                        val msg = if (playlistItem.isPinned) "Playlist desfijada" else "Playlist fijada"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // Bottom Sheet de Opciones para canción de la pestaña "Canciones"
    if (selectedFavoriteSongForMenu != null) {
        val song = selectedFavoriteSongForMenu!!
        ModalBottomSheet(
            onDismissRequest = { selectedFavoriteSongForMenu = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = DarkSurface,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 32.dp, start = 16.dp, end = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${song.artistName} • ${formatDuration(song.durationSeconds)}",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { selectedFavoriteSongForMenu = null }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = DarkSurfaceVariant.copy(alpha = 0.5f),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant)
                            .clickable {
                                selectedFavoriteSongForMenu = null
                                onPlayNext(listOf(song))
                                Toast.makeText(context, "Se reproducirá a continuación", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PlaylistPlay,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Reproducir a\ncontinuación",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant)
                            .clickable {
                                selectedFavoriteSongForMenu = null
                                onStartMix(song)
                                Toast.makeText(context, "Iniciando mix de '${song.title}'...", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Comenzar mix",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                BottomSheetActionRow(
                    icon = Icons.Default.FileDownload,
                    title = "Descargar",
                    onClick = {
                        selectedFavoriteSongForMenu = null
                        Toast.makeText(context, "Descarga iniciada...", Toast.LENGTH_SHORT).show()
                        viewModel.downloadSong(song) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                BottomSheetActionRow(
                    icon = Icons.Default.Favorite,
                    title = "Quitar de la biblioteca",
                    onClick = {
                        selectedFavoriteSongForMenu = null
                        viewModel.removeSongFromDetailPlaylist(song.id, -1L)
                        Toast.makeText(context, "Canción eliminada de tu biblioteca", Toast.LENGTH_SHORT).show()
                    }
                )

                BottomSheetActionRow(
                    icon = Icons.Default.PushPin,
                    title = "Fijar en Accesos directos",
                    onClick = {
                        selectedFavoriteSongForMenu = null
                        onPinToShortcuts(song)
                        Toast.makeText(context, "Fijado en Accesos directos", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // 7. Diálogo "Compartir" (Solo para playlists de YouTube, con copia de enlace y share nativo)
    if (sharingPlaylist != null) {
        val playlist = sharingPlaylist!!
        val shareUrl = playlist.originalUrl ?: "https://www.youtube.com/playlist?list=${playlist.playlistId}"

        AlertDialog(
            onDismissRequest = { sharingPlaylist = null },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Compartir playlist",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = playlist.name,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Opción: Copiar enlace al portapapeles
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant)
                            .clickable {
                                clipboardManager.setText(AnnotatedString(shareUrl))
                                Toast.makeText(context, "Enlace copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Copiar enlace",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = shareUrl,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copiar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Opción: Compartir en aplicaciones nativas de Android (WhatsApp, Instagram, etc.)
                    Button(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, "$shareUrl\nEscucha esta playlist: ${playlist.name}")
                                putExtra(Intent.EXTRA_SUBJECT, playlist.name)
                                type = "text/plain"
                            }
                            val shareChooser = Intent.createChooser(sendIntent, "Compartir playlist")
                            context.startActivity(shareChooser)
                            sharingPlaylist = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = OledBlack,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Compartir con otras apps",
                            color = OledBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { sharingPlaylist = null }) {
                    Text("Cerrar", color = TextSecondary)
                }
            }
        )
    }

    // 8. Diálogo de confirmación para eliminar playlist
    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Eliminar playlist",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "¿Seguro que deseas eliminar la playlist '${playlistToDelete?.name}'?",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        playlistToDelete?.let { viewModel.deletePlaylist(it.playlistId) }
                        playlistToDelete = null
                        Toast.makeText(context, "Playlist eliminada", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    // 9. Diálogo Modal "Crear nueva playlist"
    if (createPlaylistDialogVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCreatePlaylistDialog() },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Crear nueva playlist",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Introduce el nombre de la nueva playlist:",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { viewModel.onPlaylistNameChanged(it) },
                        placeholder = { Text("Ej. Favoritas del mes", color = TextMuted, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = DarkSurfaceVariant,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.createPlaylist() },
                    enabled = newPlaylistName.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = DarkCard
                    )
                ) {
                    Text("Crear", color = if (newPlaylistName.trim().isNotEmpty()) OledBlack else TextMuted)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCreatePlaylistDialog() }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    // 10. Diálogo Modal "Importar playlist por URL"
    if (dialogVisible) {
        val isValid = viewModel.isUrlValid(importUrl)

        AlertDialog(
            onDismissRequest = { if (!isImporting) viewModel.dismissImportDialog() },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Importar playlist por URL",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Pega el enlace de una playlist de YouTube para importarla a tu biblioteca local:",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = importUrl,
                        onValueChange = { viewModel.onUrlChanged(it) },
                        placeholder = { Text("https://www.youtube.com/playlist?list=...", color = TextMuted, fontSize = 13.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ImportUrlTextField"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = DarkSurfaceVariant,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    if (importError != null) {
                        Text(
                            text = importError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    if (isImporting) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                            Text("Extrayendo e importando canciones...", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.importPlaylist() },
                    enabled = isValid && !isImporting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = DarkCard
                    ),
                    modifier = Modifier.testTag("ConfirmImportButton")
                ) {
                    Text("Importar", color = if (isValid && !isImporting) OledBlack else TextMuted)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissImportDialog() },
                    enabled = !isImporting
                ) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    // 11. Vista Modal "Editar playlist" (reordenar por arrastre y soltar)
    if (editingPlaylist != null) {
        val playlist = editingPlaylist!!
        Dialog(
            onDismissRequest = { viewModel.dismissEditingPlaylist() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            PlaylistEditView(
                playlist = playlist,
                initialSongs = editingSongs,
                viewModel = viewModel,
                onDismiss = { viewModel.dismissEditingPlaylist() },
                onPlayPlaylist = onSongListClick
            )
        }
    }
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0:00"
    val minutes = seconds / 60
    val remainingSecs = seconds % 60
    return "$minutes:${remainingSecs.toString().padStart(2, '0')}"
}

private fun formatTotalDuration(seconds: Long): String {
    if (seconds <= 0) return "0 min"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) {
        "${hours} h ${minutes} min"
    } else {
        "${minutes} min"
    }
}

@Composable
private fun PlaylistCollageCover(
    songs: List<SongEntity>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(DarkSurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (songs.size >= 4) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AsyncImage(
                        model = songs[0].thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    AsyncImage(
                        model = songs[1].thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AsyncImage(
                        model = songs[2].thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    AsyncImage(
                        model = songs[3].thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        } else if (songs.isNotEmpty()) {
            AsyncImage(
                model = songs[0].thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.QueueMusic,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(64.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailView(
    playlist: PlaylistEntity,
    songs: List<SongEntity>,
    viewModel: LibraryViewModel,
    onBackClick: () -> Unit,
    onPlayPlaylist: (title: String, songs: List<SongEntity>) -> Unit,
    onPlayPlaylistIndex: (songs: List<SongEntity>, index: Int) -> Unit,
    onPlayNext: (List<SongEntity>) -> Unit,
    onAddToQueue: (SongEntity) -> Unit,
    onStartMix: (SongEntity) -> Unit = {},
    onPinToShortcuts: (SongEntity) -> Unit = {},
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { onBackClick() }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renamingPlaylistName by remember(playlist.name) { mutableStateOf(playlist.name) }
    var selectedSongForMenu by remember { mutableStateOf<SongEntity?>(null) }

    val addSongDialogVisible by viewModel.addSongDialogVisible.collectAsState()
    val searchToAddQuery by viewModel.searchToAddQuery.collectAsState()
    val searchToAddResults by viewModel.searchToAddResults.collectAsState()
    val isSearchingToAdd by viewModel.isSearchingToAdd.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Barra superior: solo botón retroceso <-
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Regresar",
                        tint = TextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Carátula Collage o individual
                item {
                    PlaylistCollageCover(
                        songs = songs,
                        modifier = Modifier
                            .padding(top = 4.dp, bottom = 14.dp)
                            .size(210.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                // 2. Nombre de la playlist
                item {
                    Text(
                        text = playlist.name,
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )
                }

                // 3. Estadísticas (canciones y duración)
                item {
                    val totalSecs = songs.sumOf { it.durationSeconds }
                    Text(
                        text = "${songs.size} canciones • ${formatTotalDuration(totalSecs)}",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 16.dp)
                    )
                }

                // 4. Fila de 3 botones de acción: Descargar (izq), Play (centro), Lápiz (der)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botón 1: Descargar (a la izquierda)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant)
                                .clickable {
                                    Toast.makeText(context, "Descargando canciones de '${playlist.name}'...", Toast.LENGTH_SHORT).show()
                                    viewModel.downloadPlaylist(playlist.playlistId) { count ->
                                        Toast.makeText(context, "$count canciones listas para escuchar offline", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Descargar playlist",
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Botón 2: Play grande blanco (en medio)
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable {
                                    if (songs.isNotEmpty()) {
                                        onPlayPlaylist(playlist.name, songs)
                                    } else {
                                        Toast.makeText(context, "La playlist está vacía", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Reproducir",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Botón 3: Lápiz (a la derecha) - solo si no es la lista de favoritos
                        if (playlist.playlistId != -1L) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(DarkSurfaceVariant)
                                    .clickable {
                                        renamingPlaylistName = playlist.name
                                        showRenameDialog = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Editar nombre",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // 6. Botón "Agregar una canción"
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewModel.openAddSongDialog() }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Text(
                            text = "Agregar una canción",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // 7. Lista de canciones
                itemsIndexed(songs) { index, song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPlayPlaylistIndex(songs, index) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${song.artistName} • ${formatDuration(song.durationSeconds)}",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = { selectedSongForMenu = song }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Opciones",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Diálogo Renombrar Playlist (botón lápiz)
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Editar nombre de playlist",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Introduce el nuevo nombre para esta playlist:",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = renamingPlaylistName,
                        onValueChange = { renamingPlaylistName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = DarkSurfaceVariant,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renamingPlaylistName.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.renamePlaylist(playlist.playlistId, trimmed)
                            showRenameDialog = false
                            Toast.makeText(context, "Nombre actualizado", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Guardar", color = OledBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    // Menú de opciones de canción en el detalle
    if (selectedSongForMenu != null) {
        val song = selectedSongForMenu!!
        ModalBottomSheet(
            onDismissRequest = { selectedSongForMenu = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = DarkSurface,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 32.dp, start = 16.dp, end = 16.dp)
            ) {
                // Cabecera: Título, Artista • Duración, y botón cerrar X
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${song.artistName} • ${formatDuration(song.durationSeconds)}",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { selectedSongForMenu = null }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = DarkSurfaceVariant.copy(alpha = 0.5f),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Fila de tarjetas superiores: "Reproducir a continuación" y "Comenzar mix"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tarjeta 1: Reproducir a continuación
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant)
                            .clickable {
                                selectedSongForMenu = null
                                onPlayNext(listOf(song))
                                Toast.makeText(context, "Se reproducirá a continuación", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PlaylistPlay,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Reproducir a\ncontinuación",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    // Tarjeta 2: Comenzar mix
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant)
                            .clickable {
                                selectedSongForMenu = null
                                onStartMix(song)
                                Toast.makeText(context, "Iniciando mix de '${song.title}'...", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Comenzar mix",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Lista de opciones:
                // 1. Descargar
                BottomSheetActionRow(
                    icon = Icons.Default.FileDownload,
                    title = "Descargar",
                    onClick = {
                        selectedSongForMenu = null
                        Toast.makeText(context, "Descarga iniciada...", Toast.LENGTH_SHORT).show()
                        viewModel.downloadSong(song) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // 2. Quitar de la playlist
                BottomSheetActionRow(
                    icon = Icons.Default.Delete,
                    title = if (playlist.playlistId == -1L) "Quitar de Me gusta" else "Quitar de la playlist",
                    onClick = {
                        selectedSongForMenu = null
                        viewModel.removeSongFromDetailPlaylist(song.id, playlist.playlistId)
                        val msg = if (playlist.playlistId == -1L) "Canción eliminada de tus Me gusta" else "Canción eliminada de la playlist"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )

                // 3. Fijar en Accesos directos
                BottomSheetActionRow(
                    icon = Icons.Default.PushPin,
                    title = "Fijar en Accesos directos",
                    onClick = {
                        selectedSongForMenu = null
                        onPinToShortcuts(song)
                        Toast.makeText(context, "Fijado en Accesos directos", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // Modal para "Agregar una canción"
    if (addSongDialogVisible) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeAddSongDialog() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = DarkSurface,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Agregar canción",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { viewModel.closeAddSongDialog() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchToAddQuery,
                    onValueChange = { viewModel.onSearchToAddQueryChanged(it) },
                    placeholder = { Text("Buscar canción en YouTube Music...", color = TextMuted, fontSize = 14.sp) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                    },
                    trailingIcon = {
                        if (searchToAddQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchToAddQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Borrar", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isSearchingToAdd) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (searchToAddResults.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchToAddResults) { song ->
                            val isAlreadyIn = songs.any { it.id == song.id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceVariant)
                                    .clickable {
                                        if (!isAlreadyIn) {
                                            viewModel.addSongToDetailPlaylist(song, playlist.playlistId) {
                                                Toast.makeText(context, "'${song.title}' agregada a la playlist", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = song.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artistName,
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isAlreadyIn) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Ya en playlist",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            viewModel.addSongToDetailPlaylist(song, playlist.playlistId) {
                                                Toast.makeText(context, "'${song.title}' agregada a la playlist", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Agregar",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (searchToAddQuery.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No se encontraron resultados", color = TextMuted, fontSize = 14.sp)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Escribe el nombre de una canción o artista para buscar", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomSheetActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun DragHandleIcon(
    modifier: Modifier = Modifier,
    color: Color = TextSecondary
) {
    Column(
        modifier = modifier.size(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(2.dp)
                .background(color, RoundedCornerShape(1.dp))
        )
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(2.dp)
                .background(color, RoundedCornerShape(1.dp))
        )
    }
}

@Composable
fun PlaylistEditView(
    playlist: PlaylistEntity,
    initialSongs: List<SongEntity>,
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit,
    onPlayPlaylist: (title: String, songs: List<SongEntity>) -> Unit
) {
    BackHandler { onDismiss() }

    var songs by remember(initialSongs) { mutableStateOf(initialSongs) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragDeltaY by remember { mutableStateOf(0f) }

    val currentSongsList = rememberUpdatedState(songs)
    val density = LocalDensity.current
    val itemHeightDp = 64.dp
    val itemHeightPx = with(density) { itemHeightDp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp)
                .padding(horizontal = 16.dp)
        ) {
            // 1. Barra superior: Flecha retroceso <- | Título playlist | "Listo"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Atrás",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = playlist.name,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                )

                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Listo",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 2. Sub-barra: conteo de pistas y "Arrastra para reordenar"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${songs.size} pistas",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Arrastra para reordenar",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            // 3. Lista de canciones arrastrables
            if (songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Esta playlist no tiene canciones",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    itemsIndexed(
                        items = songs,
                        key = { _, song -> song.id }
                    ) { index, song ->
                        val isBeingDragged = draggingIndex == index
                        val translationY = if (isBeingDragged) dragDeltaY else 0f
                        val zIndexVal = if (isBeingDragged) 5f else 1f
                        val currentIdxState = rememberUpdatedState(index)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(itemHeightDp)
                                .zIndex(zIndexVal)
                                .graphicsLayer {
                                    this.translationY = translationY
                                    if (isBeingDragged) {
                                        this.shadowElevation = with(density) { 8.dp.toPx() }
                                    }
                                }
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isBeingDragged) DarkSurfaceVariant else Color.Transparent)
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Carátula
                            AsyncImage(
                                model = song.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            // Título y artista • duración
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${song.artistName} • ${formatDuration(song.durationSeconds)}",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Botón de eliminar canción de la playlist
                            IconButton(
                                onClick = {
                                    val songId = song.id
                                    viewModel.removeSongFromEditingPlaylist(songId)
                                    songs = songs.filter { it.id != songId }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Eliminar de la playlist",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Icono arrastrable de 2 líneas horizontales (=)
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .pointerInput(song.id) {
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                draggingIndex = currentIdxState.value
                                                dragDeltaY = 0f
                                            },
                                            onDragEnd = {
                                                draggingIndex = null
                                                dragDeltaY = 0f
                                                viewModel.savePlaylistOrder(playlist.playlistId, currentSongsList.value)
                                            },
                                            onDragCancel = {
                                                draggingIndex = null
                                                dragDeltaY = 0f
                                                viewModel.savePlaylistOrder(playlist.playlistId, currentSongsList.value)
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                dragDeltaY += dragAmount
                                                val activeIdx = draggingIndex ?: return@detectVerticalDragGestures
                                                val list = currentSongsList.value.toMutableList()

                                                if (dragDeltaY > itemHeightPx * 0.5f && activeIdx < list.size - 1) {
                                                    val nextIdx = activeIdx + 1
                                                    java.util.Collections.swap(list, activeIdx, nextIdx)
                                                    songs = list
                                                    draggingIndex = nextIdx
                                                    dragDeltaY -= itemHeightPx
                                                    viewModel.savePlaylistOrder(playlist.playlistId, list)
                                                } else if (dragDeltaY < -itemHeightPx * 0.5f && activeIdx > 0) {
                                                    val prevIdx = activeIdx - 1
                                                    java.util.Collections.swap(list, activeIdx, prevIdx)
                                                    songs = list
                                                    draggingIndex = prevIdx
                                                    dragDeltaY += itemHeightPx
                                                    viewModel.savePlaylistOrder(playlist.playlistId, list)
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                DragHandleIcon(color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        // Botón Play flotante blanco abajo a la derecha (según Foto)
        FloatingActionButton(
            onClick = {
                if (songs.isNotEmpty()) {
                    onPlayPlaylist(playlist.name, songs)
                }
            },
            containerColor = Color.White,
            contentColor = Color.Black,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp)
                .size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Reproducir playlist",
                tint = Color.Black,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
