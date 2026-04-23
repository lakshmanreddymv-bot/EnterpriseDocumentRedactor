package com.example.enterprisedocumentredactor.domain.model

sealed class DocumentStatus {
    object Idle : DocumentStatus()
    object Scanning : DocumentStatus()
    object Detecting : DocumentStatus()
    data class Detected(val items: List<RedactionItem>) : DocumentStatus()
    object Redacting : DocumentStatus()
    data class Complete(val result: RedactionResult) : DocumentStatus()
    data class Error(val message: String) : DocumentStatus()
}
