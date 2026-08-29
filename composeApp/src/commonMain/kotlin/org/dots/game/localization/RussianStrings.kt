package org.dots.game.localization

import org.dots.game.core.BaseMode
import org.dots.game.core.InitPosGenType
import org.dots.game.core.InitPosType
import org.dots.game.views.ConnectionDrawMode
import org.dots.game.views.KataGoDotsSettingsFileType
import org.dots.game.views.PolygonDrawMode

object RussianStrings : Strings {
    override fun boolToString(bool: Boolean): String = if (bool) "Да" else "Нет"

    // Common UI
    override val new = "Новая"
    override val reset = "Сбросить"
    override val load = "Загрузить"
    override val save = "Сохранить"
    override val saveAs = "Сохранить как"
    override val settings = "Настройки"
    override val open = "Открыть"
    override val browse = "Обзор"
    override val aiSettings = "Настройки ИИ"

    // Game info
    override val width = "Ширина"
    override val height = "Высота"
    override val move = "Ход"
    override val game = "Игра"
    override val komi = "Коми"
    override val firstPlayerDefaultName = "Первый"
    override val secondPlayerDefaultName = "Второй"

    // Rules
    override val initPosType = "Стартовая позиция"
    override val baseMode = "Режим захвата"
    override val initPosGenType = "Тип генерации"
    override val suicideAllowed = "Самоубийство разрешено"
    override val drawIsAllowed = "Возможность ничьи"
    override val captureByBorder = "Захват через край"

    override val createNewGame = "Создать игру"

    override fun initPosTypeLabel(type: InitPosType): String = when (type) {
        InitPosType.Empty -> "Пустая"
        InitPosType.Single -> "Точка"
        InitPosType.Cross -> "Скрест"
        InitPosType.DoubleCross -> "Двойной скрест"
        InitPosType.QuadrupleCross -> "4X"
        InitPosType.Custom -> "Пользовательская"
    }

    override fun baseModeLabel(mode: BaseMode): String = when (mode) {
        BaseMode.AtLeastOneOpponentDot -> "Хотя бы одна точка соперника"
        BaseMode.AnySurrounding -> "Любые области"
        BaseMode.OnlyOpponentDots -> "Только точки соперника (как в Го)"
    }

    override fun initPosGenTypeLabel(type: InitPosGenType): String = when (type) {
        InitPosGenType.Static -> "Статичная"
        InitPosGenType.RandomNotago -> "Случайная (Notago)"
        InitPosGenType.RandomMarlov -> "Случайная (Марлов)"
    }

    // Open Dialog
    override val pathOrContent = "Путь или содержимое"
    override val pathOrContentPlaceholder = "Введите путь к .sgf файлу, к каталогу с такими файлами или его содержимое"
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
    override val link = "Ссылка"
    override val copy = "Копировать"
    override val saveDialogTitle = "Сохранить игру"
    override val refine = "Уточнить"
    override val refinementIsFailed = "Игры не могут быть уточнены, поэтому они не сохраняются"
    override val tooLongLinkMessage = "Недоступно (слишком длинная ссылка)"

    // Settings
    override val connectionDrawMode = "Отрисовка соединений"
    override val polygonDrawMode = "Отрисовка окружений"
    override val diagonalConnections = "Диагональные соединения"
    override val threats = "Угрозы окружения"
    override val surroundings = "Области под угрозой"
    override val developerMode = "Режим разработчика"
    override val experimentalMode = "Экспериментальный режим"
    override val version: String = "Версия"

    // AI Settings
    override fun aiSettingsFilePath(fileType: KataGoDotsSettingsFileType): String {
        return when (fileType) {
            KataGoDotsSettingsFileType.Exe -> "Исполняемый файл"
            KataGoDotsSettingsFileType.Model -> "Файл модели"
            KataGoDotsSettingsFileType.Config -> "Файл конфигурации"
        }
    }
    override fun aiSettingsSelectFile(fileType: KataGoDotsSettingsFileType): String {
        return "Выберите${fileType.extensions.filter { it.isNotEmpty() }.joinToString(",") { " .${it}" }} файл"
    }
    override val default: String = "По-умолчанию"
    override val initialization: String = "Инициализация..."
    override val initialize: String = "Инициализировать"

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
    override val ground = "Заземлиться"
    override val resign = "Сдаться"
    override val nextGame = "Следующая игра"
    override val previousGame = "Предыдущая игра"
    override val aiMove = "Ход бота"
    override val aiThinking = "Бот думает..."
    override val aiMoveDescription = """Движок делает ход за игрока, который должен ходить.
Долгое нажатие включает режим автохода, в котором движок автоматически отвечает на каждый ход."""
    override val autoMoveDescription = """Режим автохода включён: движок автоматически отвечает на каждый ход.
Долгое нажатие выключает его."""

    override val analyzing = "Анализ..."
    override val moveAnalysis = "Анализ ходов"
    override val moveAnalysisDescription = """Все возможные ходы оцениваются для игрока, который должен сделать ход.
Зелёным отмечены лучшие ходы, красным — худшие (учитываются и вероятность победы, и перевес в очках).
Чем прозрачнее ход, тем меньше визитов он получил, а значит тем менее надёжна его оценка."""
    override val scoreLead = "Перевес в очках"
    override val prior = "Априорная оценка"
    override val variation = "Вариант"
    override val utility = "Полезность"
    override val lowerConfidenceBound = "Нижняя граница доверия"
    override val deviation = "Отклонение"
    override val edgeVisits = "Визиты рёбер"
    override val symmetryOf = "Симметрично"
    override val candidateMoves = "Ходы-кандидаты"
    override val candidateMovesDescription = """Подсвечивает на поле ходы, которые рассматривает движок; лучший обведён.
Наведите курсор на ход, чтобы увидеть его вероятность победы.
Оценка каждого показанного хода перечисляется ниже независимо от того, включена ли подсветка."""
    override val ownership = "Владение"
    override val ownershipDescription = """Закрашивает каждую позицию цветом игрока, который предположительно её захватит.
Чем насыщеннее цвет, тем увереннее движок в том, кому достанется позиция.
Наведите курсор на позицию, чтобы увидеть точное значение."""
    override fun moreAnalyzedMoves(count: Int): String = "и ещё $count"

    override val winRate = "Вероятность победы"
    override val score = "Очки"
    override val visits = "Визиты"
    override val weight = "Вес"
    override val winRateDescription = """100% - Второй игрок выигрывает
50% - Ничья
0% - Первый игрок выигрывает"""
    override val scoreDescription = """> 0 Второй игрок выигрывает
= 0 Ничья
< 0 Первый игрок выигрывает"""
    override val weightDescription = "Чем больше вес, тем важнее этот ход для обучения"

    override val sgfStats = "SGF Статистика"
    override val sgfComment = "SGF Комментарий"
    override val avgRemainingMoves = "Оставшиеся ходы (в среднем)"
    override val avgRemainingMovesComment = """Чем ближе значение к 100%, тем более реалистичные игры.
Значение ближе к нулю означает, что игры были сыграны почти до исчерпания всех возможных ходов.
Обычно это означает, что игры не соответствуют реальному игровому поведению, так как люди не играют до самого конца (они заземляются намного раньше).
""".trimIndent()
    override val draws = "Ничьи"

    // Game result
    override val result = "Результат"
    override val reason = "Причина"
    override val draw = "Ничья"
    override val win = "победил"
    override val interrupt = "прерывание"
    override val resignation = "сдача"
    override val time = "время"
    override val unknown = "неизвестно"
}
