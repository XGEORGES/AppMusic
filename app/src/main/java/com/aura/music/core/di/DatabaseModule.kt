package com.aura.music.core.di

import android.content.Context
import com.aura.music.core.database.AuraDatabase
import com.aura.music.core.database.dao.PlaylistDao
import com.aura.music.core.database.dao.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AuraDatabase {
        return AuraDatabase.getInstance(context)
    }

    @Provides
    fun provideSongDao(database: AuraDatabase): SongDao {
        return database.songDao()
    }

    @Provides
    fun providePlaylistDao(database: AuraDatabase): PlaylistDao {
        return database.playlistDao()
    }
}
