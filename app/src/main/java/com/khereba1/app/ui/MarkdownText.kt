package com.khereba1.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed class MdBlock {
    data class TextBlock(val lines: List<String>) : MdBlock()
    data class CodeBlock(val lang: String, val code: String) : MdBlock()
}

@Composable
fun MarkdownText(
    markdown: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    onCodeCopied: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val blocks = remember(markdown) { parseBlocks(markdown) }
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    SelectionContainer {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.CodeBlock -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(codeBg)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = block.lang.ifBlank { "code" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = muted,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cm?.setPrimaryClip(ClipData.newPlainText("code", block.code))
                                        onCodeCopied("Code copied")
                                    }
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Text(
                                text = block.code,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    textDirection = TextDirection.Ltr
                                ),
                                color = textColor
                            )
                        }
                    }

                    is MdBlock.TextBlock -> {
                        block.lines.forEach { rawLine ->
                            val line = rawLine.trimEnd()
                            if (line.isBlank()) return@forEach
                            val headingMatch = Regex("^(#{1,4})\\s+(.*)$").find(line)
                            if (headingMatch != null) {
                                val level = headingMatch.groupValues[1].length
                                val content = headingMatch.groupValues[2]
                                Text(
                                    text = parseInline(content, primary, codeBg),
                                    style = (if (level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge).copy(
                                        fontWeight = FontWeight.Bold,
                                        textDirection = TextDirection.Content
                                    ),
                                    color = textColor,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                val display = when {
                                    line.startsWith("- ") -> "•  " + line.removePrefix("- ")
                                    line.startsWith("* ") -> "•  " + line.removePrefix("* ")
                                    else -> line
                                }
                                Text(
                                    text = parseInline(display, primary, codeBg),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        textDirection = TextDirection.Content
                                    ),
                                    color = textColor,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseBlocks(input: String): List<MdBlock> {
    val result = mutableListOf<MdBlock>()
    val textAcc = mutableListOf<String>()
    val codeAcc = mutableListOf<String>()
    var inCode = false
    var codeLang = ""

    for (line in input.lines()) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (!inCode) {
                if (textAcc.isNotEmpty()) {
                    result.add(MdBlock.TextBlock(textAcc.toList()))
                    textAcc.clear()
                }
                inCode = true
                codeLang = trimmed.removePrefix("```").trim()
                codeAcc.clear()
            } else {
                result.add(MdBlock.CodeBlock(codeLang, codeAcc.joinToString("\n")))
                inCode = false
                codeLang = ""
                codeAcc.clear()
            }
        } else if (inCode) {
            codeAcc.add(line)
        } else {
            textAcc.add(line)
        }
    }
    if (inCode && codeAcc.isNotEmpty()) {
        result.add(MdBlock.CodeBlock(codeLang, codeAcc.joinToString("\n")))
    }
    if (textAcc.isNotEmpty()) {
        result.add(MdBlock.TextBlock(textAcc.toList()))
    }
    return result
}

private val INLINE_REGEX = Regex(
    "(\\[([^\\]]+)\\]\\(([^)]+)\\))" + // [label](url)
        "|(`([^`]+)`)" +               // `code`
        "|(\\*\\*([^*]+)\\*\\*)" +     // **bold**
        "|(\\*([^*]+)\\*)"             // *italic*
)

private fun parseInline(text: String, accentColor: Color, codeBg: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        for (match in INLINE_REGEX.findAll(text)) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            when {
                match.groups[1] != null -> {
                    val label = match.groups[2]?.value.orEmpty()
                    withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.SemiBold)) {
                        append(label)
                    }
                }
                match.groups[4] != null -> {
                    val code = match.groups[5]?.value.orEmpty()
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBg,
                            fontSize = 13.sp
                        )
                    ) {
                        append(" $code ")
                    }
                }
                match.groups[6] != null -> {
                    val bold = match.groups[7]?.value.orEmpty()
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(bold)
                    }
                }
                match.groups[8] != null -> {
                    val italic = match.groups[9]?.value.orEmpty()
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(italic)
                    }
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}
