package org.dots.game

import androidx.compose.ui.window.WindowState
import org.dots.game.core.ClassSettings
import org.dots.game.core.Rules
import org.dots.game.core.ThisAppName
import org.dots.game.dump.DumpParameters
import org.dots.game.views.OpenGameSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Paths
import kotlin.test.assertEquals

class SettingsTests {
    @Test
    fun loading() {
        val defaultClassSettings = loadClassSettings(Rules.Standard, directory = tempDirectory)
        assertEquals(Rules.Standard.width, defaultClassSettings.width)
        assertEquals(Rules.Standard.komi, defaultClassSettings.komi)
        assertEquals(Rules.Standard.initialMoves, defaultClassSettings.initialMoves)

        val defaultDumpParameters = loadClassSettings(DumpParameters.DEFAULT, directory = tempDirectory)
        assertEquals(DumpParameters.DEFAULT.isSgf, defaultDumpParameters.isSgf)

        val defaultUiSettings = loadClassSettings(UiSettings.Standard, directory = tempDirectory)
        assertEquals(UiSettings.Standard.playerFirstColor, defaultUiSettings.playerFirstColor)

        val defaultOpenGameSettings = loadClassSettings(OpenGameSettings.Default, directory = tempDirectory)
        assertEquals(OpenGameSettings.Default.pathOrContent, defaultOpenGameSettings.pathOrContent)

        val defaultGameSettings = loadClassSettings(GameSettings.Default, directory = tempDirectory)
        assertEquals(GameSettings.Default.sgf, defaultGameSettings.sgf)

        val defaultKataGoDotsSettings = loadClassSettings(KataGoDotsSettings.Default, directory = tempDirectory)
        assertEquals(KataGoDotsSettings.Default.exePath, defaultKataGoDotsSettings.exePath)

        val defaultWindowState = loadWindowsState(directory = tempDirectory)
        assertEquals(WindowSettings.DEFAULT.placement, defaultWindowState.placement)
    }

    @Test
    fun saving() {
        fun <T : ClassSettings<T>> checkSaveAndExistence(classSettings: ClassSettings<T>) {
            if (classSettings is WindowSettings) {
                saveWindowsState(WindowState(), directory = tempDirectory)
            } else {
                saveClassSettings(classSettings.default, directory = tempDirectory)
            }
            assertTrue(File(Paths.get(tempDirectory, ThisAppName, classSettings::class.simpleName!! + ".properties").toString()).exists())
        }

        checkSaveAndExistence(Rules.Standard)
        checkSaveAndExistence(DumpParameters.DEFAULT)
        checkSaveAndExistence(UiSettings.Standard)
        checkSaveAndExistence(OpenGameSettings.Default)
        checkSaveAndExistence(GameSettings.Default)
        checkSaveAndExistence(KataGoDotsSettings.Default)
        checkSaveAndExistence(WindowSettings.DEFAULT)
    }

    @Test
    fun bigString() {
        val bigString = """
                (;FF[4]AP[katago]GM[40]SZ[12]PB[dotsgame-s7866496-d731239]PW[dotsgame-s7782144-d725682]KM[0.5]RU[dotsCaptureEmptyBase0startPosIsRandom1sui1]RE[W+0.5]AB[ff][gg]AW[gf][fg]C[startTurnIdx=0,initTurnNum=4,gameHash=9031B3F1B861D5F0C72597833E9FBEBA,gtype=normal];B[fe]C[0.87 0.13 0.00 1.0 v=165];W[eg]C[0.85 0.15 0.00 0.9 v=165];B[ef]C[0.88 0.12 0.00 1.0 v=165];W[hf]C[0.86 0.14 0.00 1.0 v=165];B[hg]C[0.88 0.12 0.00 1.0 v=165];W[if]C[0.87 0.13 0.00 1.0 v=163];B[ig]C[0.89 0.11 0.00 0.9 v=165];W[jf]C[0.87 0.13 0.00 0.9 v=165];B[jg]C[0.89 0.11 0.00 0.9 v=164];W[kf]C[0.87 0.13 0.00 0.9 v=162];B[kg]C[0.89 0.11 0.00 0.8 v=163];W[lf]C[0.87 0.13 0.00 0.7 v=164];B[gh]C[0.88 0.12 0.00 0.6 v=165];W[dg]C[0.87 0.13 0.00 0.9 v=165];B[df]C[0.90 0.10 0.00 0.7 v=165];W[cg]C[0.89 0.11 0.00 0.7 v=165];B[lg]C[0.92 0.08 0.00 0.7 v=165];W[bg]C[0.91 0.09 0.00 0.6 v=165];B[ag]C[0.95 0.05 0.00 0.4 v=164];W[ah]C[0.96 0.04 0.00 0.8 v=164];B[cf]C[0.99 0.01 0.00 0.6 v=164];W[bh]C[0.99 0.01 0.00 0.5 v=164];B[bf]C[1.00 0.00 0.00 0.5 v=164];W[]C[0.99 0.01 0.00 0.4 v=165 result=W+0.5])
                (;FF[4]AP[katago]GM[40]SZ[10]PB[dotsgame-s7866496-d731239]PW[dotsgame-s7782144-d725682]KM[0.5]RU[dotsCaptureEmptyBase0sui1]RE[W+R]AB[ee][ff]AW[fe][ef]C[startTurnIdx=0,initTurnNum=4,gameHash=9744CFEE2C1FC8F1C96431D0379DA2E4,gtype=normal];B[gf]C[0.94 0.06 0.00 0.5 v=165];W[fd]C[0.92 0.08 0.00 0.5 v=165];B[de]C[0.94 0.06 0.00 0.6 v=165];W[eg]C[0.92 0.08 0.00 0.4 v=165];B[ce]C[0.95 0.05 0.00 0.5 v=163];W[eh]C[0.94 0.06 0.00 0.4 v=165];B[be]C[0.95 0.05 0.00 0.5 v=164];W[fc]C[0.94 0.06 0.00 0.5 v=165];B[ae]C[0.95 0.05 0.00 0.4 v=164];W[ei]C[0.93 0.07 0.00 0.5 v=163];B[hf]C[0.94 0.06 0.00 0.4 v=164];W[fb]C[0.92 0.08 0.00 0.3 v=165];B[if]C[0.94 0.06 0.00 0.2 v=165];W[ej]C[0.94 0.06 0.00 0.3 v=158];B[fa]C[0.96 0.04 0.00 0.4 v=161];W[gb]C[0.96 0.04 0.00 0.4 v=165];B[ga]C[0.97 0.03 0.00 0.3 v=164];W[hb]C[0.95 0.05 0.00 0.3 v=162];B[ha]C[0.97 0.03 0.00 0.4 v=165];W[ib]C[0.96 0.04 0.00 0.3 v=163];B[jf]C[0.97 0.03 0.00 0.4 v=165];W[jb]C[0.98 0.02 0.00 0.5 v=163];C[1.00 0.00 0.00 0.5 v=165 result=W+R])
                (;FF[4]AP[katago]GM[40]SZ[12]PB[dotsgame-s7866496-d731239]PW[dotsgame-s7782144-d725682]KM[-0.5]RU[dotsCaptureEmptyBase0sui0]RE[B+0.5]AB[ff][gg]AW[gf][fg]C[startTurnIdx=0,initTurnNum=4,gameHash=08B68CDB55331483EA40BD5F402B7CBA,gtype=normal];B[hg]C[0.05 0.95 0.00 -1.2 v=165];W[hf]C[0.05 0.95 0.00 -1.3 v=165];B[ef]C[0.05 0.95 0.00 -1.2 v=165];W[eg]C[0.05 0.95 0.00 -1.3 v=164];B[df]C[0.05 0.95 0.00 -1.2 v=164];W[dg]C[0.05 0.95 0.00 -1.3 v=165];B[hh]C[0.05 0.95 0.00 -1.2 v=164];W[cg]C[0.05 0.95 0.00 -1.1 v=163];B[cf]C[0.05 0.95 0.00 -1.1 v=165];W[if]C[0.05 0.95 0.00 -1.1 v=165];B[ih]C[0.06 0.94 0.00 -1.2 v=164];W[bg]C[0.05 0.95 0.00 -1.0 v=163];B[bf]C[0.05 0.95 0.00 -0.8 v=160];W[ag]C[0.04 0.96 0.00 -0.7 v=165];B[af]C[0.05 0.95 0.00 -0.6 v=165];W[jh]C[0.04 0.96 0.00 -0.6 v=163];B[ii]C[0.06 0.94 0.00 -0.9 v=165];W[ji]C[0.04 0.96 0.00 -0.7 v=164];B[ij]C[0.05 0.95 0.00 -0.8 v=165];W[jj]C[0.03 0.97 0.00 -0.8 v=165];B[ik]C[0.04 0.96 0.00 -0.6 v=165];W[jg]C[0.02 0.98 0.00 -0.5 v=165];B[il]C[0.01 0.99 0.00 -0.5 v=165];W[jk]C[0.00 1.00 0.00 -0.5 v=165];B[]C[0.01 0.99 0.00 -0.5 v=164 result=B+0.5])
                (;FF[4]AP[katago]GM[40]SZ[12]PB[dotsgame-s7782144-d725682]PW[dotsgame-s7866496-d731239]KM[0.5]RU[dotsCaptureEmptyBase1sui0]RE[W+0.5]AB[ff][gg]AW[gf][fg]C[startTurnIdx=0,initTurnNum=4,gameHash=F8EE03A69772277275B11C4C9DC11E23,gtype=normal];B[hg]C[0.93 0.07 0.00 1.1 v=165];W[eg]C[0.91 0.09 0.00 1.0 v=165];B[fe]C[0.93 0.07 0.00 1.1 v=165];W[ge]C[0.90 0.10 0.00 1.0 v=165];B[fd]C[0.93 0.07 0.00 1.1 v=165];W[gd]C[0.92 0.08 0.00 0.9 v=163];B[if]C[0.94 0.06 0.00 1.0 v=163];W[gc]C[0.92 0.08 0.00 1.2 v=165];B[fc]C[0.95 0.05 0.00 1.0 v=163];W[gb]C[0.94 0.06 0.00 1.1 v=165];B[ef]C[0.96 0.04 0.00 0.9 v=161];W[ga]C[0.95 0.05 0.00 0.8 v=165];B[fb]C[0.96 0.04 0.00 0.7 v=165];W[fh]C[0.95 0.05 0.00 0.6 v=165];B[fa]C[0.96 0.04 0.00 0.5 v=163];W[fi]C[0.95 0.05 0.00 0.4 v=165];B[df]C[0.96 0.04 0.00 0.4 v=165];W[fj]C[0.96 0.04 0.00 0.5 v=164];B[ih]C[0.96 0.04 0.00 0.5 v=165];W[fk]C[0.96 0.04 0.00 0.4 v=165];B[fl]C[0.98 0.02 0.00 0.5 v=164];W[gl]C[0.98 0.02 0.00 0.4 v=160];B[jg]C[0.99 0.01 0.00 0.5 v=165];W[gk]C[0.99 0.01 0.00 0.5 v=164];B[je]C[1.00 0.00 0.00 0.5 v=165];W[]C[1.00 0.00 0.00 0.5 v=165 result=W+0.5])
                (;FF[4]AP[katago]GM[40]SZ[10]PB[dotsgame-s7782144-d725682]PW[dotsgame-s7866496-d731239]KM[-0]RU[dotsCaptureEmptyBase0startPosIsRandom1sui0]RE[0]AB[ee][ff]AW[fe][ef]C[startTurnIdx=0,initTurnNum=4,gameHash=F236AB7193BBB1868CBAD5FF5DAB4713,gtype=normal];B[fg]C[0.50 0.50 0.00 -0.1 v=165];W[fd]C[0.48 0.52 0.00 -0.1 v=165];B[gg]C[0.50 0.50 0.00 -0.0 v=165];W[eg]C[0.48 0.52 0.00 -0.0 v=164];B[ed]C[0.50 0.50 0.00 0.0 v=165];W[eh]C[0.49 0.51 0.00 -0.0 v=162];B[ec]C[0.50 0.50 0.00 -0.0 v=165];W[fc]C[0.49 0.51 0.00 -0.1 v=165];B[eb]C[0.51 0.49 0.00 0.0 v=165];W[fb]C[0.50 0.50 0.00 0.1 v=165];B[ea]C[0.51 0.49 0.00 0.0 v=164];W[fa]C[0.50 0.50 0.00 0.1 v=165];B[ge]C[0.51 0.49 0.00 0.1 v=165];W[df]C[0.51 0.49 0.00 0.2 v=161];B[gf]C[0.51 0.49 0.00 0.1 v=164];W[cf]C[0.51 0.49 0.00 0.2 v=165];B[de]C[0.51 0.49 0.00 0.1 v=165];W[ce]C[0.50 0.50 0.00 0.2 v=165];B[gh]C[0.51 0.49 0.00 0.1 v=165];W[ei]C[0.51 0.49 0.00 0.1 v=165];B[cd]C[0.51 0.49 0.00 0.0 v=165];W[gd]C[0.51 0.49 0.00 0.1 v=162];B[gi]C[0.52 0.48 0.00 0.1 v=164];W[ej]C[0.51 0.49 0.00 0.0 v=165];B[gj]C[0.51 0.49 0.00 -0.0 v=164];W[hd]C[0.51 0.49 0.00 0.0 v=164];B[dd]C[0.51 0.49 0.00 -0.0 v=165];W[]C[0.50 0.50 0.00 0.0 v=165 result=0])
                (;FF[4]AP[katago]GM[40]SZ[14]PB[dotsgame-s7782144-d725682]PW[dotsgame-s7866496-d731239]KM[1]RU[dotsCaptureEmptyBase0sui1]RE[W+1]AB[gg][hh]AW[hg][gh]C[startTurnIdx=0,initTurnNum=4,gameHash=7B28B4FD123FE2E838820A080591BD4C,gtype=normal];B[hi]C[0.94 0.06 0.00 2.2 v=165];W[fh]C[0.93 0.07 0.00 2.2 v=165];B[fg]C[0.95 0.05 0.00 2.3 v=164];W[eh]C[0.93 0.07 0.00 2.4 v=164];B[eg]C[0.95 0.05 0.00 2.3 v=165];W[dh]C[0.94 0.06 0.00 2.3 v=162];B[dg]C[0.96 0.04 0.00 2.4 v=165];W[ig]C[0.94 0.06 0.00 2.3 v=165];B[ch]C[0.96 0.04 0.00 2.2 v=164];W[di]C[0.93 0.07 0.00 2.8 v=164];B[ci]C[0.95 0.05 0.00 2.3 v=165];W[dj]C[0.93 0.07 0.00 2.7 v=164];B[cj]C[0.95 0.05 0.00 2.4 v=165];W[dk]C[0.94 0.06 0.00 2.8 v=165];B[ck]C[0.96 0.04 0.00 2.4 v=165];W[dl]C[0.95 0.05 0.00 2.8 v=165];B[ih]C[0.96 0.04 0.00 2.6 v=163];W[jg]C[0.95 0.05 0.00 2.6 v=164];B[jh]C[0.97 0.03 0.00 2.5 v=165];W[kg]C[0.96 0.04 0.00 2.5 v=165];B[kh]C[0.97 0.03 0.00 2.4 v=162];W[lg]C[0.97 0.03 0.00 2.3 v=165];B[cl]C[0.97 0.03 0.00 2.3 v=165];W[dm]C[0.96 0.04 0.00 2.4 v=164];B[cm]C[0.98 0.02 0.00 2.2 v=165];W[dn]C[0.97 0.03 0.00 2.2 v=165];B[lh]C[0.99 0.01 0.00 2.0 v=160];W[mg]C[0.98 0.02 0.00 1.8 v=165];B[ng]C[0.99 0.01 0.00 1.2 v=163];W[mh]C[0.99 0.01 0.00 2.1 v=165];B[nh]C[0.99 0.01 0.00 1.6 v=165];W[nf]C[0.99 0.01 0.00 2.3 v=165];B[mi]C[0.99 0.01 0.00 1.5 v=165];W[mf]C[0.99 0.01 0.00 2.0 v=165];B[cn]C[1.00 0.00 0.00 0.9 v=163];W[]C[1.00 0.00 0.00 1.0 v=161 result=W+1])
                (;FF[4]AP[katago]GM[40]SZ[12]PB[dotsgame-s7866496-d731239]PW[dotsgame-s7782144-d725682]KM[0.5]RU[dotsCaptureEmptyBase0sui0]RE[B+R]AB[ef][hf][fg][gg]AW[ff][gf][eg][hg]C[startTurnIdx=0,initTurnNum=8,gameHash=D5366A7FDE5C2AF8DCAE97924260B04E,gtype=normal];B[df]C[0.81 0.19 0.00 1.3 v=164];W[fe]C[0.77 0.23 0.00 1.2 v=165];B[fh]C[0.81 0.19 0.00 1.5 v=165];W[dg]C[0.76 0.24 0.00 1.0 v=165];B[cf]C[0.82 0.18 0.00 1.5 v=165];W[cg]C[0.78 0.22 0.00 1.1 v=160];B[if]C[0.83 0.17 0.00 1.5 v=165];W[ig]C[0.80 0.20 0.00 1.3 v=165];B[jf]C[0.85 0.15 0.00 1.6 v=164];W[jg]C[0.82 0.18 0.00 1.4 v=162];B[kg]C[0.85 0.15 0.00 1.6 v=164];W[jh]C[0.81 0.19 0.00 1.5 v=165];B[kh]C[0.85 0.15 0.00 1.7 v=165];W[ji]C[0.82 0.18 0.00 1.6 v=164];B[bg]C[0.86 0.14 0.00 1.9 v=164];W[ch]C[0.82 0.18 0.00 1.9 v=165];B[ki]C[0.86 0.14 0.00 2.1 v=165];W[ii]C[0.83 0.17 0.00 1.9 v=164];B[fi]C[0.85 0.15 0.00 2.2 v=165];W[fd]C[0.79 0.21 0.00 1.5 v=165];B[jj]C[0.76 0.24 0.00 1.4 v=163];W[ij]C[0.51 0.49 0.00 -1.1 v=160];B[ik]C[0.67 0.33 0.00 0.6 v=165];W[jk]C[0.63 0.37 0.00 0.3 v=165];B[kj]C[0.75 0.25 0.00 1.5 v=165];W[hk]C[0.75 0.25 0.00 1.5 v=164];B[il]C[0.79 0.21 0.00 2.0 v=165];W[hj]C[0.77 0.22 0.00 1.8 v=164];B[bi]C[0.84 0.16 0.00 2.4 v=164];W[dj]C[0.77 0.23 0.00 2.0 v=164];B[ej]C[0.78 0.22 0.00 2.1 v=165];W[dk]C[0.68 0.32 0.00 1.0 v=165];B[di]C[0.17 0.83 0.00 -3.7 v=162];W[ci]C[0.07 0.93 0.00 -4.3 v=164];B[cj]C[0.05 0.95 0.00 -4.9 v=165];W[kf]C[0.02 0.98 0.00 -4.7 v=163];B[hl]C[0.02 0.98 0.00 -5.9 v=163];W[gk]C[0.01 0.99 0.00 -7.8 v=165];B[ek]C[0.03 0.97 0.00 -5.2 v=165];C[0.01 0.99 0.00 -7.4 v=165 result=B+R])
            """.trimIndent()
        val gameSettings = GameSettings(
            path = null,
            bigString,
            game = 0,
            node = 0,
        )

        assertTrue(saveClassSettings(gameSettings, directory = tempDirectory))

        val newGameSettings = loadClassSettings(GameSettings.Default, directory = tempDirectory)

        assertEquals(bigString, newGameSettings.sgf)
    }

    @AfterEach
    fun cleanup() {
        Paths.get(tempDirectory, ThisAppName).toFile().listFiles()?.forEach { file ->
            require(!file.isDirectory)
            file.delete()
        }
    }
}