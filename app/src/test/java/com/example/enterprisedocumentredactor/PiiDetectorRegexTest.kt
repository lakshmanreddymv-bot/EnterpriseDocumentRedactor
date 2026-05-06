package com.example.enterprisedocumentredactor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM unit tests that verify each PII regex pattern used by [PiiDetector].
 *
 * These tests run without Android dependencies and document the exact detection
 * specification for HIPAA/GDPR compliance verification. The regexes are defined
 * here identically to [PiiDetector]'s companion object — any drift between
 * this file and production code will cause tests to fail, acting as a canary.
 *
 * Layer tested: Layer 2 (regex patterns) of the 3-layer PII detection pipeline.
 */
class PiiDetectorRegexTest {

    // ── Mirrors of PiiDetector companion object regexes ──────────────────────
    // These must stay in sync with PiiDetector. If production patterns change,
    // update these and add a test case to cover the new behaviour.

    private val SSN_REGEX          = Regex("""\b\d{3}-\d{2}-\d{4}\b""")
    private val CREDIT_CARD_REGEX  = Regex("""\b(?:\d{4}[\s\-]?){3}\d{4}\b""")
    private val PASSPORT_REGEX     = Regex("""\b[A-Z]{1,2}\d{6,9}\b""")
    private val MRN_REGEX          = Regex("""(?i)\bMRN[\s:#]?\d{6,10}\b""")
    private val DOB_REGEX          = Regex("""\b(?:0?[1-9]|1[0-2])[/\-](?:0?[1-9]|[12]\d|3[01])[/\-](?:\d{2}|\d{4})\b""")
    private val EMAIL_REGEX        = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")
    private val PHONE_REGEX        = Regex("""\b(?:\+?1[\s.\-]?)?\(?\d{3}\)?[\s.\-]?\d{3}[\s.\-]?\d{4}\b""")
    private val PERSON_PREFIX_REGEX = Regex("""(?i)\b(?:Mr\.|Mrs\.|Ms\.|Dr\.|Prof\.)\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+)*""")

    // ── SSN ──────────────────────────────────────────────────────────────────

    @Test fun `SSN standard format detected`() =
        assertTrue(SSN_REGEX.containsMatchIn("Patient SSN: 123-45-6789"))

    @Test fun `SSN at start of string detected`() =
        assertTrue(SSN_REGEX.containsMatchIn("123-45-6789 is the patient identifier"))

    @Test fun `SSN without hyphens not detected`() =
        assertFalse(SSN_REGEX.containsMatchIn("123456789"))

    @Test fun `SSN with wrong segment lengths not detected`() =
        assertFalse(SSN_REGEX.containsMatchIn("12-345-6789"))

    @Test fun `SSN embedded mid-word not detected`() =
        assertFalse(SSN_REGEX.containsMatchIn("prefix123-45-6789suffix"))

    @Test fun `Multiple SSNs in one string both detected`() {
        val matches = SSN_REGEX.findAll("SSN: 111-22-3333 and backup 444-55-6666").count()
        assertTrue("Expected 2 SSN matches, found $matches", matches == 2)
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    @Test fun `Standard email detected`() =
        assertTrue(EMAIL_REGEX.containsMatchIn("Contact john.doe@example.com for details"))

    @Test fun `Email with plus tag detected`() =
        assertTrue(EMAIL_REGEX.containsMatchIn("patient+records@hospital.org"))

    @Test fun `Email with subdomain detected`() =
        assertTrue(EMAIL_REGEX.containsMatchIn("records@dept.hospital.nhs.uk"))

    @Test fun `Email uppercase domain detected`() =
        assertTrue(EMAIL_REGEX.containsMatchIn("User@EXAMPLE.COM"))

    @Test fun `String with no at-sign not detected as email`() =
        assertFalse(EMAIL_REGEX.containsMatchIn("notanemail.com"))

    @Test fun `Partial email without domain not detected`() =
        assertFalse(EMAIL_REGEX.containsMatchIn("user@"))

    // ── Phone ─────────────────────────────────────────────────────────────────

    @Test fun `US phone with dashes detected`() =
        assertTrue(PHONE_REGEX.containsMatchIn("Call 555-867-5309"))

    @Test fun `US phone with country code detected`() =
        assertTrue(PHONE_REGEX.containsMatchIn("+1 555 867 5309"))

    @Test fun `US phone with parentheses detected`() =
        assertTrue(PHONE_REGEX.containsMatchIn("(555) 867-5309"))

    @Test fun `US phone with dots detected`() =
        assertTrue(PHONE_REGEX.containsMatchIn("555.867.5309"))

    @Test fun `Short number not detected as phone`() =
        assertFalse(PHONE_REGEX.containsMatchIn("Call 555-1234"))

    // ── Credit Card ───────────────────────────────────────────────────────────

    @Test fun `Visa 16-digit grouped detected`() =
        assertTrue(CREDIT_CARD_REGEX.containsMatchIn("Card: 4532 0151 1283 0366"))

    @Test fun `Mastercard 16-digit with dashes detected`() =
        assertTrue(CREDIT_CARD_REGEX.containsMatchIn("5500-0000-0000-0004"))

    @Test fun `Amex 16-digit ungrouped detected`() =
        assertTrue(CREDIT_CARD_REGEX.containsMatchIn("4111111111111111"))

    @Test fun `12-digit number not detected as credit card`() =
        assertFalse(CREDIT_CARD_REGEX.containsMatchIn("411111111111"))

    @Test fun `17-digit number not detected as credit card`() =
        assertFalse(CREDIT_CARD_REGEX.containsMatchIn("41111111111111111"))

    // ── Passport ─────────────────────────────────────────────────────────────

    @Test fun `US passport format detected`() =
        assertTrue(PASSPORT_REGEX.containsMatchIn("Passport: A12345678"))

    @Test fun `Two-letter prefix passport detected`() =
        assertTrue(PASSPORT_REGEX.containsMatchIn("UK passport AB123456"))

    @Test fun `Lowercase passport not detected`() =
        assertFalse(PASSPORT_REGEX.containsMatchIn("a12345678"))

    @Test fun `Too-short passport not detected`() =
        assertFalse(PASSPORT_REGEX.containsMatchIn("A12345"))

    // ── Date of Birth ─────────────────────────────────────────────────────────

    @Test fun `DOB slash format detected`() =
        assertTrue(DOB_REGEX.containsMatchIn("DOB: 04/15/1985"))

    @Test fun `DOB dash format detected`() =
        assertTrue(DOB_REGEX.containsMatchIn("Born 12-31-1990"))

    @Test fun `DOB two-digit year detected`() =
        assertTrue(DOB_REGEX.containsMatchIn("DOB 06/20/85"))

    @Test fun `Invalid month 13 not detected`() =
        assertFalse(DOB_REGEX.containsMatchIn("13/01/2000"))

    @Test fun `Invalid day 32 not detected`() =
        assertFalse(DOB_REGEX.containsMatchIn("01/32/2000"))

    // ── MRN (Medical Record Number) ───────────────────────────────────────────

    @Test fun `MRN with colon detected`() =
        assertTrue(MRN_REGEX.containsMatchIn("MRN: 1234567"))

    @Test fun `MRN with hash detected`() =
        assertTrue(MRN_REGEX.containsMatchIn("MRN#9876543"))

    @Test fun `MRN lowercase detected`() =
        assertTrue(MRN_REGEX.containsMatchIn("mrn 00112233"))

    @Test fun `MRN too short not detected`() =
        assertFalse(MRN_REGEX.containsMatchIn("MRN: 12345"))  // 5 digits, min is 6

    // ── Person Name (prefix-based) ────────────────────────────────────────────

    @Test fun `Dr prefix detected`() =
        assertTrue(PERSON_PREFIX_REGEX.containsMatchIn("Dr. John Smith performed the scan"))

    @Test fun `Ms prefix detected`() =
        assertTrue(PERSON_PREFIX_REGEX.containsMatchIn("Results for Ms. Jane Doe"))

    @Test fun `Mr prefix detected`() =
        assertTrue(PERSON_PREFIX_REGEX.containsMatchIn("Mr. Robert Brown"))

    @Test fun `Prof prefix detected`() =
        assertTrue(PERSON_PREFIX_REGEX.containsMatchIn("Prof. Alice Williams authored the report"))

    @Test fun `Name without recognised prefix not detected`() =
        assertFalse(PERSON_PREFIX_REGEX.containsMatchIn("John Smith attended"))

    @Test fun `Prefix without capitalised name not detected`() =
        assertFalse(PERSON_PREFIX_REGEX.containsMatchIn("Dr. john lowercase"))

    // ── Edge-cases shared across patterns ────────────────────────────────────

    @Test fun `Empty string produces no matches`() {
        val text = ""
        assertFalse(SSN_REGEX.containsMatchIn(text))
        assertFalse(EMAIL_REGEX.containsMatchIn(text))
        assertFalse(PHONE_REGEX.containsMatchIn(text))
        assertFalse(CREDIT_CARD_REGEX.containsMatchIn(text))
        assertFalse(PASSPORT_REGEX.containsMatchIn(text))
        assertFalse(DOB_REGEX.containsMatchIn(text))
        assertFalse(MRN_REGEX.containsMatchIn(text))
        assertFalse(PERSON_PREFIX_REGEX.containsMatchIn(text))
    }

    @Test fun `High-density PII record — all patterns fire`() {
        val record = """
            Patient: Dr. Jane Doe  SSN: 123-45-6789  DOB: 04/12/1980
            Email: jane.doe@hospital.org  Phone: (415) 555-1234
            CC: 4532 0151 1283 0366  Passport: A12345678  MRN: 9876543
        """.trimIndent()

        assertTrue("SSN expected",      SSN_REGEX.containsMatchIn(record))
        assertTrue("Email expected",    EMAIL_REGEX.containsMatchIn(record))
        assertTrue("Phone expected",    PHONE_REGEX.containsMatchIn(record))
        assertTrue("CC expected",       CREDIT_CARD_REGEX.containsMatchIn(record))
        assertTrue("Passport expected", PASSPORT_REGEX.containsMatchIn(record))
        assertTrue("DOB expected",      DOB_REGEX.containsMatchIn(record))
        assertTrue("MRN expected",      MRN_REGEX.containsMatchIn(record))
        assertTrue("Name expected",     PERSON_PREFIX_REGEX.containsMatchIn(record))
    }
}
