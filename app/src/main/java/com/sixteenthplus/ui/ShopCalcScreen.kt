package com.sixteenthplus.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalView
import com.sixteenthplus.calc.isNumTok
import com.sixteenthplus.calc.isPlainNum
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sixteenthplus.calc.CalcState
import com.sixteenthplus.calc.FRAC_SLASH
import com.sixteenthplus.calc.gaugeFor
import com.sixteenthplus.calc.isFractionTok
import com.sixteenthplus.calc.isOp
import com.sixteenthplus.calc.toNearest16Plus
import com.sixteenthplus.calc.tokenize
import java.util.Locale

private val CalcStateSaver = listSaver<CalcState, Any?>(
    save = { listOf(it.text, it.cursor, it.hasResult, it.lastResult) },
    restore = { CalcState(it[0] as String, it[1] as Int, it[2] as Boolean, it[3] as Double?) },
)

@Composable
fun ShopCalcScreen() {
    var state by rememberSaveable(stateSaver = CalcStateSaver) { mutableStateOf(CalcState()) }
    val update: (CalcState.() -> CalcState) -> Unit = { f -> state = state.copy(error = null).f() }

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
            Display(state, onCursor = { c -> update { moveCursor(c) } }, Modifier.weight(1f).fillMaxWidth())
            Keypad(update, state)
            TapeRow(onDenom = { d -> update { denom(d) } }) // bottom: easiest thumb reach, next to a b/c
        }
    }
}

@Composable
private fun Brand() {
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
    var showVersion by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicText(
            if (showVersion) "SIXTEENTH+  v$version" else "SIXTEENTH+",
            Modifier.clickable(interactionSource = null, indication = null, onClickLabel = "Toggle version") {
                showVersion = !showVersion
            },
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
private val AnswerSize = 36.sp

/** Expression on top (editable, with cursor); answer bottom-right with its gauge; mm bottom-left. */
@Composable
private fun Display(state: CalcState, onCursor: (Int) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val scroll = rememberScrollState()
    LaunchedEffect(state.text) {
        if (state.cursor == state.text.length) scroll.animateScrollTo(scroll.maxValue)
    }
    val result = state.lastResult.takeIf { state.hasResult }

    Column(
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            if (state.text.isEmpty()) {
                BasicText(
                    "Type a sequence, then press =",
                    style = TextStyle(Palette.DisplayDim, 16.sp, fontFamily = JetBrainsMono),
                )
            } else {
                ExprEditor(state, onCursor)
            }
        }
        state.error?.let { BasicText(it, style = TextStyle(Palette.Danger, 13.sp, fontFamily = JetBrainsMono)) }
        if (result != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                BasicText(
                    String.format(Locale.US, "%.1f mm", result * 25.4),
                    Modifier.weight(1f),
                    TextStyle(Palette.DisplayDim, 13.sp, fontFamily = JetBrainsMono),
                )
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnswerExpr(result)
                    gaugeFor(result)?.let { GaugeView(it.lowLabel, it.highLabel, it.position) }
                }
            }
        }
    }
}

private fun exprStyle(color: Color, weight: FontWeight) =
    TextStyle(color, ExprSize, weight, fontFamily = JetBrainsMono, letterSpacing = 0.3.sp)

/** Display chars [dStart, dStart+dLen) show engine chars [eStart, eStart+eLen); atomic = cursor can't go inside. */
private class Seg(val dStart: Int, val dLen: Int, val eStart: Int, val eLen: Int, val atomic: Boolean)

/** The expression as one annotated string (fractions are inline stacked boxes) plus cursor mapping. */
private class Expr(
    val text: AnnotatedString,
    val inline: Map<String, InlineTextContent>,
    private val segs: List<Seg>,
    val pendingActive: Boolean, // cursor is in a pending fraction, which draws its own caret
) {
    fun toDisplay(e: Int): Int {
        for (s in segs) {
            if (e <= s.eStart) return s.dStart
            if (e < s.eStart + s.eLen) return s.dStart + if (s.atomic) s.dLen else e - s.eStart
        }
        return text.length
    }

    fun toEngine(d: Int): Int {
        for (s in segs) {
            if (d < s.dStart + s.dLen) {
                val k = d - s.dStart
                return when {
                    !s.atomic -> s.eStart + k
                    k * 2 <= s.dLen -> s.eStart
                    else -> s.eStart + s.eLen
                }
            }
        }
        return segs.lastOrNull()?.let { it.eStart + it.eLen } ?: 0
    }
}

/**
 * A space starts the fraction part of a mixed number, so what follows it is shown as a
 * pending fraction with dashed boxes until a denominator key turns it into a real one.
 * [caretOn] blinks the caret inside the pending fraction when the cursor is there.
 */
private fun buildExpr(text: String, cursor: Int, style: TextStyle, caretOn: () -> Boolean): Expr {
    val segs = mutableListOf<Seg>()
    val inline = mutableMapOf<String, InlineTextContent>()
    var pendingActive = false
    var e = 0
    val toks = tokenize(text)
    val annotated = buildAnnotatedString {
        // A stacked fraction as inline content (null part = empty box). [alt] is what screen readers speak.
        fun frac(id: String, num: String?, den: String?, alt: String, numCaret: Boolean, denCaret: Boolean) {
            appendInlineContent(id, alt)
            val w = maxOf(num?.length ?: 2, den?.length ?: 2) * 0.34f + 0.55f
            inline[id] = InlineTextContent(Placeholder(w.em, 1.5.em, PlaceholderVerticalAlign.Center)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Fraction(num, den, style, numCaret = { numCaret && caretOn() }, denCaret = { denCaret && caretOn() })
                }
            }
            if (numCaret || denCaret) pendingActive = true
        }
        toks.forEachIndexed { i, tok ->
            val d = length
            val numerator = toks.getOrNull(i - 1) == " " && isPlainNum(tok)
            when {
                numerator -> frac("p$i", tok, null, "$tok/", numCaret = cursor in e + 1..e + tok.length, denCaret = false)
                isFractionTok(tok) -> {
                    val (n, den) = tok.removePrefix("-").split(FRAC_SLASH)
                    if (tok.startsWith("-")) append("-")
                    // cursor at the fraction's end: digits go to its denominator, so the caret shows there
                    frac("f$i", n, den.ifEmpty { null }, "$n/$den", numCaret = false, denCaret = cursor == e + tok.length)
                }
                isOp(tok) -> withStyle(SpanStyle(color = Palette.TapeYellow)) { append(" $tok ") }
                // a full mono space is ~0.6em; shrink it so "7 3⁄4" reads as one mixed number
                tok == " " -> withStyle(SpanStyle(fontSize = 0.3.em)) { append(tok) }
                else -> append(tok)
            }
            segs += Seg(d, length - d, e, tok.length, atomic = numerator || isFractionTok(tok) || isOp(tok))
            e += tok.length
            if (tok == " " && !isNumTok(toks.getOrNull(i + 1))) { // nothing typed after the space yet
                val d2 = length
                frac("e$i", null, null, "/", numCaret = cursor == e, denCaret = false)
                segs += Seg(d2, length - d2, e, 0, atomic = true)
            }
        }
    }
    return Expr(annotated, inline, segs, pendingActive)
}

/** The expression with a blinking cursor; tap to place it, drag sideways to slide it. */
@Composable
private fun ExprEditor(state: CalcState, onCursor: (Int) -> Unit) {
    val style = exprStyle(Palette.DisplayFg, FontWeight.Medium)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "blink",
    )
    val expr = remember(state.text, state.cursor, state.hasResult) {
        buildExpr(state.text, state.cursor, style) { !state.hasResult && blink < 0.5f }
    }
    val place = { p: Offset -> layout?.let { onCursor(expr.toEngine(it.getOffsetForPosition(p))) }; Unit }

    BasicText(
        expr.text,
        Modifier
            .fillMaxWidth()
            .pointerInput(expr) { detectTapGestures { place(it) } }
            .pointerInput(expr) { detectHorizontalDragGestures { change, _ -> place(change.position) } }
            .drawWithContent {
                drawContent()
                val l = layout
                if (l != null && !state.hasResult && !expr.pendingActive && blink < 0.5f) {
                    val r = l.getCursorRect(expr.toDisplay(state.cursor))
                    drawRect(Palette.TapeYellow, Offset(r.left, r.top), Size(2.dp.toPx(), r.height))
                }
            },
        style,
        onTextLayout = { layout = it },
        inlineContent = expr.inline,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerExpr(value: Double) {
    val f = toNearest16Plus(value)
    val style = exprStyle(Palette.TapeYellow, FontWeight.Bold).copy(fontSize = AnswerSize)
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

/** Stacked fraction. A null part is an empty dashed box; [cursor] blinks a caret in the numerator. */
@Composable
private fun Fraction(
    num: String?,
    den: String?,
    base: TextStyle,
    modifier: Modifier = Modifier,
    numCaret: () -> Boolean = { false },
    denCaret: () -> Boolean = { false },
) {
    val small = base.copy(fontSize = base.fontSize * 0.56f, lineHeight = base.fontSize * 0.6f)
    val color = base.color
    Column(
        modifier.padding(horizontal = 3.dp).offset(y = (-2).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Slot(
            num, small, numCaret,
            Modifier
                .drawBehind {
                    val h = 2.dp.toPx()
                    drawRect(color, Offset(0f, size.height - h), Size(size.width, h))
                }
                .padding(start = 4.dp, end = 4.dp, bottom = 3.dp),
        )
        Slot(den, small, denCaret, Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp))
    }
}

@Composable
private fun Slot(text: String?, style: TextStyle, cursor: () -> Boolean, modifier: Modifier) {
    val empty = text == null
    val caret = Modifier.drawWithContent {
        drawContent()
        if (cursor()) {
            val w = 1.5.dp.toPx()
            val x = if (empty) 3.dp.toPx() else size.width - w
            drawRect(Palette.TapeYellow, Offset(x, 0f), Size(w, size.height))
        }
    }
    if (text != null) {
        BasicText(text, modifier.then(caret), style)
        return
    }
    val h = with(LocalDensity.current) { style.fontSize.toDp() }
    Box(
        modifier
            .then(caret)
            .width(h * 0.9f)
            .height(h)
            .drawBehind {
                val sw = 1.dp.toPx()
                drawRoundRect(
                    Palette.TapeYellowDim,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                    style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
                )
            },
    )
}

/** Where the exact answer sits between the two nearest 1/16 ticks. */
@Composable
private fun GaugeView(low: String, high: String, position: Float) {
    Column(Modifier.width(160.dp)) {
        Canvas(Modifier.fillMaxWidth().height(30.dp)) {
            val w = size.width
            val h = size.height
            drawRect(Palette.Line, Offset(0f, h / 2 - 1.5.dp.toPx()), Size(w, 3.dp.toPx()))
            val tickW = 2.dp.toPx()
            drawRect(Palette.DisplayDim, Offset(0f, h * 0.15f), Size(tickW, h * 0.7f))
            drawRect(Palette.DisplayDim, Offset(w - tickW, h * 0.15f), Size(tickW, h * 0.7f))
            // 1/32 mark: an answer near here shows "+"
            drawRect(Palette.DisplayDim.copy(alpha = 0.6f), Offset((w - tickW) / 2, h * 0.3f), Size(tickW, h * 0.4f))
            val mw = 3.dp.toPx()
            drawRect(Palette.TapeYellow, Offset((position * w - mw / 2).coerceIn(0f, w - mw), 0f), Size(mw, h))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val s = TextStyle(Palette.DisplayDim, 13.sp, fontFamily = JetBrainsMono)
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
    enabled: Boolean = true,
    face: Color,
    lip: Color,
    radius: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    val shape = RoundedCornerShape(radius)
    val drop = if (pressed) 2.dp else 0.dp
    val lipNow = if (pressed) 1.dp else 3.dp

    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(interaction, indication = null, enabled = enabled) {
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
                .background(face),
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

/** Each key type's look. */
private enum class KeyKind(
    val face: Color,
    val fg: Color,
    val size: TextUnit,
    val weight: FontWeight,
    val lip: Color = Palette.Line,
) {
    Num(Palette.PanelRaised, Palette.KeyFg, 22.sp, FontWeight.Medium),
    Op(Palette.KeyOpBg, Palette.TapeYellow, 40.sp, FontWeight.SemiBold, lip = Palette.KeyOpLip),
    Equals(Palette.TapeYellow, Palette.Housing, 44.sp, FontWeight.Bold, lip = Palette.TapeYellowDim),
}

@Composable
private fun RowScope.Key(
    label: String,
    kind: KeyKind,
    span: Int = 1,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null, // drawn instead of the text label
    onClick: () -> Unit,
) {
    // Two weight-2 keys line up with four weight-1 keys plus their gaps.
    PressKey(
        onClick = onClick,
        // a picture has no text for screen readers to speak, so name the key
        modifier = Modifier.weight(span.toFloat()).fillMaxHeight()
            .then(if (icon != null) Modifier.semantics { contentDescription = label } else Modifier),
        enabled = enabled,
        face = kind.face,
        lip = kind.lip,
    ) {
        if (icon != null) return@PressKey icon()
        val style = TextStyle(kind.fg, kind.size, kind.weight, fontFamily = Oswald)
        BasicText(label, style = style.copy(textAlign = TextAlign.Center))
    }
}

/**
 * "a b/c" drawn as two stacked boxes, with a big "a" when a whole number is involved.
 * The box the next press fills is lit (none when disabled).
 */
@Composable
private fun FracKeyIcon(enabled: Boolean, whole: Boolean, denominatorNext: Boolean) {
    Row(
        Modifier.clearAndSetSemantics {}, // decorative: the key itself carries the spoken name
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (whole) BasicText("a", style = TextStyle(Palette.KeyFg, 26.sp, FontWeight.Medium, fontFamily = Oswald))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            FracBox(lit = enabled && !denominatorNext)
            Box(Modifier.width(18.dp).height(2.dp).background(Palette.DisplayDim))
            FracBox(lit = enabled && denominatorNext)
        }
    }
}

@Composable
private fun FracBox(lit: Boolean) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        Modifier
            .width(14.dp)
            .height(16.dp)
            .then(if (lit) Modifier.background(Palette.TapeYellow, shape) else Modifier.border(1.dp, Palette.DisplayDim, shape)),
    )
}

/** A drawn key icon, hidden from screen readers because the key carries the spoken name. */
@Composable
private fun KeyIcon(width: Dp, height: Dp, draw: DrawScope.() -> Unit) =
    // 1dp lower so it sits level with the text glyphs on the neighbouring keys
    Canvas(Modifier.clearAndSetSemantics {}.offset(y = 1.dp).width(width).height(height), onDraw = draw)

/** Backspace outline with rounded corners, joins and caps (the ⌫ font glyph is small and sharp). */
@Composable
private fun BackspaceIcon() = KeyIcon(27.dp, 20.dp) { // same height as ClearIcon
    val sw = 3.dp.toPx() // same line and corner radius as ClearIcon
    val (w, h) = size
    val r = 5.dp.toPx() // corner radius on the right side
    val tip = w * 0.32f // where the pointed left end meets the body
    val s = sw / 2 + 1.dp.toPx() // keep the stroke inside the canvas, with 1dp to spare
    val body = Path().apply {
        moveTo(tip, s)
        lineTo(w - s - r, s)
        quadraticTo(w - s, s, w - s, s + r)
        lineTo(w - s, h - s - r)
        quadraticTo(w - s, h - s, w - s - r, h - s)
        lineTo(tip, h - s)
        lineTo(s, h / 2)
        close()
    }
    drawPath(body, Palette.Danger, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val cx = tip + (w - tip) * 0.34f // left of the body's middle, which looks centred next to the point
    val d = h * 0.15f // half the ×'s width
    drawLine(Palette.Danger, Offset(cx - d, h / 2 - d), Offset(cx + d, h / 2 + d), sw, StrokeCap.Round)
    drawLine(Palette.Danger, Offset(cx - d, h / 2 + d), Offset(cx + d, h / 2 - d), sw, StrokeCap.Round)
}

/** A blocky C with an even line all the way round (Oswald's C thins out at the bottom). */
@Composable
private fun ClearIcon() = KeyIcon(16.dp, 20.dp) {
    val sw = 3.dp.toPx()
    val (w, h) = size
    val r = 5.dp.toPx() // corner radius on the left side
    val s = sw / 2 + 1.dp.toPx()
    val c = Path().apply {
        moveTo(w - s, s)
        lineTo(s + r, s)
        quadraticTo(s, s, s, s + r)
        lineTo(s, h - s - r)
        quadraticTo(s, h - s, s + r, h - s)
        lineTo(w - s, h - s)
    }
    drawPath(c, Palette.Danger, style = Stroke(sw, cap = StrokeCap.Butt, join = StrokeJoin.Round))
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(62.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), content = content)
}

@Composable
private fun Keypad(update: (CalcState.() -> CalcState) -> Unit, state: CalcState) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        KeyRow {
            Key("(", KeyKind.Num) { update { lparen() } }
            Key(")", KeyKind.Num) { update { rparen() } }
            Key("Clear", KeyKind.Num, icon = { ClearIcon() }) { update { clear() } }
            Key("Backspace", KeyKind.Num, icon = { BackspaceIcon() }) { update { backspace() } }
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
            // starts the fraction of a mixed number, then moves to its denominator
            val enabled = state.fracKeyEnabled
            val icon = @Composable { FracKeyIcon(enabled, state.fracKeyHasWhole, state.fracKeyOpensDenominator) }
            Key("Fraction", KeyKind.Num, span = 2, enabled = enabled, icon = icon) { update { space() } }
            // the font's "=" is short: stretch it sideways for longer bars, and lift it 1dp to look centred
            val eq = KeyKind.Equals
            val eqStyle = TextStyle(eq.fg, eq.size, eq.weight, fontFamily = Oswald, textGeometricTransform = TextGeometricTransform(scaleX = 1.6f))
            Key("=", eq, span = 2, icon = { BasicText("=", Modifier.offset(y = (-1).dp), eqStyle) }) { update { equals() } }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PreviewShopCalc() = ShopCalcScreen()
