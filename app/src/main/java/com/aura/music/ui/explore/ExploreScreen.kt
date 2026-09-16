package com.aura.music.ui.explore

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import com.aura.music.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbUpOffAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import com.aura.music.core.license.LicenseStatus
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aura.music.core.database.entity.SongEntity
import com.aura.music.ui.theme.DarkCard
import com.aura.music.ui.theme.DarkSurface
import com.aura.music.ui.theme.DarkSurfaceVariant
import com.aura.music.ui.theme.OledBlack
import com.aura.music.ui.theme.TextMuted
import com.aura.music.ui.theme.TextPrimary
import com.aura.music.ui.theme.TextSecondary
import android.content.Intent
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onSongClick: (SongEntity) -> Unit,
    onPlaySongList: (List<SongEntity>) -> Unit = { songs -> if (songs.isNotEmpty()) onSongClick(songs.first()) },
    onPlaySongAtIndex: (List<SongEntity>, Int) -> Unit = { songs, index ->
        if (index in songs.indices) onSongClick(songs[index])
    },
    onPlayNext: (SongEntity) -> Unit = {},
    onAddToQueue: (SongEntity) -> Unit = {},
    onStartMix: (SongEntity) -> Unit = {},
    licenseStatus: LicenseStatus = LicenseStatus.Unlicensed,
    onOpenLicenseManagement: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedChip by viewModel.selectedChip.collectAsState()
    val isChipLoading by viewModel.isChipLoading.collectAsState()
    val chipPlaylists by viewModel.chipPlaylists.collectAsState()
    val chipSongs by viewModel.chipSongs.collectAsState()
    val similarTitle by viewModel.similarTitle.collectAsState()
    val shortcutItems by viewModel.shortcutItems.collectAsState()
    val quickPicks by viewModel.quickPicks.collectAsState()
    val similarPlaylists by viewModel.similarPlaylists.collectAsState()
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val longAudioMixes by viewModel.longAudioMixes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedSongForMenu by remember { mutableStateOf<SongEntity?>(null) }
    var songForPlaylistSelection by remember { mutableStateOf<SongEntity?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistNameInput by remember { mutableStateOf("") }
    var playlistSearchQuery by remember { mutableStateOf("") }

    // Rota la selección rápida cada vez que se ingresa a la pantalla
    LaunchedEffect(Unit) {
        viewModel.onRefreshScreen()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // 1. Cabecera principal: Logo George Music + Botón VIP/Licencia
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Logo oficial de Aura Music
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_round),
                        contentDescription = "Logo Aura Music",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Aura Music",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Botón Insignia de Licencia / VIP
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkSurfaceVariant, RoundedCornerShape(20.dp))
                        .clickable { onOpenLicenseManagement() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = "Licencia",
                        tint = when (licenseStatus) {
                            is LicenseStatus.Active -> if (licenseStatus.isLifetime) Color(0xFFFFD700) else Color(0xFF4ADE80)
                            is LicenseStatus.ExpiringSoon -> Color(0xFFFBBF24)
                            else -> Color(0xFFFF5555)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (licenseStatus) {
                            is LicenseStatus.Active -> if (licenseStatus.isLifetime) "VIP" else "${licenseStatus.daysRemaining}d"
                            is LicenseStatus.ExpiringSoon -> "${licenseStatus.daysRemaining}d"
                            else -> "Licencia"
                        },
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Banner preventivo si la licencia vence en 3 días o menos
        if (licenseStatus is LicenseStatus.ExpiringSoon) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2A1C04))
                        .border(1.dp, Color(0xFFD97706), RoundedCornerShape(10.dp))
                        .clickable { onOpenLicenseManagement() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alerta",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tu suscripción vence pronto (${licenseStatus.daysRemaining} días)",
                            color = Color(0xFFFEF3C7),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Toca aquí para pre-cargar tu código y no perder el servicio.",
                            color = Color(0xFFFDE68A),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // 2. Chips / Píldoras temáticas redondeadas (Podcasts, Relajación, Sueño, etc.)
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(viewModel.chips) { chip ->
                    val isSelected = chip == selectedChip
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectChip(chip) },
                        label = { Text(chip, fontSize = 13.sp) },
                        trailingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Desmarcar $chip",
                                    modifier = Modifier.size(14.dp),
                                    tint = Color.Black
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Color.Black,
                            containerColor = DarkSurfaceVariant,
                            labelColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // -------------------------------------------------------------
        // FEED ESPECÍFICO DE CHIP SELECCIONADO (Energía, Rock, Pop, etc.)
        // -------------------------------------------------------------
        if (selectedChip != null) {
            if (isChipLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Cargando música de $selectedChip...",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                // 1. Playlists del tema seleccionado
                if (chipPlaylists.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                            Text(
                                text = "Playlists de $selectedChip",
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 14.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(chipPlaylists) { playlist ->
                                    Column(
                                        modifier = Modifier
                                            .width(145.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onSongClick(playlist) }
                                    ) {
                                        Box(modifier = Modifier.size(145.dp)) {
                                            AsyncImage(
                                                model = playlist.thumbnailUrl,
                                                contentDescription = playlist.title,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(8.dp)
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Black.copy(alpha = 0.75f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Reproducir Playlist",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = playlist.title,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = playlist.artistName,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Éxitos y canciones del tema seleccionado
                if (chipSongs.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Éxitos de $selectedChip",
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(DarkSurfaceVariant)
                                    .clickable { onPlaySongList(chipSongs) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Reproducir todo",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        val chipSongColumns = chipSongs.chunked(4)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                        ) {
                            items(chipSongColumns) { columnSongs ->
                                Column(
                                    modifier = Modifier.width(290.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    columnSongs.forEach { song ->
                                        val songIdx = chipSongs.indexOf(song).coerceAtLeast(0)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { onPlaySongAtIndex(chipSongs, songIdx) },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = song.thumbnailUrl,
                                                contentDescription = song.title,
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
                                                    fontWeight = FontWeight.SemiBold,
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
                                            IconButton(onClick = { selectedSongForMenu = song }) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = null,
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
                }

                // Si no se encontró nada
                if (chipPlaylists.isEmpty() && chipSongs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No se encontraron listas ni canciones para $selectedChip",
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.selectChip(selectedChip!!) },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                                ) {
                                    Text("Volver al inicio", color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // FEED PRINCIPAL (Se muestra cuando no hay ningún chip activo)
        // -------------------------------------------------------------
        if (selectedChip == null) {
            // 3. Sección "Accesos directos" con flecha chevron
            item {
                Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Accesos directos",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 4. Pager de Accesos Directos (Páginas de 3x3 grids)
        if (shortcutItems.isNotEmpty()) {
            item {
                val pages = remember(shortcutItems) { shortcutItems.chunked(9) }
                val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })

                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth()
                    ) { pageIndex ->
                        val pageSongs = pages.getOrNull(pageIndex) ?: emptyList()
                        val rows = pageSongs.chunked(3)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            rows.forEach { rowSongs ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowSongs.forEach { song ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(DarkCard)
                                                .clickable { onSongClick(song) },
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            AsyncImage(
                                                model = song.thumbnailUrl,
                                                contentDescription = song.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            // Degradado oscuro para que el texto sea legible
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(38.dp)
                                                    .background(
                                                        Brush.verticalGradient(
                                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = song.title,
                                                    color = TextPrimary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                    // Rellenar espacios si la última fila tiene menos de 3
                                    repeat(3 - rowSongs.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    // Puntos indicadores de paginación sincronizados con el pager
                    if (pages.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(pages.size) { index ->
                                val isCurrent = pagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = if (isCurrent) 1f else 0.4f))
                                )
                                if (index < pages.size - 1) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }

        // 5. Sección "Selección rápida" con botón "Reproducir todo"
        if (quickPicks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Selección rápida",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSurfaceVariant)
                            .clickable {
                                onPlaySongList(quickPicks)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Reproducir todo",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Carrusel de bloques de 4 canciones
                val columns = quickPicks.chunked(4)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(columns) { columnList ->
                        Column(
                            modifier = Modifier.width(290.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            columnList.forEach { song ->
                                val songIdx = quickPicks.indexOf(song).coerceAtLeast(0)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onPlaySongAtIndex(quickPicks, songIdx) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = song.thumbnailUrl,
                                        contentDescription = song.title,
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
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(13.dp)
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
                                    IconButton(onClick = { selectedSongForMenu = song }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // 6. Sección "SIMILARES A" (Playlists recomendadas basadas en las playlist del usuario)
        if (similarTitle != null && similarPlaylists.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val isTrend = similarTitle.equals("Tendencias de Hoy", ignoreCase = true)
                    Text(
                        text = if (isTrend) "DESCUBRIR" else "SIMILARES A",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = similarTitle ?: "",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(similarPlaylists) { playlist ->
                            Column(
                                modifier = Modifier
                                    .width(150.dp)
                                    .clickable { onSongClick(playlist) }
                            ) {
                                AsyncImage(
                                    model = playlist.thumbnailUrl,
                                    contentDescription = playlist.title,
                                    modifier = Modifier
                                        .size(150.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = playlist.title,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = playlist.artistName,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // 7. Sección "Audios de larga duración" (Mixes / Remixes basados en gustos del usuario)
        if (longAudioMixes.isNotEmpty()) {
            item {
                Text(
                    text = "Audios de larga duración",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val mixColumns = longAudioMixes.chunked(4)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(mixColumns) { columnMixes ->
                        Column(
                            modifier = Modifier.width(290.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            columnMixes.forEach { mixSong ->
                                val songIdx = longAudioMixes.indexOf(mixSong).coerceAtLeast(0)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onPlaySongAtIndex(longAudioMixes, songIdx) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = mixSong.thumbnailUrl,
                                        contentDescription = mixSong.title,
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mixSong.title,
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${mixSong.artistName} • Mix Extendido",
                                                color = TextSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(onClick = { selectedSongForMenu = mixSong }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
        }

        // Estado de carga si es necesario
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    // Modal BottomSheet con las opciones de los 3 puntos (Fiel a la captura)
    if (selectedSongForMenu != null) {
        val song = selectedSongForMenu!!
        ModalBottomSheet(
            onDismissRequest = { selectedSongForMenu = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF1E1E1E)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Cabecera del BottomSheet: Título, Artista, Me Gusta y Cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artistName,
                            color = TextSecondary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            viewModel.toggleFavorite(song)
                            Toast.makeText(context, if (song.isFavorite) "Eliminado de favoritos" else "Añadido a favoritos", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = if (song.isFavorite) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                                contentDescription = "Me gusta",
                                tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else TextPrimary
                            )
                        }
                        IconButton(onClick = { selectedSongForMenu = null }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(16.dp))

                // Fila de 3 tarjetas de acción destacadas: Reproducir a continuación, Guardar en una playlist, Compartir
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Reproducir a continuación
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant)
                            .clickable {
                                onPlayNext(song)
                                selectedSongForMenu = null
                                Toast.makeText(context, "Se reproducirá a continuación", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 14.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Reproducir a continuación", color = TextPrimary, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 14.sp)
                        }
                    }

                    // 2. Guardar en una playlist
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant)
                            .clickable {
                                val targetSong = song
                                selectedSongForMenu = null
                                songForPlaylistSelection = targetSong
                                playlistSearchQuery = ""
                            }
                            .padding(vertical = 14.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.PlaylistAdd, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Guardar en una playlist", color = TextPrimary, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 14.sp)
                        }
                    }

                    // 3. Compartir
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant)
                            .clickable {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Escucha ${song.title} de ${song.artistName} en Aura Music: https://youtube.com/watch?v=${song.id}")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Compartir canción"))
                                selectedSongForMenu = null
                            }
                            .padding(vertical = 14.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Compartir", color = TextPrimary, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Lista de opciones verticales
                MenuOptionItem(
                    icon = Icons.Default.Radio,
                    title = "Comenzar mix",
                    onClick = {
                        onStartMix(song)
                        selectedSongForMenu = null
                    }
                )

                MenuOptionItem(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = "Agregar a la fila",
                    onClick = {
                        onAddToQueue(song)
                        selectedSongForMenu = null
                        Toast.makeText(context, "Añadida a la fila", Toast.LENGTH_SHORT).show()
                    }
                )

                MenuOptionItem(
                    icon = Icons.Default.BookmarkBorder,
                    title = "Guardar en la biblioteca",
                    onClick = {
                        viewModel.saveToLibrary(song)
                        selectedSongForMenu = null
                        Toast.makeText(context, "Guardado en la biblioteca", Toast.LENGTH_SHORT).show()
                    }
                )

                MenuOptionItem(
                    icon = Icons.Default.Download,
                    title = "Descargar",
                    onClick = {
                        viewModel.downloadSong(song) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                        selectedSongForMenu = null
                        Toast.makeText(context, "Descarga iniciada...", Toast.LENGTH_SHORT).show()
                    }
                )

                MenuOptionItem(
                    icon = Icons.Default.PushPin,
                    title = "Fijar en Accesos directos",
                    onClick = {
                        viewModel.pinToShortcuts(song)
                        selectedSongForMenu = null
                        Toast.makeText(context, "Fijado en Accesos directos", Toast.LENGTH_SHORT).show()
                    }
                )

                MenuOptionItem(
                    icon = Icons.Default.DoNotDisturb,
                    title = "No me interesa",
                    onClick = {
                        viewModel.dismissSong(song.id)
                        selectedSongForMenu = null
                        Toast.makeText(context, "No te mostraremos más esta sugerencia", Toast.LENGTH_SHORT).show()
                    }
                )

                MenuOptionItem(
                    icon = Icons.Default.Close,
                    title = "No quiero recomendaciones de este artista",
                    onClick = {
                        viewModel.dismissSong(song.id)
                        selectedSongForMenu = null
                        Toast.makeText(context, "Ocultando recomendaciones de ${song.artistName}", Toast.LENGTH_SHORT).show()
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Modal BottomSheet: Seleccionar playlist para agregar la canción con barra de búsqueda
    if (songForPlaylistSelection != null) {
        val targetSong = songForPlaylistSelection!!
        val filteredPlaylists = userPlaylists.filter {
            playlistSearchQuery.isBlank() || it.name.contains(playlistSearchQuery, ignoreCase = true)
        }

        ModalBottomSheet(
            onDismissRequest = { songForPlaylistSelection = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF1E1E1E)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Guardar en una playlist",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { songForPlaylistSelection = null }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Opción rápida: "Nueva playlist"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .clickable {
                            newPlaylistNameInput = ""
                            showCreatePlaylistDialog = true
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(text = "Nueva playlist", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Campo de búsqueda para encontrar playlists de biblioteca
                if (userPlaylists.isNotEmpty()) {
                    OutlinedTextField(
                        value = playlistSearchQuery,
                        onValueChange = { playlistSearchQuery = it },
                        placeholder = { Text("Buscar entre tus playlists...", color = TextMuted, fontSize = 13.sp) },
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
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Lista de playlists existentes del usuario
                if (userPlaylists.isEmpty()) {
                    Text(
                        text = "Aún no tienes playlists creadas. Pulsa 'Nueva playlist' arriba para crear una.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else if (filteredPlaylists.isEmpty()) {
                    Text(
                        text = "No se encontraron playlists que coincidan con la búsqueda.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(filteredPlaylists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.addSongToPlaylist(targetSong, playlist.playlistId) {
                                            // Éxito
                                        }
                                        Toast.makeText(context, "Añadida a '${playlist.name}'", Toast.LENGTH_SHORT).show()
                                        songForPlaylistSelection = null
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (playlist.isImported) "Playlist importada" else "Playlist personal",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Diálogo Modal para crear nueva playlist desde el menú de 3 puntos
    if (showCreatePlaylistDialog && songForPlaylistSelection != null) {
        val targetSong = songForPlaylistSelection!!
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Nueva playlist",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Introduce un nombre para la nueva playlist:",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = newPlaylistNameInput,
                        onValueChange = { newPlaylistNameInput = it },
                        placeholder = { Text("Ej. Mis Favoritas", color = TextMuted, fontSize = 13.sp) },
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
                    onClick = {
                        val name = newPlaylistNameInput.trim()
                        if (name.isNotEmpty()) {
                            viewModel.createPlaylistAndAddSong(name, targetSong)
                            Toast.makeText(context, "Playlist '$name' creada y canción agregada", Toast.LENGTH_SHORT).show()
                            showCreatePlaylistDialog = false
                            songForPlaylistSelection = null
                        }
                    },
                    enabled = newPlaylistNameInput.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = DarkCard
                    )
                ) {
                    Text("Crear y Guardar", color = if (newPlaylistNameInput.trim().isNotEmpty()) OledBlack else TextMuted)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun MenuOptionItem(
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
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

