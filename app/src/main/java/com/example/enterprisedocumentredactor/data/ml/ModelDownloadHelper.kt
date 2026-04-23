package com.example.enterprisedocumentredactor.data.ml

import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractor
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloadHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ModelDownloadHelper"
    }

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    private val _downloadProgress = MutableStateFlow("ML Kit model initializing…")
    val downloadProgress: StateFlow<String> = _downloadProgress.asStateFlow()

    // EntityExtractor instance, available once model is ready
    var entityExtractor: EntityExtractor? = null
        private set

    fun isPlayServicesAvailable(): Boolean {
        val result = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context)
        return result == ConnectionResult.SUCCESS
    }

    suspend fun prepare() {
        if (!isPlayServicesAvailable()) {
            Log.w(TAG, "Google Play Services unavailable — ML Kit entity extraction disabled, regex-only mode active")
            _downloadProgress.value = "Play Services unavailable — regex mode active"
            return
        }

        _downloadProgress.value = "Downloading ML Kit entity extraction model…"
        Log.i(TAG, "Starting entity extraction model download")

        try {
            val extractor = EntityExtraction.getClient(
                EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
            )
            extractor.downloadModelIfNeeded().await()
            entityExtractor = extractor
            _isModelReady.value = true
            _downloadProgress.value = "ML Kit model ready"
            Log.i(TAG, "Entity extraction model downloaded and ready")
        } catch (e: Exception) {
            Log.w(TAG, "Entity extraction model download failed (${e.javaClass.simpleName}) — regex mode active")
            _downloadProgress.value = "ML Kit unavailable — regex mode active"
            // Do not crash — regex + context layers will handle detection
        }
    }
}
