package com.mnivesh.callyn.managers

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException

object QuickReplyManager {
    private const val PREF_NAME = "quick_replies_prefs"
    private const val KEY_QUICK_REPLIES = "quick_replies_list"

    val DEFAULT_QUICK_REPLIES = listOf(
        "Can't talk right now.",
        "I'll call you back later.",
        "I'm in a meeting.",
        "Please text me.",
        "I'm on leave, will call you back later"
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getQuickReplies(context: Context): List<String> {
        val prefs = getPrefs(context)
        val jsonString = prefs.getString(KEY_QUICK_REPLIES, null) ?: return DEFAULT_QUICK_REPLIES
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optString(i)
                if (!item.isNullOrBlank()) {
                    list.add(item)
                }
            }
            if (list.isEmpty()) DEFAULT_QUICK_REPLIES else list
        } catch (e: JSONException) {
            DEFAULT_QUICK_REPLIES
        }
    }

    fun saveQuickReplies(context: Context, replies: List<String>) {
        val jsonArray = JSONArray()
        replies.forEach { jsonArray.put(it) }
        getPrefs(context).edit().putString(KEY_QUICK_REPLIES, jsonArray.toString()).apply()
    }

    fun addQuickReply(context: Context, reply: String): List<String> {
        val current = getQuickReplies(context).toMutableList()
        val trimmed = reply.trim()
        if (trimmed.isNotEmpty() && !current.contains(trimmed)) {
            current.add(trimmed)
            saveQuickReplies(context, current)
        }
        return current
    }

    fun removeQuickReply(context: Context, index: Int): List<String> {
        val current = getQuickReplies(context).toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            saveQuickReplies(context, current)
        }
        return current
    }
}
