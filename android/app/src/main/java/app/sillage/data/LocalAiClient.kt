package app.sillage.data

import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LocalAiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun summarizeMemo(profile: AIProfileDraft, memo: Memo): MemoAI = withContext(Dispatchers.IO) {
        val result = callAI(
            profile = profile,
            systemPrompt = memoSummarySystemPrompt(),
            messages = listOf(AIMessage("user", memoSummaryUserPrompt(memo.content))),
        )
        val now = Instant.now().toString()
        MemoAI(
            memoId = memo.id,
            summary = result.content,
            sentiment = null,
            provider = profile.provider,
            model = profile.model,
            profileId = profile.id,
            promptVersion = "memo-summary-v2",
            sourceMemoIds = "[\"${memo.id}\"]",
            status = "complete",
            errorCode = null,
            startedAt = now,
            finishedAt = now,
            inputTokens = result.inputTokens,
            outputTokens = result.outputTokens,
            totalTokens = result.totalTokens,
            createdAt = now,
            updatedAt = now,
        )
    }

    suspend fun answerQuestion(
        profile: AIProfileDraft,
        question: String,
        scope: String,
        memos: List<Memo>,
        history: List<AskMessage>,
        emptySourcesAnswer: String,
    ): LocalAskAnswer = withContext(Dispatchers.IO) {
        val sources = selectLocalAskSources(question, memos, scope)
        if (sources.isEmpty()) {
            return@withContext LocalAskAnswer(
                answer = emptySourcesAnswer,
                sourceRefs = emptyList(),
                model = profile.model,
            )
        }
        val messages = history
            .filter { it.role == "user" || it.role == "assistant" }
            .map { AIMessage(it.role, it.content) } +
            AIMessage("user", askUserPrompt(scope, question, sources))
        val result = callAI(
            profile = profile,
            systemPrompt = askSystemPrompt(),
            messages = messages,
        )
        LocalAskAnswer(
            answer = result.content,
            sourceRefs = sources,
            model = profile.model,
        )
    }

    suspend fun testConnection(profile: AIProfileDraft): String = withContext(Dispatchers.IO) {
        val result = callAI(
            profile = profile,
            systemPrompt = "你是连接测试助手。",
            messages = listOf(AIMessage("user", "请只回复 OK")),
            maxTokens = 16,
        )
        result.content.ifBlank { profile.model }
    }

    private fun callAI(
        profile: AIProfileDraft,
        systemPrompt: String,
        messages: List<AIMessage>,
        maxTokens: Long = profile.maxTokens,
    ): AICallResult {
        if (profile.apiKeyInput.isBlank()) {
            throw ApiException("请先配置 AI API 密钥")
        }
        if (profile.model.isBlank()) {
            throw ApiException("请先配置 AI 模型")
        }
        val baseUrl = normalizeAIBaseUrl(profile.baseUrl, profile.provider)
        return if (profile.provider.equals("anthropic", ignoreCase = true)) {
            callAnthropic(baseUrl, profile, systemPrompt, messages, maxTokens)
        } else {
            callOpenAICompatible(baseUrl, profile, systemPrompt, messages, maxTokens)
        }
    }

    private fun callOpenAICompatible(
        baseUrl: String,
        profile: AIProfileDraft,
        systemPrompt: String,
        messages: List<AIMessage>,
        maxTokens: Long,
    ): AICallResult {
        val payloadMessages = JSONArray()
        if (systemPrompt.isNotBlank()) {
            payloadMessages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        messages.forEach { payloadMessages.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val payload = JSONObject()
            .put("model", profile.model)
            .put("messages", payloadMessages)
            .put("temperature", profile.temperature)
            .put("max_tokens", maxTokens)
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer ${profile.apiKeyInput}")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        val body = executeProviderRequest(request)
        val choices = body.optJSONArray("choices") ?: JSONArray()
        val first = choices.optJSONObject(0) ?: JSONObject()
        val content = first.optJSONObject("message")?.optString("content").orEmpty()
            .ifBlank { first.optJSONObject("delta")?.optString("content").orEmpty() }
            .trim()
        if (content.isBlank()) {
            throw ApiException("AI 返回为空")
        }
        val usage = body.optJSONObject("usage") ?: JSONObject()
        val input = usage.optLong("input_tokens", usage.optLong("prompt_tokens"))
        val output = usage.optLong("output_tokens", usage.optLong("completion_tokens"))
        return AICallResult(
            content = content,
            inputTokens = input,
            outputTokens = output,
            totalTokens = usage.optLong("total_tokens", input + output),
        )
    }

    private fun callAnthropic(
        baseUrl: String,
        profile: AIProfileDraft,
        systemPrompt: String,
        messages: List<AIMessage>,
        maxTokens: Long,
    ): AICallResult {
        val payloadMessages = JSONArray()
        messages
            .filter { it.role == "user" || it.role == "assistant" }
            .forEach { payloadMessages.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val payload = JSONObject()
            .put("model", profile.model)
            .put("system", systemPrompt)
            .put("messages", payloadMessages)
            .put("temperature", profile.temperature)
            .put("max_tokens", if (maxTokens > 0) maxTokens else 1000)
        val request = Request.Builder()
            .url("$baseUrl/messages")
            .header("x-api-key", profile.apiKeyInput)
            .header("anthropic-version", "2023-06-01")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        val body = executeProviderRequest(request)
        val contentBlocks = body.optJSONArray("content") ?: JSONArray()
        val text = buildString {
            for (index in 0 until contentBlocks.length()) {
                val block = contentBlocks.optJSONObject(index) ?: continue
                if (block.optString("type") == "text") {
                    append(block.optString("text"))
                }
            }
        }.trim()
        if (text.isBlank()) {
            throw ApiException("AI 返回为空")
        }
        val usage = body.optJSONObject("usage") ?: JSONObject()
        val input = usage.optLong("input_tokens")
        val output = usage.optLong("output_tokens")
        return AICallResult(
            content = text,
            inputTokens = input,
            outputTokens = output,
            totalTokens = input + output,
        )
    }

    private fun executeProviderRequest(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException("AI 请求失败：${response.code} ${raw.take(200)}")
            }
            return JSONObject(raw)
        }
    }

    private fun normalizeAIBaseUrl(raw: String, provider: String): String {
        val fallback = if (provider.equals("anthropic", ignoreCase = true)) {
            "https://api.anthropic.com/v1"
        } else {
            "https://api.openai.com/v1"
        }
        val value = raw.trim().ifBlank { fallback }
        return URL(value).toString().trimEnd('/')
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

data class LocalAskAnswer(
    val answer: String,
    val sourceRefs: List<AskSourceRef>,
    val model: String,
)

private data class AIMessage(
    val role: String,
    val content: String,
)

private data class AICallResult(
    val content: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
)

private fun memoSummarySystemPrompt(): String {
    return """
        你是 Sillage 的私人记录总结助手。
        只能根据提供的单条记录内容总结，不要编造，不要诊断，不要延伸到记录之外。
        输出简洁中文，尽量概括核心事实、状态变化和可见线索。
    """.trimIndent()
}

private fun memoSummaryUserPrompt(content: String): String {
    return """
        请根据下面这条记录生成简洁总结。

        记录：
        ${content.trim()}

        要求：
        1. 只根据这条记录。
        2. 不要编造背景或结论。
        3. 输出一段中文总结。
    """.trimIndent()
}

private fun askSystemPrompt(): String {
    return """
        你是 Sillage 的私人记录问答助手。
        只能根据提供的记录来源和对话历史回答，不要编造，不要做医学或心理诊断。
        如果信息不足，就明确说“现有记录不足以判断”。
        回答保持中文、简洁、具体。
    """.trimIndent()
}

private fun askUserPrompt(scope: String, question: String, sources: List<AskSourceRef>): String {
    return buildString {
        append("上下文范围：")
        append(askScopeLabel(scope))
        append("\n\n当前问题：\n")
        append(question.trim())
        append("\n\n可引用来源：\n")
        sources.forEachIndexed { index, source ->
            append("${index + 1}. [${source.entryDate}] ${source.excerpt}\n")
        }
        append("\n回答要求：\n")
        append("1. 只能根据来源和对话历史回答。\n")
        append("2. 若证据不足，直接说现有记录不足以判断。\n")
        append("3. 如有引用，尽量用来源编号对应。\n")
    }
}

private fun askScopeLabel(scope: String): String {
    return when (scope) {
        "recent_7_days" -> "最近 7 天"
        "all" -> "全部记录"
        else -> "最近 30 天"
    }
}

private fun selectLocalAskSources(question: String, memos: List<Memo>, scope: String): List<AskSourceRef> {
    val today = LocalDate.now()
    val scoped = memos
        .filter { it.isActive() }
        .filter { memo ->
            when (scope) {
                "recent_7_days" -> runCatching { LocalDate.parse(memo.entryDate).isAfter(today.minusDays(8)) }.getOrDefault(true)
                "all" -> true
                else -> runCatching { LocalDate.parse(memo.entryDate).isAfter(today.minusDays(31)) }.getOrDefault(true)
            }
        }
    val terms = localAskQueryTerms(question)
    return scoped
        .sortedWith(compareByDescending<Memo> { scoreMemo(it, terms) }.thenByDescending { it.entryDate })
        .take(8)
        .mapIndexed { index, memo ->
            AskSourceRef(
                memoId = memo.id,
                entryDate = memo.entryDate,
                excerpt = excerpt(memo.content, 160),
                rank = index + 1,
            )
        }
}

internal fun localAskQueryTerms(question: String): List<String> {
    val normalized = question.trim()
    if (normalized.isBlank()) {
        return emptyList()
    }
    val words = normalized
        .split(Regex("""\s+"""))
        .map { it.trim() }
        .filter { it.length >= 2 }
    val compact = normalized.replace(Regex("""\s+"""), "")
    val grams = if (compact.any { it.code > 127 } && compact.length >= 2) {
        compact.windowed(size = 2, step = 1).filter { it.any { char -> char.code > 127 } }
    } else {
        emptyList()
    }
    return (words + grams).distinct()
}

private fun scoreMemo(memo: Memo, terms: List<String>): Int {
    if (terms.isEmpty()) {
        return 0
    }
    return terms.count { memo.content.contains(it, ignoreCase = true) }
}
