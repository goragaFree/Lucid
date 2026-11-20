package dev.lucid.module.impl.render;

import dev.lucid.Lucid;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.SliderSetting;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Положение рук от первого лица, отдельно для правой и левой руки.
 *
 * <p>Сдвиг применяется в {@code ItemInHandRendererMixin} внутри
 * {@code renderArmWithItem}, сразу после {@code pushPose()}. У каждой руки свой
 * сдвиг, и он снимается её же {@code popPose()}. Оси экранные: X вправо, Y вверх,
 * Z к камере. Ванильные анимации работают поверх сдвига.</p>
 *
 * <p>Сторона определяется по руке на экране ({@link HumanoidArm}), а не по
 * основной/второй руке, поэтому у левши всё работает так же.</p>
 */
public class ViewModel extends Module {

    private final SliderSetting rightX = new SliderSetting("Правая X", 0, -1, 1, 0.01);
    private final SliderSetting rightY = new SliderSetting("Правая Y", 0, -1, 1, 0.01);
    private final SliderSetting rightZ = new SliderSetting("Правая Z", 0, -1, 1, 0.01);

    private final SliderSetting leftX = new SliderSetting("Левая X", 0, -1, 1, 0.01);
    private final SliderSetting leftY = new SliderSetting("Левая Y", 0, -1, 1, 0.01);
    private final SliderSetting leftZ = new SliderSetting("Левая Z", 0, -1, 1, 0.01);

    public ViewModel() {
        super("View model", "Changes first-person hand position", Category.RENDER);
        // visibleWhen возвращает Setting<T>, поэтому отдельными операторами.
        for (SliderSetting s : new SliderSetting[]{rightX, rightY, rightZ, leftX, leftY, leftZ}) {
            s.visibleWhen(this::isEnabled);
        }
        addSettings(rightX, rightY, rightZ, leftX, leftY, leftZ);
    }

    /** Доступ для миксина, тот же путь, что у {@code FreeCam#get()}. */
    public static ViewModel get() {
        Lucid lucid = Lucid.getInstance();
        if (lucid == null || lucid.getModuleManager() == null) return null;
        return lucid.getModuleManager().get(ViewModel.class);
    }

    public float getOffsetX(HumanoidArm arm) {
        return (arm == HumanoidArm.RIGHT ? rightX : leftX).getValue().floatValue();
    }

    public float getOffsetY(HumanoidArm arm) {
        return (arm == HumanoidArm.RIGHT ? rightY : leftY).getValue().floatValue();
    }

    public float getOffsetZ(HumanoidArm arm) {
        return (arm == HumanoidArm.RIGHT ? rightZ : leftZ).getValue().floatValue();
    }
}
