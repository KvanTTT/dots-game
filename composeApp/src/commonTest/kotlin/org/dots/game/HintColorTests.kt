package org.dots.game

import androidx.compose.ui.graphics.Color
import org.dots.game.views.contrastRatio
import org.dots.game.views.mostContrastingHintBackground
import org.dots.game.views.hintDarkBackgroundColor
import org.dots.game.views.hintLightBackgroundColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The hint colors every value with what it refers to, so a background has to suit any player color. */
class HintColorTests {
    companion object {
        /** The WCAG minimum for a bold text of the hint size. */
        private const val MIN_READABLE_CONTRAST_RATIO = 3.0f
    }

    @Test
    fun theDefaultFirstPlayerColorGetsALightBackground() {
        assertEquals(hintLightBackgroundColor, mostContrastingHintBackground(UiSettings.Standard.playerFirstColor))
    }

    @Test
    fun theDefaultSecondPlayerColorGetsADarkBackground() {
        assertEquals(hintDarkBackgroundColor, mostContrastingHintBackground(UiSettings.Standard.playerSecondColor))
    }

    @Test
    fun theDarkBackgroundIsWhatMadeTheFirstPlayerColorUnreadable() {
        val ratio = contrastRatio(UiSettings.Standard.playerFirstColor, hintDarkBackgroundColor)

        assertTrue(ratio < 2.0f, "The default blue on the dark background has the contrast ratio of $ratio")
    }

    @Test
    fun bothDefaultPlayerColorsAreReadableOnTheChosenBackground() {
        for (playerColor in listOf(UiSettings.Standard.playerFirstColor, UiSettings.Standard.playerSecondColor)) {
            val ratio = contrastRatio(playerColor, mostContrastingHintBackground(playerColor))

            assertTrue(
                ratio >= MIN_READABLE_CONTRAST_RATIO,
                "$playerColor on its background has the contrast ratio of $ratio",
            )
        }
    }

    @Test
    fun aCustomPlayerColorOfAnyBrightnessStaysReadable() {
        val customColors = listOf(
            Color.Black, Color.White, Color.Yellow, Color.Cyan, Color.Magenta, Color.Green,
            Color.Gray, Color.DarkGray, Color.LightGray,
        )

        for (color in customColors) {
            val ratio = contrastRatio(color, mostContrastingHintBackground(color))

            assertTrue(
                ratio >= MIN_READABLE_CONTRAST_RATIO,
                "$color on its background has the contrast ratio of $ratio",
            )
        }
    }
}
