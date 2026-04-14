package com.prism

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prism.ui.PrismDashboard
import com.prism.ui.PrismBottomNavigationBar
import com.prism.ui.PrismMenuScreen
import com.prism.ui.PrismProfileScreen
import com.prism.ui.PrismViewModel

class MainActivity : ComponentActivity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var viewModelRef: PrismViewModel? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled — camera/audio will simply not work if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()
        initSpeechRecognizer()

        setContent {
            val vm: PrismViewModel = viewModel()
            viewModelRef = vm

            val availMin by vm.availMin.collectAsState()
            val isRecording by vm.isRecording.collectAsState()
            val stage by vm.currentStage.collectAsState()
            val completed by vm.completedStages.collectAsState()
            val recipe by vm.forkedRecipe.collectAsState()
            val uncertain by vm.uncertainItems.collectAsState()
            val thermal by vm.thermalWarning.collectAsState()

            var currentTab by remember { mutableStateOf(1) } // 0=Menu, 1=Dashboard, 2=Profile

            Scaffold(
                bottomBar = {
                    PrismBottomNavigationBar(
                        currentTab = currentTab,
                        onTabSelect = { currentTab = it }
                    )
                }
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    when (currentTab) {
                        0 -> PrismMenuScreen()
                        1 -> PrismDashboard(
                            availableMinutes = availMin,
                            onTimeSelected = vm::onTimeSelected,
                            isRecording = isRecording,
                            onVoiceToggle = {
                                vm.onVoiceToggle()
                                if (!isRecording) startListening() else stopListening()
                            },
                            pipelineStage = stage,
                            stageCompleted = completed,
                            forkedRecipe = recipe,
                            uncertainIngredients = uncertain,
                            onIngredientConfirm = vm::onIngredientConfirm,
                            onCameraReady = { pv -> vm.bindCamera(this@MainActivity, pv) },
                            thermalWarning = thermal
                        )
                        2 -> PrismProfileScreen()
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        val needed = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (needed.isNotEmpty()) permissionLauncher.launch(needed)
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    viewModelRef?.onAudioResult(text)
                }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rms: Float) {}
                override fun onBufferReceived(buf: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { viewModelRef?.onAudioResult("") }
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(type: Int, p: Bundle?) {}
            })
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }
}
