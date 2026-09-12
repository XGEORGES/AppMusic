package com.aura.music.core.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.aura.music.core.network.DownloaderImpl
import java.io.File

@OptIn(UnstableApi::class)
object MediaCacheManager {

    private const val CACHE_SIZE = 2048L * 1024L * 1024L // 2048 MB = 2 GB

    @Volatile
    private var simpleCacheInstance: SimpleCache? = null

    fun getCache(context: Context): SimpleCache {
        return simpleCacheInstance ?: synchronized(this) {
            simpleCacheInstance ?: run {
                val cacheDir = File(context.cacheDir, "aura_media_cache")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                val evictor = LeastRecentlyUsedCacheEvictor(CACHE_SIZE)
                val databaseProvider = StandaloneDatabaseProvider(context)
                val cache = SimpleCache(cacheDir, evictor, databaseProvider)
                simpleCacheInstance = cache
                cache
            }
        }
    }

    fun createCacheDataSourceFactory(context: Context): DataSource.Factory {
        val cache = getCache(context)
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(DownloaderImpl.USER_AGENT)
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)

        val upstreamFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
