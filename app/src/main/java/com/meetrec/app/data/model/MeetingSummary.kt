package com.meetrec.app.data.model

import org.json.JSONArray
import org.json.JSONObject

data class ActionItem(
    val task: String,
    val assignee: String,
    val deadline: String = "Pendiente"
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("task", task)
            put("assignee", assignee)
            put("deadline", deadline)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ActionItem {
            return ActionItem(
                task = json.optString("task", ""),
                assignee = json.optString("assignee", "No asignado"),
                deadline = json.optString("deadline", "Pendiente")
            )
        }
    }
}

data class TopicItem(
    val title: String,
    val summary: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("title", title)
            put("summary", summary)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): TopicItem {
            return TopicItem(
                title = json.optString("title", ""),
                summary = json.optString("summary", "")
            )
        }
    }
}

data class MeetingSummary(
    val sessionId: String,
    val objective: String,
    val topicsDiscussed: List<TopicItem> = emptyList(),
    val actionItems: List<ActionItem> = emptyList(),
    val keyAgreements: List<String> = emptyList(),
    val generatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        val topicsArr = JSONArray()
        topicsDiscussed.forEach { topicsArr.put(it.toJson()) }

        val actionArr = JSONArray()
        actionItems.forEach { actionArr.put(it.toJson()) }

        val agrArr = JSONArray()
        keyAgreements.forEach { agrArr.put(it) }

        return JSONObject().apply {
            put("sessionId", sessionId)
            put("objective", objective)
            put("topicsDiscussed", topicsArr)
            put("actionItems", actionArr)
            put("keyAgreements", agrArr)
            put("generatedAt", generatedAt)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): MeetingSummary {
            val topics = mutableListOf<TopicItem>()
            val tArr = json.optJSONArray("topicsDiscussed")
            if (tArr != null) {
                for (i in 0 until tArr.length()) {
                    topics.add(TopicItem.fromJson(tArr.getJSONObject(i)))
                }
            }

            val actions = mutableListOf<ActionItem>()
            val aArr = json.optJSONArray("actionItems")
            if (aArr != null) {
                for (i in 0 until aArr.length()) {
                    actions.add(ActionItem.fromJson(aArr.getJSONObject(i)))
                }
            }

            val agreements = mutableListOf<String>()
            val gArr = json.optJSONArray("keyAgreements")
            if (gArr != null) {
                for (i in 0 until gArr.length()) {
                    agreements.add(gArr.getString(i))
                }
            }

            return MeetingSummary(
                sessionId = json.getString("sessionId"),
                objective = json.optString("objective", "Reunión de trabajo"),
                topicsDiscussed = topics,
                actionItems = actions,
                keyAgreements = agreements,
                generatedAt = json.optLong("generatedAt", System.currentTimeMillis())
            )
        }
    }
}

