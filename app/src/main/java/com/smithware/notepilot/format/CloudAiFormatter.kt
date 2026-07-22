package com.smithware.notepilot.format

import com.smithware.notepilot.data.AiProvider
import com.smithware.notepilot.data.CaptureType
import com.smithware.notepilot.data.FormattedCapture
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.HttpsURLConnection

class CloudAiFormatterException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Splits one long thought-dump transcript into several distinct, categorized
 * notes using the user's own cloud AI provider (OpenAI or Anthropic, whichever
 * they've configured) -- the opt-in counterpart to LocalRuleBasedFormatter,
 * which only ever produces one note per capture with no topic understanding.
 * Both providers are equally first-class: same prompt, same response schema,
 * same parsing -- only the HTTP request/response envelope differs between them.
 * Uses org.json + HttpsURLConnection (both built into Android) rather than
 * adding a networking dependency for what is a single POST request.
 * Network/parse failures throw CloudAiFormatterException; callers should
 * catch it and fall back to the local formatter rather than losing the
 * capture. Performs blocking I/O -- callers must run this off the main thread.
 */
class CloudAiFormatter {

    fun formatThoughtDump(rawTranscript: String, provider: AiProvider, apiKey: String): List<FormattedCapture> {
        val responseText = when (provider) {
            AiProvider.OPENAI -> postToOpenAi(SYSTEM_PROMPT, rawTranscript, apiKey)
            AiProvider.ANTHROPIC -> postToAnthropic(SYSTEM_PROMPT, rawTranscript, apiKey)
        }
        return parseCaptures(responseText, rawTranscript)
    }

    /**
     * Assigns just a topic category to a single already-saved note -- the backfill
     * counterpart to formatThoughtDump's per-entry category, for notes captured
     * before AI thought-dump existed (or via local rules, which don't categorize
     * at all). Same category vocabulary/guidance as the main prompt, just a much
     * shorter response since there's no splitting or field extraction to do.
     */
    fun categorize(noteText: String, provider: AiProvider, apiKey: String): String {
        val responseText = when (provider) {
            AiProvider.OPENAI -> postToOpenAi(CATEGORIZE_SYSTEM_PROMPT, noteText, apiKey)
            AiProvider.ANTHROPIC -> postToAnthropic(CATEGORIZE_SYSTEM_PROMPT, noteText, apiKey)
        }
        val parsed = try {
            // extractJsonArray only strips a possible ```json fence despite the name --
            // shared with formatThoughtDump's array response, works the same for this
            // single JSON object.
            JSONObject(extractJsonArray(responseText))
        } catch (e: Exception) {
            throw CloudAiFormatterException("Could not parse categorize response: ${e.message}", e)
        }
        return parsed.optString("category", "").trim()
    }

    // ---- Anthropic -------------------------------------------------------

    private fun postToAnthropic(systemPrompt: String, userText: String, apiKey: String): String {
        val body = JSONObject()
            .put("model", "claude-haiku-4-5-20251001")
            .put("max_tokens", 2048)
            .put("system", systemPrompt)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", userText)))

        val connection = try {
            URL(ANTHROPIC_MESSAGES_URL).openConnection() as HttpsURLConnection
        } catch (e: Exception) {
            throw CloudAiFormatterException("Could not open a connection to the Anthropic API: ${e.message}", e)
        }
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 20_000
            connection.readTimeout = 45_000
            connection.setRequestProperty("content-type", "application/json")
            connection.setRequestProperty("x-api-key", apiKey)
            connection.setRequestProperty("anthropic-version", "2023-06-01")
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw CloudAiFormatterException("Anthropic API returned HTTP $status: ${raw.take(300)}")
            }

            val response = JSONObject(raw)
            val content = response.optJSONArray("content")
                ?: throw CloudAiFormatterException("No content in Anthropic response")
            return (0 until content.length())
                .map { content.getJSONObject(it) }
                .firstOrNull { it.optString("type") == "text" }
                ?.optString("text")
                ?: throw CloudAiFormatterException("No text block in Anthropic response")
        } catch (e: CloudAiFormatterException) {
            throw e
        } catch (e: Exception) {
            throw CloudAiFormatterException("Could not reach Anthropic API: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }

    // ---- OpenAI -----------------------------------------------------------

    private fun postToOpenAi(systemPrompt: String, userText: String, apiKey: String): String {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
            .put(JSONObject().put("role", "user").put("content", userText))
        val body = JSONObject()
            .put("model", "gpt-4o-mini")
            .put("max_tokens", 2048)
            .put("messages", messages)

        val connection = try {
            URL(OPENAI_CHAT_URL).openConnection() as HttpsURLConnection
        } catch (e: Exception) {
            throw CloudAiFormatterException("Could not open a connection to the OpenAI API: ${e.message}", e)
        }
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 20_000
            connection.readTimeout = 45_000
            connection.setRequestProperty("content-type", "application/json")
            connection.setRequestProperty("authorization", "Bearer $apiKey")
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw CloudAiFormatterException("OpenAI API returned HTTP $status: ${raw.take(300)}")
            }

            val response = JSONObject(raw)
            val choices = response.optJSONArray("choices")
                ?: throw CloudAiFormatterException("No choices in OpenAI response")
            if (choices.length() == 0) throw CloudAiFormatterException("Empty choices array in OpenAI response")
            return choices.getJSONObject(0).optJSONObject("message")?.optString("content")
                ?: throw CloudAiFormatterException("No message content in OpenAI response")
        } catch (e: CloudAiFormatterException) {
            throw e
        } catch (e: Exception) {
            throw CloudAiFormatterException("Could not reach OpenAI API: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }

    // ---- shared response parsing -------------------------------------------

    // Wraps the ENTIRE array-processing pass (not just JSONArray construction) so a
    // structurally-valid-but-schema-deviating response -- e.g. the model returns an
    // array of strings instead of objects, or an "items"/"tags" array containing a
    // non-string element -- still surfaces as CloudAiFormatterException instead of a
    // raw org.json.JSONException escaping uncaught into the caller's coroutine and
    // crashing the app, which would defeat the whole point of the documented
    // catch-and-fall-back-to-local-rules contract.
    private fun parseCaptures(responseText: String, fullDumpTranscript: String): List<FormattedCapture> {
        try {
            val array = JSONArray(extractJsonArray(responseText))
            if (array.length() == 0) {
                throw CloudAiFormatterException("AI response produced zero captures")
            }
            val single = array.length() == 1
            return (0 until array.length()).map { i -> toFormattedCapture(array.getJSONObject(i), fullDumpTranscript, single) }
        } catch (e: CloudAiFormatterException) {
            throw e
        } catch (e: Exception) {
            throw CloudAiFormatterException("Could not parse AI response: ${e.message}", e)
        }
    }

    /**
     * `fullDumpTranscript` is the entire original thought-dump transcript, shared
     * across every split entry. It's only used verbatim as this entry's own
     * `rawTranscript` when the AI produced exactly one entry (nothing was actually
     * split) -- otherwise storing the whole dump on each split-out entry would mean
     * a later "convert transcript to tasks" on any single note reprocesses and
     * overwrites it with a jumble of every other entry too, defeating the point of
     * splitting. For an actual multi-entry split, an entry's own cleaned text is
     * the closest honest stand-in for "this entry's raw source" since the AI
     * response doesn't include a verbatim per-entry substring of the dump.
     */
    private fun toFormattedCapture(obj: JSONObject, fullDumpTranscript: String, single: Boolean): FormattedCapture {
        val type = parseType(obj.optString("type", "PlainNote"))
        // CaptureEntity stores tags/checklist items pipe-delimited with no escaping
        // (see toEntity() in Models.kt) -- fine for the fixed, hand-picked literal
        // tags LocalRuleBasedFormatter uses, but these come from an LLM, so strip any
        // stray "|" defensively rather than risk one tag silently splitting into two.
        val items = obj.optJSONArray("items")?.toStringList()?.map { it.replace("|", "/") }.orEmpty()
        val tags = obj.optJSONArray("tags")?.toStringList()?.map { it.replace("|", "/") }.orEmpty()
        val category = obj.optString("category", "").trim().replace("|", "/")
        val dueDateMillis = obj.optString("dueDateIso", "").takeIf { it.isNotBlank() }?.let { parseIsoDateTime(it) }
        val cleanedText = obj.optString("cleanedText", "")
        return FormattedCapture(
            title = obj.optString("title", "").ifBlank { "Untitled capture" },
            type = type,
            cleanedText = cleanedText,
            checklistItems = items,
            detectedDateTime = dueDateMillis,
            reminderDateTime = null,
            detectedTags = tags,
            category = category,
            confidenceScore = 0.75f,
            rawTranscript = if (single) fullDumpTranscript else cleanedText,
            warnings = emptyList()
        )
    }

    private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }

    private fun parseType(raw: String): CaptureType =
        CaptureType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CaptureType.PlainNote

    private fun parseIsoDateTime(iso: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getDefault() }.parse(iso)?.time
    } catch (e: Exception) {
        null
    }

    /** The model is instructed to return raw JSON, but strip a ```json fence if it adds one anyway. */
    private fun extractJsonArray(text: String): String {
        val trimmed = text.trim()
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(trimmed)
        return (fenced?.groupValues?.get(1) ?: trimmed).trim()
    }

    companion object {
        private const val ANTHROPIC_MESSAGES_URL = "https://api.anthropic.com/v1/messages"
        private const val OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions"

        private val CATEGORIZE_SYSTEM_PROMPT = """
            You classify what a single saved note is actually ABOUT by its real meaning and intent,
            not just surface keywords. For example, "I need to work on Claude's usage app tracker" is
            about building or planning a piece of software, so its category should be something like
            "App Ideas" -- even though the note never says "app idea" or "software" outright.

            Pick ONE short, Title Case category (1-3 words) that best names what the note is about.
            Common examples: "Work", "App Ideas", "Personal", "Errands", "Shopping", "Finance",
            "Health", "Home". These are only illustrative starting points, not a fixed list -- if the
            note is clearly about something these don't fit well, invent a short, clear Title Case
            category of your own rather than forcing a bad fit.

            Respond with ONLY a JSON object (no markdown fences, no explanation): {"category": "..."}
        """.trimIndent()

        private val SYSTEM_PROMPT = """
            You split a rambling, stream-of-consciousness voice transcript into separate, distinct notes.
            The speaker may mix unrelated topics: personal tasks, software/business ideas, objectives, reminders, shopping, etc.
            Each distinct thought or topic becomes its own entry.

            For each entry, also classify what it's actually ABOUT by its real meaning and intent, not just
            surface keywords. For example, "I need to work on Claude's usage app tracker" is about building or
            planning a piece of software, so its category should be something like "App Ideas" -- even though
            the sentence never says the word "app idea" or "software" outright. Read for intent, not just words.

            Pick ONE short, Title Case category (1-3 words) per entry that best names what it's about. Common
            examples: "Work", "App Ideas", "Personal", "Errands", "Shopping", "Finance", "Health", "Home". These
            are only illustrative starting points, not a fixed list -- if an entry is clearly about something
            these don't fit well (e.g. "Recipes", "Travel", "Car Maintenance"), invent a short, clear Title Case
            category of your own rather than forcing a bad fit into one of the examples.

            Respond with ONLY a JSON array (no markdown fences, no explanation). Each element:
            {
              "title": short title (a few words),
              "type": one of "PlainNote", "Checklist", "TodoList", "ShoppingList", "ReminderDraft", "IdeaNote",
              "cleanedText": a cleaned-up sentence or bullet summary of this entry,
              "items": array of short strings if this entry is a list of discrete items (checklist/todo/shopping), else [],
              "category": the single best-fit Title Case topic category for this entry, as described above,
              "tags": array of a few extra short lowercase keywords for finer search/filtering, else [],
              "dueDateIso": an ISO-8601 local date-time "yyyy-MM-ddTHH:mm:ss" if a specific date/time was mentioned for this entry, else ""
            }

            Always return at least one entry. Keep entries focused -- do not merge unrelated thoughts into one entry.
        """.trimIndent()
    }
}
