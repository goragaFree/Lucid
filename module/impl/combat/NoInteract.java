package dev.lucid.module.impl.combat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.BooleanSetting;
import dev.lucid.setting.MultiSelectSetting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Отменяет ПКМ по выбранным блокам и сущностям: сундук не открывается, торговля
 * не запускается, а ваниль вместо этого использует предмет в руке.
 *
 * <p>Перехват живёт в {@code MultiPlayerGameMode}, а не в {@code Minecraft#startUseItem}:
 * пакеты на сервер уходят именно оттуда, и иногда ещё до локальной проверки.
 * {@code interactAt} и {@code interact} шлют {@code ServerboundInteractPacket} первым
 * делом, {@code useItemOn} — {@code ServerboundUseItemOnPacket} внутри
 * {@code startPrediction}. Просто «не показать экран» на клиенте мало: сервер всё равно
 * ответит {@code ClientboundOpenScreenPacket}. Возврат {@code PASS} из HEAD обрывает
 * метод до отправки пакета, а {@code startUseItem}, получив {@code PASS}, сам переходит
 * к {@code gameMode.useItem(...)} — это и есть использование предмета в руке.</p>
 *
 * <p>Сущностей приходится закрывать двумя хуками: в 1.21.11 {@code startUseItem}
 * сначала зовёт {@code interactAt}, и только если результат не {@code consumesAction()},
 * зовёт {@code interact}. У {@code interactAt} свой пакет, поэтому хука только на
 * {@code interact} не хватило бы: житель уже получил бы команду открыть торговлю.</p>
 *
 * <p>Левый клик модуль не трогает вообще: атака уходит через
 * {@code MultiPlayerGameMode#attack} из {@code Minecraft#continueAttack}, а не через
 * эти три метода. Отмена ПКМ по мобу ничего не меняет в бою.</p>
 *
 * <p>В меню не реестровые ID, а короткие имена групп: 32 блока (из них 17 шалкеров)
 * и 32 типа сущностей (из них 20 лодок) превратили бы панель в простыню. Группа —
 * это просто имя набора ID: ни одна цель при этом не выброшена, а проверка в хуке
 * всё та же — по {@code BuiltInRegistries.*.getKey(...)}. Обратная сторона: выбрать
 * один {@code white_shulker_box}, не выбирая остальные, больше нельзя — галочка теперь
 * на всю группу. Цели вне включённых групп идут ванилью. Ванильный неймспейс в ID
 * опускается, а чужой остаётся целиком и потому не совпадёт ни с одной ванильной
 * группой.</p>
 */
public final class NoInteract extends Module {

    /** Порядок групп = порядок строк в меню и порядок записи в конфиг. */
    private static final String[] BLOCK_GROUPS = {
            "Chests", "Furnaces", "Workbench", "Anvils", "Enchanting Table",
            "Hoppers", "Dispensers", "Droppers", "Shulker Boxes"
    };

    private static final String[] ENTITY_GROUPS = {
            "Villagers", "Item Frames", "Painting", "Minecarts", "Boats"
    };

    /**
     * Состав групп: всё, что раньше было отдельными галочками, плюс полные семейства
     * лодок и вагонеток. ID сверены с байткодом {@code Blocks} и {@code EntityType}:
     * 17 шалкер-боксов, 7 вагонеток, 10 пар «лодка + лодка с сундуком» (бамбук — плот).
     * ПКМ по обычной лодке и по лодке с сундуком идут через один и тот же путь, поэтому
     * закрывать надо обе, иначе посадка в простую лодку останется ванильной.
     */
    private static final Map<String, Set<String>> BLOCK_IDS = Map.of(
            "Chests", Set.of("chest", "trapped_chest", "ender_chest", "barrel"),
            "Furnaces", Set.of("furnace", "blast_furnace", "smoker"),
            "Workbench", Set.of("crafting_table"),
            "Anvils", Set.of("anvil", "chipped_anvil", "damaged_anvil"),
            "Enchanting Table", Set.of("enchanting_table"),
            "Hoppers", Set.of("hopper"),
            "Dispensers", Set.of("dispenser"),
            "Droppers", Set.of("dropper"),
            "Shulker Boxes", Set.of(
                    "shulker_box",
                    "white_shulker_box", "orange_shulker_box", "magenta_shulker_box",
                    "light_blue_shulker_box", "yellow_shulker_box", "lime_shulker_box",
                    "pink_shulker_box", "gray_shulker_box", "light_gray_shulker_box",
                    "cyan_shulker_box", "purple_shulker_box", "blue_shulker_box",
                    "brown_shulker_box", "green_shulker_box", "red_shulker_box",
                    "black_shulker_box"
            )
    );

    private static final Map<String, Set<String>> ENTITY_IDS = Map.of(
            "Villagers", Set.of("villager", "wandering_trader"),
            "Item Frames", Set.of("item_frame", "glow_item_frame"),
            "Painting", Set.of("painting"),
            "Minecarts", Set.of("minecart", "chest_minecart", "furnace_minecart",
                    "tnt_minecart", "hopper_minecart", "spawner_minecart", "command_block_minecart"),
            "Boats", Set.of(
                    "oak_boat", "oak_chest_boat",
                    "spruce_boat", "spruce_chest_boat",
                    "birch_boat", "birch_chest_boat",
                    "jungle_boat", "jungle_chest_boat",
                    "acacia_boat", "acacia_chest_boat",
                    "dark_oak_boat", "dark_oak_chest_boat",
                    "mangrove_boat", "mangrove_chest_boat",
                    "cherry_boat", "cherry_chest_boat",
                    "pale_oak_boat", "pale_oak_chest_boat",
                    "bamboo_raft", "bamboo_chest_raft"
            )
    );

    // Модуль создаётся один раз в ModuleManager.init(); миксину незачем искать его
    // в реестре модулей на каждый правый клик.
    private static NoInteract instance;

    private final MultiSelectSetting blocks = groupsSetting("Blocks", BLOCK_GROUPS, BLOCK_IDS);
    private final MultiSelectSetting entities = groupsSetting("Entities", ENTITY_GROUPS, ENTITY_IDS);
    private final BooleanSetting sneakBypass = new BooleanSetting("Sneak Bypass", true);

    public NoInteract() {
        super("NoInteract", "Use the held item instead of interacting", Category.COMBAT);
        addSettings(blocks, entities, sneakBypass);
        instance = this;
    }

    /**
     * До групп конфиг хранил реестровые ID. Живой клиент мог перезаписать файл старыми
     * значениями уже после обновления, а {@link MultiSelectSetting#fromJson} просто
     * выкинул бы их — и все группы оказались бы выключенными. Настройка поэтому сама
     * переводит старые ID в имена групп: группе достаточно одного её ID. Новые имена
     * проходят как есть, неизвестное (удалённая цель, модный ID) молча отбрасывается.
     */
    private static MultiSelectSetting groupsSetting(String name, String[] options,
                                                    Map<String, Set<String>> byGroup) {
        Set<String> known = Set.of(options);
        return new MultiSelectSetting(name, List.of(options), options) {
            @Override
            public void fromJson(JsonElement json) {
                if (!json.isJsonArray()) return;
                Set<String> migrated = new LinkedHashSet<>();
                for (JsonElement element : json.getAsJsonArray()) {
                    if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) continue;
                    String value = element.getAsString();
                    if (known.contains(value)) {
                        migrated.add(value);
                        continue;
                    }
                    for (Map.Entry<String, Set<String>> entry : byGroup.entrySet()) {
                        if (entry.getValue().contains(value)) {
                            migrated.add(entry.getKey());
                            break;
                        }
                    }
                }
                JsonArray array = new JsonArray();
                for (String group : migrated) array.add(group);
                super.fromJson(array);
            }
        };
    }

    /**
     * Точка входа для {@code NoInteractMixin}: путь ПКМ по блоку.
     *
     * <p>Состояние блока читаем уже после {@link #active(Player)}: выключенный модуль
     * или присевший игрок не должны даже дёргать мир в хуке, который зовётся на каждый
     * правый клик и на каждый повтор зажатой кнопки. Позицию берём из аргумента
     * {@code hitResult}, а не из {@code mc.hitResult}: ваниль перебирает обе руки подряд,
     * и глобальный прицел к тому моменту уже мог обновиться.</p>
     */
    public static boolean shouldCancelBlock(Player player, BlockHitResult hitResult) {
        NoInteract module = active(player);
        if (module == null) return false;
        Level level = player.level();
        if (level == null) return false;
        Block block = level.getBlockState(hitResult.getBlockPos()).getBlock();
        Identifier key = BuiltInRegistries.BLOCK.getKey(block);
        return key != null && selected(module.blocks, BLOCK_IDS, id(key));
    }

    /** Точка входа для {@code NoInteractMixin}: оба пути ПКМ по сущности. */
    public static boolean shouldCancelEntity(Player player, Entity target) {
        NoInteract module = active(player);
        if (module == null) return false;
        EntityType<?> type = target.getType();
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return key != null && selected(module.entities, ENTITY_IDS, id(key));
    }

    /**
     * Один и тот же проход и для блоков, и для сущностей: идём по включённым группам
     * и ищем в них реестровый ID цели. Группы не пересекаются, но даже если бы ID
     * попал в две, результат от этого не изменился бы.
     */
    private static boolean selected(MultiSelectSetting setting, Map<String, Set<String>> groups, String targetId) {
        for (String group : setting.getValue()) {
            Set<String> ids = groups.get(group);
            if (ids != null && ids.contains(targetId)) return true;
        }
        return false;
    }

    /**
     * null — вмешиваться не нужно: модуля ещё нет, он выключен или игрок присел.
     * Присевшего проверяем по игроку из аргумента хука, а не по {@code mc.player}:
     * это тот самый {@code Player}, чьё состояние шифта ваниль уже отправит на сервер.
     */
    private static NoInteract active(Player player) {
        NoInteract module = instance;
        if (module == null || !module.isEnabled()) return null;
        return module.sneakBypass.getValue() && player.isShiftKeyDown() ? null : module;
    }

    /** Короткое имя для ванили, полное — для чужих неймспейсов. */
    private static String id(Identifier key) {
        return Identifier.DEFAULT_NAMESPACE.equals(key.getNamespace()) ? key.getPath() : key.toString();
    }
}
