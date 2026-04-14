package com.prism.ui

import android.app.Application
import android.graphics.Bitmap
import android.speech.SpeechRecognizer
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.prism.state.ProfileManager
import com.prism.state.proto.DietType
import com.prism.state.proto.FitnessGoal
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PrismViewModel(application: Application) : AndroidViewModel(application) {

    private val profileManager = ProfileManager(application)

    // --- Profile State ---
    val onboardingCompleted = profileManager.onboardingCompleted.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )
    val userProfile = profileManager.profileFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    // --- UI State ---
    private val _availMin = MutableStateFlow(30)
    val availMin: StateFlow<Int> = _availMin.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _currentStage = MutableStateFlow<PipelineStage?>(null)
    val currentStage: StateFlow<PipelineStage?> = _currentStage.asStateFlow()

    private val _completedStages = MutableStateFlow<Set<PipelineStage>>(emptySet())
    val completedStages: StateFlow<Set<PipelineStage>> = _completedStages.asStateFlow()

    private val _forkedRecipe = MutableStateFlow<ForkedRecipe?>(null)
    val forkedRecipe: StateFlow<ForkedRecipe?> = _forkedRecipe.asStateFlow()

    private val _uncertainItems = MutableStateFlow<List<IdentifiedItem>>(emptyList())
    val uncertainItems: StateFlow<List<IdentifiedItem>> = _uncertainItems.asStateFlow()

    private val _thermalWarning = MutableStateFlow<String?>(null)
    val thermalWarning: StateFlow<String?> = _thermalWarning.asStateFlow()

    private val _audioText = MutableStateFlow("")

    // --- Camera ---
    private var imageCapture: ImageCapture? = null
    private var lastFrame: Bitmap? = null

    init {
        // Observe thermal state
        viewModelScope.launch {
            GemmaOrchestrator.thermalState.collect { state ->
                _thermalWarning.value = when (state) {
                    is GemmaOrchestrator.ThermalState.Throttled ->
                        "⚠ Device warming up — reduced accuracy mode"
                    is GemmaOrchestrator.ThermalState.Critical ->
                        "🔴 Device too hot — using cached results"
                    else -> null
                }
            }
        }
    }

    fun onTimeSelected(minutes: Int) { _availMin.value = minutes }

    fun onVoiceToggle() {
        _isRecording.value = !_isRecording.value
        // SpeechRecognizer integration handled by Activity (requires Context)
    }

    fun onAudioResult(text: String) {
        _audioText.value = text
        _isRecording.value = false
        // Auto-trigger pipeline when voice input completes
        executePipeline()
    }

    fun completeOnboarding(diet: DietType, goal: FitnessGoal) {
        viewModelScope.launch {
            profileManager.updateDiet(diet)
            profileManager.updateGoal(goal)
            profileManager.updateOnboarding(true)
        }
    }

    fun onIngredientConfirm(confirmed: List<IdentifiedItem>) {
        _uncertainItems.value = emptyList()
        executePipeline(confirmedItems = confirmed)
    }

    fun bindCamera(lifecycleOwner: androidx.lifecycle.LifecycleOwner, previewView: PreviewView) {
        val ctx = getApplication<Application>()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun captureFrame(onCaptured: (Bitmap?) -> Unit) {
        val capture = imageCapture ?: run { onCaptured(null); return }
        val ctx = getApplication<Application>()

        capture.takePicture(
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmap()
                    image.close()
                    lastFrame = bitmap
                    onCaptured(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    onCaptured(lastFrame) // fallback to last good frame
                }
            }
        )
    }

    private fun executePipeline(confirmedItems: List<IdentifiedItem>? = null) {
        viewModelScope.launch {
            // Reset state
            _currentStage.value = PipelineStage.Perception
            _completedStages.value = emptySet()
            _forkedRecipe.value = null

            val profile = userProfile.value
            
            // Build compressed state including user preferences
            val state = StateCompressionEngine.buildManual(
                availMin = _availMin.value
            )
            // Inject diet and goal into the prompt context via the state bridge
            val stateJson = JSONObject(state.toJson()).apply {
                profile?.let {
                    put("diet", it.diet.name)
                    put("goal", it.goal.name)
                }
            }.toString()

            // Capture current camera frame
            captureFrame { bitmap ->
                viewModelScope.launch {
                    InferenceReasoningPipeline.execute(
                        frame = bitmap,
                        audioText = _audioText.value,
                        stateJson = stateJson,
                        confirmedItems = confirmedItems
                    ).collect { event ->
                        when (event) {
                            is PipelineEvent.StageStarted ->
                                _currentStage.value = event.stage
                            is PipelineEvent.StageCompleted -> {
                                _completedStages.value += event.stage
                                // Advance current stage indicator
                                val stages = listOf(
                                    PipelineStage.Perception, PipelineStage.Reasoning,
                                    PipelineStage.Synthesis, PipelineStage.Forking
                                )
                                val nextIdx = event.stage.ordinal + 1
                                _currentStage.value = stages.getOrNull(nextIdx)
                            }
                            is PipelineEvent.StageFailed -> {
                                _currentStage.value = null
                                _thermalWarning.value = "Pipeline: ${event.error}"
                            }
                            is PipelineEvent.IngredientConfirmation -> {
                                _uncertainItems.value = event.manifest.items.filter { !it.confident }
                                _currentStage.value = null // pause pipeline
                            }
                            is PipelineEvent.FinalResult -> {
                                _forkedRecipe.value = event.recipe
                                _currentStage.value = null
                            }
                            is PipelineEvent.PartialResult -> { /* streaming text — future use */ }
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { GemmaOrchestrator.shutdown() }
    }
}
