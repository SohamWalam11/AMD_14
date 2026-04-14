package com.prism

import android.app.Application
import com.prism.ai.GemmaOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PrismApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Warm-start Gemma session in background
        appScope.launch {
            try {
                GemmaOrchestrator.initialize()
            } catch (_: Exception) {
                // Non-fatal: session will lazy-init on first query
            }
        }
    }
}
