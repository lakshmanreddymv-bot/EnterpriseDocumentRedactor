package com.example.enterprisedocumentredactor.data.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    data class ScanPageResult(
        val pageIndex: Int,
        val ocrText: Text,
        val bitmap: Bitmap?
    )

    suspend fun scanFile(filePath: String): List<ScanPageResult> {
        val file = File(filePath)
        return if (filePath.endsWith(".pdf", ignoreCase = true)) {
            scanPdf(file)
        } else {
            scanImage(file)
        }
    }

    private suspend fun scanPdf(file: File): List<ScanPageResult> {
        val results = mutableListOf<ScanPageResult>()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pfd.use {
            val renderer = PdfRenderer(pfd)
            renderer.use {
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val bitmap = Bitmap.createBitmap(
                        page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    val ocrText = runOcr(bitmap)
                    results += ScanPageResult(i, ocrText, bitmap)
                }
            }
        }
        return results
    }

    private suspend fun scanImage(file: File): List<ScanPageResult> {
        // Q6: OOM guard — sample down large images before decoding
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.inSampleSize = calculateInSampleSize(options, 1920, 1920)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565

        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: return emptyList()
        val ocrText = runOcr(bitmap)
        return listOf(ScanPageResult(0, ocrText, bitmap))
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private suspend fun runOcr(bitmap: Bitmap): Text {
        val image = InputImage.fromBitmap(bitmap, 0)
        return recognizer.process(image).await()
    }

    // M8: release TextRecognizer resources when no longer needed
    fun close() = recognizer.close()
}
