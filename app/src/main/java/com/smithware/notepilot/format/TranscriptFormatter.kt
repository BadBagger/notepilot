package com.smithware.notepilot.format

import com.smithware.notepilot.data.FormattedCapture

data class FormatContext(val now: Long = System.currentTimeMillis())

interface TranscriptFormatter {
    fun formatTranscript(rawTranscript: String, context: FormatContext = FormatContext()): FormattedCapture
}

class FutureAiFormatter : TranscriptFormatter {
    override fun formatTranscript(rawTranscript: String, context: FormatContext): FormattedCapture {
        throw UnsupportedOperationException("FutureAiFormatter is a placeholder. MVP uses LocalRuleBasedFormatter only.")
    }
}
