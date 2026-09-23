package org.dots.game

import org.dots.game.core.ThisAppName
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The settings of the app are dropped altogether, so that the app is started the way it is started
 * for the very first time, see [resetSettings].
 */
class SettingsResetTests {
    private val home = createTempDirectory("dots-game-home").toString()
    private val previousHome: String = System.getProperty(HOME_PROPERTY)

    init {
        // The settings of the app are kept in the home directory of the user, and the ones of whoever
        // runs this test are none of its business
        val _ = System.setProperty(HOME_PROPERTY, home)
    }

    @AfterTest
    fun restore() {
        val _ = System.setProperty(HOME_PROPERTY, previousHome)
        settingsAreReset = false
        File(home).deleteRecursively()
    }

    @Test
    fun everySettingIsDroppedAndNothingIsSavedAfterwards() {
        assertTrue(saveClassSettings(UiSettings.Standard.copy(developerMode = true)))
        assertTrue(saveClassSettings(GameSettings.Default))
        assertTrue(settingsOf("UiSettings").exists() && settingsOf("GameSettings").exists())

        resetSettings()

        assertFalse(settingsOf("UiSettings").exists(), "The settings of the app are still there")
        assertFalse(settingsOf("GameSettings").exists(), "The settings of the app are still there")

        // The app saves the game it is left with and the window it is left in when it is closed, and
        // a first start is a start with neither
        assertFalse(saveClassSettings(UiSettings.Standard), "A setting was saved after the reset")
        assertFalse(settingsOf("UiSettings").exists(), "A setting came back after the reset")
    }

    private fun settingsOf(settingsClassName: String) = File(home, "$ThisAppName/$settingsClassName.properties")
}

private const val HOME_PROPERTY = "user.home"
