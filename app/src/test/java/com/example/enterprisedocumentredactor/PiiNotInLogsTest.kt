package com.example.enterprisedocumentredactor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * HIPAA compliance test — PII must never be written to Logcat.
 *
 * Verifies two things:
 *  1. Source-level: Log calls in the detection pipeline never concatenate raw PII
 *     text (RedactionItem.text) into log messages — only safe metadata is logged
 *     (item count, PiiType enum name, page index).
 *  2. Pattern-level: None of the known PII string patterns used in tests appear
 *     in any Log call within the source tree.
 *
 * Why this matters: Android Logcat output is readable by other apps with
 * READ_LOGS permission and by ADB-connected developer machines.
 * Under HIPAA, logging patient SSNs, MRNs, names, or financial data to Logcat
 * is a reportable data breach.
 *
 * These are static source-analysis tests that run on the JVM.
 */
class PiiNotInLogsTest {

    private val sourceRoot = File("src/main/java")

    // Known PII literals used in PiiDetectorRegexTest — should never appear in Log calls
    private val piiTestLiterals = listOf(
        "123-45-6789",   // SSN
        "jane.doe@hospital.org", // email
        "415) 555",       // phone fragment
        "4532 0151",      // credit card fragment
        "A12345678",      // passport
        "9876543"         // MRN
    )

    // ── Log content checks ────────────────────────────────────────────────────

    @Test
    fun `Log calls in detection pipeline use only safe metadata — no raw PII text`() {
        val logCallsWithPiiText = mutableListOf<String>()

        collectKotlinFiles().forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { i, line ->
                if (containsLogCall(line)) {
                    // A log call that concatenates .text from a RedactionItem or match.value
                    // from a regex is a PII leak. Only count/type/pageIndex are safe.
                    if (line.contains(".text") &&
                        (line.contains("Log.d") || line.contains("Log.e") ||
                         line.contains("Log.w") || line.contains("Log.i") ||
                         line.contains("Log.v"))) {
                        logCallsWithPiiText += "${file.name}:${i + 1}: $line"
                    }
                }
            }
        }

        assertTrue(
            "HIPAA VIOLATION: These log statements may write raw PII text to Logcat:\n" +
            logCallsWithPiiText.joinToString("\n") +
            "\nUse item count, PiiType.name, or redacted placeholders instead.",
            logCallsWithPiiText.isEmpty()
        )
    }

    @Test
    fun `Source code never hardcodes PII test literals in production Log calls`() {
        val violations = mutableListOf<String>()
        collectKotlinFiles().forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                if (containsLogCall(line)) {
                    piiTestLiterals.forEach { literal ->
                        if (line.contains(literal)) {
                            violations += "${file.name}:${i + 1}: $line"
                        }
                    }
                }
            }
        }
        assertTrue(
            "PII literals found in Log statements:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `TAG constants in detection classes do not expose sensitive identifiers`() {
        // TAG values like "SSN", "CreditCard", "PatientData" would expose context in logs.
        // Acceptable: "PiiDetector", "DocumentRepo", "RedactUseCase"
        val sensitiveTagTerms = listOf("ssn", "creditcard", "credit_card", "passport",
            "patientdata", "patient_data", "mrn", "hipaa")

        val violations = mutableListOf<String>()
        collectKotlinFiles().forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                if (line.contains("TAG") && line.contains("=")) {
                    sensitiveTagTerms.forEach { term ->
                        if (line.lowercase().contains(term)) {
                            violations += "${file.name}:${i + 1}: $line"
                        }
                    }
                }
            }
        }
        assertTrue(
            "Log TAG exposes sensitive identifier:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `Detection summary logs use count not raw text`() {
        // PiiDetector logs "Page X: detected N items" — the N (count) is safe.
        // This test verifies those log messages exist and use count-style format.
        val detectorSource = File(
            "src/main/java/com/example/enterprisedocumentredactor/data/ml/PiiDetector.kt"
        )
        if (!detectorSource.exists()) return   // guard if file renamed

        val content = detectorSource.readText()
        val logLines = content.lines().filter { containsLogCall(it) }

        assertTrue("PiiDetector should have at least one log statement.", logLines.isNotEmpty())

        logLines.forEach { line ->
            assertFalse(
                "PiiDetector log statement concatenates raw item text: $line",
                line.contains("item.text") || line.contains("redactionItem.text") ||
                line.contains("match.value") || line.contains("annotation.text")
            )
        }
    }

    @Test
    fun `Source tree is non-empty — scanner is working`() {
        assertTrue(
            "No Kotlin files found at ${sourceRoot.absolutePath}",
            collectKotlinFiles().isNotEmpty()
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun containsLogCall(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.contains("Log.d(") || trimmed.contains("Log.e(") ||
               trimmed.contains("Log.w(") || trimmed.contains("Log.i(") ||
               trimmed.contains("Log.v(")
    }

    private fun collectKotlinFiles(): List<File> =
        if (sourceRoot.exists())
            sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        else
            emptyList()
}
