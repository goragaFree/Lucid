package dev.lucid.util.render;

/** Работа с цветами ARGB. */
public final class ColorUtil {

    private ColorUtil() {}

    public static int alpha(int color) { return color >>> 24; }
    public static int red(int color) { return (color >> 16) & 0xFF; }
    public static int green(int color) { return (color >> 8) & 0xFF; }
    public static int blue(int color) { return color & 0xFF; }

    public static int argb(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    /** Заменить альфу (0..255). */
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    /** Умножить альфу на коэффициент 0..1 — удобно для анимаций появления. */
    public static int multiplyAlpha(int color, float factor) {
        return withAlpha(color, Math.round(alpha(color) * factor));
    }

    /** Плавный переход между двумя цветами, t = 0..1. */
    public static int interpolate(int from, int to, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return argb(
                Math.round(alpha(from) + (alpha(to) - alpha(from)) * t),
                Math.round(red(from) + (red(to) - red(from)) * t),
                Math.round(green(from) + (green(to) - green(from)) * t),
                Math.round(blue(from) + (blue(to) - blue(from)) * t)
        );
    }

    // ------------------------------------------------------------------- HSV
    // Для пикера цвета: цвет раскладывается на тон/насыщенность/яркость,
    // компонент правит их по отдельности и собирает обратно.

    /**
     * RGB → HSV: {@code out[0]} — тон 0..360, {@code out[1]} — насыщенность 0..1,
     * {@code out[2]} — яркость 0..1. Альфа не участвует. Чёрный даёт тон 0 —
     * это нормально: при v = 0 тон ни на что не влияет.
     */
    public static float[] hsv(int color) {
        float r = red(color) / 255.0F;
        float g = green(color) / 255.0F;
        float b = blue(color) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue;
        if (delta < 1.0E-6F) {
            hue = 0.0F;
        } else if (max == r) {
            hue = 60.0F * (((g - b) / delta) % 6.0F);
        } else if (max == g) {
            hue = 60.0F * ((b - r) / delta + 2.0F);
        } else {
            hue = 60.0F * ((r - g) / delta + 4.0F);
        }
        if (hue < 0.0F) hue += 360.0F;
        return new float[]{hue, max <= 0.0F ? 0.0F : delta / max, max};
    }

    /** Тон 0..360, насыщенность и яркость 0..1 → непрозрачный ARGB. */
    public static int fromHsv(float hue, float saturation, float value) {
        hue = ((hue % 360.0F) + 360.0F) % 360.0F;
        saturation = Math.max(0.0F, Math.min(1.0F, saturation));
        value = Math.max(0.0F, Math.min(1.0F, value));

        float c = value * saturation;
        int sector = (int) (hue / 60.0F) % 6;
        float remainder = hue / 60.0F - sector;
        float x = c * (1.0F - Math.abs(remainder * 2.0F - 1.0F));
        float m = value - c;

        float r = 0.0F;
        float g = 0.0F;
        float b = 0.0F;
        switch (sector) {
            case 0 -> { r = c; g = x; }
            case 1 -> { r = x; g = c; }
            case 2 -> { g = c; b = x; }
            case 3 -> { g = x; b = c; }
            case 4 -> { r = x; b = c; }
            default -> { r = c; b = x; }
        }
        return argb(255,
                Math.round((r + m) * 255.0F),
                Math.round((g + m) * 255.0F),
                Math.round((b + m) * 255.0F));
    }
}
