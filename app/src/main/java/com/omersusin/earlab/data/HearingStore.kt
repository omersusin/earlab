package com.omersusin.earlab.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("earlab")
private val LAST_RESULT = stringPreferencesKey("last_hearing_result")

data class Threshold(val ear: String, val freqHz: Int, val dbFs: Int)

class HearingStore(private val context: Context) {
    val lastResult: Flow<String> =
        context.store.data.map { it[LAST_RESULT] ?: "" }

    suspend fun save(result: List<Threshold>) {
        val s = result.joinToString(";") { "${it.ear}:${it.freqHz}:${it.dbFs}" }
        context.store.edit { it[LAST_RESULT] = s }
    }

    companion object {
        fun parse(s: String): List<Threshold> =
            s.split(";").mapNotNull { part ->
                val p = part.split(":")
                if (p.size == 3) Threshold(p[0], p[1].toIntOrNull() ?: 0, p[2].toIntOrNull() ?: 0)
                else null
            }
    }
}
