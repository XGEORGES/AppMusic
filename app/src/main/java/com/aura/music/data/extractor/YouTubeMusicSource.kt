package com.aura.music.data.extractor

import com.aura.music.core.network.DownloaderImpl
import com.aura.music.data.model.AudioStreamInfo
import com.aura.music.data.model.ExtractedPlaylistData
import com.aura.music.data.model.FilterType
import com.aura.music.data.model.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

open class YouTubeMusicSource(
    private val downloader: Downloader = DownloaderImpl()
) {
    init {
        initNewPipe(downloader)
    }

    companion object {
        private var isInitialized = false

        fun initNewPipe(downloader: Downloader) {
            synchronized(this) {
                if (!isInitialized) {
                    NewPipe.init(downloader)
                    isInitialized = true
                }
            }
        }
    }

    open suspend fun search(query: String, filter: FilterType = FilterType.ALL): List<SongItem> =
        withContext(Dispatchers.IO) {
            val qhFactory = ServiceList.YouTube.searchQHFactory
            val filterList = when (filter) {
                FilterType.SONGS -> listOf("music_songs")
                FilterType.COMMUNITY_PLAYLISTS -> listOf("playlists")
                FilterType.FEATURED_PLAYLISTS -> listOf("music_playlists")
                FilterType.PLAYLISTS -> listOf("playlists")
                FilterType.ARTISTS -> listOf("channels")
                FilterType.ALBUMS -> listOf("music_albums")
                else -> emptyList()
            }

            val queryHandler = if (filterList.isEmpty()) {
                qhFactory.fromQuery(query)
            } else {
                qhFactory.fromQuery(query, filterList, "")
            }

            var searchInfo = SearchInfo.getInfo(ServiceList.YouTube, queryHandler)
            var items = searchInfo.relatedItems

            // Fallback para canciones en caso de que music_songs no devuelva resultados
            if (filter == FilterType.SONGS && items.isEmpty()) {
                val fallbackHandler = qhFactory.fromQuery(query, listOf("videos"), "")
                val fallbackInfo = SearchInfo.getInfo(ServiceList.YouTube, fallbackHandler)
                items = fallbackInfo.relatedItems
            }

            items.mapNotNull { item ->
                when (item) {
                    is StreamInfoItem -> mapStreamItemToSong(item)
                    is PlaylistInfoItem -> mapPlaylistItemToSong(item)
                    else -> null
                }
            }
        }

    open suspend fun getStreamUrl(videoId: String): AudioStreamInfo =
        withContext(Dispatchers.IO) {
            val url = if (videoId.startsWith("http://") || videoId.startsWith("https://")) {
                videoId
            } else {
                "https://www.youtube.com/watch?v=$videoId"
            }

            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, url)
            val audioStreams = streamInfo.audioStreams

            val bestAudio = audioStreams.maxByOrNull {
                if (it.averageBitrate > 0) it.averageBitrate else it.bitrate
            } ?: throw IllegalStateException("No audio stream found for videoId: $videoId")

            val finalBitrate = if (bestAudio.averageBitrate > 0) bestAudio.averageBitrate else bestAudio.bitrate

            AudioStreamInfo(
                url = bestAudio.url.orEmpty(),
                format = bestAudio.format?.name ?: "m4a",
                bitrate = finalBitrate,
                durationSeconds = streamInfo.duration
            )
        }

    open suspend fun extractPlaylist(urlOrId: String): ExtractedPlaylistData =
        withContext(Dispatchers.IO) {
            val url = if (urlOrId.startsWith("http://") || urlOrId.startsWith("https://")) {
                urlOrId
            } else {
                "https://www.youtube.com/playlist?list=$urlOrId"
            }

            val playlistInfo = PlaylistInfo.getInfo(ServiceList.YouTube, url)
            val songs = playlistInfo.relatedItems.map { streamItem ->
                mapStreamItemToSong(streamItem)
            }

            val thumbUrl = playlistInfo.thumbnails?.firstOrNull()?.url.orEmpty()

            ExtractedPlaylistData(
                id = playlistInfo.url ?: urlOrId,
                name = playlistInfo.name.orEmpty(),
                thumbnailUrl = thumbUrl,
                songs = songs
            )
        }

    open suspend fun getSongRadio(videoId: String): List<SongItem> =
        withContext(Dispatchers.IO) {
            val url = if (videoId.startsWith("http://") || videoId.startsWith("https://")) {
                videoId
            } else {
                "https://www.youtube.com/watch?v=$videoId"
            }

            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, url)
            val relatedItems = streamInfo.relatedItems

            relatedItems.filterIsInstance<StreamInfoItem>().map { streamItem ->
                mapStreamItemToSong(streamItem)
            }
        }

    fun mapStreamItemToSong(item: StreamInfoItem): SongItem {
        val rawUrl = item.url.orEmpty()
        val extractedId = if (rawUrl.contains("v=")) {
            rawUrl.substringAfter("v=").substringBefore("&")
        } else if (rawUrl.contains("youtu.be/")) {
            rawUrl.substringAfter("youtu.be/").substringBefore("?")
        } else {
            rawUrl
        }

        val thumb = item.thumbnails?.maxByOrNull { it.width }?.url
            ?: item.thumbnails?.firstOrNull()?.url
            ?: "https://img.youtube.com/vi/$extractedId/hqdefault.jpg"

        return SongItem(
            id = extractedId,
            title = item.name.orEmpty(),
            artistName = item.uploaderName.orEmpty(),
            artistId = item.uploaderUrl,
            durationSeconds = item.duration,
            thumbnailUrl = thumb
        )
    }

    fun mapPlaylistItemToSong(item: PlaylistInfoItem): SongItem {
        val rawUrl = item.url.orEmpty()
        val extractedId = if (rawUrl.contains("list=")) {
            rawUrl.substringAfter("list=").substringBefore("&")
        } else {
            rawUrl
        }
        val thumb = item.thumbnails?.maxByOrNull { it.width }?.url
            ?: item.thumbnails?.firstOrNull()?.url
            ?: "https://img.youtube.com/vi/default/hqdefault.jpg"

        val countStr = if (item.streamCount > 0) " (${item.streamCount} canciones)" else ""
        val uploader = item.uploaderName ?: "Playlist"

        return SongItem(
            id = if (extractedId.startsWith("PL")) extractedId else "PL_$extractedId",
            title = item.name.orEmpty(),
            artistName = "$uploader$countStr",
            artistId = null,
            durationSeconds = item.streamCount,
            thumbnailUrl = thumb
        )
    }
}
