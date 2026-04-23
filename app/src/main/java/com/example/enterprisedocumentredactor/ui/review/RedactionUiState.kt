package com.example.enterprisedocumentredactor.ui.review

import com.example.enterprisedocumentredactor.domain.model.RedactionResult

sealed class RedactionUiState {
    object Idle : RedactionUiState()
    object Loading : RedactionUiState()
    data class Success(val result: RedactionResult) : RedactionUiState()
    data class Error(val message: String) : RedactionUiState()
}
