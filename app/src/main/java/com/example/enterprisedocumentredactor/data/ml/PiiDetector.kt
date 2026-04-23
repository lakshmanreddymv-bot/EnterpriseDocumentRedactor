package com.example.enterprisedocumentredactor.data.ml

import android.graphics.RectF
import android.util.Log
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.vision.text.Text
import com.example.enterprisedocumentredactor.domain.model.PiiType
import com.example.enterprisedocumentredactor.domain.model.RedactionItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PiiDetector @Inject constructor(
    private val modelDownloadHelper: ModelDownloadHelper
) {
    companion object {
        private const val TAG = "PiiDetector"

        // Layer 2: Precompiled regex patterns
        private val SSN_REGEX = Regex("""\b\d{3}-\d{2}-\d{4}\b""")
        private val CREDIT_CARD_REGEX = Regex("""\b(?:\d{4}[\s\-]?){3}\d{4}\b""")
        private val PASSPORT_REGEX = Regex("""\b[A-Z]{1,2}\d{6,9}\b""")
        private val MRN_REGEX = Regex("""(?i)\bMRN[\s:#]?\d{6,10}\b""")
        private val DOB_REGEX = Regex("""\b(?:0?[1-9]|1[0-2])[/\-](?:0?[1-9]|[12]\d|3[01])[/\-](?:\d{2}|\d{4})\b""")
        private val EMAIL_REGEX = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")
        private val PHONE_REGEX = Regex("""\b(?:\+?1[\s.\-]?)?\(?\d{3}\)?[\s.\-]?\d{3}[\s.\-]?\d{4}\b""")
        private val PERSON_PREFIX_REGEX = Regex("""(?i)\b(?:Mr\.|Mrs\.|Ms\.|Dr\.|Prof\.)\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+)*""")

        // Layer 3: Context-aware keywords
        private val FINANCIAL_KEYWORDS = setOf("account", "acct", "routing", "aba", "swift", "iban", "balance", "invoice")
        private val MEDICAL_KEYWORDS = setOf("patient", "diagnosis", "prescription", "mrn", "npi", "dob", "icd", "rx")
    }

    val isExtractorReady: Boolean get() = modelDownloadHelper.isModelReady.value

    suspend fun detect(ocrResult: Text, pageIndex: Int): List<RedactionItem> {
        val tokens = buildTokens(ocrResult)
        val fullText = tokens.joinToString(" ") { it.text }
        val items = mutableListOf<RedactionItem>()

        // Layer 1: ML Kit entity extraction — only if model is ready
        if (isExtractorReady) {
            items += detectWithEntityExtraction(fullText, tokens, pageIndex)
        } else {
            Log.d(TAG, "Page $pageIndex: ML Kit not ready, skipping entity extraction")
        }

        // Layer 2 & 3: Always run regardless of ML Kit status
        items += detectWithRegex(fullText, tokens, pageIndex)
        items += detectContextAware(fullText, tokens, pageIndex)

        val result = mergeOverlapping(items)
        Log.d(TAG, "Page $pageIndex: detected ${result.size} items (mlkit=${isExtractorReady})")
        return result
    }

    private data class TextToken(
        val text: String,
        val charStart: Int,
        val charEnd: Int,
        val box: RectF
    )

    private fun buildTokens(ocrResult: Text): List<TextToken> {
        val tokens = mutableListOf<TextToken>()
        var offset = 0
        for (block in ocrResult.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val rect = element.boundingBox ?: continue
                    val box = RectF(rect.left.toFloat(), rect.top.toFloat(),
                        rect.right.toFloat(), rect.bottom.toFloat())
                    tokens += TextToken(element.text, offset, offset + element.text.length, box)
                    offset += element.text.length + 1
                }
            }
        }
        return tokens
    }

    private fun boxForRange(tokens: List<TextToken>, start: Int, end: Int): RectF? {
        val matching = tokens.filter { it.charStart < end && it.charEnd > start }
        if (matching.isEmpty()) return null
        val rect = RectF(matching[0].box)
        matching.drop(1).forEach { rect.union(it.box) }
        return rect
    }

    private suspend fun detectWithEntityExtraction(
        text: String,
        tokens: List<TextToken>,
        pageIndex: Int
    ): List<RedactionItem> {
        val extractor = modelDownloadHelper.entityExtractor ?: return emptyList()
        val params = EntityExtractionParams.Builder(text).build()

        val annotations: List<EntityAnnotation> = try {
            extractor.annotate(params).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Entity extraction annotate failed on page $pageIndex: ${e.javaClass.simpleName}")
            return emptyList()
        }

        val results = annotations.mapNotNull { annotation ->
            val piiType = annotation.entities.mapNotNull { entity ->
                when (entity.type) {
                    Entity.TYPE_EMAIL -> PiiType.EMAIL
                    Entity.TYPE_PHONE -> PiiType.PHONE
                    Entity.TYPE_ADDRESS -> PiiType.ADDRESS
                    Entity.TYPE_PAYMENT_CARD -> PiiType.CREDIT_CARD
                    Entity.TYPE_MONEY -> PiiType.FINANCIAL_TERM
                    Entity.TYPE_IBAN -> PiiType.FINANCIAL_TERM
                    else -> null
                }
            }.firstOrNull() ?: return@mapNotNull null

            val box = boxForRange(tokens, annotation.start, annotation.end) ?: return@mapNotNull null
            RedactionItem(
                id = UUID.randomUUID().toString(),
                piiType = piiType,
                text = text.substring(annotation.start, annotation.end),
                boundingBox = box,
                pageIndex = pageIndex
            )
        }
        Log.d(TAG, "Page $pageIndex: ML Kit found ${results.size} entities")
        return results
    }

    private fun detectWithRegex(
        text: String,
        tokens: List<TextToken>,
        pageIndex: Int
    ): List<RedactionItem> {
        val items = mutableListOf<RedactionItem>()
        fun addMatches(regex: Regex, type: PiiType) {
            regex.findAll(text).forEach { match ->
                val box = boxForRange(tokens, match.range.first, match.range.last + 1) ?: return@forEach
                items += RedactionItem(UUID.randomUUID().toString(), type, match.value, box, pageIndex)
            }
        }
        addMatches(SSN_REGEX, PiiType.SSN)
        addMatches(CREDIT_CARD_REGEX, PiiType.CREDIT_CARD)
        addMatches(PASSPORT_REGEX, PiiType.PASSPORT)
        addMatches(MRN_REGEX, PiiType.MEDICAL_TERM)
        addMatches(DOB_REGEX, PiiType.DATE_OF_BIRTH)
        addMatches(EMAIL_REGEX, PiiType.EMAIL)
        addMatches(PHONE_REGEX, PiiType.PHONE)
        addMatches(PERSON_PREFIX_REGEX, PiiType.PERSON_NAME)
        Log.d(TAG, "Page $pageIndex: regex found ${items.size} items")
        return items
    }

    private fun detectContextAware(
        text: String,
        tokens: List<TextToken>,
        pageIndex: Int
    ): List<RedactionItem> {
        val items = mutableListOf<RedactionItem>()
        val lowerText = text.lowercase()
        val numberPattern = Regex("""\b\d{6,20}\b""")

        numberPattern.findAll(text).forEach { match ->
            val contextStart = maxOf(0, match.range.first - 30)
            val contextEnd = minOf(text.length, match.range.last + 30)
            val context = lowerText.substring(contextStart, contextEnd)

            val type = when {
                FINANCIAL_KEYWORDS.any { context.contains(it) } -> PiiType.FINANCIAL_TERM
                MEDICAL_KEYWORDS.any { context.contains(it) } -> PiiType.MEDICAL_TERM
                else -> return@forEach
            }
            val box = boxForRange(tokens, match.range.first, match.range.last + 1) ?: return@forEach
            items += RedactionItem(UUID.randomUUID().toString(), type, match.value, box, pageIndex)
        }
        Log.d(TAG, "Page $pageIndex: context-aware found ${items.size} items")
        return items
    }

    private fun iou(a: RectF, b: RectF): Float {
        val inter = RectF()
        if (!inter.setIntersect(a, b)) return 0f
        val interArea = inter.width() * inter.height()
        val unionArea = a.width() * a.height() + b.width() * b.height() - interArea
        return if (unionArea <= 0f) 0f else interArea / unionArea
    }

    private fun mergeOverlapping(items: List<RedactionItem>): List<RedactionItem> {
        if (items.size <= 1) return items
        val used = BooleanArray(items.size)
        val result = mutableListOf<RedactionItem>()
        for (i in items.indices) {
            if (used[i]) continue
            var box = RectF(items[i].boundingBox)
            var type = items[i].piiType
            used[i] = true
            for (j in i + 1 until items.size) {
                if (used[j]) continue
                if (items[i].pageIndex != items[j].pageIndex) continue
                if (iou(box, items[j].boundingBox) > 0.3f) {
                    box.union(items[j].boundingBox)
                    if (items[j].piiType.ordinal < type.ordinal) type = items[j].piiType
                    used[j] = true
                }
            }
            result += items[i].copy(boundingBox = box, piiType = type)
        }
        return result
    }
}
