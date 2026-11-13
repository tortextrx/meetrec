package com.meetrec.app.data.audio

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Escritor de segmentos de audio WAV (PCM 16-bit) en tiempo real con
 * recuento exacto de muestras y finalización segura de cabeceras RIFF.
 */
class WavAudioWriter(
    val outputFile: File,
    val sampleRate: Int = 16000,
    val channels: Short = 1,
    val bitsPerSample: Short = 16
) {
    private var fos: FileOutputStream? = null
    private val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
    private var isClosed = false

    var totalSamplesWritten: Long = 0L
        private set

    val totalBytesWritten: Long
        get() = totalSamplesWritten * 2

    init {
        outputFile.parentFile?.mkdirs()
        fos = FileOutputStream(outputFile)
        writeInitialHeader()
    }

    private fun writeInitialHeader() {
        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF descriptor
        buffer.put("RIFF".toByteArray())
        buffer.putInt(0) // ChunkSize provisional
        buffer.put("WAVE".toByteArray())

        // "fmt " sub-chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1.toShort()) // PCM
        buffer.putShort(channels)
        buffer.putInt(sampleRate)

        val byteRate = sampleRate * channels * bitsPerSample / 8
        buffer.putInt(byteRate)

        val blockAlign = (channels * bitsPerSample / 8).toShort()
        buffer.putShort(blockAlign)
        buffer.putShort(bitsPerSample)

        // "data" sub-chunk
        buffer.put("data".toByteArray())
        buffer.putInt(0) // Subchunk2Size provisional

        fos?.write(header)
        fos?.flush()
    }

    fun writeSamples(samples: ShortArray, offset: Int, count: Int) {
        if (isClosed || count <= 0) return

        val byteBuffer = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in offset until (offset + count)) {
            byteBuffer.putShort(samples[i])
        }

        val bytes = byteBuffer.array()
        fos?.write(bytes)
        digest.update(bytes)
        totalSamplesWritten += count
    }

    fun writeSamples(samples: ShortArray, count: Int) {
        writeSamples(samples, 0, count)
    }

    fun close(): Long {
        if (isClosed) return totalSamplesWritten
        isClosed = true

        try {
            fos?.flush()
            fos?.close()
        } catch (_: Exception) {}

        // Corregir tamaño en cabecera
        try {
            RandomAccessFile(outputFile, "rw").use { raf ->
                val dataSize = (totalSamplesWritten * 2).toInt()
                val chunkSize = dataSize + 36

                raf.seek(4)
                val chunkBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkSize)
                raf.write(chunkBuffer.array())

                raf.seek(40)
                val dataBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataSize)
                raf.write(dataBuffer.array())
            }
        } catch (_: Exception) {}

        return totalSamplesWritten
    }
}

