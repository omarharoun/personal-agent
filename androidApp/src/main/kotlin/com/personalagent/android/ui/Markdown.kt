package com.personalagent.android.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalagent.android.ui.theme.CodeTextStyle

/**
 * A small, dependency-free Markdown renderer good enough for assistant replies:
 * fenced code blocks, inline `code`, **bold**, *italic*, headings (#…), bullet and
 * numbered lists, block quotes, and [links](url) (styled, not navigable). Anything
 * it doesn't recognise renders as plain text, so it never throws on odd input.
 *
 * Driven entirely by the active [MaterialTheme] colours, so it looks right in both
 * the dark (default) and light schemes.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val blocks = remember(text) { parseBlocks(text) }
    val accent = MaterialTheme.colorScheme.secondary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.CodeBlock -> CodeBlockView(block.code)
                is MdBlock.Heading -> Text(
                    text = inline(block.text, accent, codeBg, color),
                    color = color,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                is MdBlock.Bullet -> Row(Modifier.fillMaxWidth()) {
                    Text("•  ", color = muted, style = MaterialTheme.typography.bodyLarge)
                    Text(inline(block.text, accent, codeBg, color), color = color, style = MaterialTheme.typography.bodyLarge)
                }
                is MdBlock.Numbered -> Row(Modifier.fillMaxWidth()) {
                    Text("${block.number}.  ", color = muted, style = MaterialTheme.typography.bodyLarge)
                    Text(inline(block.text, accent, codeBg, color), color = color, style = MaterialTheme.typography.bodyLarge)
                }
                is MdBlock.Quote -> Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        inline(block.text, accent, codeBg, color),
                        color = muted,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                is MdBlock.Paragraph -> Text(
                    text = inline(block.text, accent, codeBg, color),
                    color = color,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun CodeBlockView(code: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = code,
            color = MaterialTheme.colorScheme.onSurface,
            style = CodeTextStyle,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

// --- Block model -------------------------------------------------------------
private sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Numbered(val number: Int, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class CodeBlock(val code: String) : MdBlock
}

private val numberedRe = Regex("^(\\d+)\\.\\s+(.*)")

/** Split source markdown into ordered blocks. Robust to ragged input. */
private fun parseBlocks(src: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = src.replace("\r\n", "\n").split("\n")
    var i = 0
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) out += MdBlock.Paragraph(paragraph.toString().trim())
        paragraph.setLength(0)
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()

        // Fenced code block: ``` … ``` (language label after the opening fence is ignored).
        if (trimmed.startsWith("```")) {
            flushParagraph()
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                if (code.isNotEmpty()) code.append("\n")
                code.append(lines[i])
                i++
            }
            i++ // consume closing fence (or EOF)
            out += MdBlock.CodeBlock(code.toString())
            continue
        }

        when {
            trimmed.isBlank() -> flushParagraph()
            trimmed.startsWith("### ") -> { flushParagraph(); out += MdBlock.Heading(3, trimmed.removePrefix("### ").trim()) }
            trimmed.startsWith("## ") -> { flushParagraph(); out += MdBlock.Heading(2, trimmed.removePrefix("## ").trim()) }
            trimmed.startsWith("# ") -> { flushParagraph(); out += MdBlock.Heading(1, trimmed.removePrefix("# ").trim()) }
            trimmed.startsWith("> ") -> { flushParagraph(); out += MdBlock.Quote(trimmed.removePrefix("> ").trim()) }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                { flushParagraph(); out += MdBlock.Bullet(trimmed.drop(2).trim()) }
            numberedRe.matches(trimmed) -> {
                flushParagraph()
                val m = numberedRe.find(trimmed)!!
                out += MdBlock.Numbered(m.groupValues[1].toIntOrNull() ?: 1, m.groupValues[2].trim())
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(" ")
                paragraph.append(trimmed)
            }
        }
        i++
    }
    flushParagraph()
    return out
}

// --- Inline span parsing -----------------------------------------------------
/** Build an [AnnotatedString] handling inline code, bold, italic, and links. */
private fun inline(text: String, accent: Color, codeBg: Color, base: Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                // inline code: `…`
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, fontSize = 14.sp)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else { append(c); i++ }
                }
                // bold: **…**
                c == '*' && i + 1 < n && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                        i = end + 2
                    } else { append(c); i++ }
                }
                // italic: *…* or _…_
                (c == '*' || c == '_') -> {
                    val end = text.indexOf(c, i + 1)
                    if (end > i && end != i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                        i = end + 1
                    } else { append(c); i++ }
                }
                // link: [label](url) — styled, label shown
                c == '[' -> {
                    val close = text.indexOf(']', i + 1)
                    if (close > i && close + 1 < n && text[close + 1] == '(') {
                        val urlEnd = text.indexOf(')', close + 2)
                        if (urlEnd > close) {
                            withStyle(SpanStyle(color = accent, textDecoration = TextDecoration.Underline)) {
                                append(text.substring(i + 1, close))
                            }
                            i = urlEnd + 1
                        } else { append(c); i++ }
                    } else { append(c); i++ }
                }
                else -> { append(c); i++ }
            }
        }
    }
