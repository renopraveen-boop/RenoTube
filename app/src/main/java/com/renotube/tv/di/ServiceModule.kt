package com.renotube.tv.di

import com.liskovsoft.mediaserviceinterfaces.ContentService
import com.liskovsoft.mediaserviceinterfaces.LiveChatService
import com.liskovsoft.mediaserviceinterfaces.MediaItemService
import com.liskovsoft.mediaserviceinterfaces.RemoteControlService
import com.liskovsoft.mediaserviceinterfaces.ServiceManager
import com.liskovsoft.mediaserviceinterfaces.SignInService
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideServiceManager(): ServiceManager {
        return YouTubeServiceManager.instance()
    }

    @Provides
    @Singleton
    fun provideContentService(serviceManager: ServiceManager): ContentService {
        return serviceManager.contentService
    }

    @Provides
    @Singleton
    fun provideMediaItemService(serviceManager: ServiceManager): MediaItemService {
        return serviceManager.mediaItemService
    }

    @Provides
    @Singleton
    fun provideSignInService(serviceManager: ServiceManager): SignInService {
        return serviceManager.signInService
    }

    @Provides
    @Singleton
    fun provideRemoteControlService(serviceManager: ServiceManager): RemoteControlService {
        return serviceManager.remoteControlService
    }

    @Provides
    @Singleton
    fun provideLiveChatService(serviceManager: ServiceManager): LiveChatService {
        return serviceManager.liveChatService
    }

    @Provides
    @Singleton
    fun providePlaybackQueueManager(): com.renotube.tv.player.PlaybackQueueManager {
        return com.renotube.tv.player.PlaybackQueueManager()
    }
}
