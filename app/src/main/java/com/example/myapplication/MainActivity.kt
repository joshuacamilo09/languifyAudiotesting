package com.example.myapplication

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RecorderState {
    READY, RECORDING, PLAYING
}

class MainActivity : ComponentActivity() {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var tempAudioFile: File
    private var currentRecordingUri: Uri? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tempAudioFile = File(cacheDir, "temp_recording.3gp")
        checkPermission()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    VoiceRecorderScreen()
                }
            }
        }
    }

    private fun checkPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    @Composable
    fun VoiceRecorderScreen() {
        var state by remember { mutableStateOf(RecorderState.READY) }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            FloatingActionButton(
                onClick = {
                    when (state) {
                        RecorderState.READY -> {
                            startRecording()
                            state = RecorderState.RECORDING
                        }
                        RecorderState.RECORDING -> {
                            stopRecording()
                            currentRecordingUri = saveRecordingToMediaStore()
                            state = RecorderState.PLAYING
                            currentRecordingUri?.let { uri ->
                                playRecording(uri) {
                                    state = RecorderState.READY
                                }
                            } ?: run {
                                state = RecorderState.READY
                            }
                        }
                        RecorderState.PLAYING -> {
                            // Button is locked during playback
                        }
                    }
                },
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                containerColor = when (state) {
                    RecorderState.READY -> Color(0xFF6200EE)
                    RecorderState.RECORDING -> Color(0xFFB00020)
                    RecorderState.PLAYING -> Color.Gray
                }
            ) {
                when (state) {
                    RecorderState.READY -> {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Start Recording",
                            modifier = Modifier.size(40.dp),
                            tint = Color.White
                        )
                    }
                    RecorderState.RECORDING -> {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Recording",
                            modifier = Modifier.size(40.dp),
                            tint = Color.White
                        )
                    }
                    RecorderState.PLAYING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = Color.White,
                            strokeWidth = 4.dp
                        )
                    }
                }
            }
        }
    }

    private fun startRecording() {
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(tempAudioFile.absolutePath)

            try {
                prepare()
                start()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            try {
                stop()
            } catch (e: RuntimeException) {
                e.printStackTrace()
            }
            release()
        }
        mediaRecorder = null
    }

    private fun saveRecordingToMediaStore(): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "recording_$timestamp.3gp")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/3gpp")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Recordings")
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                tempAudioFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return uri
    }

    private fun playRecording(uri: Uri, onComplete: () -> Unit) {
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(applicationContext, uri)
                prepare()
                setOnCompletionListener {
                    release()
                    mediaPlayer = null
                    onComplete()
                }
                start()
            } catch (e: IOException) {
                e.printStackTrace()
                release()
                mediaPlayer = null
                onComplete()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        mediaRecorder = null
        mediaPlayer?.release()
        mediaPlayer = null
    }
}