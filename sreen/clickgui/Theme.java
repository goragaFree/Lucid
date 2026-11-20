package dev.lucid.sreen.clickgui;

/** Все размеры и цвета ClickGUI в одном месте. Цвета ARGB — альфа обязательна. */
public final class Theme {

    public static final float PANEL_WIDTH = 120;
    public static final float PANEL_GAP = 10;
    public static final float PANEL_HEIGHT = 250;   // фиксированная высота колонки (ужимается под маленький экран)
    public static final float SCREEN_MARGIN = 20;   // минимальный отступ колонок от краёв экрана
    // Радиус один на все четыре угла, а угол — squircle: кривизна нарастает плавно, поэтому
    // при небольшом радиусе угол читается мягче обычной дуги. Больше 16 не стоит — панель
    // начинает выглядеть как экран телефона.
    public static final float PANEL_RADIUS = 12;
    public static final float SHADOW_BLUR = 12;     // радиус размытия тени под панелью
    public static final float HEADER_HEIGHT = 22;
    public static final float MODULE_HEIGHT = 19;
    public static final float SETTING_HEIGHT = 14;
    public static final float SLIDER_HEIGHT = 20;
    public static final float OPTION_HEIGHT = 12;   // строка варианта в мультиселекте
    public static final float MODULE_PADDING = 8; // отступ текста модуля от краёв
    public static final float OUTLINE_WIDTH = 1;
    public static final float SCROLL_STEP = 24;     // прокрутка за один щелчок колёсика
    public static final float SCROLLBAR_WIDTH = 2;
    public static final float SCROLLBAR_PADDING = 2;

    // Шрифты: размер в пикселях интерфейса
    public static final float HEADER_FONT_SIZE = 10;
    public static final float MODULE_FONT_SIZE = 8;
    public static final float BIND_FONT_SIZE = 7;   // имя бинда — мельче названия модуля
    public static final float SETTING_FONT_SIZE = 7.5F;
    public static final float ICON_SIZE = 10;

    public static final int ACCENT = 0xFF7B5CFF;
    public static final int PANEL_BACKGROUND = 0xFF101015;
    public static final int HEADER_BACKGROUND = 0xFF17171F;
    // Цвет тени под панелью: как у колонок ClickGUI в 26.2
    public static final int SHADOW = 0x78000000;
    // Непрозрачный, как и обводка колонок в 26.2. Раньше это было ещё и вынужденно:
    // полупрозрачная обводка на скруглении просвечивала фоном за панелью. С тенью под
    // панелью такой нужды нет — край смешивается с ней, — но цвет оставлен прежним.
    public static final int OUTLINE = 0xFF404047;
    public static final int HOVER = 0x14FFFFFF;
    public static final int DIVIDER = 0xFF1A1A21; // линии между модулями (непрозрачные)
    public static final int SETTING_BACKGROUND = 0x40000000;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY = 0xFF9A9AA6;
    public static final int TRACK = 0xFF2A2A35;
    public static final int SCROLLBAR = 0x50FFFFFF;
    public static final int SCROLLBAR_ACTIVE = 0x90FFFFFF;
    public static final int KNOB = 0xFFFFFFFF;

    private Theme() {}
}
