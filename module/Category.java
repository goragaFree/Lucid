package dev.lucid.module;

import dev.lucid.util.render.font.Icons;

public enum Category {
    COMBAT("Combat", Icons.COMBAT),
    MOVEMENT("Movement", Icons.MOVEMENT),
    RENDER("Render", Icons.VISUALS),
    PLAYER("Player", Icons.PLAYER),
    MISC("Misc", Icons.WORLD);

    private final String displayName;
    private final char icon;

    Category(String displayName, char icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    /** Иконка из шрифта Golos (см. Icons). */
    public char getIcon() {
        return icon;
    }

    public String getDisplayName() {
        return displayName;
    }
}
