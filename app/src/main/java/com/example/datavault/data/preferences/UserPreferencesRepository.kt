package com.example.datavault.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val VIEW_TYPE = stringPreferencesKey("view_type")
        val SORT_BY = stringPreferencesKey("sort_by")
        val SORT_ASCENDING = booleanPreferencesKey("sort_ascending")
        val SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
    }

    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.THEME_MODE] ?: "system"
    }

    val viewType: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.VIEW_TYPE] ?: "list"
    }

    val sortBy: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SORT_BY] ?: "name"
    }

    val sortAscending: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SORT_ASCENDING] ?: true
    }

    val showHiddenFiles: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SHOW_HIDDEN_FILES] ?: false
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode
        }
    }

    suspend fun setViewType(type: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIEW_TYPE] = type
        }
    }

    suspend fun setSortBy(sortBy: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_BY] = sortBy
        }
    }

    suspend fun setSortAscending(ascending: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_ASCENDING] = ascending
        }
    }

    suspend fun setShowHiddenFiles(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_HIDDEN_FILES] = show
        }
    }
}
