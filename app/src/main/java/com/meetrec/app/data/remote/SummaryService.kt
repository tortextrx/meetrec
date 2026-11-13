package com.meetrec.app.data.remote

import android.content.Context
import android.util.Log
import com.meetrec.app.data.model.ActionItem
import com.meetrec.app.data.model.MeetingSession
import com.meetrec.app.data.model.MeetingSummary
import com.meetrec.app.data.model.MeetingTranscript
import com.meetrec.app.data.model.TopicItem
import com.meetrec.app.data.repository.MeetingRepository
import com.meetrec.app.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class SummaryProgress {
    object Idle : SummaryProgress()
    data class Processing(val message: String) : SummaryProgress()
    data class Success(val summary: MeetingSummary) : SummaryProgress()
    data class Error(val message: String) : SummaryProgress()
}

class SummaryService private constructor(private val context: Context) {

    private val settingsRepository = SettingsRepository.getInstance(context)
    private val meetingRepository = MeetingRepository.getInstance(context)

    companion object {
        private const val TAG = "SummaryService"
        private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"

        @Volatile
        private var instance: SummaryService? = null

        fun getInstance(context: Context): SummaryService {
            return instance ?: synchronized(this) {
                instance ?: SummaryService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val _progress = MutableStateFlow<SummaryProgress>(SummaryProgress.Idle)
    val progress: StateFlow<SummaryProgress> = _progress.asStateFlow()

    suspend fun generateSummary(
        session: MeetingSession,
        transcript: MeetingTranscript,
        forceRegenerate: Boolean = false
    ): Result<MeetingSummary> = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.getApiKey()
        if (apiKey.isBlank()) {
            val err = "No has configurado tu clave API de OpenRouter. Ve a Ajustes e introdúcela."
            _progress.value = SummaryProgress.Error(err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        if (!forceRegenerate) {
            val existing = meetingRepository.getSummary(session.id)
            if (existing != null) {
                _progress.value = SummaryProgress.Success(existing)
                return@withContext Result.success(existing)
            }
        }

        _progress.value = SummaryProgress.Processing("Sintetizando reunión y detectando acuerdos con IA...")

        val model = settingsRepository.getSelectedModel()
        val systemPrompt = """
            Eres un asistente de dirección y secretaría ejecutiva de alto nivel para reuniones de trabajo y empresas.
            Tu labor es transformar una transcripción de reunión (que puede incluir charlas informales, vacilaciones o repeticiones) en un resumen ejecutivo nítido, estructurado y orientado a la acción.
            
            Debes extraer con total exactitud:
            1. "objective": El propósito u objetivo central de la reunión (1-2 frases claras).
            2. "topicsDiscussed": Lista de los temas principales tratados. Para cada tema indica:
               - "title": Nombre del tema.
               - "summary": Resumen de lo discutido y conclusiones sobre ese tema.
            3. "keyAgreements": Lista de acuerdos clave consensuados.
            4. "actionItems": Tareas y compromisos adquiridos (To-Do List). Para cada tarea indica:
               - "task": Qué debe hacerse.
               - "assignee": Quién es el responsable (si no se menciona explícitamente, pon "Equipo").
               - "deadline": Plazo o fecha acordada (si no se especifica, pon "Próxima reunión" o "Pendiente").
               
            IMPORTANTE:
            - Devuelve ÚNICAMENTE un objeto JSON válido sin texto adicional antes o después.
            - Utiliza un tono profesional, claro y directo en español.
            - No inventes tareas o personas que no se mencionen en el audio.
        """.trimIndent()

        val userPrompt = """
            DATOS DE LA REUNIÓN:
            - Título: ${session.title}
            - Participantes: ${if (session.participants.isNotBlank()) session.participants else "No especificados"}
            - Notas iniciales: ${if (session.notes.isNotBlank()) session.notes else "Ninguna"}
            - Duración aproximada: ${session.formattedDuration}
            
            TRANSCRIPCIÓN COMPLETA:
            \"\"\"
            ${transcript.fullText}
            \"\"\"
        """.trimIndent()

        val messagesArr = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            })
        }

        val requestPayload = JSONObject().apply {
            put("model", model)
            put("messages", messagesArr)
            put("temperature", 0.2)
            put("response_format", JSONObject().put("type", "json_object"))
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestPayload.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(API_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://github.com/tortextrx/meetrec")
            .header("X-Title", "MeetRec App")
            .post(requestBody)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    throw IOException("Error OpenRouter (${response.code}): $body")
                }

                val jsonResp = JSONObject(body)
                val choices = jsonResp.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    throw IOException("Respuesta de OpenRouter sin contenido")
                }

                val messageObj = choices.getJSONObject(0).getJSONObject("message")
                val contentStr = messageObj.getString("content").trim()

                // Parsear el JSON devuelto
                val parsed = JSONObject(contentStr)
                val summary = MeetingSummary.fromJson(parsed.apply {
                    put("sessionId", session.id)
                })

                meetingRepository.saveSummary(summary)
                _progress.value = SummaryProgress.Success(summary)
                Result.success(summary)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generando resumen: ${e.message}")
            val err = "Error al generar resumen ejecutivo: ${e.message}"
            _progress.value = SummaryProgress.Error(err)
            Result.failure(e)
        }
    }
}

