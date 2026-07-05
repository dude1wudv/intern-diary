package com.sunmmy.interndiary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin OkHttp wrapper for the intern-diary backend API.
 *
 * All /api requests require `Authorization: Bearer <token>`.
 * /health is public and takes no auth header.
 */
class ApiClient(private val serverUrl: String, private val token: String) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private suspend fun runCatchingRequest(request: Request): Result<Response> {
        return try {
            withContext(Dispatchers.IO) {
                val response = client.newCall(request).execute()
                Result.success(response)
            }
        } catch (e: IOException) {
            Result.failure(ApiException(e.message ?: "网络请求失败"))
        }
    }

    suspend fun checkHealth(): Result<String> {
        val request = Request.Builder()
            .url("$serverUrl/health")
            .get()
            .build()
        return runCatchingRequest(request).mapCatching { response ->
            response.use {
                val bodyStr = it.body?.string() ?: ""
                if (it.code == 200 && bodyStr.contains("\"ok\":true") ||
                    (it.code == 200 && runCatching { JSONObject(bodyStr).optBoolean("ok") }.getOrDefault(false))
                ) {
                    "连接正常"
                } else {
                    throw ApiException("连接失败 (HTTP ${it.code})")
                }
            }
        }
    }

    suspend fun sendText(date: String, content: String, excludeFromDiary: Boolean = false): Result<String> {
        val json = JSONObject().apply {
            put("date", date)
            put("content", content)
            put("exclude_from_diary", excludeFromDiary)
        }
        val body: RequestBody = json.toString().toRequestBody(jsonMediaType)
        val request = authedRequestBuilder("/api/entries/text")
            .post(body)
            .build()
        return runCatchingRequest(request).mapCatching { response ->
            response.use { extractBodyOrThrow(it) }
        }
    }

    suspend fun uploadImage(
        date: String,
        bytes: ByteArray,
        filename: String,
        note: String = "",
        excludeFromDiary: Boolean = false
    ): Result<String> {
        val mediaType = guessImageMediaType(filename)
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("date", date)
            .addFormDataPart("note", note)
            .addFormDataPart("exclude_from_diary", excludeFromDiary.toString())
            .addFormDataPart("image", filename, bytes.toRequestBody(mediaType))
            .build()
        val request = authedRequestBuilder("/api/entries/image")
            .post(multipart)
            .build()
        return runCatchingRequest(request).mapCatching { response ->
            response.use { extractBodyOrThrow(it) }
        }
    }

    suspend fun uploadImage(
        date: String,
        file: File,
        filename: String,
        note: String = "",
        excludeFromDiary: Boolean = false
    ): Result<String> {
        val mediaType = guessImageMediaType(filename)
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("date", date)
            .addFormDataPart("note", note)
            .addFormDataPart("exclude_from_diary", excludeFromDiary.toString())
            .addFormDataPart("image", filename, file.asRequestBody(mediaType))
            .build()
        val request = authedRequestBuilder("/api/entries/image")
            .post(multipart)
            .build()
        return runCatchingRequest(request).mapCatching { response ->
            response.use { extractBodyOrThrow(it) }
        }
    }

    suspend fun getDay(date: String): Result<String> {
        val request = authedRequestBuilder("/api/days/$date")
            .get()
            .build()
        return runCatchingRequest(request).mapCatching { response ->
            response.use { extractBodyOrThrow(it) }
        }
    }

    suspend fun sortDay(date: String, extraInstruction: String = ""): Result<String> {
        val json = JSONObject().apply {
            put("date", date)
            put("extra_instruction", extraInstruction)
        }
        val body: RequestBody = json.toString().toRequestBody(jsonMediaType)
        val request = authedRequestBuilder("/api/actions/sort-day")
            .post(body)
            .build()
        return runCatchingRequest(request).mapCatching { response ->
            response.use { extractBodyOrThrow(it) }
        }
    }

    suspend fun generateDiary(date: String, wordCount: Int = 800, extraInstruction: String = ""): Result<String> {
        val json = JSONObject().apply {
            put("date", date)
            put("word_count", wordCount)
            put("extra_instruction", extraInstruction)
        }
        val body: RequestBody = json.toString().toRequestBody(jsonMediaType)
        val request = authedRequestBuilder("/api/actions/generate-diary")
            .post(body)
            .build()
        return runCatchingRequest(request).mapCatching { response ->
            response.use { extractBodyOrThrow(it) }
        }
    }

    suspend fun downloadDocx(date: String): Result<ByteArray> {
        val request = authedRequestBuilder("/api/days/$date/files/diary_final.docx")
            .get()
            .build()
        return runCatchingRequest(request).mapCatching { response ->
            response.use {
                if (!it.isSuccessful) {
                    throw ApiException("下载失败 (HTTP ${it.code})")
                }
                it.body?.bytes() ?: throw ApiException("下载内容为空")
            }
        }
    }

    suspend fun generateReport(type: String, startDate: String, endDate: String): Result<ReportResult> {
        val json = JSONObject().apply {
            put("type", type)
            put("start_date", startDate)
            put("end_date", endDate)
        }
        val body: RequestBody = json.toString().toRequestBody(jsonMediaType)
        val request = authedRequestBuilder("/api/actions/generate-report")
            .post(body)
            .build()
        return runCatchingRequest(request).mapCatching { response ->
            response.use {
                val bodyStr = extractBodyOrThrow(it)
                val obj = JSONObject(bodyStr)
                val reportId = obj.optString("report_id")
                if (reportId.isBlank()) throw ApiException("报告生成失败：缺少 report_id")
                ReportResult(reportId, obj.optString("markdown"))
            }
        }
    }

    suspend fun downloadReportDocx(reportId: String): Result<ByteArray> {
        val request = authedRequestBuilder("/api/reports/$reportId/files/report.docx")
            .get()
            .build()
        return runCatchingRequest(request).mapCatching { response ->
            response.use {
                if (!it.isSuccessful) {
                    throw ApiException("下载失败 (HTTP ${it.code})")
                }
                it.body?.bytes() ?: throw ApiException("下载内容为空")
            }
        }
    }

    suspend fun getDraft(date: String): Result<String> {
        val request = authedRequestBuilder("/api/days/$date/draft").get().build()
        return runCatchingRequest(request).mapCatching { response ->
            response.use { extractBodyOrThrow(it) }
        }
    }

    suspend fun assistantChat(messages: List<Map<String, String>>): Result<String> {
        val arr = org.json.JSONArray()
        messages.forEach { m ->
            arr.put(JSONObject().apply {
                put("role", m["role"])
                put("content", m["content"])
            })
        }
        val body = JSONObject().apply { put("messages", arr) }
            .toString().toRequestBody(jsonMediaType)
        val request = authedRequestBuilder("/api/assistant/chat").post(body).build()
        return runCatchingRequest(request).mapCatching { response ->
            response.use {
                val bodyStr = extractBodyOrThrow(it)
                JSONObject(bodyStr).optString("reply", "")
            }
        }
    }

    suspend fun diaryEditPreview(
        date: String,
        instruction: String,
        history: List<Map<String, String>>,
        targets: List<String> = emptyList(),
    ): Result<DiaryPreviewResult> {
        val arr = org.json.JSONArray()
        history.forEach { m ->
            arr.put(JSONObject().apply {
                put("role", m["role"])
                put("content", m["content"])
            })
        }
        val targetArr = org.json.JSONArray()
        targets.forEach { targetArr.put(it) }
        val body = JSONObject().apply {
            put("date", date)
            put("instruction", instruction)
            put("messages", arr)
            put("targets", targetArr)
        }.toString().toRequestBody(jsonMediaType)
        val request = authedRequestBuilder("/api/assistant/diary-edit/preview").post(body).build()
        return runCatchingRequest(request).mapCatching { response ->
            response.use {
                val bodyStr = extractBodyOrThrow(it)
                val obj = JSONObject(bodyStr)
                val changesArr = obj.optJSONArray("changes") ?: org.json.JSONArray()
                val changes = (0 until changesArr.length()).map { i ->
                    val c = changesArr.getJSONObject(i)
                    DiaryChange(
                        target = c.optString("target"),
                        beforeSummary = c.optString("before_summary"),
                        afterSummary = c.optString("after_summary"),
                        beforeContent = c.optString("before_content"),
                        newContent = c.optString("new_content"),
                    )
                }
                DiaryPreviewResult(
                    reply = obj.optString("reply"),
                    previewId = obj.optString("preview_id"),
                    changes = changes,
                )
            }
        }
    }

    suspend fun diaryEditConfirm(previewId: String): Result<List<String>> {
        val body = JSONObject().apply { put("preview_id", previewId) }
            .toString().toRequestBody(jsonMediaType)
        val request = authedRequestBuilder("/api/assistant/diary-edit/confirm").post(body).build()
        return runCatchingRequest(request).mapCatching { response ->
            response.use {
                val bodyStr = extractBodyOrThrow(it)
                val obj = JSONObject(bodyStr)
                val arr = obj.optJSONArray("changed_targets") ?: org.json.JSONArray()
                (0 until arr.length()).map { i -> arr.optString(i) }
            }
        }
    }

    private fun authedRequestBuilder(path: String): Request.Builder {
        return Request.Builder()
            .url("$serverUrl$path")
            .header("Authorization", "Bearer $token")
    }

    private fun extractBodyOrThrow(response: Response): String {
        val bodyStr = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw ApiException("请求失败 (HTTP ${response.code}): ${bodyStr.take(200)}")
        }
        return bodyStr
    }

    private fun guessImageMediaType(filename: String) = when {
        filename.endsWith(".png", ignoreCase = true) -> "image/png".toMediaType()
        filename.endsWith(".webp", ignoreCase = true) -> "image/webp".toMediaType()
        else -> "image/jpeg".toMediaType()
    }
}

class ApiException(message: String) : Exception(message)

data class DiaryChange(
    val target: String,
    val beforeSummary: String,
    val afterSummary: String,
    val beforeContent: String,
    val newContent: String,
)

data class DiaryPreviewResult(
    val reply: String,
    val previewId: String,
    val changes: List<DiaryChange>,
)

data class ReportResult(
    val reportId: String,
    val markdown: String,
)
