package com.example.service

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "video_positions")

object VideoPositionStore {
    suspend fun savePosition(context: Context, videoPath: String, positionMs: Long) {
        val key = longPreferencesKey(videoPath)
        context.dataStore.edit { preferences ->
            preferences[key] = positionMs
        }
    }

    suspend fun getPosition(context: Context, videoPath: String): Long {
        val key = longPreferencesKey(videoPath)
        return context.dataStore.data.map { preferences ->
            preferences[key] ?: 0L
        }.first()
    }
}
