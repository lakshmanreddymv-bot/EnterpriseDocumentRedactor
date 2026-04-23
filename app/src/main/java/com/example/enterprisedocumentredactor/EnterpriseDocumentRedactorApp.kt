package com.example.enterprisedocumentredactor

import android.app.Application
import android.os.StrictMode
import android.util.Log
import com.example.enterprisedocumentredactor.data.ml.ModelDownloadHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class EnterpriseDocumentRedactorApp : Application() {

    companion object {
        private const val TAG = "EDRApp"
    }

    @Inject
    lateinit var modelDownloadHelper: ModelDownloadHelper

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        enableStrictModeInDebug()

        // Check Play Services availability and log clearly
        val playServicesAvailable = modelDownloadHelper.isPlayServicesAvailable()
        if (playServicesAvailable) {
            Log.i(TAG, "Google Play Services available — ML Kit entity extraction enabled")
        } else {
            Log.w(TAG, "Google Play Services NOT available — ML Kit entity extraction disabled; regex + context-aware layers will be used")
        }

        // Download ML Kit model eagerly in background; never blocks the app
        appScope.launch {
            modelDownloadHelper.prepare()
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
