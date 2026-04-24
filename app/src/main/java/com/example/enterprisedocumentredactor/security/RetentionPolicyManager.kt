package com.example.enterprisedocumentredactor.security

import android.content.Context
import android.util.Log
import com.example.enterprisedocumentredactor.domain.repository.DocumentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetentionPolicyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository
) {
    companion object {
        private const val TAG = "RetentionPolicy"
        const val PREF_RETENTION_DAYS = "retention_days"
        const val DEFAULT_RETENTION_DAYS = 30
        const val NEVER = -1
    }

    private val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)

    var retentionDays: Int
        get() = prefs.getInt(PREF_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)
        set(value) = prefs.edit().putInt(PREF_RETENTION_DAYS, value).apply()

    suspend fun enforceRetentionPolicy() {
        val days = retentionDays
        if (days == NEVER) return
        val cutoffMs = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000L)
        repository.deleteOlderThan(cutoffMs)
        Log.d(TAG, "Enforced: deleted docs older than $days days")
    }
}
