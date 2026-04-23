package com.example.enterprisedocumentredactor.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.enterprisedocumentredactor.domain.model.RedactionItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class PdfRedactor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val blackPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    fun redact(
        sourcePath: String,
        items: List<RedactionItem>,
        outputPath: String
    ) {
        val file = File(sourcePath)
        if (sourcePath.endsWith(".pdf", ignoreCase = true)) {
            redactPdf(file, items, outputPath)
        } else {
            redactImage(file, items, outputPath)
        }
    }

    private fun redactPdf(
        sourceFile: File,
        items: List<RedactionItem>,
        outputPath: String
    ) {
        val pdfDoc = PdfDocument()
        try {
            val pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd.use {
                val renderer = PdfRenderer(pfd)
                renderer.use {
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        val scaleX = 2f
                        val scaleY = 2f
                        val bitmapW = (page.width * scaleX).toInt()
                        val bitmapH = (page.height * scaleY).toInt()

                        val bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        page.close()

                        val canvas = Canvas(bitmap)
                        items.filter { it.pageIndex == i && it.isSelected }.forEach { item ->
                            canvas.drawRect(item.boundingBox, blackPaint)
                        }

                        val pageInfo = PdfDocument.PageInfo.Builder(bitmapW, bitmapH, i + 1).create()
                        val pdfPage = pdfDoc.startPage(pageInfo)
                        pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                        pdfDoc.finishPage(pdfPage)
                        bitmap.recycle()
                    }
                }
            }
            FileOutputStream(outputPath).use { pdfDoc.writeTo(it) }
        } finally {
            pdfDoc.close()
        }
    }

    private fun redactImage(
        sourceFile: File,
        items: List<RedactionItem>,
        outputPath: String
    ) {
        val original = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return
        val mutable = original.copy(Bitmap.Config.ARGB_8888, true)
        original.recycle()
        val canvas = Canvas(mutable)
        items.filter { it.pageIndex == 0 && it.isSelected }.forEach { item ->
            canvas.drawRect(item.boundingBox, blackPaint)
        }

        val pdfDoc = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(mutable.width, mutable.height, 1).create()
            val pdfPage = pdfDoc.startPage(pageInfo)
            pdfPage.canvas.drawBitmap(mutable, 0f, 0f, null)
            pdfDoc.finishPage(pdfPage)
            mutable.recycle()
            FileOutputStream(outputPath).use { pdfDoc.writeTo(it) }
        } finally {
            pdfDoc.close()
        }
    }
}
