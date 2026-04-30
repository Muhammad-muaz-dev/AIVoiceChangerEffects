package com.example.aivoicechangersounds.utils
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream

class AudioRecorder(
    private val outputFilePath: String,
    private val onPcmData: (ByteArray, Int) -> Unit,   // callback → send to SpeechRecognizer
    private val onError: (String) -> Unit
) {

    // ─── Audio Config ────────────────────────────────────────────
    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BIT_RATE = 128_000
    private val MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC

    // ─── State ───────────────────────────────────────────────────
    @Volatile private var isRecording = false

    // ─── AudioRecord ─────────────────────────────────────────────
    private var audioRecord: AudioRecord? = null
    private val bufferSize: Int = AudioRecord.getMinBufferSize(
        SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
    ).coerceAtLeast(4096)

    // ─── MediaCodec + MediaMuxer (encode PCM → AAC → M4A) ───────
    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var audioTrackIndex = -1
    private var muxerStarted = false

    // ─── Pipe (PCM → SpeechRecognizer) ───────────────────────────
    private val pipedOutput = PipedOutputStream()
    val pipedInput = PipedInputStream(pipedOutput, bufferSize * 10)  // expose to caller

    // ─── Recording Thread ─────────────────────────────────────────
    private var recordingThread: Thread? = null

    // ─────────────────────────────────────────────────────────────
    //  PUBLIC API
    // ─────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording) return

        try {
            setupAudioRecord()
            setupEncoder()
            isRecording = true
            audioRecord?.startRecording()
            startRecordingLoop()
        } catch (e: Exception) {
            onError("Failed to start recording: ${e.message}")
            cleanup()
        }
    }

    fun stop() {
        isRecording = false
        recordingThread?.join(2000)  // wait for loop to finish
        finishEncoder()
        releaseAudioRecord()
        closePipe()
    }

    fun release() {
        stop()
    }

    // ─────────────────────────────────────────────────────────────
    //  SETUP
    // ─────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun setupAudioRecord() {
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        ).also {
            if (it.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord failed to initialize")
            }
        }
    }

    private fun setupEncoder() {
        // 1. Configure MediaFormat
        val format = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)
            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )
        }

        // 2. Create and start MediaCodec encoder
        mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE).also {
            it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            it.start()
        }

        // 3. Create MediaMuxer (will be started after first output format change)
        mediaMuxer = MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    // ─────────────────────────────────────────────────────────────
    //  RECORDING LOOP
    // ─────────────────────────────────────────────────────────────

    private fun startRecordingLoop() {
        recordingThread = Thread {
            val buffer = ByteArray(bufferSize)

            while (isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, bufferSize) ?: break

                if (bytesRead > 0) {
                    // 1. Feed raw PCM to encoder → save as M4A file
                    feedPcmToEncoder(buffer, bytesRead, endOfStream = false)

                    // 2. Feed raw PCM to pipe → SpeechRecognizer reads from pipedInput
                    feedPcmToPipe(buffer, bytesRead)

                    // 3. Notify caller (optional extra callback)
                    onPcmData(buffer.copyOf(bytesRead), bytesRead)
                }
            }
        }.also { it.start() }
    }

    // ─────────────────────────────────────────────────────────────
    //  FEED PCM → ENCODER
    // ─────────────────────────────────────────────────────────────

    private fun feedPcmToEncoder(buffer: ByteArray, bytesRead: Int, endOfStream: Boolean) {
        val codec = mediaCodec ?: return

        // Queue input buffer
        val inputIndex = codec.dequeueInputBuffer(10_000L)
        if (inputIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            inputBuffer.put(buffer, 0, bytesRead)

            val flags = if (endOfStream) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
            codec.queueInputBuffer(
                inputIndex,
                0,
                bytesRead,
                System.nanoTime() / 1000L,  // presentation time in microseconds
                flags
            )
        }

        // Drain encoder output → write to muxer
        drainEncoder(endOfStream)
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val codec = mediaCodec ?: return
        val muxer = mediaMuxer ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)

            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Must add track and start muxer here — not before
                    if (!muxerStarted) {
                        audioTrackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }

                outputIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputIndex) ?: continue

                    // Skip codec config buffers
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0 && muxerStarted) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)
                    }

                    codec.releaseOutputBuffer(outputIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }

                else -> break
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  FEED PCM → PIPE (for SpeechRecognizer)
    // ─────────────────────────────────────────────────────────────

    private fun feedPcmToPipe(buffer: ByteArray, bytesRead: Int) {
        try {
            pipedOutput.write(buffer, 0, bytesRead)
            pipedOutput.flush()
        } catch (e: IOException) {
            // Pipe closed — SpeechRecognizer was stopped
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  TEARDOWN
    // ─────────────────────────────────────────────────────────────

    private fun finishEncoder() {
        try {
            // Signal end of stream so encoder flushes remaining data
            feedPcmToEncoder(ByteArray(0), 0, endOfStream = true)
        } catch (_: Exception) {}

        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (_: Exception) {}
        mediaCodec = null

        try {
            if (muxerStarted) mediaMuxer?.stop()
            mediaMuxer?.release()
        } catch (_: Exception) {}
        mediaMuxer = null
        muxerStarted = false
    }

    private fun releaseAudioRecord() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    private fun closePipe() {
        try { pipedOutput.close() } catch (_: IOException) {}
        try { pipedInput.close() } catch (_: IOException) {}
    }

    private fun cleanup() {
        finishEncoder()
        releaseAudioRecord()
        closePipe()
    }
}