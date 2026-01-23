package com.lostsierra.chorequest.di

import com.lostsierra.chorequest.data.local.SessionManager
import com.lostsierra.chorequest.data.local.dao.*
import com.lostsierra.chorequest.data.remote.ChoreQuestApi
import com.lostsierra.chorequest.data.repository.ActivityLogRepository
import com.lostsierra.chorequest.data.repository.AuthRepository
import com.lostsierra.chorequest.data.repository.ChoreRepository
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
        tokenManager: com.lostsierra.chorequest.data.drive.TokenManager,
        gson: Gson,
        driveApiService: com.lostsierra.chorequest.data.drive.DriveApiService
    ): AuthRepository {
        return AuthRepository(api, sessionManager, userDao, choreDao, rewardDao, activityLogDao, transactionDao, tokenManager, gson, driveApiService)
    }

    @Provides
    @Singleton
    fun provideChoreRepository(
        api: ChoreQuestApi,
        choreDao: ChoreDao,
        sessionManager: SessionManager,
        gson: Gson,
        driveApiService: com.lostsierra.chorequest.data.drive.DriveApiService,
        tokenManager: com.lostsierra.chorequest.data.drive.TokenManager,
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
        driveApiService: com.lostsierra.chorequest.data.drive.DriveApiService,
        tokenManager: com.lostsierra.chorequest.data.drive.TokenManager
    ): ActivityLogRepository {
        return ActivityLogRepository(api, activityLogDao, sessionManager, gson, driveApiService, tokenManager)
    }
}
