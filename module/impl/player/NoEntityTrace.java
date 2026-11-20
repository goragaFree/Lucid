package dev.lucid.module.impl.player;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.BooleanSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Игнорирует хитбоксы сущностей при трассировке прицела: блок можно ломать и ставить
 * сквозь мобов, игроков, лодки и витрины — сущность больше не «съедает» клик.
 *
 * <p>В 1.21.11 весь расчёт прицела живёт в {@code LocalPlayer}, а не в
 * {@code GameRenderer}: {@code GameRenderer#pick(float)} только зовёт
 * {@code player.raycastHitResult(partialTick, cameraEntity)} и пишет результат в
 * {@code minecraft.hitResult}. Внутри {@code raycastHitResult} ваниль сначала пробует
 * трассировку предмета с компонентом {@code ATTACK_RANGE}, а затем приватный
 * {@code pick(Entity, double, double, float)}: клип по блокам через
 * {@code Entity#pick}, клип по сущностям через {@code ProjectileUtil#getEntityHitResult},
 * и сущность побеждает, если она ближе блока. Именно это сравнение мы и обходим:
 * хук на HEAD {@code raycastHitResult} возвращает результат, посчитанный только
 * по блокам, до всякой трассировки сущностей.</p>
 *
 * <p>Блок-онли трассировка повторяет ванильный блок-путь один в один: тот же клип
 * {@code cameraEntity.pick(blockInteractionRange(), partialTick, false)}
 * ({@code ClipContext.Block.OUTLINE}, жидкости не учитывать) и копия ванильного
 * {@code filterHitResult} — попадание дальше радиуса взаимодействия превращается
 * в MISS на месте попадания. Фильтр скопирован, а не вызван: метод приватный и
 * статический, из кода модуля до него не добраться.</p>
 *
 * <p>С мечом в главной руке модуль не вмешивается (галочка «Sword Bypass»): меч в руке —
 * намерение бить, и прицел должен ловить сущности как в ванили. В 1.21.11 класса
 * {@code SwordItem} больше нет — меч это обычный {@code Item}, собранный через
 * {@code Item.Properties#sword(...)}, поэтому меч узнаётся по реестровому ID на
 * {@code _sword}. Это строго мечи: булава и трезубец, у которых тоже есть компонент
 * {@code WEAPON}, под проверку не попадают.</p>
 *
 * <p>Обратная сторона «пробоя»: прицел всегда блок, даже когда за ним пусто (MISS),
 * поэтому без меча в руке атаковать сущности при включённом модуле нельзя — это цена
 * в классической реализации. Компонент {@code ATTACK_RANGE} (у большинства предметов
 * его нет) пропускается — блок-онли клип стандартного радиуса находит те же блоки,
 * ведь ваниль и там обрезает результат до {@code blockInteractionRange()}.</p>
 */
public final class NoEntityTrace extends Module {

    // Модуль создаётся один раз в ModuleManager.init(); миксину незачем искать его
    // в реестре модулей на каждый кадр.
    private static NoEntityTrace instance;

    private final BooleanSetting swordBypass = new BooleanSetting("Sword Bypass", true);

    public NoEntityTrace() {
        super("NoEntityTrace", "Break and place blocks through entities", Category.PLAYER);
        addSettings(swordBypass);
        instance = this;
    }

    /**
     * Точка входа для {@code LocalPlayerMixin}: результат трассировки по одним блокам
     * или null — вмешиваться не нужно (модуля ещё нет, он выключен или в руке меч
     * при включённом «Sword Bypass»).
     */
    public static HitResult blockOnlyPick(LocalPlayer player, Entity cameraEntity, float partialTick) {
        NoEntityTrace module = instance;
        if (module == null || !module.isEnabled()) return null;
        if (module.swordBypass.getValue() && holdingSword(player)) return null;
        // GameRenderer#pick проверяет cameraEntity до вызова, но миксин общий —
        // дешёвая защита лучше NPE посреди кадра.
        if (cameraEntity == null) return null;

        double blockRange = player.blockInteractionRange();
        return filterByRange(cameraEntity.pick(blockRange, partialTick, false),
                cameraEntity.getEyePosition(partialTick), blockRange);
    }

    /**
     * Меч в главной руке. Проверяется только главная рука: левой меч не атакует,
     * значит и мешать прицелу он не может.
     *
     * <p>Класс {@code SwordItem} в 1.21.11 удалён (меч — обычный {@code Item} с
     * компонентами), поэтому опознаём по реестровому ID: все ванильные мечи
     * ({@code wooden_sword} … {@code netherite_sword}) и подавляющее большинство
     * модовых называются именно так. Пустой стак — предмет {@code air}, совпадения
     * не даст, отдельная проверка не нужна.</p>
     */
    private static boolean holdingSword(LocalPlayer player) {
        Identifier id = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem());
        return id != null && id.getPath().endsWith("_sword");
    }

    /**
     * Копия ванильного {@code LocalPlayer#filterHitResult}: попадание дальше радиуса
     * взаимодействия заменяется на MISS по месту попадания (направление — ближайшая
     * сторона к вектору от глаз, позиция — блок, содержащий точку).
     *
     * <p>Для реального попадания в блок фильтр всегда пропускает: клип идёт ровно на
     * {@code blockRange}, дальше конца луча блок не найдётся. Работу делает ветка
     * MISS — у ванильного MISS точка лежит ровно на конце луча, а {@code closerThan}
     * сравнивает строго, поэтому даже «пустой» результат проходит через замену.</p>
     */
    private static HitResult filterByRange(HitResult hitResult, Vec3 eyePos, double range) {
        Vec3 location = hitResult.getLocation();
        if (location.closerThan(eyePos, range)) return hitResult;
        Vec3 delta = location.subtract(eyePos);
        return BlockHitResult.miss(location,
                Direction.getApproximateNearest(delta.x, delta.y, delta.z),
                BlockPos.containing(location.x, location.y, location.z));
    }
}
