package com.luthiworks.luthimark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes

@Composable
fun HighlightedCode(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val annotated = remember(code, language, isDark) {
        buildHighlighted(code, language, isDark)
    }
    val backgroundColor = if (isDark) Color(0xFF202124) else Color(0xFFF6F8FA)

    Text(
        text = annotated,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private fun buildHighlighted(code: String, language: String?, darkMode: Boolean): AnnotatedString {
    if (language?.lowercase()?.trim() == "sql") {
        return highlightSql(code, darkMode)
    }
    val syntaxLanguage = mapLanguage(language) ?: return AnnotatedString(code)
    val highlights = Highlights.Builder()
        .code(code)
        .language(syntaxLanguage)
        .theme(SyntaxThemes.darcula(darkMode = darkMode))
        .build()
    val codeHighlights = highlights.getHighlights()
    return buildAnnotatedString {
        append(code)
        codeHighlights.forEach { highlight ->
            when (highlight) {
                is ColorHighlight -> addStyle(
                    style = SpanStyle(color = Color(0xFF000000.toInt() or highlight.rgb)),
                    start = highlight.location.start,
                    end = highlight.location.end,
                )
                is BoldHighlight -> addStyle(
                    style = SpanStyle(fontWeight = FontWeight.Bold),
                    start = highlight.location.start,
                    end = highlight.location.end,
                )
            }
        }
    }
}

private val SQL_KEYWORDS = setOf(
    "SELECT", "FROM", "WHERE", "JOIN", "INNER", "OUTER", "LEFT", "RIGHT", "FULL",
    "CROSS", "NATURAL", "USING", "ON", "AS", "GROUP", "BY", "ORDER", "HAVING",
    "UNION", "INTERSECT", "EXCEPT", "ALL", "ANY", "DISTINCT",
    "INSERT", "UPDATE", "DELETE", "MERGE", "TRUNCATE", "VALUES", "INTO", "SET",
    "CREATE", "DROP", "ALTER", "TABLE", "INDEX", "VIEW", "SCHEMA", "DATABASE",
    "SEQUENCE", "TRIGGER", "FUNCTION", "PROCEDURE", "RETURNS",
    "AND", "OR", "NOT", "NULL", "IS", "IN", "LIKE", "ILIKE", "BETWEEN", "EXISTS",
    "CASE", "WHEN", "THEN", "ELSE", "END", "IF", "WITH", "RECURSIVE",
    "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "DEFAULT", "UNIQUE", "CHECK",
    "CONSTRAINT", "AUTO_INCREMENT", "AUTOINCREMENT",
    "BEGIN", "COMMIT", "ROLLBACK", "SAVEPOINT", "TRANSACTION",
    "GRANT", "REVOKE", "EXPLAIN", "ANALYZE", "VACUUM",
    "LIMIT", "OFFSET", "FETCH", "FIRST", "NEXT", "ROWS", "ONLY",
    "RETURNING", "CAST", "CONVERT", "OVER", "PARTITION", "WINDOW",
    "ASC", "DESC", "TRUE", "FALSE",
)
private val SQL_TYPES = setOf(
    "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT", "MEDIUMINT",
    "VARCHAR", "CHAR", "TEXT", "MEDIUMTEXT", "LONGTEXT", "CLOB",
    "BLOB", "BYTEA", "BINARY", "VARBINARY",
    "DATE", "TIME", "DATETIME", "TIMESTAMP", "INTERVAL",
    "DECIMAL", "NUMERIC", "FLOAT", "DOUBLE", "REAL", "MONEY",
    "BOOLEAN", "BOOL", "BIT",
    "JSON", "JSONB", "XML", "UUID",
    "SERIAL", "BIGSERIAL", "SMALLSERIAL", "ARRAY",
)
private val SQL_FUNCTIONS = setOf(
    "COUNT", "SUM", "AVG", "MIN", "MAX", "STDDEV", "VARIANCE",
    "COALESCE", "NULLIF", "IFNULL", "ISNULL", "GREATEST", "LEAST",
    "ROUND", "CEIL", "CEILING", "FLOOR", "ABS", "MOD", "POWER", "SQRT",
    "LENGTH", "CHAR_LENGTH", "SUBSTRING", "SUBSTR", "UPPER", "LOWER",
    "TRIM", "LTRIM", "RTRIM", "REPLACE", "CONCAT", "POSITION",
    "NOW", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP",
    "EXTRACT", "DATE_TRUNC", "DATE_PART", "DATE_ADD", "DATE_SUB",
    "ROW_NUMBER", "RANK", "DENSE_RANK", "LAG", "LEAD",
)

private fun highlightSql(code: String, darkMode: Boolean): AnnotatedString {
    val keywordColor = if (darkMode) Color(0xFFCC7832) else Color(0xFF7F0055)
    val typeColor = if (darkMode) Color(0xFF6897BB) else Color(0xFF267F99)
    val funcColor = if (darkMode) Color(0xFFFFC66D) else Color(0xFF795E26)
    val stringColor = if (darkMode) Color(0xFF6A8759) else Color(0xFF067D17)
    val numberColor = if (darkMode) Color(0xFF6897BB) else Color(0xFF098658)
    val commentColor = if (darkMode) Color(0xFF808080) else Color(0xFF8C8C8C)

    data class Span(val start: Int, val end: Int, val style: SpanStyle)
    val spans = mutableListOf<Span>()

    var i = 0
    while (i < code.length) {
        val c = code[i]
        when {
            c == '-' && i + 1 < code.length && code[i + 1] == '-' -> {
                val nl = code.indexOf('\n', i)
                val end = if (nl < 0) code.length else nl
                spans += Span(i, end, SpanStyle(color = commentColor, fontStyle = FontStyle.Italic))
                i = end
            }
            c == '/' && i + 1 < code.length && code[i + 1] == '*' -> {
                val close = code.indexOf("*/", i + 2)
                val end = if (close < 0) code.length else close + 2
                spans += Span(i, end, SpanStyle(color = commentColor, fontStyle = FontStyle.Italic))
                i = end
            }
            c == '\'' -> {
                var j = i + 1
                while (j < code.length) {
                    if (code[j] == '\'') {
                        if (j + 1 < code.length && code[j + 1] == '\'') j += 2
                        else { j++; break }
                    } else j++
                }
                spans += Span(i, j, SpanStyle(color = stringColor))
                i = j
            }
            c == '"' -> {
                val close = code.indexOf('"', i + 1)
                val end = if (close < 0) code.length else close + 1
                spans += Span(i, end, SpanStyle(color = stringColor))
                i = end
            }
            c.isDigit() -> {
                var j = i + 1
                var seenDot = false
                while (j < code.length) {
                    val ch = code[j]
                    if (ch.isDigit()) j++
                    else if (ch == '.' && !seenDot) { seenDot = true; j++ }
                    else break
                }
                spans += Span(i, j, SpanStyle(color = numberColor))
                i = j
            }
            c.isLetter() || c == '_' -> {
                var j = i + 1
                while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                val word = code.substring(i, j).uppercase()
                val style = when (word) {
                    in SQL_KEYWORDS -> SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)
                    in SQL_TYPES -> SpanStyle(color = typeColor)
                    in SQL_FUNCTIONS -> SpanStyle(color = funcColor)
                    else -> null
                }
                if (style != null) spans += Span(i, j, style)
                i = j
            }
            else -> i++
        }
    }

    return buildAnnotatedString {
        append(code)
        spans.forEach { addStyle(it.style, it.start, it.end) }
    }
}

private fun mapLanguage(lang: String?): SyntaxLanguage? = when (lang?.lowercase()?.trim()) {
    "kotlin", "kt" -> SyntaxLanguage.KOTLIN
    "java" -> SyntaxLanguage.JAVA
    "python", "py" -> SyntaxLanguage.PYTHON
    "javascript", "js" -> SyntaxLanguage.JAVASCRIPT
    "typescript", "ts" -> SyntaxLanguage.TYPESCRIPT
    "rust", "rs" -> SyntaxLanguage.RUST
    "swift" -> SyntaxLanguage.SWIFT
    "c" -> SyntaxLanguage.C
    "cpp", "c++" -> SyntaxLanguage.CPP
    "csharp", "cs", "c#" -> SyntaxLanguage.CSHARP
    "ruby", "rb" -> SyntaxLanguage.RUBY
    "go", "golang" -> SyntaxLanguage.GO
    "shell", "sh", "bash" -> SyntaxLanguage.SHELL
    "perl", "pl" -> SyntaxLanguage.PERL
    "php" -> SyntaxLanguage.PHP
    else -> null
}
