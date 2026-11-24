package com.example.datavault.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.pinDataStore: DataStore<Preferences> by preferencesDataStore(name = "pin_preferences")

class PinRepository(private val context: Context) {

    private object Keys {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
    }

    val isPinEnabled: Flow<Boolean> = context.pinDataStore.data.map { prefs ->
        prefs[Keys.PIN_ENABLED] ?: false
    }

    val hasPinSet: Flow<Boolean> = context.pinDataStore.data.map { prefs ->
        prefs[Keys.PIN_HASH]?.isNotEmpty() ?: false
    }

    suspend fun setPin(pin: String) {
        val hash = sha256(pin)
        context.pinDataStore.edit { prefs ->
            prefs[Keys.PIN_HASH] = hash
            prefs[Keys.PIN_ENABLED] = true
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val stored = context.pinDataStore.data.first()[Keys.PIN_HASH] ?: return false
        return sha256(pin) == stored
    }

    suspend fun enablePin(enabled: Boolean) {
        context.pinDataStore.edit { prefs ->
            prefs[Keys.PIN_ENABLED] = enabled
        }
    }

    suspend fun clearPin() {
        context.pinDataStore.edit { prefs ->
            prefs.remove(Keys.PIN_HASH)
            prefs[Keys.PIN_ENABLED] = false
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
