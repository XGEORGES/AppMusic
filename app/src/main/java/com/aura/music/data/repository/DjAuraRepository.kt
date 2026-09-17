package com.aura.music.data.repository

import androidx.room.withTransaction
import com.aura.music.core.database.AuraDatabase
import com.aura.music.core.database.dao.PlaylistDao
import com.aura.music.core.database.dao.SongDao
import com.aura.music.core.database.entity.PlaylistEntity
import com.aura.music.core.database.entity.PlaylistSongCrossRef
import com.aura.music.core.database.entity.SongEntity
import com.aura.music.data.extractor.YouTubeMusicSource
import com.aura.music.data.gemini.DjAdjustmentType
import com.aura.music.data.gemini.DjMixResponse
import com.aura.music.data.gemini.DjSessionContext
import com.aura.music.data.gemini.DjTrackItem
import com.aura.music.data.gemini.GeminiDjService
import com.aura.music.data.model.FilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DjProgress {
    data class Loading(val message: String) : DjProgress
    data class FirstSongReady(
        val firstSong: SongEntity,
        val shoutout: String,
        val comment: String,
        val vibeTag: String,
        val promptUsed: String
    ) : DjProgress
    data class FullMixReady(
        val songs: List<SongEntity>,
        val shoutout: String,
        val comment: String,
        val vibeTag: String,
        val promptUsed: String
    ) : DjProgress
    data class Error(val message: String) : DjProgress
}

@Singleton
open class DjAuraRepository @Inject constructor(
    private val geminiDjService: GeminiDjService,
    private val youTubeMusicSource: YouTubeMusicSource,
    private val database: AuraDatabase,
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao
) {
    val sessionContext = DjSessionContext()

    private suspend fun getUserTasteProfile(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val favorites = songDao.getFavoritesSync().take(20)
            val topPlayed = songDao.getTopPlayedSync(20)
            val playlistSongs = runCatching {
                val playlists = playlistDao.getPlaylistsSync().take(5)
                playlists.flatMap { playlistDao.getSongsForPlaylist(it.playlistId).take(5) }
            }.getOrDefault(emptyList())

            (favorites + topPlayed + playlistSongs)
                .map { "${it.artistName} - ${it.title}".trim() }
                .filter { it.isNotBlank() && it != "-" }
                .distinct()
                .shuffled()
                .take(15)
        }.getOrDefault(emptyList())
    }

    fun generateMix(prompt: String): Flow<DjProgress> = flow {
        emit(DjProgress.Loading("DJ Aura está sintonizando la vibra..."))
        sessionContext.reset(prompt)

        val tasteProfile = getUserTasteProfile()
        val mixResult = geminiDjService.generateMix(prompt, tasteProfile)
        if (mixResult.isFailure) {
            val errorMsg = mixResult.exceptionOrNull()?.message ?: "Error al conectar con DJ Aura"
            emit(DjProgress.Error(errorMsg))
            return@flow
        }

        val mix = mixResult.getOrThrow()
        resolveMixTracks(mix, prompt, this)
    }.flowOn(Dispatchers.IO)

    fun adjustMix(adjustment: DjAdjustmentType): Flow<DjProgress> = flow {
        emit(DjProgress.Loading("DJ Aura está reajustando la pista..."))

        val tasteProfile = getUserTasteProfile()
        val mixResult = geminiDjService.adjustMix(sessionContext, adjustment, tasteProfile)
        if (mixResult.isFailure) {
            val errorMsg = mixResult.exceptionOrNull()?.message ?: "Error al reajustar con DJ Aura"
            emit(DjProgress.Error(errorMsg))
            return@flow
        }

        val mix = mixResult.getOrThrow()
        resolveMixTracks(mix, sessionContext.originalPrompt, this)
    }.flowOn(Dispatchers.IO)

    fun extendMix(): Flow<DjProgress> = flow {
        emit(DjProgress.Loading("DJ Aura está encolando la siguiente tanda para que la música no pare..."))

        val tasteProfile = getUserTasteProfile()
        val mixResult = geminiDjService.extendMix(sessionContext, tasteProfile)
        if (mixResult.isFailure) {
            val errorMsg = mixResult.exceptionOrNull()?.message ?: "Error al extender la sesión con DJ Aura"
            emit(DjProgress.Error(errorMsg))
            return@flow
        }

        val mix = mixResult.getOrThrow()
        resolveMixTracks(mix, sessionContext.originalPrompt, this)
    }.flowOn(Dispatchers.IO)

    private suspend fun resolveMixTracks(
        mix: DjMixResponse,
        promptUsed: String,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<DjProgress>
    ) {
        val tracks = mix.tracks
        if (tracks.isEmpty()) {
            flowCollector.emit(DjProgress.Error("DJ Aura no encontró temas para esta vibra."))
            return
        }

        // 1. Resolver el primer tema disponible de inmediato
        var firstResolvedSong: SongEntity? = null
        var firstResolvedIndex = -1

        for (i in tracks.indices) {
            val track = tracks[i]
            val resolved = searchSingleSong("${track.title} ${track.artist}")
            if (resolved != null) {
                firstResolvedSong = resolved
                firstResolvedIndex = i
                break
            }
        }

        if (firstResolvedSong == null) {
            flowCollector.emit(DjProgress.Error("No se pudieron encontrar las canciones sugeridas en YouTube Music."))
            return
        }

        // Emitir primera canción lista para reproducir de una vez
        flowCollector.emit(
            DjProgress.FirstSongReady(
                firstSong = firstResolvedSong,
                shoutout = mix.djShoutout,
                comment = mix.djComment,
                vibeTag = mix.vibeTag,
                promptUsed = promptUsed
            )
        )

        // 2. Resolver el resto de temas en paralelo
        val remainingTracks = tracks.filterIndexed { index, _ -> index != firstResolvedIndex }
        val deferredResolved = withContext(Dispatchers.IO) {
            remainingTracks.map { track ->
                async {
                    searchSingleSong("${track.title} ${track.artist}")
                }
            }.awaitAll().filterNotNull()
        }

        val fullPlaylist = mutableListOf(firstResolvedSong)
        fullPlaylist.addAll(deferredResolved)

        // Guardar canciones en caché local Room para que tengan acceso rápido
        runCatching {
            songDao.insertOrUpdate(fullPlaylist)
        }

        flowCollector.emit(
            DjProgress.FullMixReady(
                songs = fullPlaylist,
                shoutout = mix.djShoutout,
                comment = mix.djComment,
                vibeTag = mix.vibeTag,
                promptUsed = promptUsed
            )
        )
    }

    private suspend fun searchSingleSong(query: String): SongEntity? {
        return try {
            val results = youTubeMusicSource.search(query, FilterType.SONGS)
            if (results.isNotEmpty()) {
                results.first().toEntity()
            } else {
                // Fallback a búsqueda general si SONGS no devuelve nada
                val generalResults = youTubeMusicSource.search(query, FilterType.ALL)
                generalResults.firstOrNull()?.toEntity()
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveMixAsPlaylist(
        name: String,
        description: String,
        songs: List<SongEntity>
    ): Long = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext -1L

        database.withTransaction {
            val playlist = PlaylistEntity(
                name = name,
                description = description,
                isImported = false
            )
            val playlistId = playlistDao.insertPlaylist(playlist)
            songDao.insertOrUpdate(songs)

            val crossRefs = songs.mapIndexed { index, song ->
                PlaylistSongCrossRef(
                    playlistId = playlistId,
                    songId = song.id,
                    positionInPlaylist = index
                )
            }
            playlistDao.insertPlaylistSongCrossRefs(crossRefs)
            playlistId
        }
    }

    fun recordSongPlayed(song: SongEntity) {
        sessionContext.recordPlayed("${song.title} - ${song.artistName}")
    }

    fun recordSongSkipped(song: SongEntity) {
        sessionContext.recordSkipped("${song.title} - ${song.artistName}")
    }
}
