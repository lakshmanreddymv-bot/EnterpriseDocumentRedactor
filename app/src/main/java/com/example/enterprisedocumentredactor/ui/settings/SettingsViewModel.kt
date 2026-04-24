package com.example.enterprisedocumentredactor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.enterprisedocumentredactor.domain.repository.DocumentRepository
import com.example.enterprisedocumentredactor.security.AppLockManager
import com.example.enterprisedocumentredactor.security.BiometricAuthManager
import com.example.enterprisedocumentredactor.security.RetentionPolicyManager
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val repository: DocumentRepository
) : ViewModel() {

    val isBiometricAvailable: Boolean get() = biometricAuthManager.isBiometricAvailable()

    private val _biometricEnabled = MutableStateFlow(true)
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    fun setBiometricEnabled(enabled: Boolean) {
        _biometricEnabled.value = enabled
        if (!enabled) appLockManager.unlock()
    }

    // Lock timeout in minutes; -1 = Never
    private val _lockTimeoutMinutes = MutableStateFlow(5)
    val lockTimeoutMinutes: StateFlow<Int> = _lockTimeoutMinutes.asStateFlow()

    fun setLockTimeout(minutes: Int) {
        _lockTimeoutMinutes.value = minutes
        appLockManager.lockTimeoutMs = if (minutes < 0) -1L else minutes * 60_000L
    }

    val retentionDays: Int get() = retentionPolicyManager.retentionDays

    fun setRetentionDays(days: Int) {
        retentionPolicyManager.retentionDays = days
    }

    private val _showDeleteAllDialog = MutableStateFlow(false)
    val showDeleteAllDialog: StateFlow<Boolean> = _showDeleteAllDialog.asStateFlow()

    fun requestDeleteAll() { _showDeleteAllDialog.value = true }
    fun cancelDeleteAll() { _showDeleteAllDialog.value = false }

    fun confirmDeleteAll() {
        _showDeleteAllDialog.value = false
        viewModelScope.launch(Dispatchers.IO) { repository.deleteAll() }
    }
}
