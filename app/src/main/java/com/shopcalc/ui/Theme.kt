package com.shopcalc.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.shopcalc.R

object Palette {
    val Housing = Color(0xFF1E1C1A)
    val Panel = Color(0xFF29261F)
    val PanelRaised = Color(0xFF322E26)
    val DisplayBg = Color(0xFF14130F)
    val DisplayFg = Color(0xFFF0EBE1)
    val DisplayDim = Color(0xFF8C8577)
    val TapeYellow = Color(0xFFE8A93D)
    val TapeYellowDim = Color(0xFFB9852B)
    val KeyFg = Color(0xFFEDE7DA)
    val KeyOpBg = Color(0xFF3D372B)
    val Danger = Color(0xFFC0553C)
    val Line = Color(0xFF3A362D)
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: Int) = FontFamily(
    weights.map { w ->
        Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

val Oswald = variable(R.font.oswald, 400, 500, 600, 700)
val JetBrainsMono = variable(R.font.jetbrains_mono, 400, 500, 700)
