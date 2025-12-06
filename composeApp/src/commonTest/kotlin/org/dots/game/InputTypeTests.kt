package org.dots.game

import org.dots.game.ExampleTestData.EXAMPLE_PATH
import org.dots.game.ExampleTestData.exampleSgfParams
import kotlin.test.Test
import kotlin.test.assertEquals

class InputTypeTests {
    @Test
    fun fieldContent() {
        checkInputType(InputType.Empty, "")
        checkInputType(InputType.Empty, "   ")

        // Correct data
        checkInputType(InputType.FieldContent, """
            . * .
            * + *
            . * .
        """)

        // Data with warnings
        checkInputType(InputType.FieldContent, """
            *0 +1
            +1 *2
        """)

        checkInputType(InputType.FieldContent, """
            *1 +2
            +5 *6
        """)
    }

    @Test
    fun sgf() {
        // File regex should match the entire input but not a part
        checkInputType(InputType.SgfContent, "(;GM[40]FF[4]CA[UTF-8]SZ[0:0]SO[path/to/file.sgf])")

        // Disambiguate with field content
        checkInputType(InputType.SgfContent, "(;GM[40]C[ * ])")

        // Erroneous sgf
        checkInputType(InputType.SgfContent, "     (;GM[100]C[some comment")
        checkInputType(InputType.SgfContent, "(")
    }

    @Test
    fun files() {
        checkInputType(InputType.SgfFile("F:\\Dots\\test.sgf", "test.sgf"), "F:\\Dots\\test.sgf")
        checkInputType(InputType.SgfFile("F:\\Dots\\test.sgfs", "test.sgfs"), "F:\\Dots\\test.sgfs")
        checkInputType(InputType.SgfFile("F:\\Dots\\test.sgf", "test.sgf"), "\"F:\\Dots\\test.sgf\"")
        checkInputType(InputType.SgfFile("F:\\Dots\\test.sgf", "test.sgf"), "  F:\\Dots\\test.sgf  ")
        checkInputType(InputType.SgfFile("/home/user/docs/test.sgf", "test.sgf"), "/home/user/docs/test.sgf")
        checkInputType(InputType.SgfFile("test.sgf", "test.sgf"), "test.sgf")
        checkInputType(InputType.OtherFile("path/to/test.md", "test.md"), "path/to/test.md")
    }

    @Test
    fun urls() {
        checkInputType(
            InputType.SgfServerUrl(
                "https://zagram.org/eidokropki/backend/download.py?id=zagram377454", "zagram377454"),
            "  https://zagram.org/eidokropki/backend/download.py?id=zagram377454  "
        )
        checkInputType(
            InputType.SgfServerUrl("https://zagram.org/eidokropki/backend/download.py?id=zagram377454", "zagram377454"),
            "https://zagram.org/eidokropki/index.html#url:zagram377454"
        )
        checkInputType(
            InputType.SgfServerUrl("https://zagram.org/eidokropki/backend/download.py?id=zagram377454", "zagram377454"),
            "zagram377454"
        )
        checkInputType(
            InputType.OtherUrl("https://zagram.org/"),
            "https://zagram.org/"
        )

        checkInputType(
            InputType.SgfClientUrl("${THIS_APP_LOCAL_URL}/$exampleSgfParams", EXAMPLE_PATH, exampleSgfParams, 22),
            "  ${THIS_APP_LOCAL_URL}/${exampleSgfParams} "
        )
        checkInputType(
            InputType.SgfClientUrl("${THIS_APP_LOCAL_URL}$exampleSgfParams", EXAMPLE_PATH, exampleSgfParams, 21),
            "${THIS_APP_LOCAL_URL}${exampleSgfParams}"
        )
        checkInputType(
            InputType.SgfClientUrl("${THIS_APP_SERVER_URL}/$exampleSgfParams", EXAMPLE_PATH, exampleSgfParams, 36),
            "${THIS_APP_SERVER_URL}/${exampleSgfParams}"
        )
    }

    @Test
    fun other() {
        checkInputType(InputType.Other, "test")
    }

    private fun checkInputType(expectedInputType: InputType, text: String) {
        assertEquals(expectedInputType, InputTypeDetector.getInputType(text))
    }
}