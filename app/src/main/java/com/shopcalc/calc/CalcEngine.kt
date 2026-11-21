package com.shopcalc.calc

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/*
 * Pure-Kotlin port of the Sixteenth+ (originally "Shop Calc") engine from cabinet_calc.html.
 *
 * Tokens are strings:
 *  - plain numbers, e.g. "24", "-3", "0.5" (may still be "entering")
 *  - fraction tokens, e.g. "3⁄16" (built via a preset denominator button)
 *  - operators: "+", "−", "×", "÷"
 *  - parens: "(" ")"
 *  - space: " " (implicit "+" between two number-like tokens, e.g. "20 1⁄2")
 */

const val FRAC_SLASH = "⁄" // ⁄
val OPS = listOf("+", "−", "×", "÷")
private val PRECEDENCE = mapOf("+" to 1, "−" to 1, "×" to 2, "÷" to 2)

fun isOp(t: String?) = t != null && t in OPS
fun isFractionTok(t: String?) = t != null && t.contains(FRAC_SLASH)
fun isNumTok(t: String?) = t != null && t != " " && t !in OPS && t != "(" && t != ")"

private fun parseNum(s: String): Double = s.toDoubleOrNull() ?: Double.NaN

private fun fracValue(tok: String): Double {
    val parts = tok.split(FRAC_SLASH)
    return parseNum(parts[0]) / parseNum(parts[1])
}

private fun numericValueOf(tok: String): Double =
    if (isFractionTok(tok)) fracValue(tok) else parseNum(tok)

fun gcd(a: Int, b: Int): Int {
    var x = abs(a); var y = abs(b)
    while (y != 0) { val t = x % y; x = y; y = t }
    return if (x == 0) 1 else x
}

data class Rounded(val sign: Int, val whole: Long, val num: Int, val den: Int, val plus: Boolean)

/**
 * Round to the nearest 1/16, flagging with "+" when the true value sits
 * halfway to the next 1/16 (i.e. the nearest-32nd numerator is odd).
 */
fun toNearest16Plus(value: Double): Rounded {
    var sign = if (value < 0) -1 else 1
    val a = abs(value)
    var whole = floor(a).toLong()
    val rem = a - whole
    var n32 = Math.round(rem * 32).toInt()
    if (n32 == 32) { n32 = 0; whole += 1 }
    val plus = n32 % 2 == 1
    val n16 = if (plus) (n32 - 1) / 2 else n32 / 2
    if (whole == 0L && n16 == 0 && !plus) sign = 1 // avoid showing "−0"
    if (n16 == 0) return Rounded(sign, whole, 0, 1, plus)
    val g = gcd(n16, 16)
    return Rounded(sign, whole, n16 / g, 16 / g, plus)
}

fun trimNumber(n: Double): String {
    if (!n.isFinite()) return "Err"
    var s = String.format(Locale.US, "%.6f", n)
    s = s.trimEnd('0').trimEnd('.')
    if (s == "-0") s = "0"
    return s
}

/**
 * A mixed number ("7" SPACE "1⁄4") must collapse into ONE value (7.25)
 * before operator precedence is applied — otherwise "3 × 7 1/4" would
 * wrongly compute as (3×7) + 1/4 instead of 3 × (7 + 1/4).
 */
internal fun mergeMixedNumbers(toks: List<String>): List<String> {
    val out = mutableListOf<String>()
    var i = 0
    while (i < toks.size) {
        val t = toks[i]
        if (t == " ") {
            val prev = out.lastOrNull()
            val next = toks.getOrNull(i + 1)
            if (isNumTok(prev) && isNumTok(next)) {
                val prevVal = numericValueOf(prev!!)
                val nextMag = abs(numericValueOf(next!!))
                val combined = if (prevVal < 0) prevVal - nextMag else prevVal + nextMag
                out[out.size - 1] = combined.toString()
                i += 2
                continue
            }
            i += 1 // stray space with no number on one side — ignore
            continue
        }
        out.add(if (isFractionTok(t)) numericValueOf(t).toString() else t)
        i += 1
    }
    return out
}

fun evaluateTokens(rawTokens: List<String>): Double? {
    val t = mergeMixedNumbers(rawTokens).toMutableList()
    while (t.isNotEmpty() && (isOp(t.last()) || t.last() == "(")) t.removeAt(t.size - 1)
    if (t.isEmpty()) return null
    var open = 0
    for (tok in t) { if (tok == "(") open++; if (tok == ")") open-- }
    while (open > 0) { t.add(")"); open-- }

    // Shunting-yard to RPN
    val output = mutableListOf<Any>()
    val opStack = ArrayDeque<String>()
    for (tok in t) {
        when {
            isNumTok(tok) -> {
                val v = parseNum(tok)
                if (v.isNaN()) return null
                output.add(v)
            }
            isOp(tok) -> {
                while (opStack.isNotEmpty() && isOp(opStack.last()) &&
                    PRECEDENCE.getValue(opStack.last()) >= PRECEDENCE.getValue(tok)
                ) output.add(opStack.removeLast())
                opStack.addLast(tok)
            }
            tok == "(" -> opStack.addLast(tok)
            tok == ")" -> {
                while (opStack.isNotEmpty() && opStack.last() != "(") output.add(opStack.removeLast())
                opStack.removeLastOrNull()
            }
        }
    }
    while (opStack.isNotEmpty()) output.add(opStack.removeLast())

    val stack = ArrayDeque<Double>()
    for (tok in output) {
        if (tok is Double) { stack.addLast(tok); continue }
        val b = stack.removeLastOrNull() ?: return null
        val a = stack.removeLastOrNull() ?: return null
        val r = when (tok) {
            "+" -> a + b
            "−" -> a - b
            "×" -> a * b
            "÷" -> if (b == 0.0) Double.NaN else a / b
            else -> return null
        }
        stack.addLast(r)
    }
    return if (stack.size == 1) stack.first() else null
}

/** Gauge showing where the exact answer falls between two 1/16 ticks. */
data class Gauge(val lowLabel: String, val highLabel: String, val position: Float)

private fun sixteenthLabel(count16: Int, whole: Long): String {
    if (count16 == 0) return whole.toString()
    if (count16 == 16) return (whole + 1).toString()
    val g = gcd(count16, 16)
    return "${count16 / g}/${16 / g}"
}

fun gaugeFor(value: Double): Gauge? {
    val a = abs(value)
    val whole = floor(a).toLong()
    val sixteenths = (a - whole) * 16
    if (abs(sixteenths - sixteenths.roundToLong()) < 1e-6) return null
    var lower16 = floor(sixteenths + 1e-9).toInt()
    if (lower16 >= 16) lower16 = 15
    val position = (sixteenths - lower16).coerceIn(0.0, 1.0).toFloat()
    return Gauge(sixteenthLabel(lower16, whole), sixteenthLabel(lower16 + 1, whole), position)
}

/** Immutable calculator state; every key returns a new state. */
data class CalcState(
    val tokens: List<String> = emptyList(),
    val entering: Boolean = false,   // last token is a plain number being typed
    val hasResult: Boolean = false,  // display shows an evaluated answer
    val lastResult: Double? = null,  // exact (unrounded) last answer
) {
    private val last get() = tokens.lastOrNull()
    private fun replaceLast(v: String) = tokens.dropLast(1) + v

    private fun startFresh() =
        if (hasResult) copy(tokens = emptyList(), entering = false, hasResult = false) else this

    fun digit(d: String): CalcState {
        val s = startFresh()
        return if (s.entering) s.copy(tokens = s.replaceLast(s.last + d))
        else s.copy(tokens = s.tokens + d, entering = true)
    }

    fun dot(): CalcState {
        val s = startFresh()
        return if (s.entering) {
            if (s.last!!.contains('.')) s else s.copy(tokens = s.replaceLast(s.last + "."))
        } else s.copy(tokens = s.tokens + "0.", entering = true)
    }

    fun op(op: String): CalcState {
        var s = this
        if (hasResult && lastResult != null) {
            // continue the calculation from the exact previous answer
            s = copy(tokens = listOf(trimNumber(lastResult)), hasResult = false, entering = false)
        }
        if (s.tokens.isEmpty()) {
            return if (op == "−") s.copy(tokens = listOf("-"), entering = true) else s
        }
        val l = s.last
        val toks = when {
            isOp(l) -> s.replaceLast(op)
            l == " " || l == "(" -> return s
            else -> s.tokens + op
        }
        return s.copy(tokens = toks, entering = false)
    }

    fun space(): CalcState {
        val s = startFresh()
        val l = s.last
        if (l == null || l == " " || isOp(l) || l == "(") return s
        return s.copy(tokens = s.tokens + " ", entering = false)
    }

    fun lparen(): CalcState {
        val s = startFresh()
        val l = s.last
        val implicitTimes = l != null && (isNumTok(l) || l == ")")
        return s.copy(tokens = s.tokens + (if (implicitTimes) listOf("×", "(") else listOf("(")), entering = false)
    }

    fun rparen(): CalcState {
        val s = startFresh()
        val open = s.tokens.count { it == "(" } - s.tokens.count { it == ")" }
        val l = s.last
        if (open <= 0 || l == "(" || l == " " || isOp(l)) return s
        return s.copy(tokens = s.tokens + ")", entering = false)
    }

    fun backspace(): CalcState {
        if (hasResult) return copy(hasResult = false) // reveal the expression again for editing
        val l = last ?: return this
        if (entering && isNumTok(l) && !isFractionTok(l) && l.length > 1) {
            return copy(tokens = replaceLast(l.dropLast(1)))
        }
        val toks = tokens.dropLast(1)
        val nl = toks.lastOrNull()
        return copy(tokens = toks, entering = isNumTok(nl) && !isFractionTok(nl))
    }

    fun clear() = CalcState()

    fun toggleSign(): CalcState {
        val s = startFresh()
        val l = s.last
        return when {
            isNumTok(l) && !isFractionTok(l) ->
                s.copy(tokens = s.replaceLast(if (l!!.startsWith("-")) l.drop(1) else "-$l"))
            l == null || isOp(l) || l == "(" -> s.copy(tokens = s.tokens + "-", entering = true)
            else -> s
        }
    }

    fun denom(d: Int): CalcState {
        if (!entering || hasResult) return this
        val l = last
        if (!isNumTok(l) || isFractionTok(l) || l == "" || l == "-" || l == ".") return this
        return copy(tokens = replaceLast("$l$FRAC_SLASH$d"), entering = false)
    }

    fun equals(): CalcState {
        val v = evaluateTokens(tokens) ?: return this
        if (!v.isFinite()) return this
        return copy(lastResult = v, hasResult = true)
    }
}
