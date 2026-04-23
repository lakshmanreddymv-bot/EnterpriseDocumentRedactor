package com.example.enterprisedocumentredactor.data.repository

import android.util.Log
import com.example.enterprisedocumentredactor.data.local.DocumentDao
import com.example.enterprisedocumentredactor.data.local.DocumentEntity
import com.example.enterprisedocumentredactor.data.local.SessionCache
import com.example.enterprisedocumentredactor.data.ml.DocumentScanner
import com.example.enterprisedocumentredactor.data.ml.PiiDetector
import com.example.enterprisedocumentredactor.data.pdf.PdfRedactor
import com.example.enterprisedocumentredactor.domain.model.Document
import com.example.enterprisedocumentredactor.domain.model.RedactionItem
import com.example.enterprisedocumentredactor.domain.model.RedactionResult
import com.example.enterprisedocumentredactor.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val piiDetector: PiiDetector,
    private val documentScanner: DocumentScanner,
    private val pdfRedactor: PdfRedactor,
    private val documentDao: DocumentDao,
    private val sessionCache: SessionCache
) : DocumentRepository {

    companion object {
        private const val TAG = "Repository"
    }

    override suspend fun scanDocument(filePath: String): List<RedactionItem> {
        val pages = documentScanner.scanFile(filePath)
        return pages.flatMap { page ->
            piiDetector.detect(page.ocrText, page.pageIndex).also { page.bitmap?.recycle() }
        }
    }

    override suspend fun redactDocument(
        documentId: String,
        items: List<RedactionItem>,
        sourcePath: String
    ): RedactionResult {
        val outputDir = File(sourcePath).parentFile ?: File("/data/local/tmp")
        val outputFile = File(outputDir, "redacted_${System.currentTimeMillis()}.pdf")

        // items are already pre-filtered to selected-only by ReviewViewModel
        pdfRedactor.redact(sourcePath, items, outputFile.absolutePath)

        val result = RedactionResult(
            documentId = documentId,
            outputPath = outputFile.absolutePath,
            redactedItems = items
        )
        sessionCache.setResult(result)

        // *** PRIMARY FIX: persist to Room history immediately after redaction ***
        val document = Document(
            id = documentId,
            filePath = sourcePath,
            fileName = File(sourcePath).name,
            pageCount = sessionCache.currentPageCount.value,
            redactedItemCount = result.totalCount,
            redactedPath = outputFile.absolutePath,
            createdAt = result.completedAt
        )
        saveDocument(document)

        return result
    }

    override fun getDocumentHistory(): Flow<List<Document>> =
        documentDao.getDocumentHistory().map { entities ->
            entities.map { entity ->
                Document(
                    id = entity.id,
                    filePath = entity.filePath,
                    fileName = entity.fileName,
                    pageCount = entity.pageCount,
                    redactedItemCount = entity.redactedItemCount,
                    redactedPath = entity.redactedPath,
                    createdAt = entity.createdAt
                )
            }
        }

    override suspend fun saveDocument(document: Document) {
        documentDao.insertDocument(
            DocumentEntity(
                id = document.id,
                filePath = document.filePath,
                fileName = document.fileName,
                pageCount = document.pageCount,
                redactedItemCount = document.redactedItemCount,
                redactedPath = document.redactedPath,
                createdAt = document.createdAt
            )
        )
        Log.d(TAG, "Document saved to history: ${document.id}")
    }
}
