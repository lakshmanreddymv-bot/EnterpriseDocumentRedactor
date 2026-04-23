package com.example.enterprisedocumentredactor.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.enterprisedocumentredactor.data.local.SessionCache
import com.example.enterprisedocumentredactor.data.ml.ModelDownloadHelper
import com.example.enterprisedocumentredactor.domain.model.DocumentStatus
import com.example.enterprisedocumentredactor.domain.usecase.ScanDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val scanDocumentUseCase: ScanDocumentUseCase,
    private val sessionCache: SessionCache,
    private val modelDownloadHelper: ModelDownloadHelper
) : ViewModel() {

    private val _status = MutableStateFlow<DocumentStatus>(DocumentStatus.Idle)
    val status: StateFlow<DocumentStatus> = _status.asStateFlow()

    val isModelReady: StateFlow<Boolean> = modelDownloadHelper.isModelReady
    val modelStatus: StateFlow<String> = modelDownloadHelper.downloadProgress

    fun processScannedUri(uri: Uri, realPath: String, pageCount: Int = 1) {
        processFile(realPath, pageCount)
    }

    fun processPickedFile(filePath: String) {
        val pageCount = if (filePath.endsWith(".pdf", ignoreCase = true)) {
            countPdfPages(filePath)
        } else 1
        processFile(filePath, pageCount)
    }

    private fun processFile(filePath: String, pageCount: Int) {
        viewModelScope.launch {
            _status.value = DocumentStatus.Scanning
            try {
                _status.value = DocumentStatus.Detecting
                val items = scanDocumentUseCase(filePath)
                val documentId = UUID.randomUUID().toString()

                sessionCache.setSession(
                    documentId = documentId,
                    filePath = filePath,
                    pageCount = pageCount,
                    items = items
                )
                _status.value = DocumentStatus.Detected(items)
            } catch (e: Exception) {
                _status.value = DocumentStatus.Error(e.message ?: "Scan failed")
            }
        }
    }

    fun resetStatus() {
        _status.value = DocumentStatus.Idle
    }

    private fun countPdfPages(filePath: String): Int {
        return try {
            android.graphics.pdf.PdfRenderer(
                android.os.ParcelFileDescriptor.open(
                    File(filePath),
                    android.os.ParcelFileDescriptor.MODE_READ_ONLY
                )
            ).use { it.pageCount }
        } catch (_: Exception) { 1 }
    }
}
