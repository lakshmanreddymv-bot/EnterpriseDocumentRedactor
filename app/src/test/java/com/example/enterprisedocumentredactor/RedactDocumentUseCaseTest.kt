package com.example.enterprisedocumentredactor

import android.graphics.RectF
import com.example.enterprisedocumentredactor.domain.model.PiiType
import com.example.enterprisedocumentredactor.domain.model.RedactionItem
import com.example.enterprisedocumentredactor.domain.model.RedactionResult
import com.example.enterprisedocumentredactor.domain.repository.DocumentRepository
import com.example.enterprisedocumentredactor.domain.usecase.RedactDocumentUseCase
import com.example.enterprisedocumentredactor.domain.usecase.ScanDocumentUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [RedactDocumentUseCase] and [ScanDocumentUseCase].
 *
 * Verifies the use-case contract:
 *  - Correct delegation to [DocumentRepository]
 *  - Return values are passed through unchanged
 *  - Each use-case has a single responsibility (no business logic leaking in)
 *
 * [DocumentRepository] is a pure Kotlin interface — no Android framework
 * dependencies, mocked with Mockito.
 *
 * testOptions.isReturnDefaultValues = true handles RectF instantiation on JVM.
 */
class RedactDocumentUseCaseTest {

    private lateinit var repository: DocumentRepository
    private lateinit var redactUseCase: RedactDocumentUseCase
    private lateinit var scanUseCase: ScanDocumentUseCase

    private val fakeBox = RectF()      // (0,0,0,0) — fine for unit test; coordinates not tested

    @Before
    fun setUp() {
        repository   = mock()
        redactUseCase = RedactDocumentUseCase(repository)
        scanUseCase   = ScanDocumentUseCase(repository)
    }

    // ── RedactDocumentUseCase ─────────────────────────────────────────────────

    @Test
    fun `redact delegates to repository with correct arguments`() = runTest {
        val items = listOf(
            RedactionItem("id-1", PiiType.SSN,   "123-45-6789", fakeBox, 0),
            RedactionItem("id-2", PiiType.EMAIL, "j@h.org",     fakeBox, 0)
        )
        val expected = RedactionResult(
            documentId   = "doc-001",
            outputPath   = "/redacted/doc-001.pdf",
            redactedItems = items
        )
        whenever(repository.redactDocument("doc-001", items, "/source/doc.pdf"))
            .thenReturn(expected)

        val result = redactUseCase("doc-001", items, "/source/doc.pdf")

        assertEquals(expected, result)
        verify(repository).redactDocument("doc-001", items, "/source/doc.pdf")
    }

    @Test
    fun `redact with empty item list delegates correctly`() = runTest {
        val expected = RedactionResult("doc-002", "/redacted/doc-002.pdf", emptyList())
        whenever(repository.redactDocument("doc-002", emptyList(), "/source/doc2.pdf"))
            .thenReturn(expected)

        val result = redactUseCase("doc-002", emptyList(), "/source/doc2.pdf")

        assertEquals(0, result.totalCount)
        verify(repository).redactDocument("doc-002", emptyList(), "/source/doc2.pdf")
    }

    @Test
    fun `redact result countByType aggregates correctly`() = runTest {
        val items = listOf(
            RedactionItem("1", PiiType.SSN,   "111-22-3333", fakeBox, 0),
            RedactionItem("2", PiiType.SSN,   "444-55-6666", fakeBox, 1),
            RedactionItem("3", PiiType.EMAIL, "a@b.com",     fakeBox, 0)
        )
        val result = RedactionResult("doc-003", "/out/doc.pdf", items)

        assertEquals(2, result.countByType[PiiType.SSN])
        assertEquals(1, result.countByType[PiiType.EMAIL])
        assertEquals(3, result.totalCount)
    }

    @Test
    fun `redact propagates repository exception`() = runTest {
        whenever(repository.redactDocument(any(), any(), any()))
            .thenThrow(RuntimeException("Redaction engine failure"))

        try {
            redactUseCase("doc-fail", emptyList(), "/fail.pdf")
            assertTrue("Expected exception was not thrown", false)
        } catch (e: RuntimeException) {
            assertEquals("Redaction engine failure", e.message)
        }
    }

    // ── ScanDocumentUseCase ───────────────────────────────────────────────────

    @Test
    fun `scan delegates to repository with correct file path`() = runTest {
        val expected = listOf(
            RedactionItem("s-1", PiiType.PHONE, "555-1234", fakeBox, 0)
        )
        whenever(repository.scanDocument("/docs/patient.pdf")).thenReturn(expected)

        val result = scanUseCase("/docs/patient.pdf")

        assertEquals(expected, result)
        verify(repository).scanDocument("/docs/patient.pdf")
    }

    @Test
    fun `scan returns empty list when no PII found`() = runTest {
        whenever(repository.scanDocument(any())).thenReturn(emptyList())

        val result = scanUseCase("/docs/clean.pdf")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `scan result contains all PII types detected`() = runTest {
        val allTypes = listOf(
            RedactionItem("1", PiiType.SSN,            "123-45-6789", fakeBox, 0),
            RedactionItem("2", PiiType.EMAIL,          "a@b.com",     fakeBox, 0),
            RedactionItem("3", PiiType.PHONE,          "555-1234",    fakeBox, 0),
            RedactionItem("4", PiiType.CREDIT_CARD,    "4111 1111",   fakeBox, 0),
            RedactionItem("5", PiiType.PASSPORT,       "A1234567",    fakeBox, 0),
            RedactionItem("6", PiiType.MEDICAL_TERM,   "MRN:123456",  fakeBox, 0),
            RedactionItem("7", PiiType.DATE_OF_BIRTH,  "04/12/1980",  fakeBox, 0),
            RedactionItem("8", PiiType.PERSON_NAME,    "Dr. Doe",     fakeBox, 0),
            RedactionItem("9", PiiType.ADDRESS,        "123 Main St", fakeBox, 0),
            RedactionItem("10", PiiType.FINANCIAL_TERM,"Account 999", fakeBox, 0)
        )
        whenever(repository.scanDocument(any())).thenReturn(allTypes)

        val result = scanUseCase("/docs/full.pdf")

        assertEquals(10, result.size)
        val types = result.map { it.piiType }.toSet()
        PiiType.entries
            .filter { it != PiiType.CUSTOM }
            .forEach { type ->
                assertTrue("PiiType.$type should be in result", types.contains(type))
            }
    }

    @Test
    fun `scan propagates repository exception`() = runTest {
        whenever(repository.scanDocument(any()))
            .thenThrow(IllegalStateException("Document locked"))

        try {
            scanUseCase("/locked.pdf")
            assertTrue("Expected exception was not thrown", false)
        } catch (e: IllegalStateException) {
            assertEquals("Document locked", e.message)
        }
    }
}
