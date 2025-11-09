package org.dots.game.localization

enum class Language(val displayName: String) {
    English("English"),
    Russian("Русский");

    fun getStrings(): Strings = when (this) {
        English -> EnglishStrings
        Russian -> RussianStrings
    }
}
