package dev.lucid.util.render.font;

/**
 * Коды иконок, вшитых в шрифты Golos Text (область частного использования Unicode).
 *
 * <p>Иконки — обычные глифы шрифта: лежат в том же MSDF-атласе, что и буквы
 * ({@code regular}, {@code medium}, {@code semibold}), рисуются тем же шейдером текста,
 * остаются резкими на любом размере и красятся любым цветом. Их можно вставлять прямо
 * в строку: {@code Icons.SEARCH + " Поиск"}.</p>
 *
 * <p>Каждая иконка — квадрат ровно в один кегль: {@code Render2D.icon(.., size, ..)}
 * займёт {@code size × size} от левого верхнего угла.</p>
 *
 * <p>Коды фиксированы: глифы с этими номерами запечены в regular/medium/semibold.</p>
 */
public final class Icons {

    /** Знак Lucid — ножка буквы L. Рисуется вместе с {@link #LOGO_FOOT} в том же квадрате. */
    public static final char LOGO = '\uE000';
    public static final char COMBAT = '\uE001';
    public static final char MOVEMENT = '\uE002';
    public static final char VISUALS = '\uE003';
    public static final char PLAYER = '\uE004';
    public static final char WORLD = '\uE005';
    public static final char SEARCH = '\uE006';
    public static final char SETTINGS = '\uE007';
    public static final char CHEVRON_DOWN = '\uE008';
    public static final char CHECK = '\uE009';

    /** Основание буквы L — отдельный глиф, чтобы красить его своим цветом. */
    public static final char LOGO_FOOT = '\uE00A';

    private Icons() {
    }
}
