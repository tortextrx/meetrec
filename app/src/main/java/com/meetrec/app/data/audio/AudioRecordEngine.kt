package com.meetrec.app.data.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

class AudioRecordEngine(
    private val context: Context,
    private val sessionDir: File,
    private val listener: AudioCaptureListener,
    val segmentDurationSeconds: Int = 300 // 5 minutos por bloque
) {
    interface AudioCaptureListener {
        fun onAudioLevel(rmsDb: Float, normalizedLevel: Float, isClipping: Boolean)
        fun onSegmentCompleted(segmentFile: File, durationSec: Long, index: Int)
        fun onError(message: String, isFatal: Boolean)
    }

    private var audioRecord: AudioRecord? = null
    private var activeWriter: WavAudioWriter? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)

    var actualSampleRate: Int = 16000
        private set

    private var currentSegmentIndex = 1
    private var samplesInCurrentSegment: Long = 0L

    companion object {
        private const val TAG = "AudioRecordEngine"
        private const val MIN_USABLE_STORAGE_BYTES = 100L * 1024L * 1024L // 100 MB
        private val CANDIDATE_SAMPLE_RATES = intArrayOf(16000, 44100, 48000)
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording.get()) return

        val usableSpace = context.filesDir.usableSpace
        if (usableSpace < MIN_USABLE_STORAGE_BYTES) {
            listener.onError("Espacio insuficiente en disco para grabar.", isFatal = true)
            return
        }

        val (record, selectedRate, bufferSize) = initializeAudioRecord()
        if (record == null) {
            listener.onError("No se pudo iniciar AudioRecord (recurso ocupado o no compatible).", isFatal = true)
            return
        }

        this.audioRecord = record
        this.actualSampleRate = selectedRate
        this.currentSegmentIndex = 1
        this.samplesInCurrentSegment = 0L

        sessionDir.mkdirs()
        val firstFile = getSegmentFile(currentSegmentIndex)
        try {
            activeWriter = WavAudioWriter(firstFile, sampleRate = selectedRate)
        } catch (e: Exception) {
            listener.onError("Error al crear archivo de audio: ${e.message}", isFatal = true)
            record.release()
            return
        }

        isRecording.set(true)
        try {
            record.startRecording()
        } catch (e: Exception) {
            listener.onError("Fallo al iniciar captura de audio: ${e.message}", isFatal = true)
            isRecording.set(false)
            activeWriter?.close()
            record.release()
            return
        }

        startCaptureLoop(bufferSize)
    }

    private fun startCaptureLoop(bufferSize: Int) {
        val maxSamplesPerSegment = actualSampleRate.toLong() * segmentDurationSeconds.toLong()

        recordingThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val buffer = ShortArray(bufferSize / 2)

            while (isRecording.get()) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    processAudioMetrics(buffer, read)

                    // Rotación transparente de segmento si supera duración
                    if (samplesInCurrentSegment + read > maxSamplesPerSegment) {
                        val samplesForCurrent = (maxSamplesPerSegment - samplesInCurrentSegment).toInt()
                        if (samplesForCurrent > 0) {
                            activeWriter?.writeSamples(buffer, 0, samplesForCurrent)
                        }

                        val completedWriter = activeWriter
                        val completedIndex = currentSegmentIndex
                        completedWriter?.close()
                        val segDuration = samplesInCurrentSegment / actualSampleRate
                        listener.onSegmentCompleted(getSegmentFile(completedIndex), segDuration, completedIndex)

                        currentSegmentIndex++
                        samplesInCurrentSegment = 0L

                        val nextFile = getSegmentFile(currentSegmentIndex)
                        activeWriter = WavAudioWriter(nextFile, sampleRate = actualSampleRate)

                        val remaining = read - samplesForCurrent
                        if (remaining > 0) {
                            activeWriter?.writeSamples(buffer, samplesForCurrent, remaining)
                            samplesInCurrentSegment += remaining
                        }
                    } else {
                        activeWriter?.writeSamples(buffer, 0, read)
                        samplesInCurrentSegment += read
                    }
                } else if (read < 0) {
                    listener.onError("Error de lectura AudioRecord (código $read)", isFatal = false)
                }
            }

            // Cerrar último segmento
            val lastWriter = activeWriter
            lastWriter?.close()
            if (samplesInCurrentSegment > 0) {
                val segDuration = samplesInCurrentSegment / actualSampleRate
                listener.onSegmentCompleted(getSegmentFile(currentSegmentIndex), segDuration, currentSegmentIndex)
            }
        }, "MeetRecAudioLoop").apply { start() }
    }

    private fun processAudioMetrics(buffer: ShortArray, readCount: Int) {
        var sumSquares = 0.0
        var isClipping = false

        for (i in 0 until readCount) {
            val sample = buffer[i]
            if (sample >= 32760 || sample <= -32760) isClipping = true
            sumSquares += (sample.toDouble() * sample.toDouble())
        }

        val meanSquare = sumSquares / readCount
        val rms = sqrt(meanSquare)
        val rmsDb = if (rms > 0) (20 * log10(rms / 32768.0)).toFloat().coerceIn(-96f, 0f) else -96f
        val normalized = ((rmsDb + 60f) / 60f).coerceIn(0f, 1f)

        listener.onAudioLevel(rmsDb, normalized, isClipping)
    }

    fun stop() {
        if (!isRecording.compareAndSet(true, false)) return

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}

        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            recordingThread?.join(1000)
        } catch (_: Exception) {}
        recordingThread = null
    }

    private fun getSegmentFile(index: Int): File {
        return File(sessionDir, "segment_${index}.wav")
    }

    @SuppressLint("MissingPermission")
    private fun initializeAudioRecord(): Triple<AudioRecord?, Int, Int> {
        for (rate in CANDIDATE_SAMPLE_RATES) {
            val bufferSize = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (bufferSize > 0) {
                val optimalBufferSize = (bufferSize * 2).coerceAtLeast(4096)
                try {
                    val record = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        rate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        optimalBufferSize
                    )
                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        return Triple(record, rate, optimalBufferSize)
                    }
                    record.release()
                } catch (_: Exception) {}
            }
        }
        return Triple(null, 0, 0)
    }
}

