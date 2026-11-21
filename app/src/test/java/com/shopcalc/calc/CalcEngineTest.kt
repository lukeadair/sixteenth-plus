package com.shopcalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcEngineTest {

    /** Tiny key-script DSL: "7 sp 1 /4 × 3 =" */
    private fun press(script: String): CalcState =
        script.split(" ").filter { it.isNotEmpty() }.fold(CalcState()) { s, k ->
            when {
                k.all { it.isDigit() } -> k.fold(s) { acc, c -> acc.digit(c.toString()) }
                k.startsWith("/") -> s.denom(k.drop(1).toInt())
                k == "sp" -> s.space()
                k == "." -> s.dot()
                k == "(" -> s.lparen()
                k == ")" -> s.rparen()
                k == "=" -> s.equals()
                k == "±" -> s.toggleSign()
                k == "bk" -> s.backspace()
                k == "C" -> s.clear()
                k in OPS -> s.op(k)
                else -> error("unknown key $k")
            }
        }

    private fun result(script: String) = press(script).also { assertTrue(it.hasResult) }.lastResult!!

    @Test fun simpleArithmetic() {
        assertEquals(7.0, result("3 + 4 ="), 1e-9)
        assertEquals(14.0, result("2 + 3 × 4 ="), 1e-9)
        assertEquals(20.0, result("( 2 + 3 ) × 4 ="), 1e-9)
    }

    @Test fun mixedNumberBindsBeforePrecedence() {
        // 3 × 7 1/4 = 21.75, not 21.25
        assertEquals(21.75, result("3 × 7 sp 1 /4 ="), 1e-9)
    }

    @Test fun negativeMixedNumber() {
        assertEquals(-7.25, result("7 ± sp 1 /4 ="), 1e-9)
    }

    @Test fun cabinetWidthExample() {
        // 36" opening minus two 3/4" sides, split in half
        assertEquals(17.25, result("( 36 − 3 /4 × 2 ) ÷ 2 ="), 1e-9)
    }

    @Test fun implicitTimesBeforeParen() {
        assertEquals(10.0, result("2 ( 3 + 2 ="), 1e-9) // auto-closes paren
    }

    @Test fun implicitTimesAfterParen() {
        assertEquals(25.0, result("( 2 + 3 ) 5 ="), 1e-9)
        assertEquals(2.5, result("( 2 + 3 ) . 5 ="), 1e-9)
    }

    @Test fun continueFromResult() {
        val s = press("10 ÷ 4 =").op("+").digit("1").equals()
        assertEquals(3.5, s.lastResult!!, 1e-9)
    }

    @Test fun divideByZeroIgnored() {
        val s = press("5 ÷ 0 =")
        assertFalse(s.hasResult)
    }

    @Test fun backspaceRevealsExpression() {
        val s = press("12 + 3 = bk")
        assertFalse(s.hasResult)
        assertEquals(listOf("12", "+", "3"), s.tokens)
    }

    @Test fun denomBuildsFraction() {
        assertEquals(listOf("3${FRAC_SLASH}16"), press("3 /16").tokens)
        assertEquals(emptyList<String>(), press("/16").tokens)
    }

    @Test fun roundingToSixteenths() {
        assertEquals(Rounded(1, 17, 1, 4, false), toNearest16Plus(17.25))
        // 1/32 above 1/16 → shows 1/16+
        assertEquals(Rounded(1, 0, 1, 16, true), toNearest16Plus(3.0 / 32))
        assertEquals(Rounded(1, 3, 0, 1, false), toNearest16Plus(2.999))
        assertEquals(Rounded(-1, 1, 1, 2, false), toNearest16Plus(-1.5))
        assertEquals(Rounded(1, 0, 0, 1, false), toNearest16Plus(-0.001))
    }

    @Test fun gauge() {
        assertNull(gaugeFor(1.25))
        val g = gaugeFor(1.0 + 1.0 / 3)!! // 5.33/16
        assertEquals("5/16", g.lowLabel)
        assertEquals("3/8", g.highLabel)
        assertEquals(0.333f, g.position, 0.01f)
        assertEquals("2", gaugeFor(1.99)!!.highLabel)
    }

    @Test fun trimNumberFormatting() {
        assertEquals("2.5", trimNumber(2.5))
        assertEquals("3", trimNumber(3.0))
        assertEquals("0.333333", trimNumber(1.0 / 3))
    }
}
