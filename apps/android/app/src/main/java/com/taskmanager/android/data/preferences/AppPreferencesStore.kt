package com.taskmanager.android.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.taskmanager.android.BuildConfig
import com.taskmanager.android.data.api.ApiServiceFactory
import com.taskmanager.android.model.AppPreferences
import com.taskmanager.android.model.NewTaskPlacement
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.taskManagerPreferencesDataStore by preferencesDataStore(name = "task_manager_preferences")

@Singleton
class AppPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val showCompletedKey = booleanPreferencesKey("show_completed")
    private val newTaskPlacementKey = stringPreferencesKey("new_task_placement")
    private val dailyNotificationEnabledKey = booleanPreferencesKey("daily_notification_enabled")
    private val dailyNotificationTimeKey = stringPreferencesKey("daily_notification_time")
    private val baseUrlKey = stringPreferencesKey("api_base_url")

    val preferences: Flow<AppPreferences> = context.taskManagerPreferencesDataStore.data.map { preferences ->
        AppPreferences(
            showCompleted = preferences[showCompletedKey] ?: true,
            newTaskPlacement = NewTaskPlacement.fromWire(preferences[newTaskPlacementKey]),
            dailyNotificationEnabled = preferences[dailyNotificationEnabledKey] ?: false,
            dailyNotificationTime = preferences[dailyNotificationTimeKey] ?: "09:00",
            baseUrl = ApiServiceFactory.normalizeBaseUrl(
                preferences[baseUrlKey] ?: BuildConfig.DEFAULT_API_BASE_URL,
            ),
        )
    }

    suspend fun currentPreferences(): AppPreferences = preferences.first()

    suspend fun setShowCompleted(value: Boolean) {
        update { mutablePreferences: MutablePreferences ->
            mutablePreferences[showCompletedKey] = value
        }
    }

    suspend fun setNewTaskPlacement(value: NewTaskPlacement) {
        update { mutablePreferences: MutablePreferences ->
            mutablePreferences[newTaskPlacementKey] = value.wire
        }
    }

    suspend fun setDailyNotificationEnabled(value: Boolean) {
        update { mutablePreferences: MutablePreferences ->
            mutablePreferences[dailyNotificationEnabledKey] = value
        }
    }

    suspend fun setDailyNotificationTime(value: String) {
        update { mutablePreferences: MutablePreferences ->
            mutablePreferences[dailyNotificationTimeKey] = value
        }
    }

    suspend fun setBaseUrl(value: String) {
        update { mutablePreferences: MutablePreferences ->
            mutablePreferences[baseUrlKey] = ApiServiceFactory.normalizeBaseUrl(value)
        }
    }

    private suspend fun update(transform: (MutablePreferences) -> Unit) {
        context.taskManagerPreferencesDataStore.edit { preferences ->
            transform(preferences)
        }
    }
}
