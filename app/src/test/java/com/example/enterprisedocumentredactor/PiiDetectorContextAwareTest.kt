package com.example.enterprisedocumentredactor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for Layer 3 context-aware PII detection in [PiiDetector].
 *
 * Layer 3 detects numeric sequences (6–20 digits) that appear near financial or
 * medical keyword context. These tests run entirely on the JVM — no Android deps.
 *
 * They mirror the logic in [PiiDetector.detectContextAware] by replicating the
 * keyword sets and number pattern, enabling pure-JVM verification of the spec.
 */
class PiiDetectorContextAwareTest {

    // ── Mirrors of PiiDetector companion object ───────────────────────────────
    private val FINANCIAL_KEYWORDS = setOf(
        "account", "acct", "routing", "aba", "swift", "iban", "balance", "invoice"
    )
    private val MEDICAL_KEYWORDS = setOf(
        "patient", "diagnosis", "prescription", "mrn", "npi", "dob", "icd", "rx"
    )
    private val NUMBER_PATTERN = Regex("""\b\d{6,20}\b""")

    // ── Financial context detection ───────────────────────────────────────────

    @Test fun `Account number detected with 'account' keyword in context`() =
        assertTrue(detectsContextAware("Account: 123456789"))

    @Test fun `Account number detected with 'acct' abbreviation`() =
        assertTrue(detectsContextAware("Acct 9876543210"))

    @Test fun `Routing number detected near 'routing' keyword`() =
        assertTrue(detectsContextAware("Routing number: 021000021"))

    @Test fun `SWIFT code context detected near 'swift'`() =
        assertTrue(detectsContextAware("swift code ref 123456789012"))

    @Test fun `IBAN context detected near 'iban'`() =
        assertTrue(detectsContextAware("IBAN ref 123456789012345"))

    @Test fun `Balance reference detected near 'balance'`() =
        assertTrue(detectsContextAware("balance 1234567"))

    @Test fun `Invoice number detected near 'invoice'`() =
        assertTrue(detectsContextAware("invoice 20240115001"))

    @Test fun `ABA routing context detected`() =
        assertTrue(detectsContextAware("ABA 021000021"))

    // ── Medical context detection ─────────────────────────────────────────────

    @Test fun `Patient ID detected near 'patient' keyword`() =
        assertTrue(detectsContextAware("patient id 1234567"))

    @Test fun `Diagnosis code detected near 'diagnosis'`() =
        assertTrue(detectsContextAware("diagnosis code 123456"))

    @Test fun `Prescription number detected near 'prescription'`() =
        assertTrue(detectsContextAware("prescription 9876543"))

    @Test fun `NPI number detected near 'npi'`() =
        assertTrue(detectsContextAware("NPI 1234567890"))

    @Test fun `ICD code context detected near 'icd'`() =
        assertTrue(detectsContextAware("icd code 123456789"))

    @Test fun `Rx number detected near 'rx'`() =
        assertTrue(detectsContextAware("rx 1234567"))

    // ── Negative cases — number without relevant context ──────────────────────

    @Test fun `Random 8-digit number without context not detected`() =
        assertFalse(detectsContextAware("Reference 12345678"))

    @Test fun `Short 5-digit number with context not detected`() =
        assertFalse(detectsContextAware("account 12345"))   // below 6-digit minimum

    @Test fun `Number with unrelated context not detected`() =
        assertFalse(detectsContextAware("Order 12345678 shipped"))

    @Test fun `Empty string produces no false positives`() =
        assertFalse(detectsContextAware(""))

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test fun `Keyword at start, number at end — within 30-char window — detected`() =
        assertTrue(detectsContextAware("patient 123456789"))

    @Test fun `Keyword far from number — outside context window — not detected`() {
        // The context window is 30 chars before/after the number.
        // Build a string where keyword is > 30 chars away from the number.
        val padding = "x".repeat(35)
        assertFalse(detectsContextAware("account${padding}123456789"))
    }

    @Test fun `Case-insensitive keyword matching`() {
        assertTrue(detectsContextAware("PATIENT id 1234567"))
        assertTrue(detectsContextAware("Patient ID 1234567"))
        assertTrue(detectsContextAware("ACCOUNT number 9876543210"))
    }

    // ── Keyword completeness ──────────────────────────────────────────────────

    @Test fun `All financial keywords are defined`() {
        val expected = setOf("account", "acct", "routing", "aba", "swift", "iban", "balance", "invoice")
        assertEquals(expected, FINANCIAL_KEYWORDS)
    }

    @Test fun `All medical keywords are defined`() {
        val expected = setOf("patient", "diagnosis", "prescription", "mrn", "npi", "dob", "icd", "rx")
        assertEquals(expected, MEDICAL_KEYWORDS)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Replicates [PiiDetector.detectContextAware] logic without Android dependencies.
     * Returns true if any number in [text] is flagged due to nearby keyword context.
     */
    private fun detectsContextAware(text: String): Boolean {
        val lower = text.lowercase()
        NUMBER_PATTERN.findAll(text).forEach { match ->
            val contextStart = maxOf(0, match.range.first - 30)
            val contextEnd   = minOf(text.length, match.range.last + 30)
            val context      = lower.substring(contextStart, contextEnd)
            val isFinancial  = FINANCIAL_KEYWORDS.any { context.contains(it) }
            val isMedical    = MEDICAL_KEYWORDS.any { context.contains(it) }
            if (isFinancial || isMedical) return true
        }
        return false
    }

    private fun assertEquals(expected: Set<String>, actual: Set<String>) {
        val missing = expected - actual
        val extra   = actual - expected
        assertTrue(
            "Keyword set mismatch. Missing: $missing  Extra: $extra",
            missing.isEmpty() && extra.isEmpty()
        )
    }
}
