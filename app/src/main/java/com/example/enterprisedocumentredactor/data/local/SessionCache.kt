package com.example.enterprisedocumentredactor.data.local

import com.example.enterprisedocumentredactor.domain.model.RedactionItem
import com.example.enterprisedocumentredactor.domain.model.RedactionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionCache @Inject constructor() {
    private val _currentDocumentId = MutableStateFlow("")
    val currentDocumentId: StateFlow<String> = _currentDocumentId.asStateFlow()

    private val _currentFilePath = MutableStateFlow("")
    val currentFilePath: StateFlow<String> = _currentFilePath.asStateFlow()

    private val _currentPageCount = MutableStateFlow(0)
    val currentPageCount: StateFlow<Int> = _currentPageCount.asStateFlow()

    private val _detectedItems = MutableStateFlow<List<RedactionItem>>(emptyList())
    val detectedItems: StateFlow<List<RedactionItem>> = _detectedItems.asStateFlow()

    private val _lastResult = MutableStateFlow<RedactionResult?>(null)
    val lastResult: StateFlow<RedactionResult?> = _lastResult.asStateFlow()

    fun setSession(
        documentId: String,
        filePath: String,
        pageCount: Int,
        items: List<RedactionItem>
    ) {
        _currentDocumentId.value = documentId
        _currentFilePath.value = filePath
        _currentPageCount.value = pageCount
        _detectedItems.value = items
        _lastResult.value = null
    }

    fun setResult(result: RedactionResult) {
        _lastResult.value = result
    }
}
