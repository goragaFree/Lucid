package dev.lucid.util;

import net.minecraft.client.Minecraft;

/** Быстрый доступ к Minecraft: просто implements Wrapper и пользуйся mc. */
public interface Wrapper {
    Minecraft mc = Minecraft.getInstance();
}
