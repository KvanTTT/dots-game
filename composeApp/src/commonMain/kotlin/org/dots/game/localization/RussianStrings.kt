package org.dots.game.localization

import org.dots.game.core.BaseMode
import org.dots.game.core.InitPosType
import org.dots.game.views.ConnectionDrawMode
import org.dots.game.views.KataGoDotsSettingsFileType
import org.dots.game.views.PolygonDrawMode

object RussianStrings : Strings {
    // Common UI
    override val new = "Новая"
    override val reset = "Сбросить"
    override val load = "Загрузить"
    override val save = "Сохранить"
    override val settings = "Настройки"
    override val open = "Открыть"
    override val browse = "Обзор"
    override val create = "Создать"
    override val aiSettings = "Настройки ИИ"

    // Game info
    override val width = "Ширина"
    override val height = "Высота"
    override val move = "Ход"
    override val game = "Игра"
    override val komi = "Коми"
    override val firstPlayerDefaultName = "Первый"
    override val secondPlayerDefaultName = "Второй"

    // New Game Dialog
    override val initPosType = "Стартовая позиция"
    override val baseMode = "Режим захвата"
    override val captureByBorder = "Захват через край"
    override val suicideAllowed = "Самоубийство разрешено"
    override val drawIsAllowed = "Возможность ничьи"
    override val createNewGame = "Создать новую игру"
    override val randomStartPosition = "Случайная стартовая позиция"

    override fun initPosTypeLabel(type: InitPosType): String = when (type) {
        InitPosType.Empty -> "Нет"
        InitPosType.Single -> "Точка"
        InitPosType.Cross -> "Скрест"
        InitPosType.DoubleCross -> "Двойной скрест"
        InitPosType.QuadrupleCross -> "4X"
        InitPosType.Custom -> "Пользовательская"
    }

    override fun baseModeLabel(mode: BaseMode): String = when (mode) {
        BaseMode.AtLeastOneOpponentDot -> "Захват при одной точке"
        BaseMode.AnySurrounding -> "Захват пустой области"
        BaseMode.AllOpponentDots -> "Захват при полном заполнении"
    }

    // Open Dialog
    override val pathOrContent = "Путь или содержимое"
    override val pathOrContentPlaceholder = "Введите путь к .sgf файлу или его содержимое"
    override val rewindToEnd = "Просмотр с конца"
    override val addFinishingMove = "Добавить завершающий ход"
    override val openSgfFile = "Открыть SGF файл"

    // Save Dialog
    override val sgf = "SGF"
    override val fieldRepresentation = "Отображение поля"
    override val printNumbers = "Печатать номера"
    override val printCoordinates = "Печатать координаты"
    override val debugInfo = "Отладочная информация"
    override val padding = "Отступ"
    override val path = "Путь"
    override fun saveDialogTitle(isSgf: Boolean) = "Сохранить игру в .${if (isSgf) "sgf" else "txt"}"

    // Settings
    override val connectionDrawMode = "Отрисовка соединений"
    override val polygonDrawMode = "Отрисовка окружений"
    override val diagonalConnections = "Диагональные соединения"
    override val threats = "Угрозы окружения"
    override val surroundings = "Области под угрозой"
    override val developerMode = "Режим разработчика"
    override val version: String = "Версия"

    // AI Settings
    override fun aiSettingsFilePath(fileType: KataGoDotsSettingsFileType): String {
        return "Путь к .$fileType файлу"
    }
    override fun aiSettingsSelectFile(fileType: KataGoDotsSettingsFileType): String {
        return "Выберите .$fileType файл"
    }
    override val default: String = "По-умолчанию"
    override val checking: String = "Проверка..."
    override val check: String = "Проверить"

    override fun connectionDrawModeLabel(mode: ConnectionDrawMode): String = when (mode) {
        ConnectionDrawMode.None -> "Нет"
        ConnectionDrawMode.Lines -> "Линии"
        ConnectionDrawMode.PolygonOutline -> "Контуры полигонов"
        ConnectionDrawMode.PolygonFill -> "Заливка полигонов"
        ConnectionDrawMode.PolygonOutlineAndFill -> "Контуры и заливка полигонов"
    }

    override fun polygonDrawModeLabel(mode: PolygonDrawMode): String = when (mode) {
        PolygonDrawMode.Outline -> "Контур"
        PolygonDrawMode.Fill -> "Заливка"
        PolygonDrawMode.OutlineAndFill -> "Контур и заливка"
    }

    override val language = "Язык"
    override val languageName = "Русский"

    override val nextPlayer = "Следующий игрок"
    override val firstPlayer = "Игрок 1"
    override val secondPlayer = "Игрок 2"
    override val ground = "Заземлиться"
    override val resign = "Сдаться"
    override val nextGame = "Следующая игра"
    override val previousGame = "Предыдущая игра"
    override val aiMove = "Ход бота"
    override val aiThinking = "Бот думает..."
    override val autoMove = "Авто"
}
