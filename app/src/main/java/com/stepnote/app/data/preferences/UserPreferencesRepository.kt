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
import com.stepnote.app.domain.model.WalkSession
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
    val heightCm: Int? = null,
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
        val HEIGHT_CM = intPreferencesKey("height_cm")
        val WALK_START = longPreferencesKey("walk_start")
        val WALK_STOP = longPreferencesKey("walk_stop")
        val WALK_SOURCE = stringPreferencesKey("walk_source")
        val WALK_SCOPE = stringPreferencesKey("walk_scope")
        val WALK_STEPS = longPreferencesKey("walk_steps")
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
            heightCm = prefs[Keys.HEIGHT_CM],
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

    suspend fun setBodyProfile(heightCm: Int?, weightKg: Float?) {
        context.dataStore.edit { prefs ->
            if (heightCm != null) prefs[Keys.HEIGHT_CM] = heightCm else prefs.remove(Keys.HEIGHT_CM)
            prefs[Keys.SHOW_CALORIES] = weightKg != null
            if (weightKg != null) {
                prefs[Keys.WEIGHT_KG] = weightKg.coerceIn(25f, 250f)
            } else {
                prefs.remove(Keys.WEIGHT_KG)
            }
        }
    }

    val walkSessionFlow: Flow<WalkSession?> = context.dataStore.data.map { it.walkSession() }

    suspend fun saveWalkSession(expected: WalkSession?, session: WalkSession): Boolean {
        var saved = false
        context.dataStore.edit { prefs ->
            if (prefs.walkSession() != expected) return@edit
            prefs[Keys.WALK_START] = session.startedAt.toEpochMilli()
            prefs[Keys.WALK_SOURCE] = session.source.name
            prefs[Keys.WALK_SCOPE] = session.scope.name
            val end = session.stoppedAt
            if (end == null) prefs.remove(Keys.WALK_STOP) else prefs[Keys.WALK_STOP] = end.toEpochMilli()
            val steps = session.steps
            if (steps == null) prefs.remove(Keys.WALK_STEPS) else prefs[Keys.WALK_STEPS] = steps
            saved = true
        }
        return saved
    }

    private fun Preferences.walkSession(): WalkSession? {
        val start = this[Keys.WALK_START] ?: return null
        val source = StepSourceKind.entries.firstOrNull { it.name == this[Keys.WALK_SOURCE] } ?: return null
        val scope = SourceScope.entries.firstOrNull { it.name == this[Keys.WALK_SCOPE] } ?: return null
        return WalkSession(
            startedAt = Instant.ofEpochMilli(start),
            stoppedAt = this[Keys.WALK_STOP]?.let(Instant::ofEpochMilli),
            scope = scope,
            source = source,
            steps = this[Keys.WALK_STEPS]
        )
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
