package com.stepnote.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stepnote.app.domain.model.SourceScope
import com.stepnote.app.domain.model.StepSourceKind
import com.stepnote.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stepnote_prefs")

data class UserPreferences(
    val dailyGoal: Int = 10_000,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val sourceScope: SourceScope = SourceScope.THIS_PHONE_ONLY,
    val preferredSourceKind: StepSourceKind = StepSourceKind.HEALTH_CONNECT,
    val strideCm: Int = 70,
    val showCalories: Boolean = false,
    val weightKg: Float? = null,
    val onboardingCompleted: Boolean = false,
    val lastSyncAt: Instant? = null
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SOURCE_SCOPE = stringPreferencesKey("source_scope")
        val PREFERRED_SOURCE = stringPreferencesKey("preferred_source")
        val STRIDE_CM = intPreferencesKey("stride_cm")
        val SHOW_CALORIES = booleanPreferencesKey("show_calories")
        val WEIGHT_KG = floatPreferencesKey("weight_kg")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LAST_SYNC_EPOCH_MILLIS = longPreferencesKey("last_sync_epoch_millis")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            dailyGoal = (prefs[Keys.DAILY_GOAL] ?: 10_000).coerceIn(1_000, 30_000),
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            sourceScope = runCatching {
                SourceScope.valueOf(prefs[Keys.SOURCE_SCOPE] ?: SourceScope.THIS_PHONE_ONLY.name)
            }.getOrDefault(SourceScope.THIS_PHONE_ONLY),
            preferredSourceKind = runCatching {
                StepSourceKind.valueOf(prefs[Keys.PREFERRED_SOURCE] ?: StepSourceKind.HEALTH_CONNECT.name)
            }.getOrDefault(StepSourceKind.HEALTH_CONNECT),
            strideCm = (prefs[Keys.STRIDE_CM] ?: 70).coerceIn(30, 140),
            showCalories = prefs[Keys.SHOW_CALORIES] ?: false,
            weightKg = prefs[Keys.WEIGHT_KG],
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            lastSyncAt = prefs[Keys.LAST_SYNC_EPOCH_MILLIS]?.let { Instant.ofEpochMilli(it) }
        )
    }

    suspend fun setDailyGoal(goal: Int) {
        context.dataStore.edit { it[Keys.DAILY_GOAL] = goal.coerceIn(1_000, 30_000) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setSourceScope(scope: SourceScope) {
        context.dataStore.edit {
            it[Keys.SOURCE_SCOPE] = scope.name
            it.remove(Keys.LAST_SYNC_EPOCH_MILLIS)
        }
    }

    suspend fun setPreferredSourceKind(kind: StepSourceKind) {
        context.dataStore.edit {
            it[Keys.PREFERRED_SOURCE] = kind.name
            it.remove(Keys.LAST_SYNC_EPOCH_MILLIS)
        }
    }

    suspend fun setStrideCm(strideCm: Int) {
        context.dataStore.edit { it[Keys.STRIDE_CM] = strideCm.coerceIn(30, 140) }
    }

    suspend fun setCalorieSettings(enabled: Boolean, weightKg: Float?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_CALORIES] = enabled
            if (weightKg != null) {
                prefs[Keys.WEIGHT_KG] = weightKg.coerceIn(25f, 250f)
            } else {
                prefs.remove(Keys.WEIGHT_KG)
            }
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun updateLastSyncAt(instant: Instant) {
        context.dataStore.edit { it[Keys.LAST_SYNC_EPOCH_MILLIS] = instant.toEpochMilli() }
    }

    suspend fun clearLastSync() {
        context.dataStore.edit { it.remove(Keys.LAST_SYNC_EPOCH_MILLIS) }
    }

    suspend fun resetAllPreferences() {
        context.dataStore.edit { it.clear() }
    }
}
