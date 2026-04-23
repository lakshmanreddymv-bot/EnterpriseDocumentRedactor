package com.example.enterprisedocumentredactor.domain.usecase

import com.example.enterprisedocumentredactor.domain.model.RedactionItem
import com.example.enterprisedocumentredactor.domain.repository.DocumentRepository
import javax.inject.Inject

class ScanDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository
) {
    suspend operator fun invoke(filePath: String): List<RedactionItem> =
        repository.scanDocument(filePath)
}
