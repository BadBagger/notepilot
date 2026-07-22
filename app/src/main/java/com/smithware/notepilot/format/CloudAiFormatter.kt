package com.smithware.notepilot.format

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
 * notes using the Anthropic Messages API -- the opt-in counterpart to
 * LocalRuleBasedFormatter, which only ever produces one note per capture.
 * Uses org.json + HttpsURLConnection (both built into Android) rather than
 * adding a networking dependency for what is a single POST request.
 * Network/parse failures throw CloudAiFormatterException; callers should
 * catch it and fall back to the local formatter rather than losing the
 * capture. Performs blocking I/O -- callers must run this off the main thread.
 */
class CloudAiFormatter {

    fun formatThoughtDump(rawTranscript: String, apiKey: String): List<FormattedCapture> {
        val requestBody = buildRequestBody(rawTranscript)
        val responseText = postToAnthropic(requestBody, apiKey)
        return parseCaptures(responseText, rawTranscript)
    }

    private fun buildRequestBody(rawTranscript: String): String {
        val body = JSONObject()
        body.put("model", "claude-haiku-4-5-20251001")
        body.put("max_tokens", 2048)
        body.put("system", SYSTEM_PROMPT)
        val userMessage = JSONObject().put("role", "user").put("content", rawTranscript)
        body.put("messages", JSONArray().put(userMessage))
        return body.toString()
    }

    private fun postToAnthropic(requestBody: String, apiKey: String): String {
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
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(requestBody) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw CloudAiFormatterException("Anthropic API returned HTTP $status: ${raw.take(300)}")
            }

            val response = JSONObject(raw)
            val content = response.optJSONArray("content")
                ?: throw CloudAiFormatterException("No content in Anthropic response")
            val text = (0 until content.length())
                .map { content.getJSONObject(it) }
                .firstOrNull { it.optString("type") == "text" }
                ?.optString("text")
                ?: throw CloudAiFormatterException("No text block in Anthropic response")
            return text
        } catch (e: CloudAiFormatterException) {
            throw e
        } catch (e: Exception) {
            throw CloudAiFormatterException("Could not reach Anthropic API: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCaptures(responseText: String, fullDumpTranscript: String): List<FormattedCapture> {
        val array = try {
            JSONArray(extractJsonArray(responseText))
        } catch (e: Exception) {
            throw CloudAiFormatterException("Could not parse AI response as JSON: ${e.message}", e)
        }
        if (array.length() == 0) {
            throw CloudAiFormatterException("AI response produced zero captures")
        }
        val single = array.length() == 1
        return (0 until array.length()).map { i -> toFormattedCapture(array.getJSONObject(i), fullDumpTranscript, single) }
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
        val items = obj.optJSONArray("items")?.toStringList().orEmpty()
        val tags = obj.optJSONArray("tags")?.toStringList().orEmpty().toMutableList()
        if (obj.optBoolean("workRelated", false) && tags.none { it.equals("work", ignoreCase = true) }) {
            tags.add("work")
        }
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

        private val SYSTEM_PROMPT = """
            You split a rambling, stream-of-consciousness voice transcript into separate, distinct notes.
            The speaker may mix unrelated topics: personal tasks, software/business ideas, objectives, reminders, shopping, etc.
            Each distinct thought or topic becomes its own entry.

            Respond with ONLY a JSON array (no markdown fences, no explanation). Each element:
            {
              "title": short title (a few words),
              "type": one of "PlainNote", "Checklist", "TodoList", "ShoppingList", "ReminderDraft", "IdeaNote",
              "cleanedText": a cleaned-up sentence or bullet summary of this entry,
              "items": array of short strings if this entry is a list of discrete items (checklist/todo/shopping), else [],
              "tags": array of short lowercase topic tags (e.g. "software", "personal", "errands"),
              "workRelated": true if this entry is about a job, business, or professional obligation, else false,
              "dueDateIso": an ISO-8601 local date-time "yyyy-MM-ddTHH:mm:ss" if a specific date/time was mentioned for this entry, else ""
            }

            Always return at least one entry. Keep entries focused -- do not merge unrelated thoughts into one entry.
        """.trimIndent()
    }
}
