package com.meetrec.app.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class TranscriptSegment(
    val startSec: Float,
    val endSec: Float,
    val text: String
) {
    val formattedTimestamp: String
        get() {
            val totalSeconds = startSec.toInt()
            val m = totalSeconds / 60
            val s = totalSeconds % 60
            return String.format(Locale.getDefault(), "[%02d:%02d]", m, s)
        }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("startSec", startSec.toDouble())
            put("endSec", endSec.toDouble())
            put("text", text)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): TranscriptSegment {
            return TranscriptSegment(
                startSec = json.optDouble("startSec", 0.0).toFloat(),
                endSec = json.optDouble("endSec", 0.0).toFloat(),
                text = json.optString("text", "")
            )
        }
    }
}

data class MeetingTranscript(
    val sessionId: String,
    val fullText: String,
    val segments: List<TranscriptSegment> = emptyList(),
    val transcribedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        val arr = JSONArray()
        segments.forEach { arr.put(it.toJson()) }
        return JSONObject().apply {
            put("sessionId", sessionId)
            put("fullText", fullText)
            put("transcribedAt", transcribedAt)
            put("segments", arr)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): MeetingTranscript {
            val segList = mutableListOf<TranscriptSegment>()
            val arr = json.optJSONArray("segments")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    segList.add(TranscriptSegment.fromJson(arr.getJSONObject(i)))
                }
            }
            return MeetingTranscript(
                sessionId = json.getString("sessionId"),
                fullText = json.optString("fullText", ""),
                segments = segList,
                transcribedAt = json.optLong("transcribedAt", System.currentTimeMillis())
            )
        }
    }
}

