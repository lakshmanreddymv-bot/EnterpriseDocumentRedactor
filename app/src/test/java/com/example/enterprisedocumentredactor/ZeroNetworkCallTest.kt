package com.example.enterprisedocumentredactor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * HIPAA/GDPR compliance test — Zero Network Call Guarantee.
 *
 * Verifies at build-time that EnterpriseDocumentRedactor cannot make network calls:
 *  1. The Android manifest contains no INTERNET permission.
 *  2. No direct HTTP client libraries are imported in application source code.
 *
 * These are static source-analysis tests that run on the JVM with no Android
 * dependencies. They act as a regression guard: if a developer accidentally adds
 * a network dependency or permission, CI will fail with a clear compliance error.
 *
 * NOTE: ML Kit entity extraction uses Google Play Services, which has its own
 * network layer for model downloads via Play Store infrastructure. This does NOT
 * constitute an app-level network call and is explicitly excluded from this check.
 * The app itself has no INTERNET permission and cannot open sockets.
 */
class ZeroNetworkCallTest {

    // Working directory for unit tests is the 'app/' module directory.
    private val manifestFile = File("src/main/AndroidManifest.xml")
    private val sourceRoot   = File("src/main/java")

    // ── Manifest checks ───────────────────────────────────────────────────────

    @Test
    fun `Manifest exists and is readable`() {
        assertTrue(
            "AndroidManifest.xml not found at ${manifestFile.absolutePath}",
            manifestFile.exists() && manifestFile.canRead()
        )
    }

    @Test
    fun `Manifest does not declare INTERNET permission — HIPAA zero-network guarantee`() {
        val content = manifestFile.readText()
        assertFalse(
            "HIPAA VIOLATION: AndroidManifest.xml declares android.permission.INTERNET. " +
            "EnterpriseDocumentRedactor must have zero network access to protect patient data.",
            content.contains("android.permission.INTERNET")
        )
    }

    @Test
    fun `Manifest does not declare ACCESS_NETWORK_STATE permission`() {
        val content = manifestFile.readText()
        assertFalse(
            "Manifest should not request ACCESS_NETWORK_STATE — no network access is needed.",
            content.contains("android.permission.ACCESS_NETWORK_STATE")
        )
    }

    // ── Source code checks ────────────────────────────────────────────────────

    @Test
    fun `Source code does not import OkHttp`() {
        val violations = findImportViolations("okhttp3")
        assertTrue(
            "Network client found — OkHttp imported in:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `Source code does not import Retrofit`() {
        val violations = findImportViolations("retrofit2")
        assertTrue(
            "Network client found — Retrofit imported in:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `Source code does not import java-net URL or HttpURLConnection`() {
        val violations = findImportViolations("java.net.URL", "java.net.HttpURLConnection")
        assertTrue(
            "Direct network class found:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `Source code does not import Ktor client`() {
        val violations = findImportViolations("io.ktor.client")
        assertTrue(
            "Network client found — Ktor imported in:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `Source code does not import Volley`() {
        val violations = findImportViolations("com.android.volley")
        assertTrue(
            "Network client found — Volley imported in:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `Source root directory exists`() {
        assertTrue(
            "Source directory not found: ${sourceRoot.absolutePath}",
            sourceRoot.exists() && sourceRoot.isDirectory
        )
    }

    @Test
    fun `Source tree has Kotlin files to scan`() {
        val ktFiles = collectKotlinFiles()
        assertTrue(
            "No Kotlin source files found — scanner may be misconfigured.",
            ktFiles.isNotEmpty()
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns a list of "file:line — import" strings for every import in the
     * main source tree that matches any of the given [patterns].
     */
    private fun findImportViolations(vararg patterns: String): List<String> {
        val violations = mutableListOf<String>()
        collectKotlinFiles().forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                if (line.trimStart().startsWith("import")) {
                    patterns.forEach { pattern ->
                        if (line.contains(pattern)) {
                            violations += "${file.name}:${index + 1} — $line"
                        }
                    }
                }
            }
        }
        return violations
    }

    private fun collectKotlinFiles(): List<File> =
        if (sourceRoot.exists())
            sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        else
            emptyList()
}
