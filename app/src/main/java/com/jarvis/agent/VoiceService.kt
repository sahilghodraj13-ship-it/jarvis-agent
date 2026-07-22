package com.jarvis.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.*

class VoiceService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "JarvisVoice"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "jarvis_voice"
        const val ACTION_START_LISTENING = "com.jarvis.agent.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.jarvis.agent.STOP_LISTENING"
        const val ACTION_SPEAK = "com.jarvis.agent.SPEAK"
        const val EXTRA_TEXT = "text"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false
    private var ttsReady = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Ready"))
        textToSpeech = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (isListening) { scope.launch { delay(1000); startListening() } }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull() ?: return
                processVoiceCommand(spoken)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_LISTENING -> startListening()
            ACTION_STOP_LISTENING -> stopListening()
            ACTION_SPEAK -> { val text = intent.getStringExtra(EXTRA_TEXT) ?: ""; speak(text) }
            else -> startListening()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopListening()
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            textToSpeech?.language = Locale.US
            speak("Jarvis is online. I'm listening.")
        }
    }

    fun speak(text: String) {
        if (!ttsReady || textToSpeech == null) return
        updateNotification("Speaking...")
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_tts")
    }

    private fun startListening() {
        if (isListening) return
        isListening = true
        updateNotification("Listening...")
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        updateNotification("Ready")
    }

    private fun processVoiceCommand(spoken: String) {
        val a11y = JarvisAccessibilityService.instance
        if (a11y == null) { speak("Please enable Jarvis in Accessibility settings first."); return }
        if (spoken.lowercase().contains("jarvis") || spoken.lowercase().contains("hey jarvis")) {
            val command = spoken.replace(Regex("jarvis|hey jarvis", RegexOption.IGNORE_CASE), "").trim()
            if (command.isEmpty()) { speak("Yes? I'm listening."); scope.launch { delay(1500); if (isListening) startListening() }; return }
            executeCommand(command, a11y)
            return
        }
        executeCommand(spoken, a11y)
    }

    private fun executeCommand(command: String, a11y: JarvisAccessibilityService) {
        updateNotification("Processing: $command")
        a11y.executeCommand(command) { result ->
            speak(result)
            scope.launch { delay(2000); if (isListening) startListening() }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Jarvis Voice Service", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows when Jarvis is listening"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Jarvis")
        .setContentText(status)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(status))
    }
}