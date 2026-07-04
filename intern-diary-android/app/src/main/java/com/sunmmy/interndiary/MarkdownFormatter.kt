package com.sunmmy.interndiary

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan

/**
 * Minimal Markdown renderer for diary drafts.
 *
 * The backend returns drafts as Markdown (a `# title` followed by paragraphs).
 * Rather than pull in a full Markdown library, this handles the small subset the
 * drafts actually use: ATX headings (`#`..`###`) and inline `**bold**`. Anything
 * else is rendered as plain text, so unexpected markup degrades gracefully.
 */
object MarkdownFormatter {

    fun format(markdown: String): CharSequence {
        val builder = SpannableStringBuilder()
        val lines = markdown.replace("\r\n", "\n").split("\n")
        for ((index, raw) in lines.withIndex()) {
            val start = builder.length
            val headingLevel = leadingHashes(raw)
            val lineText = if (headingLevel > 0) raw.substring(headingLevel).trim() else raw
            val inlineStart = builder.length
            appendWithBold(builder, lineText)
            if (headingLevel > 0 && builder.length > inlineStart) {
                val scale = when (headingLevel) {
                    1 -> 1.4f
                    2 -> 1.2f
                    else -> 1.1f
                }
                builder.setSpan(
                    RelativeSizeSpan(scale),
                    inlineStart,
                    builder.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    inlineStart,
                    builder.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            if (index < lines.size - 1) builder.append("\n")
            // Avoid an unused-variable warning while keeping start for clarity.
            check(start <= builder.length)
        }
        return builder
    }

    private fun leadingHashes(line: String): Int {
        var count = 0
        while (count < line.length && line[count] == '#') count++
        // Only treat as a heading when followed by a space (ATX style).
        return if (count in 1..6 && count < line.length && line[count] == ' ') count else 0
    }

    private fun appendWithBold(builder: SpannableStringBuilder, text: String) {
        var i = 0
        while (i < text.length) {
            val open = text.indexOf("**", i)
            if (open < 0) {
                builder.append(text.substring(i))
                break
            }
            val close = text.indexOf("**", open + 2)
            if (close < 0) {
                builder.append(text.substring(i))
                break
            }
            builder.append(text.substring(i, open))
            val boldStart = builder.length
            builder.append(text.substring(open + 2, close))
            builder.setSpan(
                StyleSpan(Typeface.BOLD),
                boldStart,
                builder.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            i = close + 2
        }
    }
}
