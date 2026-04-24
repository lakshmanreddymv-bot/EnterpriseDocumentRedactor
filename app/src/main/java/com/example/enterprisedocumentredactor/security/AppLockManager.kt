package com.example.enterprisedocumentredactor.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockManager @Inject constructor() {
    // Configurable timeout — default 5 minutes. Set from SettingsViewModel.
    var lockTimeoutMs: Long = 5 * 60 * 1000L

    private var backgroundedAt: Long = 0L

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    fun onAppBackground() {
        backgroundedAt = System.currentTimeMillis()
    }

    fun onAppForeground() {
        if (lockTimeoutMs < 0L) return // "Never" timeout
        val elapsed = System.currentTimeMillis() - backgroundedAt
        if (backgroundedAt > 0 && elapsed > lockTimeoutMs) {
            _isLocked.value = true
        }
    }

    fun unlock() {
        _isLocked.value = false
        backgroundedAt = 0L
    }

    fun lockNow() {
        _isLocked.value = true
    }
}
