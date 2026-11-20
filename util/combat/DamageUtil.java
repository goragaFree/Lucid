package dev.lucid.util.combat;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** * Предсказание урона по формулам ванилы. Считается на клиенте, поэтому это оценка: * эффекты чужих игроков клиенту обычно неизвестны. */
public final class DamageUtil {

    private static final Minecraft mc = Minecraft.getInstance();

    public static final float CRYSTAL_POWER = 6.0f;
    public static final float TNT_POWER = 4.0f;

    /** Сопротивление воздуха по вертикали за тик, как в ваниле. */
    private static final double AIR_DRAG = 0.98;
    private static final int MAX_SIM_TICKS = 200;

    /** Смещения лучей вниз: центр и четыре угла хитбокса. */
    private static final double[][] FEET_OFFSETS = {
            {0, 0}, {0.29, 0.29}, {-0.29, 0.29}, {0.29, -0.29}, {-0.29, -0.29}
    };

    /** * @param height полная высота падения к моменту приземления * @param damage урон при приземлении * @param ticksToImpact через сколько тиков удар о землю */
    public record FallPrediction(double height, float damage, int ticksToImpact) {
        public static final FallPrediction NONE = new FallPrediction(0, 0, Integer.MAX_VALUE);
    }

    private DamageUtil() {}

    // ---------------- Взрывы ----------------

    /** Урон взрыва по цели: стены, сложность, броня, Защита от взрывов, Сопротивление. */
    public static float explosion(LivingEntity target, Vec3 center, float power) {
        double radius = power * 2.0;
        double dist = Math.sqrt(target.distanceToSqr(center)) / radius;
        if (dist > 1.0) return 0f;

        double impact = (1.0 - dist) * exposure(center, target);
        if (impact <= 0) return 0f;

        float damage = (float) ((impact * impact + impact) / 2.0 * 7.0 * radius + 1.0);
        if (target instanceof Player) damage = scaleByDifficulty(damage);
        damage = afterArmor(target, damage);
        damage = afterProtection(damage, protectionPoints(target, Enchantments.BLAST_PROTECTION, 2));
        return afterResistance(target, damage);
    }

    /** Доля точек хитбокса, которые видят центр взрыва (0..1). */
    private static float exposure(Vec3 center, LivingEntity target) {
        AABB box = target.getBoundingBox();
        double sx = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double sy = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double sz = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        double ox = (1.0 - Math.floor(1.0 / sx) * sx) / 2.0;
        double oz = (1.0 - Math.floor(1.0 / sz) * sz) / 2.0;

        int clear = 0;
        int total = 0;
        for (double x = 0; x <= 1.0; x += sx) {
            for (double y = 0; y <= 1.0; y += sy) {
                for (double z = 0; z <= 1.0; z += sz) {
                    Vec3 point = new Vec3(
                            Mth.lerp(x, box.minX, box.maxX) + ox,
                            Mth.lerp(y, box.minY, box.maxY),
                            Mth.lerp(z, box.minZ, box.maxZ) + oz);
                    HitResult hit = mc.level.clip(new ClipContext(point, center,
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
                    if (hit.getType() == HitResult.Type.MISS) clear++;
                    total++;
                }
            }
        }
        return total == 0 ? 0f : (float) clear / total;
    }

    private static float scaleByDifficulty(float damage) {
        return switch (mc.level.getDifficulty()) {
            case PEACEFUL -> 0f;
            case EASY -> Math.min(damage / 2f + 1f, damage);
            case NORMAL -> damage;
            case HARD -> damage * 1.5f;
        };
    }

    // ---------------- Падение и элитры ----------------

    /** * Высота, урон и время до приземления. Землю ищем лучами вниз не глубже scanDepth. * Земли не видно — {@link FallPrediction#NONE}: судить рано, ждём. */
    public static FallPrediction fall(LivingEntity p, double scanDepth) {
        if (p.onGround() || p.isFallFlying() || p.isInWater() || p.isInLava() || p.onClimbable()
                || p.hasEffect(MobEffects.SLOW_FALLING) || p.getDeltaMovement().y > -0.1) {
            return FallPrediction.NONE;
        }
        if (p instanceof Player pl && pl.getAbilities().flying) return FallPrediction.NONE;

        Vec3 feet = p.position();
        BlockHitResult ground = null;
        double nearest = scanDepth;
        for (double[] o : FEET_OFFSETS) {
            Vec3 from = feet.add(o[0], 0, o[1]);
            BlockHitResult hit = mc.level.clip(new ClipContext(from, from.subtract(0, scanDepth, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
            if (hit.getType() == HitResult.Type.MISS) continue;
            double d = from.y - hit.getLocation().y;
            if (d < nearest) {
                nearest = d;
                ground = hit;
            }
        }
        if (ground == null) return FallPrediction.NONE;

        BlockState state = mc.level.getBlockState(ground.getBlockPos());
        if (!state.getFluidState().isEmpty()) return FallPrediction.NONE; // вода/лава гасят падение

        double height = p.fallDistance + nearest;
        int ticks = ticksToFall(p.getDeltaMovement().y, p.getGravity(), nearest);

        double safe = p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        double attr = p.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
        float damage = (float) Math.ceil((height - safe) * landingMultiplier(state, p) * attr);
        if (damage <= 0) return new FallPrediction(height, 0f, ticks);

        damage = afterProtection(damage, protectionPoints(p, Enchantments.FEATHER_FALLING, 3));
        return new FallPrediction(height, afterResistance(p, damage), ticks);
    }

    /** Сколько тиков лететь distance блоков вниз при текущей скорости vy (отрицательная). */
    private static int ticksToFall(double vy, double gravity, double distance) {
        double fallen = 0;
        for (int tick = 1; tick <= MAX_SIM_TICKS; tick++) {
            fallen -= vy;
            if (fallen >= distance) return tick;
            vy = (vy - gravity) * AIR_DRAG;
        }
        return MAX_SIM_TICKS;
    }

    private static float landingMultiplier(BlockState state, LivingEntity p) {
        if (state.is(Blocks.SLIME_BLOCK)) return p.isSuppressingBounce() ? 1f : 0f;
        if (state.is(Blocks.HAY_BLOCK) || state.is(Blocks.HONEY_BLOCK)) return 0.2f;
        if (state.is(BlockTags.BEDS)) return 0.5f;
        if (state.is(Blocks.POWDER_SNOW) || state.is(Blocks.COBWEB)) return 0f;
        return 1f;
    }

    /** Удар об стену на элитрах в ближайшие lookahead тиков. Броня его не режет. */
    public static float elytraCrash(LivingEntity p, int lookahead) {
        if (!p.isFallFlying()) return 0f;
        Vec3 v = p.getDeltaMovement();
        float damage = (float) (v.horizontalDistance() * 10.0 - 3.0);
        if (damage <= 0) return 0f;

        Vec3 from = p.getBoundingBox().getCenter();
        BlockHitResult hit = mc.level.clip(new ClipContext(from, from.add(v.scale(lookahead)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() == HitResult.Type.MISS) return 0f;
        if (hit.getDirection().getAxis() == Direction.Axis.Y) return 0f; // посадка, не стена
        return afterResistance(p, damage);
    }

    // ---------------- Снижение урона ----------------

    private static float afterArmor(LivingEntity target, float damage) {
        float armor = target.getArmorValue();
        float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float f = 2f + toughness / 4f;
        float effective = Mth.clamp(armor - damage / f, armor * 0.2f, 20f);
        return damage * (1f - effective / 25f);
    }

    private static float afterProtection(float damage, int points) {
        return damage * (1f - Mth.clamp(points, 0, 20) / 25f);
    }

    private static float afterResistance(LivingEntity target, float damage) {
        MobEffectInstance r = target.getEffect(MobEffects.RESISTANCE);
        if (r == null) return damage;
        return damage * Math.max(0f, 1f - (r.getAmplifier() + 1) * 0.2f);
    }

    /** Очки защиты: Защита x1 + особое зачарование x weight по всем частям брони. */
    private static int protectionPoints(LivingEntity target, ResourceKey<Enchantment> special, int weight) {
        int total = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack stack = target.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            total += level(stack, Enchantments.PROTECTION);
            if (special != null) total += level(stack, special) * weight;
        }
        return total;
    }

    private static int level(ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack.isEmpty()) return 0;
        for (Object2IntMap.Entry<Holder<Enchantment>> e : stack.getEnchantments().entrySet()) {
            if (e.getKey().is(key)) return e.getIntValue();
        }
        return 0;
    }
}
