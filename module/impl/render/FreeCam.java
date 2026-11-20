package dev.lucid.module.impl.render;

import dev.lucid.Lucid;
import dev.lucid.event.Priority;
import dev.lucid.event.Subscribe;
import dev.lucid.event.impl.MouseRotationEvent;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.BooleanSetting;
import dev.lucid.setting.SliderSetting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Камера-призрак: персонаж замирает на месте, а камера летает сквозь стены,
 * без гравитации и коллизий.
 *
 * <p><b>Почему персонаж ничего не делает.</b> Игрока не телепортируют и не двигают:
 * у него просто отнимается ввод. WASD/прыжок/присед гасит {@code KeyboardInputMixin},
 * поворот мышью — {@link #onMouseRotation}, удары и ПКМ — {@code MinecraftMixin}.
 * Для сервера игрок стоит и смотрит туда же, куда смотрел, ни одного лишнего пакета
 * движения.</p>
 *
 * <p><b>Как летает камера.</b> Позиция своя, а не сущности: каждый кадр она ставится
 * в конце {@code Camera.setup} ({@code CameraMixin}), поэтому ни коллизий, ни физики
 * у неё нет. Движение считается по реальному времени кадра, а не по тикам — на любом
 * FPS полёт плавный и скорость одна.</p>
 *
 * <p><b>Сквозь стены.</b> Камера летит в обход блоков, а изнутри блока ванильная
 * отрисовка не показывает внутренние грани — поэтому стена не загораживает обзор.</p>
 *
 * <p><b>Почему первое лицо.</b> В первом лице ваниль не рисует сущность, за которой
 * стоит камера, — тело персонажа невидимо, ты смотришь глазами призрака. Руку гасим
 * отдельно в {@code GameRendererMixin}. Прежняя перспектива возвращается при
 * выключении.</p>
 */
public final class FreeCam extends Module {

    private static final float PITCH_LIMIT = 90.0F;

    /** Та же поправка, что в {@code Entity.turn}: дельта мыши → градусы. */
    private static final double DEGREES_PER_UNIT = 0.15;

    /** Длинные кадры (подвисание, загрузка чанков) не должны швырять камеру. */
    private static final double MAX_FRAME_SECONDS = 0.1;

    /** Время, за которое скорость почти догоняет цель при включённой плавности. */
    private static final double SMOOTH_SECONDS = 0.12;

    private final SliderSetting speed = new SliderSetting("Скорость", 10, 1, 50, 0.5);
    private final BooleanSetting followView = new BooleanSetting("По взгляду", false);
    private final BooleanSetting smooth = new BooleanSetting("Плавность", true);
    private final BooleanSetting disableOnDamage = new BooleanSetting("Выкл. при уроне", true);
    /** Видеть своё тело, пока камера летает. */
    private final BooleanSetting showSelf = new BooleanSetting("Показывать себя", true);

    /** Позиция и углы ещё не взяты у камеры — возьмём на первом кадре. */
    private boolean positionReady;
    private boolean rotationReady;

    private Vec3 position = Vec3.ZERO;
    private Vec3 velocity = Vec3.ZERO;
    private float yaw;
    private float pitch;

    private long lastFrameNanos;

    /** Игрок и мир, в которых взята точка: сменился — продолжать нельзя. */
    private LocalPlayer owner;
    private Level ownerLevel;

    /** Перспектива, которую сменила камера; null — не меняла. */
    private CameraType savedPerspective;

    /** Старая красная вспышка не должна выключать модуль сразу после включения. */
    private boolean hurtSeen;

    public FreeCam() {
        super(
                "FreeCam",
                "Ghost camera: fly through walls while your body stays put",
                Category.RENDER);

        addSettings(speed, followView, smooth, disableOnDamage, showSelf);

        // Вышли из мира — камере некуда возвращаться, а включённой из конфига она быть
        // не должна.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> this.setEnabled(false));
    }

    /** Доступ для миксинов: тот же путь, что у {@code AirStuck}. */
    public static FreeCam get() {
        Lucid lucid = Lucid.getInstance();
        if (lucid == null || lucid.getModuleManager() == null) return null;
        return lucid.getModuleManager().get(FreeCam.class);
    }

    /** true, пока призрак владеет картинкой; миксины читают это каждый кадр/тик. */
    public static boolean shouldControlCamera() {
        FreeCam module = get();
        return module != null && module.isActive();
    }

    public static boolean shouldFreezeInput() { return shouldControlCamera(); }
    public static boolean shouldCancelInteractions() { return shouldControlCamera(); }
    public static boolean shouldHideHand() { return shouldControlCamera(); }

    /**
     * true — камера сообщает ванили, что она «отцеплена» ({@code CameraMixin#isDetached}),
     * и та рисует тело игрока, как в третьем лице.
     */
    public static boolean shouldRenderSelf() {
        FreeCam module = get();
        return module != null && module.isActive() && module.showSelf.getValue();
    }

    /** Камера летает только за нашим игроком — чужой вид (наблюдатели) не трогаем. */
    public boolean isActive() {
        return isEnabled()
                && mc.player != null
                && mc.level != null
                && mc.getCameraEntity() == mc.player;
    }

    // ------------------------------------------------------------------ включение

    @Override
    protected void onEnable() {
        this.positionReady = false;
        this.rotationReady = false;
        this.velocity = Vec3.ZERO;
        this.lastFrameNanos = 0L;
        this.hurtSeen = false;
        this.owner = mc.player;
        this.ownerLevel = mc.level;

        // Включили вне мира (например, из конфига на старте) — всё возьмём на первом
        // кадре.
        if (this.owner == null || mc.gameRenderer == null) {
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();

        // Стартуем ровно из того, что игрок видит сейчас, — картинка не дёргается.
        if (camera.isInitialized()) {
            this.position = camera.position();
            this.yaw = camera.yRot();
            this.pitch = camera.xRot();
            this.positionReady = true;
            this.rotationReady = true;
        }

        // Начатое ломание и натянутый лук иначе так и висели бы на персонаже.
        if (mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
            if (this.owner.isUsingItem()) {
                mc.gameMode.releaseUsingItem(this.owner);
            }
        }

        this.forceFirstPerson();
    }

    @Override
    protected void onDisable() {
        // Возвращаем только ту перспективу, что сменили сами и что с тех пор стоит.
        if (this.savedPerspective != null
                && mc.options != null
                && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            mc.options.setCameraType(this.savedPerspective);
        }

        this.savedPerspective = null;
        this.owner = null;
        this.ownerLevel = null;
        this.velocity = Vec3.ZERO;
        this.positionReady = false;
        this.rotationReady = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) {
            this.setEnabled(false);
            return;
        }

        // Респавн: новый LocalPlayer, старая точка больше не про него.
        if (mc.player != this.owner) {
            this.setEnabled(false);
            return;
        }

        // Реагируем на новый удар, а не на старую красную вспышку.
        boolean hurt = mc.player.hurtTime > 0;
        if (this.disableOnDamage.getValue() && hurt && !this.hurtSeen) {
            this.setEnabled(false);
            return;
        }
        this.hurtSeen = hurt;

        // Тело стоит: горизонтальную скорость гасим сами — иначе откид, ветер или
        // сталкивание сдвинули бы игрока, пока камера летает. Вертикаль не трогаем:
        // падение и гравитация остаются ванильными, player просто приземлится и встанет.
        Vec3 motion = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(0.0, motion.y, 0.0);

        // Портал или /tp в другое измерение: старая точка в новом мире — случайное место.
        if (mc.level != this.ownerLevel) {
            this.ownerLevel = mc.level;
            this.positionReady = false;
            this.velocity = Vec3.ZERO;
        }

        // F5 вытащил бы камеру назад и показал тело — держим первое лицо.
        this.forceFirstPerson();
    }

    // ------------------------------------------------------------------ события

    /** Мышь крутит камеру, игрок продолжает смотреть туда же. */
    @Subscribe(priority = Priority.HIGHEST)
    private void onMouseRotation(MouseRotationEvent event) {
        if (this.rotationReady) {
            this.yaw = Mth.wrapDegrees(
                    this.yaw + (float) (event.getCursorDeltaX() * DEGREES_PER_UNIT));
            this.pitch = Mth.clamp(
                    this.pitch + (float) (event.getCursorDeltaY() * DEGREES_PER_UNIT),
                    -PITCH_LIMIT, PITCH_LIMIT);
        }

        event.cancel();
    }

    // ------------------------------------------------------------------ камера

    /**
     * Зовётся из {@code CameraMixin} в конце {@code Camera.setup} каждый кадр.
     * Берёт стартовую позицию у ванильной камеры, считает полёт по реальному времени
     * и складывает результат в поля, которые миксин потом переливает в камеру.
     */
    public void advanceCamera(Camera camera) {
        if (!this.positionReady) {
            this.position = camera.position();
            this.yaw = camera.yRot();
            this.pitch = camera.xRot();
            this.positionReady = true;
            this.rotationReady = true;
            this.lastFrameNanos = 0L;
        }

        long now = System.nanoTime();
        double dt = this.lastFrameNanos == 0L
                ? 0.0
                : Math.min((now - this.lastFrameNanos) / 1.0E9, MAX_FRAME_SECONDS);
        this.lastFrameNanos = now;

        Vec3 target = this.wishVelocity();

        if (this.smooth.getValue()) {
            double alpha = 1.0 - Math.exp(-dt / SMOOTH_SECONDS);
            this.velocity = this.velocity.lerp(target, alpha);
        } else {
            this.velocity = target;
        }

        this.position = this.position.add(this.velocity.scale(dt));
    }

    public Vec3 getCameraPosition() { return this.position; }
    public float getCameraYaw() { return this.yaw; }
    public float getCameraPitch() { return this.pitch; }

    // ------------------------------------------------------------------ движение

    /** Желаемая скорость камеры в блоках в секунду по зажатым клавишам. */
    private Vec3 wishVelocity() {
        Minecraft client = mc;

        // В чате и меню буквы — это текст, а не полёт.
        if (client.screen != null) {
            return Vec3.ZERO;
        }

        Options options = client.options;

        float forward = impulse(options.keyUp.isDown(), options.keyDown.isDown());
        float strafe = impulse(options.keyRight.isDown(), options.keyLeft.isDown());
        float vertical = impulse(options.keyJump.isDown(), options.keyShift.isDown());

        if (forward == 0.0F && strafe == 0.0F && vertical == 0.0F) {
            return Vec3.ZERO;
        }

        double yawRadians = Math.toRadians(this.yaw);
        double sin = Math.sin(yawRadians);
        double cos = Math.cos(yawRadians);

        // При yaw=0 взгляд на +Z; вправо — на -X.
        Vec3 forwardDir = new Vec3(-sin, 0.0, cos);
        Vec3 rightDir = new Vec3(-cos, 0.0, -sin);

        Vec3 direction;

        if (this.followView.getValue()) {
            double pitchRadians = Math.toRadians(this.pitch);
            double cosPitch = Math.cos(pitchRadians);
            Vec3 look = new Vec3(-sin * cosPitch, -Math.sin(pitchRadians), cos * cosPitch);
            direction = look.scale(forward).add(rightDir.scale(strafe));
        } else {
            direction = forwardDir.scale(forward).add(rightDir.scale(strafe));
        }

        direction = direction.add(0.0, vertical, 0.0);

        double length = direction.length();

        if (length < 1.0E-6) {
            return Vec3.ZERO;
        }

        return direction.scale(this.speed.getValue() / length);
    }

    private static float impulse(boolean positive, boolean negative) {
        return (positive ? 1.0F : 0.0F) - (negative ? 1.0F : 0.0F);
    }

    /**
     * Первое лицо: камера не «отцеплена», поэтому ваниль сама не рисует тело игрока,
     * за которым она числится, — себя не видно. Руку прячет {@code GameRendererMixin}.
     */
    private void forceFirstPerson() {
        if (mc.options == null) {
            return;
        }

        CameraType type = mc.options.getCameraType();

        if (type.isFirstPerson()) {
            return;
        }

        // Запоминаем только первый раз: это и есть перспектива, в которую надо вернуться.
        if (this.savedPerspective == null) {
            this.savedPerspective = type;
        }

        mc.options.setCameraType(CameraType.FIRST_PERSON);
    }
}
