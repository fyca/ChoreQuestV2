package com.chorequest.di

import com.chorequest.data.local.SessionManager
import com.chorequest.data.local.dao.*
import com.chorequest.data.remote.ChoreQuestApi
import com.chorequest.data.repository.ActivityLogRepository
import com.chorequest.data.repository.AuthRepository
import com.chorequest.data.repository.ChoreRepository
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        api: ChoreQuestApi,
        sessionManager: SessionManager,
        userDao: UserDao,
        choreDao: ChoreDao,
        rewardDao: RewardDao,
        activityLogDao: ActivityLogDao,
        transactionDao: TransactionDao,
        tokenManager: com.chorequest.data.drive.TokenManager
    ): AuthRepository {
        return AuthRepository(api, sessionManager, userDao, choreDao, rewardDao, activityLogDao, transactionDao, tokenManager)
    }

    @Provides
    @Singleton
    fun provideChoreRepository(
        api: ChoreQuestApi,
        choreDao: ChoreDao,
        sessionManager: SessionManager,
        gson: Gson,
        driveApiService: com.chorequest.data.drive.DriveApiService,
        tokenManager: com.chorequest.data.drive.TokenManager,
        userDao: UserDao
    ): ChoreRepository {
        return ChoreRepository(api, choreDao, sessionManager, gson, driveApiService, tokenManager, userDao)
    }

    @Provides
    @Singleton
    fun provideActivityLogRepository(
        api: ChoreQuestApi,
        activityLogDao: ActivityLogDao,
        sessionManager: SessionManager,
        gson: Gson,
        driveApiService: com.chorequest.data.drive.DriveApiService,
        tokenManager: com.chorequest.data.drive.TokenManager
    ): ActivityLogRepository {
        return ActivityLogRepository(api, activityLogDao, sessionManager, gson, driveApiService, tokenManager)
    }
}
