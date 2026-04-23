package com.example.enterprisedocumentredactor.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.enterprisedocumentredactor.domain.model.Document
import com.example.enterprisedocumentredactor.domain.usecase.GetDocumentHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    getDocumentHistoryUseCase: GetDocumentHistoryUseCase
) : ViewModel() {

    val documents: StateFlow<List<Document>> = getDocumentHistoryUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
