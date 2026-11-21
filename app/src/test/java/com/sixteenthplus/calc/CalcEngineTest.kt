package com.sixteenthplus.calc

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

    @Test fun divideByZeroShowsError() {
        for (keys in listOf("5 ÷ 0 =", "3 /16 ÷ 0 =", "7 sp 3 sp 0 =")) {
            val s = press(keys)
            assertFalse(keys, s.hasResult)
            assertEquals(keys, "Can't divide by zero", s.error)
        }
    }

    @Test fun signBeforeBareFraction() {
        // cursor moved to the start of " 3⁄4", then − or ±
        assertEquals(-0.75, press("sp 3 /4").moveCursor(0).op("−").equals().lastResult!!, 1e-9)
        assertEquals(-0.75, press("sp 3 /4").moveCursor(0).toggleSign().equals().lastResult!!, 1e-9)
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

    @Test fun cursorInsertsMidExpression() {
        val s = press("12 + 3").moveCursor(2).digit("5")
        assertEquals("125+3", s.text)
        assertEquals(3, s.cursor)
        assertEquals(128.0, s.equals().lastResult!!, 1e-9)
    }

    @Test fun backspaceAtCursor() {
        assertEquals("1+3", press("12 + 3").moveCursor(2).backspace().text)
    }

    @Test fun fractionEditing() {
        val s = press("3 /16 + 1")
        assertEquals(4, s.moveCursor(2).cursor) // inside "3⁄16" snaps to its end
        assertEquals("3${FRAC_SLASH}1+1", s.moveCursor(4).backspace().text) // one denominator digit
        assertEquals("3${FRAC_SLASH}165+1", s.moveCursor(4).digit("5").text) // extends the denominator
        assertEquals(s.text, s.moveCursor(0).digit("5").text) // no digits glued onto a numerator
    }

    @Test fun customDenominator() {
        // a b/c twice opens the denominator: 7 3/32
        assertEquals(7.09375, result("7 sp 3 sp 32 ="), 1e-9)
        // a preset key replaces whatever denominator is there
        assertEquals(7.375, result("7 sp 3 sp 32 /8 ="), 1e-9)
        // an empty denominator can't be evaluated
        assertFalse(press("7 sp 3 sp =").hasResult)
    }

    @Test fun fracKeyStep() {
        assertFalse(press("7").fracKeyOpensDenominator)
        assertFalse(press("7 sp").fracKeyOpensDenominator) // numerator not typed yet
        assertTrue(press("7 sp 3").fracKeyOpensDenominator)
        assertFalse(press("7 sp 3 sp").fracKeyOpensDenominator) // already in the denominator
    }

    @Test fun fracKeyEnabled() {
        for (keys in listOf("", "7", "7 +", "(", "7 =", "7 sp 3", "( 2 + 3 )")) {
            assertTrue(keys, press(keys).fracKeyEnabled)
        }
        for (keys in listOf("7 sp", "7 sp 3 sp", "7 sp 3 /4", "sp")) assertFalse(keys, press(keys).fracKeyEnabled)
    }

    @Test fun bareFraction() {
        assertEquals(0.09375, result("sp 3 sp 32 ="), 1e-9) // 3/32 with no whole number
        assertEquals(7.75, result("7 + sp 3 /4 ="), 1e-9)
        assertEquals("Enter a denominator", press("sp 3 =").error)
    }

    @Test fun negativeBareFraction() {
        assertTrue(press("±").fracKeyEnabled)
        assertEquals(-0.09375, result("± sp 3 sp 32 ="), 1e-9) // −3/32
        assertEquals(6.25, result("7 + ± sp 3 /4 ="), 1e-9) // 7 + (−3/4)
        assertEquals(" 3⁄4", press("± sp 3 /4 ±").text) // ± drops the lone sign, no "- -3⁄4"
        assertEquals(0.75, result("± sp 3 /4 ± ="), 1e-9)
        assertEquals(-0.75, result("± sp 3 /4 ± ± ="), 1e-9)
        assertEquals(" 3", press("± sp 3 ±").text) // also on a typed numerator
    }

    @Test fun fracKeyHasWhole() {
        for (keys in listOf("7", "7 sp", "7 sp 3", "7 sp 3 sp", "± 7")) assertTrue(keys, press(keys).fracKeyHasWhole)
        for (keys in listOf("", "7 +", "sp", "sp 3", "sp 3 sp", "7 =", "±", "± sp", "± sp 3")) {
            assertFalse(keys, press(keys).fracKeyHasWhole)
        }
    }

    @Test fun missingDenominatorError() {
        for (keys in listOf("7 sp 3 sp =", "7 sp 3 =", "7 sp =", "7 sp 3 + 1 =")) {
            val s = press(keys)
            assertFalse(keys, s.hasResult)
            assertEquals(keys, "Enter a denominator", s.error)
        }
        assertNull(press("7 sp 3 /4 =").error)
    }

    @Test fun toggleSignAtCursor() {
        assertEquals("2×-3", press("2 × 3").moveCursor(2).toggleSign().text)
    }

    @Test fun negatedParen() {
        assertEquals(-5.0, result("± ( 2 + 3 ="), 1e-9)
    }
}
