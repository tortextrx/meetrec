package com.meetrec.app.data.remote

import android.content.Context
import android.util.Log
import com.meetrec.app.data.model.MeetingSession
import com.meetrec.app.data.model.MeetingTranscript
import com.meetrec.app.data.model.TranscriptSegment
import com.meetrec.app.data.repository.MeetingRepository
import com.meetrec.app.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class TranscriptionProgress {
    object Idle : TranscriptionProgress()
    data class Processing(val currentSegment: Int, val totalSegments: Int, val status: String) : TranscriptionProgress()
    data class Success(val transcript: MeetingTranscript) : TranscriptionProgress()
    data class Error(val message: String) : TranscriptionProgress()
}

class TranscriptionService private constructor(private val context: Context) {

    private val settingsRepository = SettingsRepository.getInstance(context)
    private val meetingRepository = MeetingRepository.getInstance(context)

    companion object {
        private const val TAG = "TranscriptionService"
        private const val API_URL = "https://openrouter.ai/api/v1/audio/transcriptions"
        private const val MODEL_NAME = "openai/whisper-large-v3"

        @Volatile
        private var instance: TranscriptionService? = null

        fun getInstance(context: Context): TranscriptionService {
            return instance ?: synchronized(this) {
                instance ?: TranscriptionService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _progress = MutableStateFlow<TranscriptionProgress>(TranscriptionProgress.Idle)
    val progress: StateFlow<TranscriptionProgress> = _progress.asStateFlow()

    suspend fun transcribeMeeting(session: MeetingSession): Result<MeetingTranscript> = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.getApiKey()
        if (apiKey.isBlank()) {
            val err = "No has configurado tu clave API de OpenRouter. Ve a Ajustes e introdúcela."
            _progress.value = TranscriptionProgress.Error(err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        // Si ya hay transcripción guardada
        val existing = meetingRepository.getTranscript(session.id)
        if (existing != null && existing.fullText.isNotBlank()) {
            _progress.value = TranscriptionProgress.Success(existing)
            return@withContext Result.success(existing)
        }

        val segments = session.audioSegments.map { File(it) }.filter { it.exists() && it.length() > 44 }
        if (segments.isEmpty()) {
            val err = "No se encontraron archivos de audio válidos para transcribir."
            _progress.value = TranscriptionProgress.Error(err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        val fullTextBuilder = StringBuilder()
        val allSegments = mutableListOf<TranscriptSegment>()
        var accumulatedTimeSec = 0f

        val total = segments.size
        for (i in segments.indices) {
            val file = segments[i]
            val index = i + 1
            _progress.value = TranscriptionProgress.Processing(index, total, "Transcribiendo bloque $index de $total con Whisper...")

            try {
                val text = transcribeFile(file, apiKey)
                if (text.isNotBlank()) {
                    if (fullTextBuilder.isNotEmpty()) fullTextBuilder.append("\n\n")
                    fullTextBuilder.append(text)

                    // Estimar duración por tamaño (16kHz 16-bit mono = 32000 bytes/seg)
                    val duration = (file.length() - 44) / 32000f
                    allSegments.add(
                        TranscriptSegment(
                            startSec = accumulatedTimeSec,
                            endSec = accumulatedTimeSec + duration,
                            text = text
                        )
                    )
                    accumulatedTimeSec += duration
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en bloque $index: ${e.message}")
                val err = "Error al transcribir bloque $index: ${e.message}"
                _progress.value = TranscriptionProgress.Error(err)
                return@withContext Result.failure(e)
            }
        }

        val resultTranscript = MeetingTranscript(
            sessionId = session.id,
            fullText = fullTextBuilder.toString().trim(),
            segments = allSegments
        )

        meetingRepository.saveTranscript(resultTranscript)
        _progress.value = TranscriptionProgress.Success(resultTranscript)
        Result.success(resultTranscript)
    }

    private fun transcribeFile(file: File, apiKey: String): String {
        val mediaType = "audio/wav".toMediaType()
        val fileBody = file.asRequestBody(mediaType)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", MODEL_NAME)
            .addFormDataPart("file", file.name, fileBody)
            .addFormDataPart("language", "es")
            .addFormDataPart("response_format", "json")
            .build()

        val request = Request.Builder()
            .url(API_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://github.com/tortextrx/meetrec")
            .header("X-Title", "MeetRec App")
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IOException("Error OpenRouter Whisper (${response.code}): $body")
            }
            val json = JSONObject(body)
            return json.optString("text", "").trim()
        }
    }
}

