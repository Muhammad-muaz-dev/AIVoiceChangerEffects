package com.example.aivoicechangersounds.utils

import android.media.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioReverser @Inject constructor() {

    suspend fun reverse(inputPath: String, outputDir: File): String =
        withContext(Dispatchers.IO) {
            val decodeResult = decodeToRawPcm(inputPath)

            // FIX 1: Use the actual bytes-per-sample from the decoded format
            val frameSize = decodeResult.bytesPerSample * decodeResult.channelCount
            val reversedPcm = reversePcmFrames(decodeResult.pcmData, frameSize)

            val outputFile = File(outputDir, "reversed_${System.currentTimeMillis()}.wav")
            writeWavFile(
                outputFile,
                reversedPcm,
                decodeResult.sampleRate,
                decodeResult.channelCount,
                decodeResult.bytesPerSample * 8
            )
            outputFile.absolutePath
        }

    private data class DecodeResult(
        val pcmData: ByteArray,
        val sampleRate: Int,
        val channelCount: Int,
        val bytesPerSample: Int  // FIX 2: carry actual encoding out of decode
    )

    private fun decodeToRawPcm(inputPath: String): DecodeResult {
        val extractor = MediaExtractor()
        extractor.setDataSource(inputPath)

        var trackIndex = -1
        var format: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                trackIndex = i
                format = f
                break
            }
        }

        require(trackIndex != -1 && format != null) { "No valid audio track found" }
        extractor.selectTrack(trackIndex)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val outputStream = ByteArrayOutputStream()

        var inputEOS = false
        var outputEOS = false

        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        // FIX 3: Default to 16-bit but update from actual output format
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        while (!outputEOS) {
            if (!inputEOS) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)!!
                    val size = extractor.readSampleData(inputBuffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(
                            inputIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputEOS = true
                    } else {
                        codec.queueInputBuffer(
                            inputIndex, 0, size,
                            extractor.sampleTime, 0
                        )
                        extractor.advance()
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            when (outputIndex) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = codec.outputFormat
                    sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    // FIX 4: Read actual PCM encoding from output format
                    pcmEncoding = if (newFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        newFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else {
                        AudioFormat.ENCODING_PCM_16BIT
                    }
                }
                else -> if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                    if (bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.get(chunk)
                        outputStream.write(chunk)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputEOS = true
                    }
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        // FIX 5: Convert float PCM to 16-bit PCM for WAV compatibility
        val (finalPcm, finalEncoding) = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
            Pair(convertFloatTo16Bit(outputStream.toByteArray()), AudioFormat.ENCODING_PCM_16BIT)
        } else {
            Pair(outputStream.toByteArray(), pcmEncoding)
        }

        val bytesPerSample = when (finalEncoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_8BIT  -> 1
            else -> 2
        }

        return DecodeResult(finalPcm, sampleRate, channels, bytesPerSample)
    }

    // FIX 6: Convert 32-bit float samples to 16-bit signed integer samples
    private fun convertFloatTo16Bit(floatPcm: ByteArray): ByteArray {
        val floatBuffer = ByteBuffer.wrap(floatPcm).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val numSamples = floatPcm.size / 4
        val output = ByteArray(numSamples * 2)
        val shortBuffer = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in 0 until numSamples) {
            val sample = floatBuffer.get(i).coerceIn(-1f, 1f)
            shortBuffer.put(i, (sample * 32767f).toInt().toShort())
        }
        return output
    }

    private fun reversePcmFrames(pcm: ByteArray, frameSize: Int): ByteArray {
        val totalFrames = pcm.size / frameSize
        val reversed = ByteArray(pcm.size)
        for (i in 0 until totalFrames) {
            val src = i * frameSize
            val dst = (totalFrames - 1 - i) * frameSize
            System.arraycopy(pcm, src, reversed, dst, frameSize)
        }
        return reversed
    }

    private fun writeWavFile(
        file: File,
        pcm: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcm.size

        FileOutputStream(file).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(dataSize + 36)   // FIX 7: Correct RIFF chunk size
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1)             // PCM format
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray())
            header.putInt(dataSize)
            fos.write(header.array())
            fos.write(pcm)
        }
    }
}