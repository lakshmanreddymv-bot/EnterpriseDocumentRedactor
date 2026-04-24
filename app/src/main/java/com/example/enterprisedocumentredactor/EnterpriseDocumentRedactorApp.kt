package com.example.enterprisedocumentredactor

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.enterprisedocumentredactor.data.ml.DocumentScanner
import com.example.enterprisedocumentredactor.data.ml.ModelDownloadHelper
import com.example.enterprisedocumentredactor.security.AppLockManager
import com.example.enterprisedocumentredactor.security.RetentionPolicyManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class EnterpriseDocumentRedactorApp : Application() {

    companion object {
        private const val TAG = "EDRApp"
        private const val CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    @Inject lateinit var modelDownloadHelper: ModelDownloadHelper
    @Inject lateinit var documentScanner: DocumentScanner
    @Inject lateinit var appLockManager: AppLockManager
    @Inject lateinit var retentionPolicyManager: RetentionPolicyManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        enableStrictModeInDebug()

        // Observe app-level lifecycle for auto-lock
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                appLockManager.onAppBackground()
            }
            override fun onStart(owner: LifecycleOwner) {
                appLockManager.onAppForeground()
            }
        })

        val playServicesAvailable = modelDownloadHelper.isPlayServicesAvailable()
        if (playServicesAvailable) {
            Log.i(TAG, "Google Play Services available — ML Kit entity extraction enabled")
        } else {
            Log.w(TAG, "Google Play Services NOT available — regex + context-aware layers active")
        }

        appScope.launch {
            // Download ML Kit model eagerly; never blocks the app
            modelDownloadHelper.prepare()

            // Enforce data retention policy on launch
            retentionPolicyManager.enforceRetentionPolicy()

            // Clean up stale cache files older than 24 hours
            cleanOldCacheFiles()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // M8: release ML Kit resources
        documentScanner.close()
        modelDownloadHelper.close()
    }

    private fun cleanOldCacheFiles() {
        val cutoff = System.currentTimeMillis() - CACHE_MAX_AGE_MS
        cacheDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                file.delete()
                Log.d(TAG, "Cleaned stale cache file: ${file.name}")
            }
        }
    }

    private fun enableStrictModeInDebug() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }
}
