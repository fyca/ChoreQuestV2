package com.chorequest.di

import android.content.Context
import androidx.room.Room
import com.chorequest.data.local.ChoreQuestDatabase
import com.chorequest.data.local.CustomRoomCallback
import com.chorequest.data.local.migrations.MIGRATION_1_2
import com.chorequest.data.remote.ChoreQuestApi
import com.chorequest.utils.Constants
import com.chorequest.data.remote.ActivityActionTypeDeserializer
import com.chorequest.data.remote.ChoreStatusDeserializer
import com.chorequest.data.remote.DeviceTypeDeserializer
import com.chorequest.data.remote.RewardRedemptionStatusDeserializer
import com.chorequest.data.remote.UserRoleDeserializer
import com.chorequest.domain.models.ActivityActionType
import com.chorequest.domain.models.ChoreStatus
import com.chorequest.domain.models.RewardRedemptionStatus
import com.chorequest.domain.models.DeviceType
import com.chorequest.domain.models.UserRole
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .registerTypeAdapter(UserRole::class.java, com.chorequest.data.remote.UserRoleTypeAdapter())
            .registerTypeAdapter(ChoreStatus::class.java, ChoreStatusDeserializer())
            .registerTypeAdapter(ActivityActionType::class.java, ActivityActionTypeDeserializer())
            .registerTypeAdapter(DeviceType::class.java, DeviceTypeDeserializer())
            .registerTypeAdapter(RewardRedemptionStatus::class.java, RewardRedemptionStatusDeserializer())
            .create()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        // Interceptor to remove trailing slash before query parameters for Apps Script compatibility
        // Also adds detailed logging to debug why requests might not reach Apps Script
        val urlFixInterceptor = okhttp3.Interceptor { chain ->
            val originalRequest = chain.request()
            val originalUrl = originalRequest.url // Property, not function
            
            // Log the original URL for debugging
            android.util.Log.d("ChoreQuestApi", "=== HTTP REQUEST ===")
            android.util.Log.d("ChoreQuestApi", "Method: ${originalRequest.method}")
            android.util.Log.d("ChoreQuestApi", "Original URL: $originalUrl")
            android.util.Log.d("ChoreQuestApi", "Headers: ${originalRequest.headers}")
            
            // If URL ends with /exec/?... or /exec/.?..., change it to /exec?...
            val urlString = originalUrl.toString()
            val fixedUrlString = urlString
                .replace("/exec/?", "/exec?")
                .replace("/exec/.?", "/exec?")
            
            val fixedUrl = if (urlString != fixedUrlString) {
                android.util.Log.d("ChoreQuestApi", "Fixed URL: $fixedUrlString")
                fixedUrlString.toHttpUrlOrNull() ?: originalUrl
            } else {
                originalUrl
            }
            
            val fixedRequest = originalRequest.newBuilder()
                .url(fixedUrl)
                .build()
            
            android.util.Log.d("ChoreQuestApi", "Final URL: ${fixedRequest.url}")
            android.util.Log.d("ChoreQuestApi", "===================")
            
            chain.proceed(fixedRequest)
        }

        return OkHttpClient.Builder()
            .addInterceptor(urlFixInterceptor) // Add URL fix before logging
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        // Apps Script URL without trailing slash (works in browser)
        // Retrofit requires base URLs to end with /, so we add it programmatically
        // The interceptor will remove it before the query parameters
        val baseUrlString = com.chorequest.utils.Constants.APPS_SCRIPT_WEB_APP_URL
        
        // Parse the URL and ensure it ends with / for Retrofit's requirement
        val httpUrl = baseUrlString.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid base URL: $baseUrlString")
        
        // Build the base URL with trailing slash for Retrofit
        val baseUrl = httpUrl.newBuilder()
            .addPathSegment("") // Ensures URL ends with / for Retrofit requirement
            .build()
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideChoreQuestApi(retrofit: Retrofit): ChoreQuestApi {
        return retrofit.create(ChoreQuestApi::class.java)
    }

    @Provides
    @Singleton
    fun provideChoreQuestDatabase(
        @ApplicationContext context: Context
    ): ChoreQuestDatabase {
        return Room.databaseBuilder(
            context,
            ChoreQuestDatabase::class.java,
            "chorequest_database_v2" // Changed name to force fresh database
        )
            .addCallback(CustomRoomCallback())
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideChoreDao(database: ChoreQuestDatabase) = database.choreDao()

    @Provides
    @Singleton
    fun provideRewardDao(database: ChoreQuestDatabase) = database.rewardDao()

    @Provides
    @Singleton
    fun provideUserDao(database: ChoreQuestDatabase) = database.userDao()

    @Provides
    @Singleton
    fun provideActivityLogDao(database: ChoreQuestDatabase) = database.activityLogDao()

    @Provides
    @Singleton
    fun provideTransactionDao(database: ChoreQuestDatabase) = database.transactionDao()

    @Provides
    @Singleton
    fun provideRewardRedemptionDao(database: ChoreQuestDatabase) = database.rewardRedemptionDao()
}
