package com.meetrec.app.data.repository

import android.content.Context
import com.meetrec.app.data.model.MeetingSession
import com.meetrec.app.data.model.MeetingSummary
import com.meetrec.app.data.model.MeetingTranscript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MeetingRepository private constructor(private val context: Context) {

    private val indexFile = File(context.filesDir, "meetings_index.json")
    private val _sessionsFlow = MutableStateFlow<List<MeetingSession>>(emptyList())
    val sessionsFlow: StateFlow<List<MeetingSession>> = _sessionsFlow.asStateFlow()

    companion object {
        @Volatile
        private var instance: MeetingRepository? = null

        fun getInstance(context: Context): MeetingRepository {
            return instance ?: synchronized(this) {
                instance ?: MeetingRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        loadSessions()
    }

    fun loadSessions(): List<MeetingSession> {
        val list = mutableListOf<MeetingSession>()
        if (indexFile.exists()) {
            try {
                val content = indexFile.readText()
                val arr = JSONArray(content)
                for (i in 0 until arr.length()) {
                    val s = MeetingSession.fromJson(arr.getJSONObject(i))
                    list.add(s)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val sorted = list.sortedByDescending { it.createdAt }
        _sessionsFlow.value = sorted
        return sorted
    }

    suspend fun saveSession(session: MeetingSession) = withContext(Dispatchers.IO) {
        val current = _sessionsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == session.id }
        if (index >= 0) {
            current[index] = session
        } else {
            current.add(0, session)
        }
        val arr = JSONArray()
        current.forEach { arr.put(it.toJson()) }
        indexFile.writeText(arr.toString(2))
        _sessionsFlow.value = current
    }

    fun getSession(id: String): MeetingSession? {
        return _sessionsFlow.value.find { it.id == id }
    }

    suspend fun saveTranscript(transcript: MeetingTranscript) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "session_${transcript.sessionId}_transcript.json")
        file.writeText(transcript.toJson().toString(2))
        getSession(transcript.sessionId)?.let { s ->
            saveSession(s.copy(hasTranscript = true))
        }
    }

    fun getTranscript(sessionId: String): MeetingTranscript? {
        val file = File(context.filesDir, "session_${sessionId}_transcript.json")
        if (!file.exists()) return null
        return try {
            MeetingTranscript.fromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveSummary(summary: MeetingSummary) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "session_${summary.sessionId}_summary.json")
        file.writeText(summary.toJson().toString(2))
        getSession(summary.sessionId)?.let { s ->
            saveSession(s.copy(hasSummary = true))
        }
    }

    fun getSummary(sessionId: String): MeetingSummary? {
        val file = File(context.filesDir, "session_${sessionId}_summary.json")
        if (!file.exists()) return null
        return try {
            MeetingSummary.fromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            null
        }
    }

    fun getPdfFile(sessionId: String): File {
        return File(context.filesDir, "meeting_${sessionId}_report.pdf")
    }

    suspend fun markPdfGenerated(sessionId: String) = withContext(Dispatchers.IO) {
        getSession(sessionId)?.let { s ->
            saveSession(s.copy(hasPdf = true))
        }
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        val current = _sessionsFlow.value.toMutableList()
        val session = current.find { it.id == sessionId }
        if (session != null) {
            // Eliminar audios
            session.audioSegments.forEach { path ->
                try { File(path).delete() } catch (_: Exception) {}
            }
            // Eliminar json transcripción, json resumen, pdf
            File(context.filesDir, "session_${sessionId}_transcript.json").delete()
            File(context.filesDir, "session_${sessionId}_summary.json").delete()
            File(context.filesDir, "meeting_${sessionId}_report.pdf").delete()

            current.removeAll { it.id == sessionId }
            val arr = JSONArray()
            current.forEach { arr.put(it.toJson()) }
            indexFile.writeText(arr.toString(2))
            _sessionsFlow.value = current
        }
    }
}

