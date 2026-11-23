package com.wa.beastclient.ui.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppThemeContrastTest {
    private fun srgbChannelLuminance(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun relativeLuminance(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return 0.2126 * srgbChannelLuminance(r) +
            0.7152 * srgbChannelLuminance(g) +
            0.0722 * srgbChannelLuminance(b)
    }

    private fun contrastRatio(lumA: Double, lumB: Double): Double {
        val hi = max(lumA, lumB)
        val lo = min(lumA, lumB)
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun allThemeAccentAndBrightColorsMeetWcagAaWithContrastingContentColor() {
        val whiteLum = 1.0
        val darkInkLum = relativeLuminance(0xFF0B141A)

        val paletteColors = listOf(
            "Dark accentGreen" to 0xFF00A884L,
            "Dark brightGreen" to 0xFF25D366L,
            "Light accentGreen" to 0xFF008069L,
            "Light brightGreen" to 0xFF1DAA61L,
            "AMOLED accentGreen" to 0xFF00C49AL,
            "AMOLED brightGreen" to 0xFF25D366L,
            "Aurora accentGreen" to 0xFF00E5B8L,
            "Aurora brightGreen" to 0xFF3DF5C8L,
            "Synthwave accentGreen" to 0xFFF43F5EL,
            "Synthwave brightGreen" to 0xFFFB7185L,
            "Sakura accentGreen" to 0xFFBE185DL,
            "Sakura brightGreen" to 0xFFEC4899L,
            "NoirGold accentGreen" to 0xFFD9A441L,
            "NoirGold brightGreen" to 0xFFFBBF24L,
            "PaperInk accentGreen" to 0xFF2D5A27L,
            "PaperInk brightGreen" to 0xFF3E7B34L,
            "Matcha accentGreen" to 0xFF2E7D32L,
            "Matcha brightGreen" to 0xFF43A047L,
            "Glacier accentGreen" to 0xFF06B6D4L,
            "Glacier brightGreen" to 0xFF22D3EEL,
            "iOS accentGreen" to 0xFF007AFFL,
            "iOS brightGreen" to 0xFF007AFFL
        )

        for ((label, argb) in paletteColors) {
            val lum = relativeLuminance(argb)
            val useDark = shouldPreferDarkContentOnLuminance(lum.toFloat())
            val chosenLum = if (useDark) darkInkLum else whiteLum
            val ratio = contrastRatio(lum, chosenLum)
            assertTrue(
                "$label contrast was $ratio (< 4.5:1) with useDark=$useDark",
                ratio >= 4.5
            )
        }
    }
}
