package com.example.enterprisedocumentredactor.ui.review

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.enterprisedocumentredactor.data.local.SessionCache
import com.example.enterprisedocumentredactor.domain.model.Document
import com.example.enterprisedocumentredactor.domain.model.RedactionItem
import com.example.enterprisedocumentredactor.domain.usecase.RedactDocumentUseCase
import com.example.enterprisedocumentredactor.domain.usecase.ScanDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val sessionCache: SessionCache,
    private val redactDocumentUseCase: RedactDocumentUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _items = MutableStateFlow<List<RedactionItem>>(emptyList())
    val items: StateFlow<List<RedactionItem>> = _items.asStateFlow()

    private val _currentPageBitmap = MutableStateFlow<Bitmap?>(null)
    val currentPageBitmap: StateFlow<Bitmap?> = _currentPageBitmap.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private val _uiState = MutableStateFlow<RedactionUiState>(RedactionUiState.Idle)
    val uiState: StateFlow<RedactionUiState> = _uiState.asStateFlow()

    val filePath: String get() = sessionCache.currentFilePath.value
    val pageCount: Int get() = sessionCache.currentPageCount.value
    val documentId: String get() = sessionCache.currentDocumentId.value

    init {
        _items.value = sessionCache.detectedItems.value
        loadPage(0)
    }

    fun toggleItem(id: String) {
        _items.update { list ->
            list.map { if (it.id == id) it.copy(isSelected = !it.isSelected) else it }
        }
    }

    fun selectAllOnPage(pageIndex: Int) {
        _items.update { list ->
            list.map { if (it.pageIndex == pageIndex) it.copy(isSelected = true) else it }
        }
    }

    fun loadPage(pageIndex: Int) {
        _currentPageIndex.value = pageIndex
        viewModelScope.launch(Dispatchers.IO) {
            val old = _currentPageBitmap.value
            val bitmap = renderPage(filePath, pageIndex)
            _currentPageBitmap.value = bitmap
            old?.recycle()
        }
    }

    override fun onCleared() {
        super.onCleared()
        _currentPageBitmap.value?.recycle()
        _currentPageBitmap.value = null
    }

    fun redact() {
        viewModelScope.launch {
            _uiState.value = RedactionUiState.Loading
            try {
                val selected = _items.value.filter { it.isSelected }
                val result = redactDocumentUseCase(documentId, selected, filePath)
                _uiState.value = RedactionUiState.Success(result)
            } catch (e: Exception) {
                _uiState.value = RedactionUiState.Error(e.message ?: "Redaction failed")
            }
        }
    }

    private fun renderPage(path: String, pageIndex: Int): Bitmap? {
        return if (path.endsWith(".pdf", ignoreCase = true)) {
            try {
                val pfd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex >= renderer.pageCount) return null
                    val page = renderer.openPage(pageIndex)
                    val bitmap = Bitmap.createBitmap(
                        page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmap
                }
            } catch (_: Exception) { null }
        } else {
            BitmapFactory.decodeFile(path)
        }
    }
}
