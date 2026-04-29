package com.example.aivoicechangersounds.data.repository

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecorderRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "AudioRecorderRepository"
    }

    private var mediaRecorder: MediaRecorder? = null
    private var currentFilePath: String? = null

    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    suspend fun startRecording(): String = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "recordings")
        if (!outputDir.exists()) outputDir.mkdirs()

        val fileName = "recording_${System.currentTimeMillis()}.m4a"
        val outputFile = File(outputDir, fileName)
        currentFilePath = outputFile.absolutePath

        mediaRecorder = createMediaRecorder().apply {
            // VOICE_RECOGNITION is the audio source the OS is most willing to share
            // with SpeechRecognizer running concurrently. With AudioSource.MIC the
            // second client is denied on most devices and STT silently fails.
            setAudioSource(MediaRecorder.AudioSource.MIC)   // was VOICE_RECOGNITION
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(currentFilePath)
            prepare()
            start()
        }

        Log.d(TAG, "Recording started → $currentFilePath")
        currentFilePath!!
    }

    suspend fun pauseRecording() = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
            } catch (e: Exception) {
                Log.e(TAG, "pause failed: ${e.message}")
            }
        }
    }

    suspend fun resumeRecording() = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
            } catch (e: Exception) {
                Log.e(TAG, "resume failed: ${e.message}")
            }
        }
    }

    suspend fun stopRecording(): String? = withContext(Dispatchers.IO) {
        val recorder = mediaRecorder
        mediaRecorder = null
        try {
            recorder?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "stop failed: ${e.message}")
            // If stop() throws (e.g. recording too short), the file is invalid
            currentFilePath?.let { File(it).takeIf { f -> f.exists() }?.delete() }
            currentFilePath = null
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }

        val path = currentFilePath
        currentFilePath = null

        // Sanity check: file must exist and be non-empty
        if (path != null) {
            val f = File(path)
            Log.d("file path","$f")
            if (!f.exists() || f.length() == 0L) {
                Log.w(TAG, "Recorded file missing or empty: $path")
                if (f.exists()) f.delete()
                return@withContext null
            }
        }
       path
    }

    suspend fun cancelRecording() = withContext(Dispatchers.IO) {
        val recorder = mediaRecorder
        mediaRecorder = null
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }

        currentFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) file.delete()
        }
        currentFilePath = null
    }

    fun getMaxAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
    }
}
