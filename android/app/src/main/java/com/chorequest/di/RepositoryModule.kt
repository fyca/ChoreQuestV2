package com.chorequest.di

import com.chorequest.data.local.SessionManager
import com.chorequest.data.local.dao.*
import com.chorequest.data.remote.ChoreQuestApi
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
        transactionDao: TransactionDao
    ): AuthRepository {
        return AuthRepository(api, sessionManager, userDao, choreDao, rewardDao, activityLogDao, transactionDao)
    }

    @Provides
    @Singleton
    fun provideChoreRepository(
        api: ChoreQuestApi,
        choreDao: ChoreDao,
        sessionManager: SessionManager,
        gson: Gson
    ): ChoreRepository {
        return ChoreRepository(api, choreDao, sessionManager, gson)
    }
}
