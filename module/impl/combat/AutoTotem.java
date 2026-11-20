package dev.lucid.module.impl.combat;

import dev.lucid.event.Subscribe;
import dev.lucid.event.impl.ClientTickEvent;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.MultiSelectSetting;
import dev.lucid.setting.SelectSetting;
import dev.lucid.setting.SliderSetting;
import dev.lucid.util.combat.DamageUtil;
import dev.lucid.util.inventory.InventoryUtil;
import dev.lucid.util.inventory.SwapProfile;
import dev.lucid.util.inventory.SwapSequence;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** * Ставит тотем во вторую руку при опасности. * * <p>Опасность двух уровней. {@link Danger#DEADLY}: HP уже ниже порога «Брать при HP» * или станет ниже после ожидаемого удара (взрыв, падение, стена на элитрах), либо * сработал анти-ваншот. Тотем ставится всегда, даже вместо сферы. * {@link Danger#RISKY}: «на всякий случай» (взрыв рядом в режиме радиуса, большая высота, * мало HP на элитрах). Сферу во второй руке такая опасность не трогает.</p> * * <p>Падение оценивается заранее, но тотем ставится только за несколько тиков до удара * о землю: ровно столько, чтобы свап успел пройти в выбранном режиме.</p> */
public final class AutoTotem extends Module {

    private enum Danger { NONE, RISKY, DEADLY }

    // ---- Подписи настроек ----

    private static final String D_NONE = "Мгновенный";
    private static final String D_LEGIT = "Легит";

    private static final String S_CRYSTALS = "Кристаллы";
    private static final String S_EXPLOSIVES = "Динамит";
    private static final String S_HEIGHT = "Падение";
    private static final String S_ONESHOT = "Анти-ваншот";
    private static final String S_FLIGHT = "Элитры";

    private static final String E_EXACT = "По урону";
    private static final String E_RADIUS = "По радиусу";

    private static final String B_RETURN = "Возвращать предмет";
    private static final String B_SPECIAL = "Сначала обычные тотемы";
    private static final String B_ABSORB = "Учитывать золотые сердца";

    // ---- Константы ----

    private static final double FALL_SCAN = 64.0;
    /** За сколько тиков до приземления ставить тотем: паузы свапа + пинг. */
    private static final int LEAD_TICKS_LEGIT = 12;
    private static final int LEAD_TICKS_NONE = 4;
    private static final int CRASH_LOOKAHEAD = 4;

    /** Зона над игроком, где падающий враг считается угрозой. */
    private static final double ABOVE_RADIUS = 7.0;
    private static final double ABOVE_HEIGHT = 50.0;
    private static final double ABOVE_MIN_FALL = 3.0;

    private static final double RAM_MIN_SPEED_SQ = 0.8 * 0.8;
    private static final double RAM_MAX_DIST_SQ = 30.0 * 30.0;
    private static final double RAM_HIT_DIST_SQ = 3.5 * 3.5;
    private static final int RAM_LOOKAHEAD = 20;

    private static final SwapProfile NO_DELAY =
            new SwapProfile(false, false, false, 0, 0, 0, 0, 0, true, 1);

    // ---------------- Настройки ----------------

    private final SelectSetting delay = new SelectSetting("Свап", D_LEGIT, D_NONE, D_LEGIT);
    private final SliderSetting criticalHp = new SliderSetting("Брать при HP", 6, 1, 20, 1);

    private final MultiSelectSetting sources = new MultiSelectSetting("Угрозы",
            List.of(S_CRYSTALS, S_EXPLOSIVES, S_HEIGHT, S_ONESHOT, S_FLIGHT),
            S_CRYSTALS, S_HEIGHT, S_ONESHOT);

    private final SelectSetting blastCheck = new SelectSetting("Расчёт взрывов",
            E_EXACT, E_EXACT, E_RADIUS);
    private final SliderSetting blastRadius = new SliderSetting("Радиус взрывов", 8, 2, 16, 1);
    private final SliderSetting dangerHeight = new SliderSetting("Мин. высота", 15, 5, 50, 1);
    private final SliderSetting flightHp = new SliderSetting("HP на элитрах", 10, 1, 20, 1);

    private final MultiSelectSetting behavior = new MultiSelectSetting("Дополнительно",
            List.of(B_RETURN, B_SPECIAL, B_ABSORB),
            B_RETURN, B_SPECIAL);
    private final SliderSetting returnPause = new SliderSetting("Задержка возврата", 10, 0, 60, 1);

    // ---------------- Состояние ----------------

    private final SwapSequence sequence = new SwapSequence();

    /** Предмет, который лежал в руке до тотема, и слот меню, куда он ушёл. */
    private ItemStack savedStack = ItemStack.EMPTY;
    private int savedSlot = -1;

    private int calmTicks;
    private boolean warnedEmpty;
    private LocalPlayer owner;

    /** Наблюдение за другими игроками: клиент не знает их скорость, считаем сами. */
    private static final class Track {
        Vec3 pos;
        Vec3 vel = Vec3.ZERO;
        double peakY;
        boolean airborne;
        int seen;

        Track(Vec3 pos) {
            this.pos = pos;
            this.peakY = pos.y;
        }
    }

    private final Map<Integer, Track> tracks = new HashMap<>();
    private int tickId;

    public AutoTotem() {
        super("Auto Totem", "Keeps you alive with a totem in the offhand", Category.COMBAT);

        blastCheck.visibleWhen(this::watchesBlasts);
        blastRadius.visibleWhen(this::watchesBlasts);
        dangerHeight.visibleWhen(() -> sources.isSelected(S_HEIGHT));
        flightHp.visibleWhen(() -> sources.isSelected(S_FLIGHT));
        returnPause.visibleWhen(() -> behavior.isSelected(B_RETURN));

        addSettings(delay, criticalHp, sources, blastCheck, blastRadius, dangerHeight,
                flightHp, behavior, returnPause);
    }

    private boolean watchesBlasts() {
        return sources.isSelected(S_CRYSTALS) || sources.isSelected(S_EXPLOSIVES);
    }

    // ---------------- Тик ----------------

    @Subscribe
    private void onClientTick(ClientTickEvent event) {
        if (!isEnabled()) return;

        if (event.getPhase() == ClientTickEvent.Phase.START) {
            sequence.tickStart();
            return;
        }

        if (!InventoryUtil.isReady()) {
            reset();
            return;
        }
        if (mc.player != owner) {
            reset();
            owner = mc.player;
        }

        sequence.tickEnd();
        updateTracks();

        LocalPlayer p = mc.player;
        if (p.isSpectator() || p.isDeadOrDying()) return;

        Danger danger = evaluate();
        calmTicks = danger == Danger.NONE ? calmTicks + 1 : 0;

        if (!canClick() || sequence.isBusy()) return;

        ItemStack off = p.getOffhandItem();
        // Сферу меняем только при смертельной опасности.
        boolean need = danger == Danger.DEADLY
                || (danger == Danger.RISKY && !off.is(Items.PLAYER_HEAD));

        if (need) equipIfNeeded(off);
        else returnIfCalm(off);
    }

    // ---------------- Оценка опасности ----------------

    private Danger evaluate() {
        if (tooLowAfter(0f)) return Danger.DEADLY;
        if (oneshotThreat()) return Danger.DEADLY;

        Danger result = Danger.NONE;
        for (Danger d : new Danger[]{blasts(), height(), flight()}) {
            if (d == Danger.DEADLY) return d;
            if (d.ordinal() > result.ordinal()) result = d;
        }
        return result;
    }

    /** * Станет ли HP ниже порога после урона damage. Золотые сердца гасят урон первыми; * без «Учитывать золотые сердца» сравнивается только оставшееся настоящее здоровье. */
    private boolean tooLowAfter(float damage) {
        LocalPlayer p = mc.player;
        float left = p.getHealth() + p.getAbsorptionAmount() - damage;
        float compared = behavior.isSelected(B_ABSORB) ? left : Math.min(p.getHealth(), left);
        return compared <= criticalHp.getLong();
    }

    private Danger blasts() {
        boolean crystals = sources.isSelected(S_CRYSTALS);
        boolean explosives = sources.isSelected(S_EXPLOSIVES);
        if (!crystals && !explosives) return Danger.NONE;

        LocalPlayer p = mc.player;
        double radius = blastRadius.getLong();
        boolean exact = blastCheck.is(E_EXACT);
        boolean any = false;
        float total = 0f;

        if (crystals) {
            for (EndCrystal c : around(EndCrystal.class, radius)) {
                any = true;
                if (!exact) break;
                total += DamageUtil.explosion(p, c.position(), DamageUtil.CRYSTAL_POWER);
                if (tooLowAfter(total)) return Danger.DEADLY;
            }
        }
        if (explosives) {
            for (PrimedTnt t : around(PrimedTnt.class, radius)) {
                any = true;
                if (!exact) break;
                Vec3 center = new Vec3(t.getX(), t.getY(0.0625), t.getZ());
                total += DamageUtil.explosion(p, center, DamageUtil.TNT_POWER);
                if (tooLowAfter(total)) return Danger.DEADLY;
            }
            for (MinecartTNT m : around(MinecartTNT.class, radius)) {
                double speed = m.getDeltaMovement().horizontalDistance();
                // Опасна только подожжённая или разогнанная вагонетка.
                if (!m.isPrimed() && speed < 0.5) continue;
                any = true;
                if (!exact) break;
                float power = DamageUtil.TNT_POWER + 1.5f * (float) Math.min(5.0, speed);
                total += DamageUtil.explosion(p, m.position(), power);
                if (tooLowAfter(total)) return Danger.DEADLY;
            }
        }

        if (!any) return Danger.NONE;
        return exact ? Danger.NONE : Danger.RISKY;
    }

    private Danger height() {
        if (!sources.isSelected(S_HEIGHT)) return Danger.NONE;
        DamageUtil.FallPrediction f = DamageUtil.fall(mc.player, FALL_SCAN);
        if (f.damage() <= 0) return Danger.NONE;
        // Рано: до земли ещё далеко, свап успеет и позже.
        if (f.ticksToImpact() > leadTicks()) return Danger.NONE;
        if (tooLowAfter(f.damage())) return Danger.DEADLY;
        return f.height() >= dangerHeight.getLong() ? Danger.RISKY : Danger.NONE;
    }

    private Danger flight() {
        LocalPlayer p = mc.player;
        if (!sources.isSelected(S_FLIGHT) || !p.isFallFlying()) return Danger.NONE;
        float crash = DamageUtil.elytraCrash(p, CRASH_LOOKAHEAD);
        if (crash > 0 && tooLowAfter(crash)) return Danger.DEADLY;
        return p.getHealth() <= flightHp.getLong() ? Danger.RISKY : Danger.NONE;
    }

    private int leadTicks() {
        return delay.is(D_NONE) ? LEAD_TICKS_NONE : LEAD_TICKS_LEGIT;
    }

    // ---------------- Анти-ваншот ----------------

    private boolean oneshotThreat() {
        if (!sources.isSelected(S_ONESHOT)) return false;
        LocalPlayer me = mc.player;

        for (Player other : mc.level.players()) {
            if (other == me || !other.isAlive()) continue;
            Track t = tracks.get(other.getId());
            if (t == null) continue;
            if (dropsOnMe(me, other, t) || ramsMe(other, t)) return true;
        }
        return false;
    }

    /** Падает в зоне над нами и уже пролетел хотя бы ABOVE_MIN_FALL блоков. */
    private boolean dropsOnMe(LocalPlayer me, Player other, Track t) {
        if (!t.airborne || t.vel.y >= 0 || other.isFallFlying()) return false;

        double above = other.getY() - me.getY();
        if (above < 0 || above > ABOVE_HEIGHT) return false;
        if (Math.abs(other.getX() - me.getX()) > ABOVE_RADIUS) return false;
        if (Math.abs(other.getZ() - me.getZ()) > ABOVE_RADIUS) return false;

        return t.peakY - other.getY() >= ABOVE_MIN_FALL;
    }

    /** Летит на элитрах так, что в ближайшие тики пройдёт вплотную к нам. */
    private boolean ramsMe(Player other, Track t) {
        if (!other.isFallFlying()) return false;
        double speedSq = t.vel.lengthSqr();
        if (speedSq < RAM_MIN_SPEED_SQ) return false;

        Vec3 me = mc.player.position();
        Vec3 rel = me.subtract(t.pos);
        if (rel.lengthSqr() > RAM_MAX_DIST_SQ) return false;

        double tick = rel.dot(t.vel) / speedSq; // момент наибольшего сближения
        if (tick < 0 || tick > RAM_LOOKAHEAD) return false;
        return t.pos.add(t.vel.scale(tick)).distanceToSqr(me) < RAM_HIT_DIST_SQ;
    }

    private void updateTracks() {
        tickId++;
        for (Player other : mc.level.players()) {
            if (other == mc.player) continue;
            Vec3 now = other.position();
            Track t = tracks.computeIfAbsent(other.getId(), id -> new Track(now));
            t.vel = now.subtract(t.pos);
            t.pos = now;
            t.seen = tickId;

            boolean grounded = other.onGround() || other.isInWater()
                    || other.onClimbable() || other.isFallFlying();
            if (grounded) {
                t.airborne = false;
                t.peakY = now.y;
            } else if (!t.airborne) {
                t.airborne = true;
                t.peakY = now.y;
            } else {
                t.peakY = Math.max(t.peakY, now.y);
            }
        }
        tracks.values().removeIf(t -> t.seen != tickId);
    }

    private <T extends Entity> List<T> around(Class<T> type, double range) {
        LocalPlayer p = mc.player;
        double sq = range * range;
        return mc.level.getEntitiesOfClass(type, p.getBoundingBox().inflate(range),
                e -> e.isAlive() && e.distanceToSqr(p) <= sq);
    }

    // ---------------- Свапы ----------------

    private void equipIfNeeded(ItemStack off) {
        if (isTotem(off)) {
            // Особый тотем в руке: меняем на обычный, если он есть.
            if (!saveSpecial() || !isSpecialTotem(off)) return;
            if (findSlot(this::isPlainTotem) == -1) return;
        } else if (findTotemSlot() == -1) {
            if (!warnedEmpty) {
                notifyPlayer("Тотемы закончились");
                warnedEmpty = true;
            }
            return;
        }
        warnedEmpty = false;
        startSwap(this::doEquip);
    }

    private void doEquip() {
        if (!canClick()) return;
        ItemStack off = mc.player.getOffhandItem();

        int slot;
        if (isTotem(off)) {
            if (!saveSpecial() || !isSpecialTotem(off)) return;
            slot = findSlot(this::isPlainTotem);
        } else {
            slot = findTotemSlot();
            // Пустую руку (тотем сработал) не запоминаем, чтобы не затереть
            // сохранённый ранее щит или сферу.
            if (slot != -1 && !off.isEmpty() && savedStack.isEmpty()) {
                savedStack = off.copy();
                savedSlot = slot;
            }
        }
        if (slot != -1) InventoryUtil.swapWithOffhand(slot);
    }

    private void returnIfCalm(ItemStack off) {
        if (!behavior.isSelected(B_RETURN) || savedStack.isEmpty()) return;
        // В руке что-то третье: игрок поменял сам, не вмешиваемся.
        if (!off.isEmpty() && !isTotem(off)) {
            forgetSaved();
            return;
        }
        if (calmTicks < returnPause.getLong()) return;
        if (findSaved() == -1) {
            forgetSaved();
            return;
        }
        startSwap(this::doReturn);
    }

    private void doReturn() {
        if (!canClick()) return;
        ItemStack off = mc.player.getOffhandItem();
        if (!off.isEmpty() && !isTotem(off)) return;
        int slot = findSaved();
        if (slot != -1) InventoryUtil.swapWithOffhand(slot);
        forgetSaved();
    }

    private void startSwap(Runnable action) {
        boolean inInventory = mc.screen instanceof AbstractContainerScreen<?>;
        // В открытом инвентаре клик и так естественный, а пакет закрытия сломал бы экран.
        SwapProfile profile = (delay.is(D_NONE) || inInventory) ? NO_DELAY : SwapProfile.legit();
        if (inInventory) profile = withoutClose(profile);
        sequence.start(action, profile);
    }

    private static SwapProfile withoutClose(SwapProfile p) {
        return new SwapProfile(p.stopMovement(), p.stopSprint(), p.waitStandstill(),
                p.maxWaitTicks(), p.minDelayTicks(), p.maxDelayTicks(),
                p.minAfterTicks(), p.maxAfterTicks(), false, p.cooldownTicks());
    }

    // ---------------- Поиск ----------------

    private int findTotemSlot() {
        if (saveSpecial()) {
            int plain = findSlot(this::isPlainTotem);
            if (plain != -1) return plain;
        }
        return findSlot(this::isTotem);
    }

    private int findSaved() {
        InventoryMenu menu = mc.player.inventoryMenu;
        if (savedSlot >= 0 && ItemStack.isSameItemSameComponents(menu.getSlot(savedSlot).getItem(), savedStack)) {
            return savedSlot;
        }
        ItemStack saved = savedStack;
        int exact = findSlot(s -> ItemStack.isSameItemSameComponents(s, saved));
        return exact != -1 ? exact : findSlot(s -> s.is(saved.getItem()));
    }

    /** Сначала основной инвентарь, потом хотбар: хотбарные тотемы остаются под руку. */
    private int findSlot(Predicate<ItemStack> filter) {
        InventoryMenu menu = mc.player.inventoryMenu;
        for (int i = InventoryUtil.MAIN_START; i <= InventoryUtil.HOTBAR_END; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty() && filter.test(stack)) return i;
        }
        return -1;
    }

    private boolean isTotem(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING);
    }

    private boolean isSpecialTotem(ItemStack stack) {
        return isTotem(stack) && isCustomized(stack);
    }

    private boolean isPlainTotem(ItemStack stack) {
        return isTotem(stack) && !isCustomized(stack);
    }

    private boolean saveSpecial() {
        return behavior.isSelected(B_SPECIAL);
    }

    /** Предмет изменён относительно ванильного: чары, имя, лор, модель или данные. */
    private static boolean isCustomized(ItemStack stack) {
        return differs(stack, DataComponents.ENCHANTMENTS)
                || differs(stack, DataComponents.CUSTOM_NAME)
                || differs(stack, DataComponents.ITEM_NAME)
                || differs(stack, DataComponents.LORE)
                || differs(stack, DataComponents.CUSTOM_MODEL_DATA)
                || differs(stack, DataComponents.ITEM_MODEL)
                || differs(stack, DataComponents.CUSTOM_DATA);
    }

    private static boolean differs(ItemStack stack, DataComponentType<?> type) {
        return !Objects.equals(stack.get(type), stack.getItem().components().get(type));
    }

    // ---------------- Служебное ----------------

    private boolean canClick() {
        LocalPlayer p = mc.player;
        return p != null && p.containerMenu == p.inventoryMenu
                && p.containerMenu.getCarried().isEmpty();
    }

    private void forgetSaved() {
        savedStack = ItemStack.EMPTY;
        savedSlot = -1;
    }

    private void reset() {
        sequence.cancel();
        forgetSaved();
        tracks.clear();
        calmTicks = 0;
        warnedEmpty = false;
        owner = null;
    }

    private void notifyPlayer(String text) {
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(text), true);
    }

    @Override
    protected void onDisable() {
        reset();
    }
}
