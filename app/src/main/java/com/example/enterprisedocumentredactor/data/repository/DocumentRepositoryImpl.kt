package com.example.enterprisedocumentredactor.data.repository

import android.content.Context
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
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val sessionCache: SessionCache,
    @ApplicationContext private val context: Context
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
        val outputDir = File(sourcePath).parentFile ?: context.filesDir
        val outputFile = File(outputDir, "redacted_${System.currentTimeMillis()}.pdf")

        pdfRedactor.redact(sourcePath, items, outputFile.absolutePath)

        val result = RedactionResult(
            documentId = documentId,
            outputPath = outputFile.absolutePath,
            redactedItems = items
        )
        sessionCache.setResult(result)

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

    override suspend fun deleteDocument(id: String) {
        val entity = documentDao.getDocumentById(id) ?: return
        if (entity.redactedPath.isNotEmpty()) secureDelete(File(entity.redactedPath))
        val sourceFile = File(entity.filePath)
        if (sourceFile.exists() && sourceFile.parent == context.cacheDir.absolutePath) {
            secureDelete(sourceFile)
        }
        documentDao.deleteDocument(id)
        Log.d(TAG, "Document deleted: ${entity.fileName}")
    }

    override suspend fun deleteAll() {
        val all = documentDao.getAllDocumentsOnce()
        all.forEach { entity ->
            if (entity.redactedPath.isNotEmpty()) secureDelete(File(entity.redactedPath))
        }
        documentDao.deleteAll()
        Log.d(TAG, "All documents deleted: ${all.size} records")
    }

    override suspend fun deleteOlderThan(cutoffMs: Long) {
        val old = documentDao.getOlderThan(cutoffMs)
        old.forEach { entity ->
            if (entity.redactedPath.isNotEmpty()) secureDelete(File(entity.redactedPath))
        }
        documentDao.deleteOlderThan(cutoffMs)
        Log.d(TAG, "Retention enforced: deleted ${old.size} docs older than cutoff")
    }

    // Overwrites file with zeros before deleting — prevents forensic recovery (HIPAA requirement)
    private fun secureDelete(file: File) {
        if (!file.exists()) return
        try {
            val size = file.length()
            if (size <= 1_048_576L) {
                file.writeBytes(ByteArray(size.toInt()))
            } else {
                // Chunk-based overwrite for large files to avoid OOM
                file.outputStream().use { out ->
                    val chunk = ByteArray(65536)
                    var remaining = size
                    while (remaining > 0) {
                        val toWrite = minOf(chunk.size.toLong(), remaining).toInt()
                        out.write(chunk, 0, toWrite)
                        remaining -= toWrite
                    }
                }
            }
            file.delete()
        } catch (e: Exception) {
            file.delete()
        }
    }
}
