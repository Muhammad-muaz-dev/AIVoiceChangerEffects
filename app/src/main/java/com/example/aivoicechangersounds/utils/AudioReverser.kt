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

            val bytesPerSample = 2
            val frameSize = bytesPerSample * decodeResult.channelCount

            val reversedPcm = reversePcmFrames(decodeResult.pcmData, frameSize)

            val outputFile = File(outputDir, "reversed_${System.currentTimeMillis()}.wav")

            writeWavFile(
                outputFile,
                reversedPcm,
                decodeResult.sampleRate,
                decodeResult.channelCount,
                bytesPerSample * 8
            )

            outputFile.absolutePath
        }

    private data class DecodeResult(
        val pcmData: ByteArray,
        val sampleRate: Int,
        val channelCount: Int
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

        while (!outputEOS) {

            if (!inputEOS) {
                val inputIndex = codec.dequeueInputBuffer(10_000)

                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)!!

                    val size = extractor.readSampleData(inputBuffer, 0)

                    if (size < 0) {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputEOS = true
                    } else {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            size,
                            extractor.sampleTime,
                            0
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

        return DecodeResult(
            outputStream.toByteArray(),
            sampleRate,
            channels
        )
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
        val totalSize = 36 + dataSize

        FileOutputStream(file).use { fos ->

            val header = ByteBuffer.allocate(44)
                .order(ByteOrder.LITTLE_ENDIAN)

            header.put("RIFF".toByteArray())
            header.putInt(totalSize)
            header.put("WAVE".toByteArray())

            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1)
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