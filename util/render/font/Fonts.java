package dev.lucid.util.render.font;

/**
 * Шрифты клиента.
 *
 * <p>Атлас читается один раз при первом обращении и дальше живёт всю сессию. Один атлас
 * обслуживает все размеры сразу, поэтому плодить копии под кегли не нужно.</p>
 *
 * <p>{@link #bold()} — жирный шрифт. Остальные — Golos Text в трёх начертаниях.
 * Иконки вшиты прямо в Golos глифами U+E000.. ({@link Icons}) и лежат в том же
 * MSDF-атласе, что и буквы.</p>
 */
public final class Fonts {

    private static FontAtlas bold;
    private static FontAtlas regular;
    private static FontAtlas medium;
    private static FontAtlas semibold;
    private static FontAtlas richBold;
    private static FontAtlas targetIcons;

    private Fonts() {
    }

    /** Оригинальный MSDF-шрифт ватермарки Rich; остальные шрифты Lucid не меняет. */
    public static FontAtlas richBold() {
        if (richBold == null) richBold = FontAtlas.load("rich_bold");
        return richBold;
    }

    /** Сглаженные контурные иконки Target HUD: U+E100 игрок, U+E101 сердце. */
    public static FontAtlas targetIcons() {
        if (targetIcons == null) targetIcons = FontAtlas.load("target_icons");
        return targetIcons;
    }

    /** Жирный шрифт. */
    public static FontAtlas bold() {
        if (bold == null) {
            bold = FontAtlas.load("bold");
        }
        return bold;
    }

    /** Golos Text Regular: подписи и значения настроек. */
    public static FontAtlas regular() {
        if (regular == null) {
            regular = FontAtlas.load("regular");
        }
        return regular;
    }

    /** Golos Text Medium: вкладки, плитки, кнопки. */
    public static FontAtlas medium() {
        if (medium == null) {
            medium = FontAtlas.load("medium");
        }
        return medium;
    }

    /** Golos Text SemiBold: имена модулей. */
    public static FontAtlas semibold() {
        if (semibold == null) {
            semibold = FontAtlas.load("semibold");
        }
        return semibold;
    }

    /**
     * Нарисует ли клиент этот символ.
     *
     * <p>В атласе глифов конечное число, а на отсутствующий знак {@link FontAtlas}
     * отдаёт вопросительный знак. Поля ввода этим и пользуются: пускают только те
     * символы, у которых есть настоящая буква.</p>
     */
    public static boolean hasGlyph(int codePoint) {
        return bold().has(codePoint) || regular().has(codePoint);
    }
}
