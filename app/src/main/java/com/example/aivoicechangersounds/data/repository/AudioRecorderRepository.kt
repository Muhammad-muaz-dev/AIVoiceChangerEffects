package com.example.aivoicechangersounds.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Records audio using [AudioRecord] (low-level PCM capture) instead of
 * [android.media.MediaRecorder].
 *
 * Why: [android.media.MediaRecorder] exclusively locks the microphone on most
 * devices, which prevents [android.speech.SpeechRecognizer] from receiving
 * audio — causing STT to silently fail and return empty text.
 *
 * [AudioRecord] is cooperative: it does NOT block concurrent mic access, so
 * STT can run at the same time and actually produce a transcript.
 *
 * The output is a standard WAV file (PCM 16-bit, mono, 44 100 Hz) which
 * [android.media.MediaPlayer] can play without any extra codecs.
 */
@Singleton
class AudioRecorderRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "AudioRecorderRepository"
        private const val SAMPLE_RATE = 44_100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val WAV_HEADER_SIZE = 44
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var currentFilePath: String? = null

    @Volatile private var isRecording = false
    @Volatile private var isPaused = false
    @Volatile private var lastMaxAmplitude = 0

    private val bufferSize: Int by lazy {
        AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(4096)
    }

    // ── Public API (same contract as the old MediaRecorder version) ──────────

    @SuppressLint("MissingPermission")
    suspend fun startRecording(): String = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "recordings")
        if (!outputDir.exists()) outputDir.mkdirs()

        val fileName = "recording_${System.currentTimeMillis()}.wav"
        val outputFile = File(outputDir, fileName)
        currentFilePath = outputFile.absolutePath

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("AudioRecord failed to initialize")
        }

        audioRecord = recorder
        isRecording = true
        isPaused = false
        lastMaxAmplitude = 0

        recorder.startRecording()

        recordingThread = Thread { writeAudioData(outputFile) }
            .also { it.start() }

        Log.d(TAG, "Recording started → $currentFilePath")
        currentFilePath!!
    }

    suspend fun pauseRecording() = withContext(Dispatchers.IO) {
        isPaused = true
        Log.d(TAG, "Recording paused")
    }

    suspend fun resumeRecording() = withContext(Dispatchers.IO) {
        isPaused = false
        Log.d(TAG, "Recording resumed")
    }

    suspend fun stopRecording(): String? = withContext(Dispatchers.IO) {
        isRecording = false
        try { recordingThread?.join(3_000) } catch (_: InterruptedException) {}
        releaseRecorder()

        val path = currentFilePath
        currentFilePath = null

        if (path != null) {
            val f = File(path)
            // WAV header is 44 bytes; anything <= that means no audio data
            if (!f.exists() || f.length() <= WAV_HEADER_SIZE) {
                Log.w(TAG, "Recorded file missing or empty: $path")
                if (f.exists()) f.delete()
                return@withContext null
            }
        }
        Log.d(TAG, "Recording stopped → $path")
        path
    }

    suspend fun cancelRecording() = withContext(Dispatchers.IO) {
        isRecording = false
        try { recordingThread?.join(3_000) } catch (_: InterruptedException) {}
        releaseRecorder()

        currentFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) file.delete()
        }
        currentFilePath = null
        Log.d(TAG, "Recording cancelled")
    }

    fun getMaxAmplitude(): Int {
        val amp = lastMaxAmplitude
        lastMaxAmplitude = 0
        return amp
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun releaseRecorder() {
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        recordingThread = null
    }

    /**
     * Runs on a background thread. Reads PCM samples from [AudioRecord],
     * computes amplitude for the waveform view, and writes everything into
     * a WAV file via [RandomAccessFile] (header is back-patched at the end
     * with the correct data size).
     */
    private fun writeAudioData(outputFile: File) {
        val samples = ShortArray(bufferSize / 2)

        try {
            RandomAccessFile(outputFile, "rw").use { raf ->
                // Placeholder header — patched when recording stops.
                raf.write(ByteArray(WAV_HEADER_SIZE))
                var totalAudioBytes = 0L

                while (isRecording) {
                    val shortsRead = audioRecord?.read(samples, 0, samples.size) ?: 0
                    if (shortsRead <= 0) continue

                    if (!isPaused) {
                        // Peak amplitude for the waveform view
                        var peak = 0
                        for (i in 0 until shortsRead) {
                            val v = abs(samples[i].toInt())
                            if (v > peak) peak = v
                        }
                        lastMaxAmplitude = peak

                        // Convert shorts → little-endian bytes and write
                        val byteCount = shortsRead * 2
                        val bytes = ByteArray(byteCount)
                        for (i in 0 until shortsRead) {
                            bytes[i * 2] = (samples[i].toInt() and 0xFF).toByte()
                            bytes[i * 2 + 1] = (samples[i].toInt() shr 8 and 0xFF).toByte()
                        }
                        raf.write(bytes, 0, byteCount)
                        totalAudioBytes += byteCount
                    }
                    // When paused: still reading (draining the buffer) but discarding data.
                }

                // Back-patch the WAV header with real sizes.
                writeWavHeader(raf, totalAudioBytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeAudioData error: ${e.message}", e)
        }
    }

    private fun writeWavHeader(raf: RandomAccessFile, dataSize: Long) {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        raf.seek(0)
        raf.write("RIFF".toByteArray(Charsets.US_ASCII))
        raf.write(intLE((36 + dataSize).toInt()))
        raf.write("WAVE".toByteArray(Charsets.US_ASCII))

        raf.write("fmt ".toByteArray(Charsets.US_ASCII))
        raf.write(intLE(16))                          // sub-chunk size
        raf.write(shortLE(1))                         // PCM format
        raf.write(shortLE(channels.toShort()))
        raf.write(intLE(SAMPLE_RATE))
        raf.write(intLE(byteRate))
        raf.write(shortLE(blockAlign.toShort()))
        raf.write(shortLE(bitsPerSample.toShort()))

        raf.write("data".toByteArray(Charsets.US_ASCII))
        raf.write(intLE(dataSize.toInt()))
    }

    private fun intLE(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(),
        (v shr 8 and 0xFF).toByte(),
        (v shr 16 and 0xFF).toByte(),
        (v shr 24 and 0xFF).toByte()
    )

    private fun shortLE(v: Short) = byteArrayOf(
        (v.toInt() and 0xFF).toByte(),
        (v.toInt() shr 8 and 0xFF).toByte()
    )
}
