package com.sixteenthplus.calc

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/*
 * Pure-Kotlin port of the Sixteenth+ (originally "Shop Calc") engine from cabinet_calc.html.
 *
 * The expression is plain text that tokenizes into:
 *  - plain numbers, e.g. "24", "-3", "0.5" (ASCII "-" is a sign, never an operator)
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
fun isPlainNum(t: String?) = isNumTok(t) && !isFractionTok(t)

// A fraction's denominator may be empty ("3⁄") while a custom one is being typed.
private val TOKEN = Regex("-?[0-9.]*$FRAC_SLASH[0-9]*|-?[0-9.]+|-|[+−×÷() ]")

/** Split expression text into tokens. Tokens are contiguous: their lengths sum to text.length. */
fun tokenize(text: String): List<String> = TOKEN.findAll(text).map { it.value }.toList()

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
                val prevVal = if (prev == "-") 0.0 else numericValueOf(prev!!) // "- 3⁄4": sign before a bare fraction
                val nextMag = abs(numericValueOf(next!!))
                val combined = if (prev!!.startsWith("-")) prevVal - nextMag else prevVal + nextMag
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
    val t = mutableListOf<String>()
    for (tok in mergeMixedNumbers(rawTokens)) {
        if (tok == "(" && t.lastOrNull() == "-") t[t.size - 1] = "-1" // "-(…)" is -1 × (…)
        val p = t.lastOrNull()
        val leftDone = p == ")" || (isNumTok(p) && p != "-")
        if ((tok == "(" && leftDone) || (p == ")" && isNumTok(tok))) t.add("×") // 2(3), (2)3, (2)(3)
        t.add(tok)
    }
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

/** An unfinished fraction: "7 3⁄" (empty denominator), "7 3" or "7 " (nothing picked after a b/c). */
fun missingDenominator(tokens: List<String>) = tokens.withIndex().any { (i, t) ->
    (isFractionTok(t) && t.endsWith(FRAC_SLASH)) || (t == " " && !isFractionTok(tokens.getOrNull(i + 1)))
}

/** Keep the cursor in range and out of the middle of a fraction token (fractions are atomic). */
private fun snap(text: String, c: Int): Int {
    val cc = c.coerceIn(0, text.length)
    var start = 0
    for (tok in tokenize(text)) {
        val end = start + tok.length
        if (isFractionTok(tok) && cc > start && cc < end) return end
        start = end
    }
    return cc
}

/**
 * Immutable calculator state; every key returns a new state.
 * [text] is the expression and [cursor] a char index into it; keys edit at the cursor.
 */
data class CalcState(
    val text: String = "",
    val cursor: Int = 0,
    val hasResult: Boolean = false,  // display shows an evaluated answer
    val lastResult: Double? = null,  // exact (unrounded) last answer
    val error: String? = null,       // short message from "=", cleared by the UI on the next key
) {
    val tokens: List<String> get() = tokenize(text)
    private val before get() = text.substring(0, cursor)
    private val after get() = text.substring(cursor)
    /** Token left / right of the cursor (only part of it when the cursor is inside a number). */
    private val prev get() = tokenize(before).lastOrNull()
    private val next get() = tokenize(after).firstOrNull()

    private fun edit(t: String, c: Int) = copy(text = t, cursor = snap(t, c))
    private fun insert(s: String) = edit(before + s + after, cursor + s.length)
    private fun startFresh() = if (hasResult) copy(text = "", cursor = 0, hasResult = false) else this

    fun moveCursor(to: Int) = copy(cursor = snap(text, to), hasResult = false)

    /** Right after a fraction, digits extend its denominator (custom denominators like 3/32). */
    fun digit(d: String): CalcState {
        val s = startFresh()
        return if (isFractionTok(s.next)) s else s.insert(d)
    }

    fun dot(): CalcState {
        val s = startFresh()
        val p = s.prev.takeIf(::isPlainNum) ?: ""
        val n = s.next?.takeIf { isPlainNum(it) && !it.startsWith("-") } ?: ""
        if (isFractionTok(s.prev) || isFractionTok(s.next) || '.' in p + n) return s
        return s.insert(if (p.any(Char::isDigit)) "." else "0.")
    }

    fun op(op: String): CalcState {
        val s = if (hasResult && lastResult != null) {
            val t = trimNumber(lastResult) // continue from the previous answer
            copy(text = t, cursor = t.length, hasResult = false)
        } else this
        val p = s.prev
        return when {
            p == null -> if (op == "−") s.insert("-") else s // leading minus is a sign
            isOp(p) -> s.edit(s.before.dropLast(1) + op + s.after, s.cursor)
            p == " " || p == "(" || p == "-" -> s
            isOp(s.next) -> s.edit(s.before + op + s.after.drop(1), s.cursor + 1)
            else -> s.insert(op)
        }
    }

    /** True when the next "a b/c" press opens the denominator: the cursor ends a typed numerator ("7 3|"). */
    val fracKeyOpensDenominator: Boolean
        get() {
            if (hasResult) return false
            val toks = tokenize(before)
            val p = toks.lastOrNull()
            return isPlainNum(p) && p!!.any(Char::isDigit) && toks.getOrNull(toks.size - 2) == " " && !isNumTok(next)
        }

    /** False when pressing "a b/c" would do nothing, so the UI can disable the key. */
    val fracKeyEnabled: Boolean get() = space() != this

    /** True when the fraction the "a b/c" key starts or edits has a whole number in front ("7 3⁄4", not "3⁄4"). */
    val fracKeyHasWhole: Boolean
        get() {
            val toks = tokenize(startFresh().before)
            var i = toks.size - 1
            if (i >= 1 && toks[i - 1] == " " && isNumTok(toks[i])) i-- // skip a numerator or fraction after a space
            if (i >= 0 && toks[i] == " ") i--
            return isNumTok(toks.getOrNull(i))
        }

    /**
     * The "a b/c" key: after a number it starts the fraction part ("7 "), where no number can attach
     * (start, after an operator or "(") it starts a bare fraction (" ", evaluated as just the fraction),
     * and inside a typed numerator it opens the denominator ("7 3⁄"). Otherwise it returns this unchanged.
     */
    fun space(): CalcState {
        val s = startFresh()
        if (s.fracKeyOpensDenominator) return s.insert(FRAC_SLASH)
        val p = s.prev
        val n = s.next
        if ((p == null || isOp(p) || p == "(") && !isNumTok(n) && n != " ") return s.insert(" ")
        if (p == null || p == " " || p == "(" || p == "-" || isOp(p) || isFractionTok(p) || n == " ") return this
        return s.insert(" ")
    }

    fun lparen() = startFresh().insert("(")

    fun rparen(): CalcState {
        val s = startFresh()
        val open = s.before.count { it == '(' } - s.before.count { it == ')' }
        val p = s.prev
        if (open <= 0 || p == null || p == "(" || p == " " || p == "-" || isOp(p)) return s
        return s.insert(")")
    }

    fun backspace(): CalcState {
        if (hasResult) return copy(hasResult = false) // hide the answer, back to editing
        if (cursor == 0) return this
        return edit(before.dropLast(1) + after, cursor - 1) // one char, so a denominator can be fixed
    }

    fun clear() = CalcState()

    fun toggleSign(): CalcState {
        val s = startFresh()
        val p = s.prev
        val n = s.next
        val (start, end) = when {
            isNumTok(p) -> {
                val sameNumber = isPlainNum(p) && isPlainNum(n) && !n!!.startsWith("-")
                s.cursor - p!!.length to s.cursor + (if (sameNumber) n!!.length else 0)
            }
            isNumTok(n) && p != " " -> s.cursor to s.cursor + n!!.length
            p == null || isOp(p) || p == "(" -> return s.insert("-")
            else -> return s
        }
        val tok = s.text.substring(start, end)
        val flipped = if (tok.startsWith("-")) tok.drop(1) else "-$tok"
        return s.edit(s.text.replaceRange(start, end, flipped), start + flipped.length)
    }

    fun denom(d: Int): CalcState {
        val p = prev
        if (hasResult || isNumTok(next)) return this
        if (isFractionTok(p)) { // replace the denominator (typed or preset)
            val old = p!!.substringAfter(FRAC_SLASH)
            return edit(before.dropLast(old.length) + d + after, cursor - old.length + d.toString().length)
        }
        if (!isPlainNum(p) || p!!.none(Char::isDigit)) return this
        return insert("$FRAC_SLASH$d")
    }

    fun equals(): CalcState {
        if (missingDenominator(tokens)) return copy(error = "Enter a denominator")
        val v = evaluateTokens(tokens) ?: return this
        if (!v.isFinite()) return copy(error = "Can't divide by zero") // ÷ 0 and x/0 are the only ways to get here
        return copy(lastResult = v, hasResult = true)
    }
}
