package com.example.enterprisedocumentredactor.domain.repository

import com.example.enterprisedocumentredactor.domain.model.Document
import com.example.enterprisedocumentredactor.domain.model.RedactionItem
import com.example.enterprisedocumentredactor.domain.model.RedactionResult
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    suspend fun scanDocument(filePath: String): List<RedactionItem>
    suspend fun redactDocument(documentId: String, items: List<RedactionItem>, sourcePath: String): RedactionResult
    fun getDocumentHistory(): Flow<List<Document>>
    suspend fun saveDocument(document: Document)
    suspend fun deleteDocument(id: String)
    suspend fun deleteAll()
    suspend fun deleteOlderThan(cutoffMs: Long)
}
