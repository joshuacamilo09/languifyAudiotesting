// OUTRA VERSAO DO CODIGO COM MUDANÇAS
package com.example.myapplication

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
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
import java.util.*

enum class RecorderState { READY, RECORDING, PLAYING }

class MainActivity : ComponentActivity() {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var tempAudioFile: File
    private var currentRecordingUri: Uri? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (!allGranted) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tempAudioFile = File(cacheDir, "temp_recording.m4a")
        checkPermissions()

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

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            requestPermissionLauncher.launch(notGranted.toTypedArray())
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
                            Log.d(
                                "Recorder",
                                "Saved URI: $currentRecordingUri | Exists: ${tempAudioFile.exists()} | Size: ${tempAudioFile.length()}"
                            )
                            currentRecordingUri?.let { uri ->
                                if (tempAudioFile.length() > 0) {
                                    state = RecorderState.PLAYING
                                    playRecording(uri) { state = RecorderState.READY }
                                } else {
                                    Log.e("Recorder", "Arquivo vazio, não pode reproduzir")
                                    state = RecorderState.READY
                                }
                            } ?: run {
                                Log.e("Recorder", "Falha ao salvar áudio")
                                state = RecorderState.READY
                            }
                        }
                        RecorderState.PLAYING -> {
                            // Botão bloqueado durante playback
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
                    RecorderState.READY -> Icon(Icons.Default.Mic, "Start", Modifier.size(40.dp), Color.White)
                    RecorderState.RECORDING -> Icon(Icons.Default.Stop, "Stop", Modifier.size(40.dp), Color.White)
                    RecorderState.PLAYING -> CircularProgressIndicator(Modifier.size(40.dp), Color.White, 4.dp)
                }
            }
        }
    }

    /*
    * STARTRECORDING V1 * se a outra nao funcionar volta a usar essa.

    private fun startRecording() {
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(tempAudioFile.absolutePath)
            try {
                prepare()
                start()
                Log.d("Recorder", "Recording started")
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
     */

    //se este startecording nao funcionar volta a usar o padrao ai em cima
    private fun startRecording() {
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            //setAudioEncodingBitRate(128000) // 128 kbps
            //setAudioSamplingRate(44100)     // 44.1 kHz

            //se nao funcionar volte a usar a padrao de 128!
            setAudioEncodingBitRate(192000) // 192 kbps
            setAudioSamplingRate(44100)     // 44.1 kHz

            setOutputFile(tempAudioFile.absolutePath)
            try {
                prepare()
                start()
                Log.d("Recorder", "Recording started")
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
                tempAudioFile.delete()
            }
            release()
        }
        mediaRecorder = null
        Log.d("Recorder", "Recording stopped")
    }

    private fun saveRecordingToMediaStore(): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "recording_$timestamp.m4a")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/m4a")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Recordings")
            }
        }

        return try {
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { output ->
                    tempAudioFile.inputStream().use { input -> input.copyTo(output) }
                    output.flush()
                }
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun playRecording(uri: Uri, onComplete: () -> Unit) {
        try {
            val pfd = contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(pfd.fileDescriptor)
                    setOnPreparedListener { start() }
                    setOnCompletionListener {
                        release()
                        mediaPlayer = null
                        onComplete()
                    }
                    prepareAsync()
                }
            } else {
                Log.e("Recorder", "Falha ao abrir FileDescriptor")
                onComplete()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            mediaPlayer?.release()
            mediaPlayer = null
            onComplete()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        mediaPlayer?.release()
    }
}



// CODIGO VERSAO HENRIQUE:
/*
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

 */