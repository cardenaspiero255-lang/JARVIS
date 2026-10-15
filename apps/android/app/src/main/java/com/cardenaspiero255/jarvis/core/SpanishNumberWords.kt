package com.cardenaspiero255.jarvis.core

/**
 * Strict, local preprocessor for Spanish cardinal number phrases in arithmetic.
 * Never interprets free-form instructions, currencies, contacts or Android actions.
 * Unrecognized tokens are left untouched so OfflineArithmetic rejects the input.
 */
object SpanishNumberWords {
    private val operators = Regex(
        """\b(?:multiplicado\s+por|dividido\s+por|por|entre|mas|menos|x)\b|[+\-*/()×÷]"""
    )
    private val wordOrDigits = Regex("""[a-z0-9]+(?:\s+[a-z0-9]+)*""")
    private val units = mapOf(
        "cero" to 0L, "un" to 1L, "uno" to 1L, "una" to 1L,
        "dos" to 2L, "tres" to 3L, "cuatro" to 4L, "cinco" to 5L,
        "seis" to 6L, "siete" to 7L, "ocho" to 8L, "nueve" to 9L,
        "diez" to 10L, "once" to 11L, "doce" to 12L, "trece" to 13L,
        "catorce" to 14L, "quince" to 15L, "dieciseis" to 16L,
        "diecisiete" to 17L, "dieciocho" to 18L, "diecinueve" to 19L,
        "veinte" to 20L, "veintiuno" to 21L, "veintidos" to 22L,
        "veintitres" to 23L, "veinticuatro" to 24L, "veinticinco" to 25L,
        "veintiseis" to 26L, "veintisiete" to 27L, "veintiocho" to 28L,
        "veintinueve" to 29L
    )
    private val tens = mapOf(
        "treinta" to 30L, "cuarenta" to 40L, "cincuenta" to 50L,
        "sesenta" to 60L, "setenta" to 70L, "ochenta" to 80L,
        "noventa" to 90L
    )
    private val hundreds = mapOf(
        "cien" to 100L, "ciento" to 100L, "doscientos" to 200L,
        "trescientos" to 300L, "cuatrocientos" to 400L,
        "quinientos" to 500L, "seiscientos" to 600L,
        "setecientos" to 700L, "ochocientos" to 800L,
        "novecientos" to 900L
    )

    fun rewrite(expression: String): String {
        val out = StringBuilder()
        var offset = 0
        for (match in operators.findAll(expression)) {
            out.append(rewriteOperand(expression.substring(offset, match.range.first)))
            out.append(match.value)
            offset = match.range.last + 1
        }
        out.append(rewriteOperand(expression.substring(offset)))
        return out.toString()
    }

    private fun rewriteOperand(raw: String): String {
        val trimmed = raw.trim()
        if (!wordOrDigits.matches(trimmed)) return raw
        val converted = parseNumber(trimmed.split(Regex("""\s+"""))) ?: return raw
        val leading = raw.takeWhile { it.isWhitespace() }
        val trailing = raw.takeLastWhile { it.isWhitespace() }
        return leading + converted.toString() + trailing
    }

    private fun parseNumber(tokens: List<String>): Long? {
        if (tokens.isEmpty() || tokens.size > 12) return null
        val millionIndex = tokens.indexOfFirst { it == "millon" || it == "millones" }
        if (millionIndex < 0) return parseThousands(tokens)
        if (tokens.drop(millionIndex + 1).any { it == "millon" || it == "millones" }) return null
        val coefficient = if (millionIndex == 0) 1L
            else parseSmall(tokens.take(millionIndex)) ?: return null
        if (coefficient !in 1..999) return null
        val remainder = tokens.drop(millionIndex + 1)
        val tail = if (remainder.isEmpty()) 0L else parseThousands(remainder) ?: return null
        if (tail !in 0..999_999) return null
        return coefficient * 1_000_000L + tail
    }

    private fun parseThousands(tokens: List<String>): Long? {
        val index = tokens.indexOf("mil")
        if (index < 0) return parseSmall(tokens)
        if (tokens.drop(index + 1).contains("mil")) return null
        val coefficient = if (index == 0) 1L else parseSmall(tokens.take(index)) ?: return null
        if (coefficient !in 1..999) return null
        val remainder = tokens.drop(index + 1)
        val tail = if (remainder.isEmpty()) 0L else parseSmall(remainder) ?: return null
        if (tail !in 0..999) return null
        return coefficient * 1_000L + tail
    }

    private fun parseSmall(tokens: List<String>): Long? {
        if (tokens.isEmpty() || tokens.size > 5) return null
        if (tokens.size == 1) {
            tokens[0].toLongOrNull()?.let { return it.takeIf { n -> n in 0..999_999_999_999L } }
            return units[tokens[0]] ?: tens[tokens[0]] ?: hundreds[tokens[0]]
        }
        val hundredsValue = hundreds[tokens[0]]
        if (hundredsValue != null) {
            if (tokens[0] == "cien") return null // "ciento veintidós", not "cien veintidós".
            val below = parseUnderHundred(tokens.drop(1)) ?: return null
            if (below == 0L) return null
            return hundredsValue + below
        }
        return parseUnderHundred(tokens)
    }

    private fun parseUnderHundred(tokens: List<String>): Long? {
        if (tokens.size == 1) return units[tokens[0]] ?: tens[tokens[0]]
        if (tokens.size == 3 && tokens[1] == "y") {
            val ten = tens[tokens[0]] ?: if (tokens[0] == "veinte") 20L else return null
            val unit = units[tokens[2]] ?: return null
            if (unit in 1..9) return ten + unit
        }
        return null
    }
}
