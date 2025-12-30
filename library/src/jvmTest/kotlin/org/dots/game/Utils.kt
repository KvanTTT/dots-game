package org.dots.game

import org.dots.game.core.InitPosType
import org.dots.game.core.Rules

fun createStandardRules(
    width: Int = Rules.Standard.width,
    height: Int = Rules.Standard.height,
    initPosType: InitPosType = Rules.Standard.initPosType,
): Rules {
    return Rules.create(
        width,
        height,
        captureByBorder = Rules.Standard.captureByBorder,
        baseMode = Rules.Standard.baseMode,
        suicideAllowed = Rules.Standard.suicideAllowed,
        initPosType = initPosType,
        random = Rules.Standard.random,
        initPosGenType = Rules.Standard.initPosGenType,
        komi = Rules.Standard.komi
    )
}