package com.jarvis.agent

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object AICommandInterpreter {

    private const val AI_API_URL = "https://api.openai.com/v1/chat/completions"
    private const val AI_API_KEY = "YOUR_API_KEY_HERE"
    private const val AI_MODEL = "gpt-4o-mini"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    suspend fun interpret(command: String): AIResult {
        if (AI_API_KEY == "YOUR_API_KEY_HERE") {
            return AIResult.LocalOnly("AI backend not configured. Using local command processing.")
        }
        return try {
            val steps = callAI(command)
            AIResult.Remote(steps)
        } catch (e: Exception) {
            AIResult.LocalOnly("AI unavailable (${e.message}). Using local processing.")
        }
    }

    private suspend fun callAI(command: String): List<String> = withContext(Dispatchers.IO) {
        val systemPrompt = """You are Jarvis, an AI agent that controls an Android phone via accessibility service. You can: open apps, tap buttons by text, type text, swipe, scroll, press back/home/recents, and read the screen. Given a user's voice command, break it into a sequence of simple, executable steps. Supported step formats: "open [app name]", "tap [button text]", "type [text]", "swipe up" / "swipe down", "back" / "home" / "recents", "wait [ms]", "search for [query]". Return ONLY a JSON array of step strings. No explanation."""

        val json = JSONObject().apply {
            put("model", AI_MODEL)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                put(JSONObject().apply { put("role", "user"); put("content", command) })
            })
            put("temperature", 0.3)
            put("max_tokens", 300)
        }

        val request = Request.Builder()
            .url(AI_API_URL)
            .addHeader("Authorization", "Bearer $AI_API_KEY")
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(JSON_MEDIA, json.toString()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw IOException("Empty response")
        if (!response.isSuccessful) throw IOException("AI API error ${response.code}: $body")

        val content = JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        val cleaned = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val steps = JSONArray(cleaned)
        (0 until steps.length()).map { steps.getString(it) }
    }
}

sealed class AIResult {
    data class Remote(val steps: List<String>) : AIResult()
    data class LocalOnly(val message: String) : AIResult()
}