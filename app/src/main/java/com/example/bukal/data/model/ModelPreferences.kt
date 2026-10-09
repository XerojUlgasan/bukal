package com.example.bukal.data.model

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.modelPreferencesDataStore by preferencesDataStore(name = "model_preferences")
private val SelectedQuizModelId = stringPreferencesKey("selected_quiz_model_id")

class ModelPreferences(context: Context) {
    private val dataStore = context.applicationContext.modelPreferencesDataStore

    val selectedQuizModelId: Flow<String?> = dataStore.data.map { preferences ->
        preferences[SelectedQuizModelId]
    }

    suspend fun selectQuizModel(modelId: String?) {
        dataStore.edit { preferences ->
            if (modelId == null) {
                preferences.remove(SelectedQuizModelId)
            } else {
                preferences[SelectedQuizModelId] = modelId
            }
        }
    }
}
