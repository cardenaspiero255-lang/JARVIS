package com.cardenaspiero255.jarvis.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Locale

/** Deterministic, offline arithmetic. Input is DATA, never executable code. */
object OfflineArithmetic {
    data class Calculation(val answer: String, val value: BigDecimal?)
    private val limit = BigDecimal("1000000000000000000")
    private val number = Regex("""\d+(?:\.\d+)?""")
    private val allowed = Regex("""[0-9+*/().\-\s]+""")
    private val request = Regex("""^(cuanto es|cuanto da|calcula|resuelve|resultado de)\s*[:=]?\s*""")
    private val thousands = Regex("""\b\d{1,3}(?:\.\d{3})+\b""")
    private val unit = Regex("""(?<=\d)\s+(millones|millon|mil)\b""")

    fun evaluate(input: String, previous: BigDecimal? = null): Calculation? {
        val text = Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "").trim().trimStart('¿').trim().trimEnd('?', ' ')
        val following = Regex("""^y\s+(por|multiplicado por|dividido por|entre|mas|menos)\s+(.+)$""")
            .matchEntire(text)
        if (following != null && previous == null) {
            return Calculation("No hay un resultado anterior para continuar.", null)
        }
        val contextual = following != null
        val explicit = request.containsMatchIn(text)
        var expr = if (following != null) {
            val op = when (following.groupValues[1]) {
                "por", "multiplicado por" -> "*"
                "dividido por", "entre" -> "/"
                "mas" -> "+"
                else -> "-"
            }
            "(${previous!!.toPlainString()}) $op ${following.groupValues[2]}"
        } else request.replaceFirst(text, "")
        expr = SpanishNumberWords.rewrite(expr)
        if (!contextual && !explicit && !Regex("""^[\d(\-+]""").containsMatchIn(expr)) return null
        if (expr.length > 160) return Calculation("La expresión es demasiado larga para el cálculo local.", null)
        expr = thousands.replace(expr) { it.value.replace(".", "") }
            .replace(Regex("""(?<=\d),(?=\d)"""), ".")
        expr = unit.replace(expr) {
            "*" + if (it.groupValues[1] == "mil") "1000" else "1000000"
        }
        expr = expr.replace('×', '*').replace('x', '*').replace('÷', '/')
            .replace(Regex("""\bmultiplicado\s+por\b"""), "*")
            .replace(Regex("""\bdividido\s+por\b"""), "/")
            .replace(Regex("""\bpor\b"""), "*")
            .replace(Regex("""\bentre\b"""), "/")
            .replace(Regex("""\bmas\b"""), "+")
            .replace(Regex("""\bmenos\b"""), "-")
        if (!allowed.matches(expr) || expr.isBlank() || !expr.any(Char::isDigit)) {
            return if (explicit || contextual) Calculation("No pude interpretar esa expresión matemática.", null)
            else null
        }
        return try {
            val value = Parser(expr).evaluate().stripTrailingZeros()
            if (value.abs() > limit) Calculation("Resultado fuera del límite del cálculo local.", null)
            else Calculation("Resultado: ${display(value)}", value)
        } catch (_: ArithmeticException) {
            Calculation("No se puede dividir por cero.", null)
        } catch (_: IllegalArgumentException) {
            Calculation("No pude interpretar esa expresión matemática.", null)
        }
    }

    private fun display(value: BigDecimal): String {
        val s = value.toPlainString().split('.')
        val integral = s[0]
        val negative = integral.startsWith('-')
        val digits = integral.removePrefix("-")
        val groups = digits.reversed().chunked(3).map { it.reversed() }.reversed().joinToString(".")
        return (if (negative) "-" else "") + groups +
            (if (s.size == 2) ",${s[1]}" else "")
    }

    private class Parser(private val source: String) {
        private var index = 0
        private var operations = 0
        fun evaluate(): BigDecimal {
            val result = expression(0)
            spaces()
            if (index != source.length) throw IllegalArgumentException("trailing data")
            return bounded(result)
        }
        private fun expression(depth: Int): BigDecimal {
            if (depth > 12 || ++operations > 64) throw IllegalArgumentException("too complex")
            var value = term(depth)
            while (true) {
                spaces()
                val op = peek()
                if (op != '+' && op != '-') break
                index++
                val rhs = term(depth)
                value = bounded(if (op == '+') value.add(rhs) else value.subtract(rhs))
            }
            return value
        }
        private fun term(depth: Int): BigDecimal {
            var value = factor(depth)
            while (true) {
                spaces()
                val op = peek()
                if (op != '*' && op != '/') break
                index++
                val rhs = factor(depth)
                value = bounded(if (op == '*') value.multiply(rhs)
                    else value.divide(rhs, 10, RoundingMode.HALF_UP))
            }
            return value
        }
        private fun factor(depth: Int): BigDecimal {
            spaces()
            val c = peek()
            if (c == '+' || c == '-') {
                index++
                val result = factor(depth + 1)
                return if (c == '-') result.negate() else result
            }
            if (c == '(') {
                index++
                val result = expression(depth + 1)
                spaces()
                if (peek() != ')') throw IllegalArgumentException("missing )")
                index++
                return result
            }
            val match = number.find(source, index)
            if (match == null || match.range.first != index || match.value.length > 22) {
                throw IllegalArgumentException("invalid number")
            }
            index = match.range.last + 1
            return bounded(BigDecimal(match.value))
        }
        private fun bounded(value: BigDecimal): BigDecimal {
            if (value.abs() > limit) throw IllegalArgumentException("too large")
            return value
        }
        private fun peek(): Char? = source.getOrNull(index)
        private fun spaces() { while (source.getOrNull(index)?.isWhitespace() == true) index++ }
    }
}
