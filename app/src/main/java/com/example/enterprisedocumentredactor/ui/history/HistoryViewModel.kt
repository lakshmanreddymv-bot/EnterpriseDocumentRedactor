package com.example.enterprisedocumentredactor.ui.history

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.enterprisedocumentredactor.domain.model.Document
import com.example.enterprisedocumentredactor.domain.repository.DocumentRepository
import com.example.enterprisedocumentredactor.domain.usecase.GetDocumentHistoryUseCase
import com.example.enterprisedocumentredactor.security.BiometricAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    getDocumentHistoryUseCase: GetDocumentHistoryUseCase,
    private val repository: DocumentRepository,
    private val biometricAuthManager: BiometricAuthManager
) : ViewModel() {

    val documents: StateFlow<List<Document>> = getDocumentHistoryUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Biometric auth — starts authenticated if biometric is unavailable
    val isBiometricAvailable: Boolean get() = biometricAuthManager.isBiometricAvailable()

    private val _isAuthenticated = MutableStateFlow(!biometricAuthManager.isBiometricAvailable())
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _authError = MutableSharedFlow<String>()
    val authError: SharedFlow<String> = _authError.asSharedFlow()

    fun authenticate(activity: FragmentActivity) {
        biometricAuthManager.authenticate(
            activity = activity,
            onSuccess = { _isAuthenticated.value = true },
            onFailure = { error -> viewModelScope.launch { _authError.emit(error) } }
        )
    }

    // Single delete
    private val _showDeleteDialog = MutableStateFlow<String?>(null)
    val showDeleteDialog: StateFlow<String?> = _showDeleteDialog.asStateFlow()

    fun requestDelete(id: String) { _showDeleteDialog.value = id }
    fun cancelDelete() { _showDeleteDialog.value = null }

    fun confirmDelete() {
        val id = _showDeleteDialog.value ?: return
        _showDeleteDialog.value = null
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDocument(id)
            _deleteEvent.emit("Document deleted")
        }
    }

    // Delete all
    private val _showDeleteAllDialog = MutableStateFlow(false)
    val showDeleteAllDialog: StateFlow<Boolean> = _showDeleteAllDialog.asStateFlow()

    fun requestDeleteAll() { _showDeleteAllDialog.value = true }
    fun cancelDeleteAll() { _showDeleteAllDialog.value = false }

    fun confirmDeleteAll() {
        _showDeleteAllDialog.value = false
        val count = documents.value.size
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAll()
            _deleteEvent.emit("All history cleared ($count documents deleted)")
        }
    }

    // One-shot snackbar events
    private val _deleteEvent = MutableSharedFlow<String>()
    val deleteEvent: SharedFlow<String> = _deleteEvent.asSharedFlow()
}
