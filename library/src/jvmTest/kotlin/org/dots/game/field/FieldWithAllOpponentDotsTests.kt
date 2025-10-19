package org.dots.game.field

import org.dots.game.core.BaseMode
import org.dots.game.core.LegalMove
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.SuicidalIllegalMove
import org.dots.game.core.getSortedClosurePositions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FieldWithAllOpponentDotsAndBorderTests : FieldTests() {
    override val baseMode: BaseMode = BaseMode.AllOpponentDots
    override val captureByBorder: Boolean = true
    override val suicideAllowed: Boolean = false

    @Test
    fun simple() {
        testFieldWithRollback(
            """
            + * .
            . * .
            * . .
            . . .
        """
        ) {
            assertEquals(0, it.player1Score)
            assertEquals(0, it.player2Score)

            assertIs<SuicidalIllegalMove>(it.makeMove(1, 2, Player.Second)) // Suicide is disallowed
            val legalMove = assertIs<LegalMove>(it.makeMove(1, 2, Player.First))

            val base = legalMove.bases.single()
            val (outerClosure, innerClosure) = base.getSortedClosurePositions(it)

            assertEquals(
                listOf(
                    Position(1, 0, it.realWidth),
                    Position(0, 1, it.realWidth),
                    Position(1, 2, it.realWidth),
                    Position(2, 1, it.realWidth)
                ), outerClosure
            )
            assertTrue(innerClosure.isEmpty())
            assertEquals(1, it.player1Score)
            assertEquals(0, it.player2Score)

            it.makeMove(3, 4, Player.Second)
            it.makeMove(2, 4, Player.First)
            val base2 = assertIs<LegalMove>(it.makeMove(3, 3, Player.First)).bases.single()

            val (outerClosure2, innerClosure2) = base2.getSortedClosurePositions(it)
            assertEquals(
                listOf(
                    Position(3, 3, it.realWidth),
                    Position(2, 4, it.realWidth),
                    Position(3, 5, it.realWidth),
                    Position(4, 4, it.realWidth)
                ), outerClosure2
            )
            assertTrue(innerClosure2.isEmpty())
        }
    }

    @Test
    fun prohibitMoveIfResultGroupHasNoLiberties() {
        testFieldWithRollback("""
            * *
            * .
        """) {
            // Follow Go game in this case: the result group should have at least one liberty
            assertIs<SuicidalIllegalMove>(it.makeMove(2, 2, Player.First))
        }
    }

    @Test
    fun tryPutDotToEmptyBaseAndCapture() {
        testFieldWithRollback(
            """
            *5 +0 *2 ..
            +1 *3 .. ..
            *4 .. .. ..
            .. .. .. ..
        """
        ) {
            assertEquals(2, it.player1Score)
        }
    }

    @Test
    fun baseWithInternalCornerHole() {
        testFieldWithRollback(
            """
            ... +00 *02 +07 ...
            +01 *04 *03 +08 ...
            *06 *05 +09 ... ...
            +11 +10 ... ... ...
            ... ... ... ... ...
        """
        ) {
            assertEquals(5, it.player2Score)
            assertIs<SuicidalIllegalMove>(it.makeMove(1, 1, Player.First))
            assertIs<LegalMove>(it.makeMove(1, 1, Player.Second))
        }
    }

    @Test
    fun dontRecaptureAlreadyCaptured() {
        testFieldWithRollback(
            """
            +00 *01 +03 *08 ...
            *02 +05 +04 *09 ...
            +07 +06 *10 ... ...
            ... *11 ... ... ...
            ... ... ... ... ...
        """
        ) {
            assertEquals(0, it.player1Score)
            assertEquals(2, it.player2Score)

            assertNotNull(it.makeMoveUnsafe(Position(1, 4, it.realWidth), Player.First))

            assertEquals(6, it.player1Score)
            assertEquals(0, it.player2Score)
        }
    }
}

class FieldWithAllOpponentDotsAndNoBorderTests : FieldTests() {
    override val baseMode: BaseMode = BaseMode.AllOpponentDots
    override val captureByBorder: Boolean = false
    override val suicideAllowed: Boolean = false

    @Test
    fun simple() {
        testFieldWithRollback("""
            + * .
            * . .
        """) {
            assertEquals(0, it.player1Score)
            assertEquals(0, it.player2Score)
        }
    }

    @Test
    fun quadrupleCapture() {
        testFieldWithRollback("""
            . . *0 . .
            . *7 + *1 .
            *6 + . + *2
            . *5 + *3 .
            . . *4 . .
        """) {
            assertEquals(0, it.player1Score)
            assertEquals(0, it.player2Score)

            val legalMove = assertIs<LegalMove>(it.makeMove(3, 3, Player.First))
            val bases = legalMove.bases
            assertEquals(4, bases.size)
            assertEquals(4, it.player1Score)
            assertEquals(0, it.player2Score)
        }
    }

    @Test
    fun baseWithInternalCenterHole() {
        testFieldWithRollback(
            """
            . . + + +
            . . * * * +
            + * * + * * +
            + * + . + * +
            + * * + * * +
            . + * * * +
            . . + + +
        """
        ) {
            val legalMove = assertIs<LegalMove>(it.makeMove(2, 2, Player.Second))
            val base = legalMove.bases.single()
            assertEquals(16, it.player2Score)
            val (_, innerClosures) = base.getSortedClosurePositions(it)
            val innerClosure = innerClosures.single()
            assertEquals(4, innerClosure.size)
        }
    }

    @Test
    fun baseWithDoubleAdjacentInternalCenterHole() {
        testFieldWithRollback(
            """
            . . + + + + +
            . . * * * * * +
            + * * + * + * * +
            + * + . + . + * +
            + * * + * + * * +
            . + * * * * * +
            . . + + + + +
        """
        ) {
            val legalMove = assertIs<LegalMove>(it.makeMove(2, 2, Player.Second))
            val base = legalMove.bases.single()
            assertEquals(22, it.player2Score)
            val (_, innerClosures) = base.getSortedClosurePositions(it)
            assertTrue(innerClosures.single().size.let { size -> size == 7 || size == 8 })
        }
    }

    @Test
    fun baseWithDoubleIndependentInternalCenterHoles() {
        testFieldWithRollback(
            """
            . . + + + + + + +
            . . * * * * * * * +
            + * * + * * * + * * +
            + * + . + * + . + * +
            + * * + * * * + * * +
            . + * * * * * * * +
            . . + + + + + + +
        """
        ) {
            val legalMove = assertIs<LegalMove>(it.makeMove(2, 2, Player.Second))
            val base = legalMove.bases.single()
            assertEquals(31, it.player2Score)
            val (_, innerClosures) = base.getSortedClosurePositions(it)
            assertEquals(2, innerClosures.size)
        }
    }

    @Test
    fun baseWithInternalSingleConnections() {
        testFieldWithRollback(
            """
            . * * * 
            * + + + *
            * + . + *
            * + + + *
            . * * * 
        """
        ) {
            val legalMove = assertIs<LegalMove>(it.makeMove(3, 3, Player.First))
            val base = legalMove.bases.single()
            val (outerClosure, innerClosure) = base.getSortedClosurePositions(it)
            assertEquals(
                listOf(
                    Position(2, 1, it.realWidth),
                    Position(1, 2, it.realWidth),
                    Position(1, 3, it.realWidth),
                    Position(1, 4, it.realWidth),
                    Position(2, 5, it.realWidth),
                    Position(3, 5, it.realWidth),
                    Position(4, 5, it.realWidth),
                    Position(5, 4, it.realWidth),
                    Position(5, 3, it.realWidth),
                    Position(5, 2, it.realWidth),
                    Position(4, 1, it.realWidth),
                    Position(3, 1, it.realWidth),
                ),
                outerClosure
            )
            assertEquals(listOf(Position(3, 3, it.realWidth)), innerClosure.single())
        }
    }

    @Test
    fun baseWithInternalDoubleConnections() {
        testFieldWithRollback(
            """
            . * * * *
            * + + + + *
            * + * . + *
            * + + + + *
            . * * * *
        """
        ) {
            val legalMove = assertIs<LegalMove>(it.makeMove(4, 3, Player.First))
            val base = legalMove.bases.single()
            val (outerClosure, innerClosure) = base.getSortedClosurePositions(it)
            assertTrue(outerClosure.isNotEmpty())
            assertEquals(listOf(
                    Position(3, 3, it.realWidth),
                    Position(4, 3, it.realWidth)
                ),
                innerClosure.single()
            )
        }
    }

    @Test
    fun baseWithInternalTripleConnections() {
        testFieldWithRollback(
            """
            . * * * *
            * + + + + *
            * + * * + *
            * + + . + *
            . * + + + *
            . . * * *
        """
        ) {
            val legalMove = assertIs<LegalMove>(it.makeMove(4, 4, Player.First))
            val base = legalMove.bases.single()
            val (outerClosure, innerClosure) = base.getSortedClosurePositions(it)
            assertTrue(outerClosure.isNotEmpty())
            assertEquals(
                listOf(
                    Position(3, 3, it.realWidth),
                    Position(4, 3, it.realWidth),
                    Position(4, 4, it.realWidth)
                ), innerClosure.single()
            )
        }
    }
}