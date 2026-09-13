package com.aura.music.core.di

import com.aura.music.core.network.DownloaderImpl
import com.aura.music.data.extractor.YouTubeMusicSource
import com.aura.music.core.network.NetworkTimeInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.downloader.Downloader
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(networkTimeInterceptor: NetworkTimeInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(networkTimeInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideDownloader(client: OkHttpClient): Downloader {
        return DownloaderImpl(client)
    }

    @Provides
    @Singleton
    fun provideYouTubeMusicSource(downloader: Downloader): YouTubeMusicSource {
        return YouTubeMusicSource(downloader)
    }
}
