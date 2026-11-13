package com.meetrec.app.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MeetingSession(
    val id: String,
    val title: String,
    val participants: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val durationSec: Long = 0,
    val audioSegments: List<String> = emptyList(),
    val hasTranscript: Boolean = false,
    val hasSummary: Boolean = false,
    val hasPdf: Boolean = false
) {
    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(createdAt))
        }

    val formattedDuration: String
        get() {
            val m = durationSec / 60
            val s = durationSec % 60
            return String.format(Locale.getDefault(), "%02d:%02d", m, s)
        }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("participants", participants)
            put("notes", notes)
            put("createdAt", createdAt)
            put("durationSec", durationSec)
            put("audioSegments", JSONArray(audioSegments))
            put("hasTranscript", hasTranscript)
            put("hasSummary", hasSummary)
            put("hasPdf", hasPdf)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): MeetingSession {
            val segments = mutableListOf<String>()
            val arr = json.optJSONArray("audioSegments")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    segments.add(arr.getString(i))
                }
            }
            return MeetingSession(
                id = json.getString("id"),
                title = json.optString("title", "Reunión de trabajo"),
                participants = json.optString("participants", ""),
                notes = json.optString("notes", ""),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                durationSec = json.optLong("durationSec", 0),
                audioSegments = segments,
                hasTranscript = json.optBoolean("hasTranscript", false),
                hasSummary = json.optBoolean("hasSummary", false),
                hasPdf = json.optBoolean("hasPdf", false)
            )
        }
    }
}

