package com.example.aivoicechangersounds.utils

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton class to reverse an audio file programmatically.
 * Managed by Hilt DI — injected wherever needed.
 *
 * Flow:
 * 1. MediaExtractor reads the compressed audio file (MP3, AAC, M4A, WAV, etc.)
 * 2. MediaCodec decodes it to raw PCM samples
 * 3. PCM samples are reversed (respecting 16-bit sample boundaries)
 * 4. Reversed PCM is written as a standard WAV file
 *
 * Usage (injected via Hilt):
 *   @Inject lateinit var audioReverser: AudioReverser
 *   val reversedPath = audioReverser.reverse(inputFilePath, outputDir)
 */
@Singleton
class AudioReverser @Inject constructor() {

    /**
     * Reverses the audio in [inputPath] and writes a WAV file to [outputDir].
     *
     * @param inputPath  Absolute path to the source audio file (MP3, AAC, WAV, etc.)
     * @param outputDir  Directory where the reversed WAV file will be saved
     * @return           Absolute path to the reversed WAV file
     * @throws Exception if decoding or writing fails
     *
     * This is a suspend function that runs entirely on Dispatchers.IO,
     * so it never blocks the UI thread.
     */
    suspend fun reverse(inputPath: String, outputDir: File): String =
        withContext(Dispatchers.IO) {
            // 1. Decode audio to raw PCM bytes
            val decodeResult = decodeToRawPcm(inputPath)

            // 2. Reverse the PCM samples
            val reversedPcm = reversePcmSamples(
                decodeResult.pcmData,
                decodeResult.bytesPerSample * decodeResult.channelCount
            )

            // 3. Write reversed PCM as WAV
            val outputFile = File(
                outputDir,
                "reversed_${System.currentTimeMillis()}.wav"
            )
            writeWavFile(
                outputFile,
                reversedPcm,
                decodeResult.sampleRate,
                decodeResult.channelCount,
                decodeResult.bytesPerSample * 8 // bits per sample
            )

            outputFile.absolutePath
        }

    // --- Internal data class for decode results ---
    private data class DecodeResult(
        val pcmData: ByteArray,
        val sampleRate: Int,
        val channelCount: Int,
        val bytesPerSample: Int // typically 2 for 16-bit audio
    )

    /**
     * Decodes any supported audio file to raw PCM byte array using
     * Android's MediaExtractor + MediaCodec.
     */
    private fun decodeToRawPcm(inputPath: String): DecodeResult {
        val extractor = MediaExtractor()
        extractor.setDataSource(inputPath)

        // Find the first audio track
        var audioTrackIndex = -1
        var inputFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                inputFormat = format
                break
            }
        }

        if (audioTrackIndex == -1 || inputFormat == null) {
            extractor.release()
            throw IllegalArgumentException("No audio track found in file: $inputPath")
        }

        extractor.selectTrack(audioTrackIndex)

        val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        // Create decoder
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inputFormat, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val pcmChunks = mutableListOf<ByteArray>()
        var totalPcmBytes = 0
        var isEos = false

        while (!isEos) {
            // Feed input buffers
            val inputBufferIndex = codec.dequeueInputBuffer(10_000)
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                if (sampleSize < 0) {
                    // End of stream
                    codec.queueInputBuffer(
                        inputBufferIndex, 0, 0, 0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                } else {
                    val presentationTimeUs = extractor.sampleTime
                    codec.queueInputBuffer(
                        inputBufferIndex, 0, sampleSize,
                        presentationTimeUs, 0
                    )
                    extractor.advance()
                }
            }

            // Drain output buffers
            var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            while (outputBufferIndex >= 0) {
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    isEos = true
                }

                if (bufferInfo.size > 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!
                    val chunk = ByteArray(bufferInfo.size)
                    outputBuffer.get(chunk)
                    pcmChunks.add(chunk)
                    totalPcmBytes += chunk.size
                }

                codec.releaseOutputBuffer(outputBufferIndex, false)
                outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        // Combine all PCM chunks into one byte array
        val pcmData = ByteArray(totalPcmBytes)
        var offset = 0
        for (chunk in pcmChunks) {
            System.arraycopy(chunk, 0, pcmData, offset, chunk.size)
            offset += chunk.size
        }

        return DecodeResult(
            pcmData = pcmData,
            sampleRate = sampleRate,
            channelCount = channelCount,
            bytesPerSample = 2 // 16-bit PCM (standard MediaCodec output)
        )
    }

    /**
     * Reverses PCM samples respecting frame boundaries.
     *
     * A "frame" = bytesPerSample * channelCount bytes.
     * For stereo 16-bit: 1 frame = 4 bytes (2 bytes left + 2 bytes right).
     * For mono 16-bit: 1 frame = 2 bytes.
     *
     * We swap entire frames so channels stay correct.
     */
    private fun reversePcmSamples(pcmData: ByteArray, bytesPerFrame: Int): ByteArray {
        val totalFrames = pcmData.size / bytesPerFrame
        val reversed = ByteArray(pcmData.size)

        for (i in 0 until totalFrames) {
            val srcOffset = i * bytesPerFrame
            val destOffset = (totalFrames - 1 - i) * bytesPerFrame
            System.arraycopy(pcmData, srcOffset, reversed, destOffset, bytesPerFrame)
        }

        return reversed
    }

    /**
     * Writes raw PCM data as a standard WAV file with proper header.
     *
     * WAV format: 44-byte RIFF header + raw PCM data
     */
    private fun writeWavFile(
        outputFile: File,
        pcmData: ByteArray,
        sampleRate: Int,
        channelCount: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channelCount * bitsPerSample / 8
        val blockAlign = channelCount * bitsPerSample / 8
        val dataSize = pcmData.size
        val totalSize = 36 + dataSize // file size minus 8 bytes for RIFF header

        FileOutputStream(outputFile).use { fos ->
            val buffer = ByteBuffer.allocate(44)
            buffer.order(ByteOrder.LITTLE_ENDIAN)

            // RIFF header
            buffer.put('R'.code.toByte())
            buffer.put('I'.code.toByte())
            buffer.put('F'.code.toByte())
            buffer.put('F'.code.toByte())
            buffer.putInt(totalSize)            // ChunkSize
            buffer.put('W'.code.toByte())
            buffer.put('A'.code.toByte())
            buffer.put('V'.code.toByte())
            buffer.put('E'.code.toByte())

            // fmt sub-chunk
            buffer.put('f'.code.toByte())
            buffer.put('m'.code.toByte())
            buffer.put('t'.code.toByte())
            buffer.put(' '.code.toByte())
            buffer.putInt(16)                   // Subchunk1Size (16 for PCM)
            buffer.putShort(1)                  // AudioFormat (1 = PCM)
            buffer.putShort(channelCount.toShort())
            buffer.putInt(sampleRate)
            buffer.putInt(byteRate)
            buffer.putShort(blockAlign.toShort())
            buffer.putShort(bitsPerSample.toShort())

            // data sub-chunk
            buffer.put('d'.code.toByte())
            buffer.put('a'.code.toByte())
            buffer.put('t'.code.toByte())
            buffer.put('a'.code.toByte())
            buffer.putInt(dataSize)

            fos.write(buffer.array())
            fos.write(pcmData)
        }
    }
}
