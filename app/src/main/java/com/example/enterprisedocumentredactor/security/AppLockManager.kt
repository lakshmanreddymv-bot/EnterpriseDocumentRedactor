package com.example.enterprisedocumentredactor.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockManager @Inject constructor() {

    private var timeoutMinutes: Int = 5
    private var backgroundedAt: Long = 0L

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    fun setTimeoutMinutes(minutes: Int) {
        timeoutMinutes = minutes
    }

    fun onAppBackground() {
        backgroundedAt = System.currentTimeMillis()
    }

    fun onAppForeground() {
        if (timeoutMinutes == -1) return // "Never" option
        val timeoutMs = timeoutMinutes * 60 * 1000L
        val elapsed = System.currentTimeMillis() - backgroundedAt
        if (backgroundedAt > 0 && elapsed > timeoutMs) {
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
