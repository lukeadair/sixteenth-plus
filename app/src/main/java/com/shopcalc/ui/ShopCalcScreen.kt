package com.shopcalc.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopcalc.calc.CalcState
import com.shopcalc.calc.FRAC_SLASH
import com.shopcalc.calc.gaugeFor
import com.shopcalc.calc.isFractionTok
import com.shopcalc.calc.isOp
import com.shopcalc.calc.toNearest16Plus

private val CalcStateSaver = listSaver<CalcState, Any?>(
    save = { listOf(ArrayList(it.tokens), it.entering, it.hasResult, it.lastResult) },
    restore = {
        @Suppress("UNCHECKED_CAST")
        CalcState(it[0] as List<String>, it[1] as Boolean, it[2] as Boolean, it[3] as Double?)
    },
)

@Composable
fun ShopCalcScreen() {
    var state by rememberSaveable(stateSaver = CalcStateSaver) { mutableStateOf(CalcState()) }
    val update: (CalcState.() -> CalcState) -> Unit = { f -> state = state.f() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Housing)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxHeight()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Brand()
            Display(state, Modifier.weight(1f).fillMaxWidth())
            TapeRow(onDenom = { d -> update { denom(d) } })
            Keypad(update)
        }
    }
}

@Composable
private fun Brand() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicText(
            "SIXTEENTH+",
            style = TextStyle(Palette.DisplayDim, 12.sp, FontWeight.SemiBold, fontFamily = Oswald, letterSpacing = 3.sp),
        )
        BasicText(
            "± 1/16+",
            style = TextStyle(Palette.TapeYellow, 12.sp, FontWeight.SemiBold, fontFamily = Oswald, letterSpacing = 2.sp),
        )
    }
}

// ---------------------------------------------------------------- display

private val ExprSize = 28.sp

@Composable
private fun Display(state: CalcState, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val scroll = rememberScrollState()
    LaunchedEffect(state.tokens, state.hasResult) { scroll.animateScrollTo(scroll.maxValue) }

    Box(
        modifier
            .heightIn(min = 110.dp)
            .clip(shape)
            .background(Palette.DisplayBg)
            .border(1.dp, Palette.Line, shape)
            .drawBehind {
                // inset shadow along the top edge
                drawRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                        endY = 10.dp.toPx(),
                    )
                )
                // tape-measure tick strip
                val step = 8.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawRect(Palette.TapeYellow.copy(alpha = 0.35f), Offset(x, 0f), Size(1.dp.toPx(), 3.dp.toPx()))
                    x += step
                }
            }
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.BottomStart,
            ) {
                Box(Modifier.verticalScroll(scroll)) {
                    when {
                        state.hasResult && state.lastResult != null -> AnswerExpr(state.lastResult)
                        state.tokens.isEmpty() -> BasicText(
                            "Type a sequence, then press =",
                            style = TextStyle(Palette.DisplayDim, 16.sp, fontFamily = JetBrainsMono),
                        )
                        else -> TokenExpr(state.tokens)
                    }
                }
            }
            if (state.hasResult && state.lastResult != null) {
                gaugeFor(state.lastResult)?.let { GaugeView(it.lowLabel, it.highLabel, it.position) }
            }
        }
    }
}

private fun exprStyle(color: Color, weight: FontWeight) =
    TextStyle(color, ExprSize, weight, fontFamily = JetBrainsMono, letterSpacing = 0.3.sp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TokenExpr(tokens: List<String>) {
    val style = exprStyle(Palette.DisplayFg, FontWeight.Medium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        tokens.forEach { tok ->
            val m = Modifier.align(Alignment.CenterVertically)
            when {
                tok == " " -> Spacer(m.width(16.dp).height(1.dp))
                isFractionTok(tok) -> {
                    val (n, d) = tok.split(FRAC_SLASH)
                    Fraction(n, d, style, m)
                }
                isOp(tok) -> BasicText(tok, m.padding(horizontal = 3.dp), style.copy(color = Palette.TapeYellow))
                else -> BasicText(tok, m, style)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerExpr(value: Double) {
    val f = toNearest16Plus(value)
    val style = exprStyle(Palette.TapeYellow, FontWeight.Bold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val m = Modifier.align(Alignment.CenterVertically)
        if (f.sign < 0) BasicText("−", m, style)
        if (f.num == 0) {
            BasicText(f.whole.toString(), m, style)
        } else {
            if (f.whole != 0L) BasicText(f.whole.toString(), m.padding(end = 8.dp), style)
            Fraction(f.num.toString(), f.den.toString(), style, m)
        }
        if (f.plus) BasicText("+", m.padding(start = 3.dp), style)
    }
}

@Composable
private fun Fraction(num: String, den: String, base: TextStyle, modifier: Modifier = Modifier) {
    val small = base.copy(fontSize = base.fontSize * 0.56f, lineHeight = base.fontSize * 0.6f)
    val color = base.color
    Column(
        modifier.padding(horizontal = 3.dp).offset(y = (-2).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            num,
            Modifier
                .drawBehind {
                    val h = 2.dp.toPx()
                    drawRect(color, Offset(0f, size.height - h), Size(size.width, h))
                }
                .padding(start = 4.dp, end = 4.dp, bottom = 3.dp),
            small,
        )
        BasicText(den, Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp), small)
    }
}

@Composable
private fun GaugeView(low: String, high: String, position: Float) {
    Column(Modifier.width(52.dp)) {
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val w = size.width
            drawRect(Palette.Line, Offset(0f, 9.dp.toPx()), Size(w, 2.dp.toPx()))
            val tickW = 1.5.dp.toPx()
            drawRect(Palette.DisplayDim, Offset(0f, 3.dp.toPx()), Size(tickW, 13.dp.toPx()))
            drawRect(Palette.DisplayDim, Offset(w - tickW, 3.dp.toPx()), Size(tickW, 13.dp.toPx()))
            val mw = 2.dp.toPx()
            drawRect(
                Palette.TapeYellow,
                Offset((position * w - mw / 2).coerceIn(0f, w - mw), 1.dp.toPx()),
                Size(mw, 17.dp.toPx()),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val s = TextStyle(Palette.DisplayDim, 9.sp, fontFamily = JetBrainsMono)
            BasicText(low, style = s)
            BasicText(high, style = s)
        }
    }
}

// ---------------------------------------------------------------- keys

/**
 * A physical-feeling key: a face sitting on a darker bottom "lip". When
 * pressed, the face drops 2dp and the lip shrinks, like the CSS original.
 */
@Composable
private fun PressKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    face: Color,
    lip: Color,
    lipHeight: Dp = 3.dp,
    radius: Dp = 8.dp,
    outline: Color? = null,
    dashed: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    val shape = RoundedCornerShape(radius)
    val drop = if (pressed && lipHeight > 1.dp) 2.dp else 0.dp
    val lipNow = if (pressed && lipHeight > 1.dp) 1.dp else lipHeight

    Box(
        modifier.clickable(interaction, indication = null) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = drop)
                .clip(shape)
                .background(lip)
                .padding(bottom = lipNow)
                .clip(shape)
                .background(face)
                .then(
                    if (outline == null) Modifier
                    else Modifier.drawBehind {
                        val sw = 1.dp.toPx()
                        val r = radius.toPx()
                        drawRoundRect(
                            outline,
                            topLeft = Offset(sw / 2, sw / 2),
                            size = Size(size.width - sw, size.height - sw),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                            style = Stroke(
                                sw,
                                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
                            ),
                        )
                    }
                ),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
private fun TapeRow(onDenom: (Int) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(shape)
                .background(Palette.Panel)
                .border(1.dp, Palette.Line, shape)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            listOf(16, 8, 4, 2).forEach { d ->
                PressKey(
                    onClick = { onDenom(d) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    face = Palette.Housing,
                    lip = Palette.TapeYellowDim,
                    radius = 6.dp,
                ) {
                    BasicText(
                        "/$d",
                        style = TextStyle(Palette.TapeYellow, 17.sp, FontWeight.Bold, fontFamily = JetBrainsMono),
                    )
                    // ruler ticks along the bottom edge
                    Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)) {
                        var x = 0f
                        val on = 2.dp.toPx(); val step = 6.dp.toPx()
                        while (x < size.width) {
                            drawRect(Palette.TapeYellow.copy(alpha = 0.5f), Offset(x, 0f), Size(on, size.height))
                            x += step
                        }
                    }
                }
            }
        }
        BasicText(
            "PRESET DENOMINATOR",
            Modifier
                .padding(start = 12.dp)
                .background(Palette.Housing)
                .padding(horizontal = 6.dp),
            TextStyle(Palette.DisplayDim, 9.sp, fontFamily = Oswald, letterSpacing = 2.sp),
        )
    }
}

private enum class KeyKind { Num, Op, Paren, Clear, Func, Space, Equals }

@Composable
private fun RowScope.Key(
    label: String,
    kind: KeyKind,
    span: Int = 1,
    onClick: () -> Unit,
) {
    val face = when (kind) {
        KeyKind.Op, KeyKind.Paren -> Palette.KeyOpBg
        KeyKind.Equals -> Palette.TapeYellow
        KeyKind.Func -> Palette.Housing
        KeyKind.Space -> Palette.Panel
        else -> Palette.PanelRaised
    }
    val lip = if (kind == KeyKind.Equals) Palette.TapeYellowDim else Palette.Line
    val fg = when (kind) {
        KeyKind.Op -> Palette.TapeYellow
        KeyKind.Equals -> Palette.Housing
        KeyKind.Clear -> Palette.Danger
        KeyKind.Func, KeyKind.Space -> Palette.DisplayDim
        else -> Palette.KeyFg
    }
    val (size, weight, spacing) = when (kind) {
        KeyKind.Paren -> Triple(19.sp, FontWeight.SemiBold, 0.sp)
        KeyKind.Op -> Triple(32.sp, FontWeight.SemiBold, 0.sp)
        KeyKind.Equals -> Triple(34.sp, FontWeight.Bold, 0.sp)
        KeyKind.Func -> Triple(18.sp, FontWeight.Normal, 1.sp)
        KeyKind.Space -> Triple(13.sp, FontWeight.Medium, 2.sp)
        else -> Triple(22.sp, FontWeight.Medium, 0.sp)
    }
    // Two weight-2 keys line up with four weight-1 keys plus their gaps.
    PressKey(
        onClick = onClick,
        modifier = Modifier.weight(span.toFloat()).fillMaxHeight(),
        face = face,
        lip = lip,
        lipHeight = if (kind == KeyKind.Func || kind == KeyKind.Space) 1.dp else 3.dp,
        outline = when (kind) { KeyKind.Func, KeyKind.Space -> Palette.Line; else -> null },
        dashed = kind == KeyKind.Space,
    ) {
        val family = if (kind == KeyKind.Func) FontFamily.Default else Oswald
        BasicText(label, style = TextStyle(fg, size, weight, fontFamily = family, letterSpacing = spacing, textAlign = TextAlign.Center))
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(62.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), content = content)
}

@Composable
private fun Keypad(update: (CalcState.() -> CalcState) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        KeyRow {
            Key("(", KeyKind.Paren) { update { lparen() } }
            Key(")", KeyKind.Paren) { update { rparen() } }
            Key("C", KeyKind.Clear) { update { clear() } }
            Key("⌫", KeyKind.Func) { update { backspace() } }
        }
        listOf(
            listOf("7", "8", "9") to "÷",
            listOf("4", "5", "6") to "×",
            listOf("1", "2", "3") to "−",
        ).forEach { (digits, opSym) ->
            KeyRow {
                digits.forEach { d -> Key(d, KeyKind.Num) { update { digit(d) } } }
                Key(opSym, KeyKind.Op) { update { op(opSym) } }
            }
        }
        KeyRow {
            Key("±", KeyKind.Num) { update { toggleSign() } }
            Key("0", KeyKind.Num) { update { digit("0") } }
            Key(".", KeyKind.Num) { update { dot() } }
            Key("+", KeyKind.Op) { update { op("+") } }
        }
        KeyRow {
            Key("SPACE", KeyKind.Space, span = 2) { update { space() } }
            Key("=", KeyKind.Equals, span = 2) { update { equals() } }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PreviewShopCalc() = ShopCalcScreen()
