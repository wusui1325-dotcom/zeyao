package com.wusper.zeyao.ai

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Send a screenshot to the cheapest available model and ask only for a news title or link.
 * Prefers title. Used by the experimental image-recognition path.
 */
object ImageTitleExtractor {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun extract(apiKey: String, providerId: String, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        val b64 = encode(bitmap) ?: return@withContext null
        val prompt = "只從這張截圖找出新聞標題或網址。優先返回標題。只輸出一行，不要解釋。如果沒有就輸出空。"
        when (providerId) {
            "gemini" -> callGemini(apiKey, b64, prompt)
            else -> callOpenAiStyle(apiKey, providerId, b64, prompt)
        }
    }

    private fun encode(bitmap: Bitmap): String? = runCatching {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    private fun callOpenAiStyle(apiKey: String, providerId: String, b64: String, prompt: String): String? {
        val base = when (providerId) {
            "deepseek" -> "https://api.deepseek.com"
            "grok" -> "https://api.x.ai/v1"
            "openai" -> "https://api.openai.com/v1"
            else -> "https://api.deepseek.com"
        }
        val model = when (providerId) {
            "deepseek" -> "deepseek-flash"
            "grok" -> "grok-2-vision-1212"
            "openai" -> "gpt-4o-mini"
            else -> "deepseek-flash"
        }
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 80)
            .put("temperature", 0.2)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "content",
                            JSONArray()
                                .put(JSONObject().put("type", "text").put("text", prompt))
                                .put(
                                    JSONObject()
                                        .put("type", "image_url")
                                        .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64")),
                                ),
                        ),
                ),
            )
            .toString()
        val request = Request.Builder()
            .url("$base/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        return runCatching {
            HttpClients.init.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val text = response.body?.string().orEmpty()
                val obj = JSONObject(text)
                val choices = obj.optJSONArray("choices") ?: return@use null
                if (choices.length() == 0) return@use null
                choices.getJSONObject(0).optJSONObject("message")?.optString("content")?.trim()
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun callGemini(apiKey: String, b64: String, prompt: String): String? {
        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("text", prompt))
                            .put(
                                JSONObject().put(
                                    "inline_data",
                                    JSONObject()
                                        .put("mime_type", "image/jpeg")
                                        .put("data", b64),
                                ),
                            ),
                    ),
                ),
            )
            .toString()
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        return runCatching {
            HttpClients.init.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val text = response.body?.string().orEmpty()
                val obj = JSONObject(text)
                val candidates = obj.optJSONArray("candidates") ?: return@use null
                if (candidates.length() == 0) return@use null
                val parts = candidates.getJSONObject(0)
                    .optJSONObject("content")
                    ?.optJSONArray("parts") ?: return@use null
                if (parts.length() == 0) return@use null
                parts.getJSONObject(0).optString("text").trim()
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
