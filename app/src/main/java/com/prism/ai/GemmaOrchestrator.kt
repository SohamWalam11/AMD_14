package com.prism.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Custom Cloud VM Orchestrator.
 * Connects to the user's GCP instance instead of full on-device execution.
 */
object GemmaOrchestrator {

    private const val TAG = "GemmaOrchestrator"

    // Set to match user's provided GCP IP
    private const val GCP_VM_IP = "34.173.91.32"
    
    // Most common open-weights endpoints (Ollama format assumed here, can be easily changed to OpenAI v1)
    private const val API_URL = "http://$GCP_VM_IP:11434/api/generate"

    // --- Thermal State Machine (Retained for UI compatibility) ---
    sealed class ThermalState {
        object Normal : ThermalState()
        object Throttled : ThermalState()   // reduce image res, disable CoT
        object Critical : ThermalState()    // return cached response only
    }

    private val _thermalState = MutableStateFlow<ThermalState>(ThermalState.Normal)
    val thermalState: StateFlow<ThermalState> = _thermalState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var consecutiveFailures = 0
    private const val MAX_FAILURES_BEFORE_COOLDOWN = 3
    private const val IMAGE_MAX_DIM_NORMAL = 512
    private const val IMAGE_MAX_DIM_THROTTLED = 256

    @Volatile
    private var lastGoodResponse: String? = null

    suspend fun initialize() {
        Log.i(TAG, "GCP VM Orchestrator initialized (IP: $GCP_VM_IP)")
        _thermalState.value = ThermalState.Normal
        consecutiveFailures = 0
    }

    suspend fun safeInfer(prompt: String): Result<String> {
        if (_thermalState.value is ThermalState.Critical) {
            return lastGoodResponse?.let { Result.success(it) }
                ?: Result.failure(InferenceException("Thermal critical, no cached response"))
        }

        return withContext(Dispatchers.IO) {
            try {
                // OpenAI-compatible format (uncomment if using vLLM/TGI instead of Ollama)
                /*
                val bodyJson = JSONObject().apply {
                    put("model", "gemma-2b-it") // or your specific model name
                    put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                    put("temperature", 0.3)
                }
                val request = Request.Builder()
                    .url("http://$GCP_VM_IP:8000/v1/chat/completions")
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                */

                // Ollama Format (defaulting to this as it's the easiest way to run Gemma)
                val bodyJson = JSONObject().apply {
                    put("model", "gemma:2b")
                    put("prompt", prompt)
                    put("stream", false)
                    put("options", JSONObject().apply {
                        put("temperature", 0.3)
                    })
                }

                val request = Request.Builder()
                    .url(API_URL)
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                Log.d(TAG, "Sending request to GCP VM...")
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    throw Exception("HTTP Error: ${response.code}")
                }

                val responseString = response.body?.string() ?: ""
                
                // Parse Ollama response format
                val jsonRes = JSONObject(responseString)
                val text = jsonRes.optString("response", "")

                consecutiveFailures = 0
                _thermalState.value = ThermalState.Normal
                lastGoodResponse = text

                Log.d(TAG, "GCP Instance responded successfully")
                Result.success(text)

            } catch (e: Exception) {
                Log.e(TAG, "GCP Inference error: ${e.message}")
                handleInferenceError(e)
            }
        }
    }

    fun inferStream(prompt: String): Flow<String> = flow {
        // Fallback to simple blocking execution for streaming simplicity in MVP
        val res = safeInfer(prompt)
        res.getOrNull()?.let { emit(it) }
    }

    fun encodeFrame(bitmap: Bitmap): String {
        val maxDim = when (_thermalState.value) {
            is ThermalState.Throttled -> IMAGE_MAX_DIM_THROTTLED
            else -> IMAGE_MAX_DIM_NORMAL
        }

        val scaled = scaleBitmap(bitmap, maxDim)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun scaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
        if (src.width <= maxDim && src.height <= maxDim) return src
        val ratio = minOf(maxDim.toFloat() / src.width, maxDim.toFloat() / src.height)
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt(),
            (src.height * ratio).toInt(),
            true
        )
    }

    // Stubbed token budget
    fun estimateTokens(vararg parts: String): Int = parts.sumOf { it.length } / 4
    fun isWithinBudget(vararg parts: String): Boolean = estimateTokens(*parts) <= 2048

    private fun handleInferenceError(e: Exception): Result<String> {
        consecutiveFailures++
        return when {
            consecutiveFailures >= MAX_FAILURES_BEFORE_COOLDOWN -> {
                _thermalState.value = ThermalState.Critical
                lastGoodResponse?.let { Result.success(it) }
                    ?: Result.failure(InferenceException("Critical thermal, no cache"))
            }
            consecutiveFailures >= 2 -> {
                _thermalState.value = ThermalState.Throttled
                Result.failure(InferenceException("Throttled: ${e.message}", e))
            }
            else -> {
                Result.failure(InferenceException("AI error: ${e.message}", e))
            }
        }
    }

    fun resetThermalState() {
        consecutiveFailures = 0
        _thermalState.value = ThermalState.Normal
    }

    suspend fun shutdown() {
        // No persistent resources to shut down for HTTP client except thread pool (optional)
    }
}

class InferenceException(message: String, cause: Throwable? = null) : Exception(message, cause)
