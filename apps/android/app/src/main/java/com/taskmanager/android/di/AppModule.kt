package com.taskmanager.android.di

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.taskmanager.android.data.api.ApiServiceFactory
import com.taskmanager.android.data.local.LocalCacheMapper
import com.taskmanager.android.data.local.TaskManagerDao
import com.taskmanager.android.data.local.TaskManagerDatabase
import com.taskmanager.android.data.preferences.AppPreferencesStore
import com.taskmanager.android.data.sync.TaskSyncScheduler
import com.taskmanager.android.data.sync.WorkManagerTaskSyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import androidx.core.app.NotificationManagerCompat

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
        encodeDefaults = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
            .build()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TaskManagerDatabase =
        Room.databaseBuilder(
            context,
            TaskManagerDatabase::class.java,
            "task_manager_cache.db",
        ).addMigrations(
            TaskManagerDatabase.MIGRATION_1_2,
            TaskManagerDatabase.MIGRATION_2_3,
            TaskManagerDatabase.MIGRATION_3_4,
        )
            .build()

    @Provides
    fun provideTaskManagerDao(database: TaskManagerDatabase): TaskManagerDao = database.taskManagerDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    @Provides
    @Singleton
    fun provideNotificationManager(@ApplicationContext context: Context): NotificationManagerCompat =
        NotificationManagerCompat.from(context)

    @Provides
    @Singleton
    fun provideLocalCacheMapper(json: Json): LocalCacheMapper = LocalCacheMapper(json)

    @Provides
    @Singleton
    fun provideTaskSyncScheduler(impl: WorkManagerTaskSyncScheduler): TaskSyncScheduler = impl
}
