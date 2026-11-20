package dev.lucid.sreen.hud;

import dev.lucid.util.Wrapper;
import dev.lucid.util.render.Render2D;
import dev.lucid.util.render.font.Fonts;
import dev.lucid.util.render.pipelines.PlayerHeadRenderState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Компактная Staff-панель: роли из scoreboard, наличие в табе — Online/Vanish. */
public final class Staff implements Wrapper {
    static final float ROW_HEIGHT = 22f;
    static final float FACE_SIZE = 14f;
    static final float NAME_SIZE = 8.5f;
    static final float ROLE_SIZE = 6.3f;
    static final float STATUS_SIZE = 7.5f;
    private static final float TEXT_X = 28f;
    private static final float STATUS_GAP = 8f;
    private static final int ONLINE = 0xFF86DEA0;
    private static final int VANISH = 0xFFFF9A9F;
    private static final int ROLE_COLOR = 0xFFB5B5C2;
    private static final long REFRESH_NS = 250_000_000L;
    private static final Identifier FALLBACK = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern DIGITS = Pattern.compile("[0-9]+");
    private static final Pattern FORMATTING = Pattern.compile("§[0-9A-FK-ORX]", Pattern.CASE_INSENSITIVE);
    private static final RoleRule[] ROLES = {
            rule("Owner", "owner|влад[\\p{L}]*"),
            rule("Admin", "admin(?:istrator)?|адм[\\p{L}]*"),
            rule("Mod", "mod(?:erator)?|мод[\\p{L}]*"),
            rule("Helper", "helper|хелп[\\p{L}]*"),
            rule("Trainee", "trainee|стаж[\\p{L}]*"),
            rule("Dev", "dev(?:eloper)?|разраб[\\p{L}]*"),
            rule("Curator", "curator|куратор[\\p{L}]*"),
            rule("YouTube", "yt|youtube|ютуб[\\p{L}]*"),
            rule("Support", "support|саппорт[\\p{L}]*|суппорт[\\p{L}]*")
    };
    private List<Row> rows = List.of();
    private Object lastWorld;
    private Object lastConnection;
    private long refreshedAt;

    record Row(String name, String role, Identifier skin, boolean vanished) {}
    private record RoleRule(String label, Pattern pattern) {}

    private static RoleRule rule(String label, String words) {
        return new RoleRule(label, Pattern.compile("(?<![\\p{L}\\p{N}_])(?:" + words
                + ")(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    }

    static boolean validName(String name) {
        return name != null && USERNAME.matcher(name).matches() && !DIGITS.matcher(name).matches();
    }

    static String role(String prefix) {
        if (prefix == null) return null;
        String clean = FORMATTING.matcher(prefix).replaceAll("").toLowerCase(Locale.ROOT);
        for (RoleRule rule : ROLES) {
            if (!rule.pattern().matcher(clean).find()) continue;
            if (rule.label().equals("Admin") || rule.label().equals("Mod") || rule.label().equals("Helper")) {
                if (clean.contains("ст.") || clean.contains("st.")) return "St." + rule.label();
                if (clean.contains("мл.") || clean.contains("ml.")) return "Ml." + rule.label();
            }
            return rule.label();
        }
        return null;
    }

    private void refresh() {
        var connection = mc.getConnection();
        if (mc.player == null || mc.level == null || connection == null) {
            rows = List.of(); lastWorld = null; lastConnection = null; refreshedAt = 0;
            return;
        }
        long now = System.nanoTime();
        if (lastWorld == mc.level && lastConnection == connection && now - refreshedAt < REFRESH_NS) return;
        boolean sameWorld = lastWorld == mc.level && lastConnection == connection;
        Map<String, Identifier> previousSkins = new HashMap<>();
        if (sameWorld) for (Row row : rows) previousSkins.put(row.name().toLowerCase(Locale.ROOT), row.skin());
        lastWorld = mc.level; lastConnection = connection; refreshedAt = now;
        Map<String, PlayerInfo> online = new HashMap<>();
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            if (info.getProfile() != null && info.getProfile().name() != null)
                online.put(info.getProfile().name().toLowerCase(Locale.ROOT), info);
        }
        String self = mc.player.getGameProfile().name();
        Map<String, Row> found = new TreeMap<>();
        List<PlayerTeam> teams = new ArrayList<>(mc.level.getScoreboard().getPlayerTeams());
        teams.sort(Comparator.comparing(PlayerTeam::getName));
        for (PlayerTeam team : teams) {
            String role = role(team.getPlayerPrefix().getString());
            if (role == null) continue;
            for (String name : team.getPlayers()) {
                if (!validName(name) || name.equalsIgnoreCase(self)) continue;
                String key = name.toLowerCase(Locale.ROOT);
                PlayerInfo info = online.get(key);
                Identifier skin = info == null ? previousSkins.getOrDefault(key, FALLBACK)
                        : info.getSkin().body().texturePath();
                found.putIfAbsent(key, new Row(name, role, skin, info == null));
            }
        }
        rows = List.copyOf(found.values());
    }

    static float width(List<Row> rows) {
        float width = HudCard.MIN_WIDTH;
        for (Row row : rows) {
            float text = Math.max(Fonts.regular().width(row.name(), NAME_SIZE),
                    Fonts.regular().width(row.role(), ROLE_SIZE));
            float status = Fonts.regular().width(row.vanished() ? "Vanish" : "Online", STATUS_SIZE);
            width = Math.max(width, TEXT_X + text + STATUS_GAP + status + 8f);
        }
        return (float) Math.ceil(width);
    }

    public void render(GuiGraphics graphics, boolean preview) {
        refresh();
        if (rows.isEmpty() && !preview) return;
        // Большой список не выходит за экран; число активных в шапке не рисуется.
        int maxRows = Math.max(0, (int) ((graphics.guiHeight() - 8f - HudCard.HEADER_HEIGHT
                - HudCard.BODY_TOP - HudCard.BODY_BOTTOM) / ROW_HEIGHT));
        List<Row> visible = rows.subList(0, Math.min(rows.size(), maxRows));
        float width = width(visible);
        float height = visible.isEmpty() ? HudCard.emptyHeight() : HudCard.height(visible.size(), ROW_HEIGHT);
        DragHud.Rect rect = DragHud.place(DragHud.Slot.STAFF, graphics, 4f,
                Math.max(4f, (graphics.guiHeight() - height) * .5f), width, height);
        float x = Math.round(rect.x()), y = Math.round(rect.y());
        HudCard.draw(graphics, x, y, width, height, "Staff");
        float rowY = HudCard.bodyTop(y);
        for (Row row : visible) {
            Render2D.textInRow(graphics, Fonts.regular(), row.name(), x + TEXT_X,
                    rowY + 1f, 10f, NAME_SIZE, HudCard.TEXT);
            Render2D.textInRow(graphics, Fonts.regular(), row.role(), x + TEXT_X,
                    rowY + 12f, 8f, ROLE_SIZE, ROLE_COLOR);
            String status = row.vanished() ? "Vanish" : "Online";
            Render2D.textInRow(graphics, Fonts.regular(), status,
                    x + width - 8f - Fonts.regular().width(status, STATUS_SIZE),
                    rowY, ROW_HEIGHT, STATUS_SIZE, row.vanished() ? VANISH : ONLINE);
            rowY += ROW_HEIGHT;
        }
        graphics.nextStratum();
        rowY = HudCard.bodyTop(y);
        for (Row row : visible) {
            graphics.guiRenderState.submitGuiElement(new PlayerHeadRenderState(graphics,
                    row.skin(), x + 8f, rowY + 3f, FACE_SIZE, 2.5f));
            rowY += ROW_HEIGHT;
        }
    }
}
