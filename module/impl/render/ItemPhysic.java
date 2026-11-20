package dev.lucid.module.impl.render;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.lucid.Lucid;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.util.render.ItemPhysicState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

/**
 * Физика выброшенных предметов: без ванильного парения, спин только вокруг
 * вертикали, на земле угол не щёлкает.
 */
public class ItemPhysic extends Module {

    /** Порог ванили: тоньше — «плоский» предмет (меч, еда), толще — блок/3D-модель. */
    private static final float FLAT_ITEM_DEPTH_THRESHOLD = 0.0625F;

    /** Спин вокруг вертикали в воздухе, градусов за тик. */
    private static final float AIR_SPIN_Y = 5.0F;

    /** Последний yaw в воздухе — на земле замораживаем, а не сбрасываем. */
    private final Map<Integer, Float> airYaw = new HashMap<>();

    public ItemPhysic() {
        super("ItemPhysic", "Changes dropped item physics", Category.RENDER);
    }

    /** Доступ для миксина — тот же путь, что у {@code ViewModel#get()}. */
    public static ItemPhysic get() {
        Lucid lucid = Lucid.getInstance();
        if (lucid == null || lucid.getModuleManager() == null) return null;
        return lucid.getModuleManager().get(ItemPhysic.class);
    }

    /**
     * Считает yaw, пока сущность ещё есть (TAIL ванильного {@code extractRenderState},
     * когда {@code bobOffset} и {@code item} уже заполнены). В воздухе спин обновляется,
     * на земле — замораживается.
     */
    public void capture(ItemEntity entity, ItemEntityRenderState state) {
        if (!this.isEnabled() || state.item.isEmpty()) return;

        float yaw;
        if (entity.onGround()) {
            yaw = this.airYaw.getOrDefault(entity.getId(), 0.0F);
        } else {
            // bobOffset — ванильный случайный стартовый угол в радианах, свой у каждого предмета
            yaw = (float) Math.toDegrees(state.bobOffset) + state.ageInTicks * AIR_SPIN_Y;
            this.airYaw.put(entity.getId(), yaw);
            if (this.airYaw.size() > 512) this.airYaw.clear();
        }
        ((ItemPhysicState) state).lucid$setSpinYaw(yaw);
    }

    /**
     * Подменяет ванильный submit: без подскока, один и тот же поворот в воздухе и на земле.
     *
     * @return {@code true}, если ванильную отрисовку нужно отменить
     */
    public boolean submit(ItemEntityRenderState state, PoseStack poseStack,
                          SubmitNodeCollector collector, RandomSource random) {
        if (!this.isEnabled() || state.item.isEmpty()) return false;

        AABB box = state.item.getModelBoundingBox();
        boolean flat = box.getZsize() <= FLAT_ITEM_DEPTH_THRESHOLD;
        float yaw = ((ItemPhysicState) state).lucid$spinYaw();

        poseStack.pushPose();
        // Ванильный hover (Mth.sin + запас 0.0625) не нужен: предмет стоит ровно на земле
        poseStack.translate(0.0F, -(float) box.minY, 0.0F);

        if (flat) {
            // И в воздухе, и на земле одна схема: иначе в конце падения
            // меняется пивот и предмет визуально переворачивается.
            float thickness = (float) Math.max(box.getZsize() / 2.0, 0.02);
            poseStack.translate(0.0F, thickness, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        } else {
            float pivotX = (float) box.getCenter().x;
            float pivotY = (float) (box.getYsize() / 2.0);
            float pivotZ = (float) box.getCenter().z;
            poseStack.translate(pivotX, pivotY, pivotZ);
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.translate(-pivotX, -pivotY, -pivotZ);
        }

        ItemEntityRenderer.submitMultipleFromCount(
                poseStack, collector, state.lightCoords, state, random, box);

        poseStack.popPose();
        return true;
    }
}
