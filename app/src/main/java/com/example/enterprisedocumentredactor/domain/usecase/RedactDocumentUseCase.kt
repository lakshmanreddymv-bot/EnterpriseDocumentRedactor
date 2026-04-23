package com.example.enterprisedocumentredactor.domain.usecase

import com.example.enterprisedocumentredactor.domain.model.RedactionItem
import com.example.enterprisedocumentredactor.domain.model.RedactionResult
import com.example.enterprisedocumentredactor.domain.repository.DocumentRepository
import javax.inject.Inject

class RedactDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository
) {
    suspend operator fun invoke(
        documentId: String,
        items: List<RedactionItem>,
        sourcePath: String
    ): RedactionResult = repository.redactDocument(documentId, items, sourcePath)
}
