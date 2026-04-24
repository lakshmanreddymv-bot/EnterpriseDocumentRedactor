package com.example.enterprisedocumentredactor.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.enterprisedocumentredactor.domain.repository.DocumentRepository
import com.example.enterprisedocumentredactor.security.AppLockManager
import com.example.enterprisedocumentredactor.security.BiometricAuthManager
import com.example.enterprisedocumentredactor.security.BiometricStatus
import com.example.enterprisedocumentredactor.security.RetentionPolicyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appLockManager: AppLockManager,
    private val biometricAuthManager: BiometricAuthManager,
    private val retentionPolicyManager: RetentionPolicyManager,
    private val repository: DocumentRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)

    // Auto-lock timeout in minutes (-1 = Never)
    private val _lockTimeoutMinutes = MutableStateFlow(
        prefs.getInt("lock_timeout_minutes", 5)
    )
    val lockTimeoutMinutes: StateFlow<Int> = _lockTimeoutMinutes.asStateFlow()

    // Data retention in days (-1 = Never delete)
    private val _retentionDays = MutableStateFlow(
        prefs.getInt(RetentionPolicyManager.PREF_RETENTION_DAYS, RetentionPolicyManager.DEFAULT_RETENTION_DAYS)
    )
    val retentionDays: StateFlow<Int> = _retentionDays.asStateFlow()

    // Biometric lock enabled
    private val _biometricEnabled = MutableStateFlow(
        prefs.getBoolean("biometric_enabled", false)
    )
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    // Non-reactive — hardware doesn't change at runtime
    val biometricStatus: BiometricStatus get() = biometricAuthManager.getStatus()

    init {
        // Sync AppLockManager with the persisted timeout on ViewModel creation
        appLockManager.setTimeoutMinutes(_lockTimeoutMinutes.value)
    }

    fun setLockTimeout(minutes: Int) {
        _lockTimeoutMinutes.value = minutes
        prefs.edit().putInt("lock_timeout_minutes", minutes).apply()
        appLockManager.setTimeoutMinutes(minutes)
    }

    fun setRetentionDays(days: Int) {
        _retentionDays.value = days
        retentionPolicyManager.retentionDays = days
    }

    fun setBiometricEnabled(enabled: Boolean) {
        _biometricEnabled.value = enabled
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
    }

    // Delete all history
    private val _showDeleteAllDialog = MutableStateFlow(false)
    val showDeleteAllDialog: StateFlow<Boolean> = _showDeleteAllDialog.asStateFlow()

    fun requestDeleteAll() { _showDeleteAllDialog.value = true }
    fun cancelDeleteAll() { _showDeleteAllDialog.value = false }

    fun confirmDeleteAll() {
        _showDeleteAllDialog.value = false
        viewModelScope.launch(Dispatchers.IO) { repository.deleteAll() }
    }
}
