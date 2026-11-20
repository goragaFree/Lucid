package dev.lucid.module.impl.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.BooleanSetting;
import dev.lucid.setting.MultiSelectSetting;
import dev.lucid.setting.SelectSetting;
import dev.lucid.setting.SliderSetting;
import dev.lucid.util.math.MathUtil;
import dev.lucid.util.render.ColorUtil;
import dev.lucid.util.render.Render3D;

public class Predictions extends Module {

	private static final String PROJECTILE_ARROW = "Стрела";
	private static final String PROJECTILE_PEARL = "Эндер-перл";
	private static final String PROJECTILE_TRIDENT = "Трезубец";
	private static final String PROJECTILE_SNOWBALL = "Снежок";
	private static final String PROJECTILE_POTION = "Зелья";

	public static final String STYLE_NORMAL = "Обычный";
	public static final String STYLE_GLOW = "Светящийся";

	private static final List<String> PROJECTILES = List.of(
			PROJECTILE_ARROW, PROJECTILE_PEARL, PROJECTILE_TRIDENT, PROJECTILE_SNOWBALL,
			PROJECTILE_POTION);

	private static final int COLOR_ARROW = 0xFFB454;
	private static final int COLOR_PEARL = 0xB45CFF;
	private static final int COLOR_TRIDENT = 0x54D6FF;
	private static final int COLOR_SNOWBALL = 0xEAF7FF;
	private static final int COLOR_POTION = 0xFF6BD6;

	private static final double GRAVITY_ARROW = 0.05;
	private static final double GRAVITY_THROWN = 0.03;
	private static final double GRAVITY_POTION = 0.05;

	private static final float DRAG_AIR = 0.99F;
	private static final float DRAG_WATER_ARROW = 0.6F;
	private static final float DRAG_WATER_TRIDENT = 0.99F;
	private static final float DRAG_WATER_THROWN = 0.8F;

	private static final double EYE_DROP = 0.1;

	private static final float VELOCITY_BOW = 3.0F;
	private static final float VELOCITY_CROSSBOW = 3.15F;
	private static final float VELOCITY_CROSSBOW_FIREWORK = 1.6F;
	private static final float MIN_DRAW_POWER = 0.1F;
	private static final float VELOCITY_TRIDENT = 2.5F;
	private static final float VELOCITY_THROWN = 1.5F;
	private static final float VELOCITY_POTION = 0.5F;

	private static final float PITCH_OFFSET_LOBBED = -20.0F;

	private static final boolean INHERITS_MOTION = true;
	private static final boolean IGNORES_MOTION = false;

	private static final float DEG_TO_RAD = 0.017453292F;

	private static final double MIN_SPEED_SQUARED = 1.0E-6;
	private static final int MAX_TRACKED = 16;

	private static final double CURVE_SPACING = 0.25;
	private static final int CURVE_MAX_POINTS = 320;
	private static final int ARC_STEPS = 8;
	private static final double MIN_LENGTH = 1.0E-6;

	private static final double MARKER_SIZE_NEAR = 0.12;
	private static final double MARKER_GROWTH_PER_BLOCK = 0.004;
	private static final double MARKER_MAX_SIZE = 0.6;
	private static final float MARKER_ALPHA_BOOST = 1.6F;

	private static final int MAX_TICKS = 240;

	private static final double[] CONTROL = new double[(MAX_TICKS + 2) * 3];
	private static final double[] TANGENT = new double[(MAX_TICKS + 2) * 3];
	private static final double[] DENSE = new double[(MAX_TICKS + 1) * ARC_STEPS * 3 + 3];
	private static final double[] ARC = new double[(MAX_TICKS + 1) * ARC_STEPS + 1];

	private static final double MAX_RANGE = 256.0;
	private static final double GHOST_MIN_DISTANCE = 32.0;
	private static final double MAX_TARGET_SPEED = 2.0;
	private static final double MAX_SHOOTER_SPEED = 4.0;
	private static final double LEAD_SPAN_PER_TICK = 0.6;
	private static final double LEAD_SPAN_MAX = 64.0;

	private static final double MOTION_SMOOTH_TIME = 0.08;
	private static final double MAX_FRAME_TIME = 0.1;

	private static final double HAND_SHARE = 0.75;
	private static final double HAND_SIDE = 0.35;
	private static final double HAND_DISTANCE = 1.5;
	private static final int GLOW_SUBDIVISIONS = 12;
	private static final double GLOW_WIDTH = 0.035;
	private static final double GLOW_MIN_SIZE = 0.03;
	private static final double GLOW_MAX_SIZE = 1.2;
	private static final double GLOW_OVERLAP = 0.7;
	private static final double GLOW_RAMP_LENGTH = 0.8;
	private static final double GLOW_RAMP_MIN = 0.3;
	private static final double SHEAR_PER_SHIFT = 6.0;
	private static final double SHEAR_LIMIT = 0.6;
	private static final double MIN_HAND_PATH = 1.5;
	private static final double HAND_FADE_MIN = 0.15;
	private static final double HAND_FADE_FULL = 0.5;

	private final List<Vec3> scratch = new ArrayList<>();
	private final double[] drawn = new double[CURVE_MAX_POINTS * 3];
	private final double[] glowPoints = new double[CURVE_MAX_POINTS * GLOW_SUBDIVISIONS * 3];
	private final float[] glowSizes = new float[CURVE_MAX_POINTS * GLOW_SUBDIVISIONS];

	private final double[] shooterMotion = new double[3];
	private boolean shooterMotionKnown;
	private long frameTime;

	private final Map<Integer, Path> paths = new HashMap<>();
	private int pathsTick = Integer.MIN_VALUE;

	private final Map<Integer, Ghost> ghosts = new HashMap<>();
	private final Set<Integer> present = new HashSet<>();
	private ClientLevel ghostLevel;

	private final MultiSelectSetting projectiles = new MultiSelectSetting(
			"Снаряды", PROJECTILES, PROJECTILES.toArray(new String[0]));

	private final SelectSetting lineStyle = new SelectSetting(
			"Линия", STYLE_GLOW, STYLE_NORMAL, STYLE_GLOW);

	private final BooleanSetting marker = new BooleanSetting("Точка попадания", true);
	private final SliderSetting thickness = new SliderSetting("Толщина", 6.0, 0.5, 20.0, 0.5);
	private final SliderSetting glowSize = new SliderSetting("Свечение", 30.0, 5.0, 100.0, 5.0);
	private final SliderSetting opacity = new SliderSetting("Прозрачность", 70.0, 5.0, 100.0, 5.0);
	private final BooleanSetting throughWalls = new BooleanSetting("Сквозь стены", true);

	public Predictions() {
		super("Predictions", "Траектория летящих снарядов и будущего выстрела", Category.RENDER);
		addSettings(projectiles, lineStyle, marker, thickness, glowSize, opacity, throughWalls);

		Render3D.register(this::extract);
	}

	@Override
	public void onDisable() {
		this.paths.clear();
		this.pathsTick = Integer.MIN_VALUE;
		this.ghosts.clear();
		this.present.clear();
		this.ghostLevel = null;

		this.forgetMotion();
	}

	private void extract(Render3D.Frame frame) {
		if (!this.isEnabled() || this.projectiles.count() == 0) {
			this.forgetMotion();
			return;
		}

		Minecraft client = mc;

		if (client.level == null || client.player == null) {
			this.forgetMotion();
			return;
		}

		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		Vec3 camera = client.gameRenderer.getMainCamera().position();

		this.easeMotion(player);

		Style style = new Style(
				this.thickness.getValue() / 100.0,
				(float) (this.opacity.getValue() / 100.0),
				this.throughWalls.getValue(),
				(float) (this.glowSize.getValue() / 100.0),
				camera);

		this.drawAim(frame, level, player, camera, style);

		if (player.tickCount != this.pathsTick) {
			this.paths.clear();
			this.pathsTick = player.tickCount;
		}

		double maxRangeSquared = MAX_RANGE * MAX_RANGE;

		if (this.ghostLevel != level) {
			this.ghosts.clear();
			this.ghostLevel = level;
		}

		long now = level.getGameTime();

		this.present.clear();

		int tracked = 0;

		for (Entity entity : level.entitiesForRendering()) {
			Kind kind = this.kindOf(entity);

			if (kind == null) {
				continue;
			}

			if (entity.isRemoved() || !level.hasChunkAt(entity.blockPosition())) {
				continue;
			}

			this.present.add(entity.getId());

			if (tracked >= MAX_TRACKED) {
				continue;
			}

			double distanceSquared = entity.position().distanceToSqr(camera);

			if (distanceSquared > maxRangeSquared) {
				this.ghosts.remove(entity.getId());
				continue;
			}

			Path path = this.paths.get(entity.getId());

			if (path == null) {
				Vec3 origin = entity.position();

				path = this.simulate(level, entity, kind,
						origin, entity.getDeltaMovement(),
						origin.subtract(Predictions.motionOf(entity, MAX_SHOOTER_SPEED)));

				if (path == null) {
					this.ghosts.remove(entity.getId());
					continue;
				}

				this.paths.put(entity.getId(), path);
				this.ghosts.put(entity.getId(),
						new Ghost(path, kind, now, Math.sqrt(distanceSquared)));
			}

			int color = withAlpha(kind.rgb, style.alpha());

			this.draw(frame, path, Anchor.at(MathUtil.interpolate(entity)), color, style);

			if (path.hit() != null && this.marker.getValue()) {
				this.drawMarker(frame, camera, path.hit(), color, style);
			}

			tracked++;
		}

		this.drawGhosts(frame, now, camera, style);
	}

	private void drawGhosts(Render3D.Frame frame, long now, Vec3 camera, Style style) {
		if (this.ghosts.isEmpty()) {
			return;
		}

		float partial = MathUtil.partialTick();

		Iterator<Map.Entry<Integer, Ghost>> iterator = this.ghosts.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<Integer, Ghost> entry = iterator.next();

			if (this.present.contains(entry.getKey())) {
				continue;
			}

			Ghost ghost = entry.getValue();
			int points = ghost.path().ticks().length / 3;

			double progress = Math.max(0.0, (now - ghost.time()) + partial - 1.0);

			if (ghost.distance() < GHOST_MIN_DISTANCE
					|| progress >= points - 1
					|| now - ghost.time() > MAX_TICKS) {
				iterator.remove();
				continue;
			}

			this.drawGhost(frame, ghost, progress, camera, style);
		}
	}

	private void drawGhost(
			Render3D.Frame frame,
			Ghost ghost,
			double progress,
			Vec3 camera,
			Style style) {
		Path path = ghost.path();
		double[] ticks = path.ticks();
		double[] curve = path.curve();
		int count = curve.length / 3;

		int color = withAlpha(ghost.kind().rgb, style.alpha());

		int tick = (int) Math.floor(progress);
		double fraction = progress - tick;

		double x = ticks[tick * 3] + (ticks[tick * 3 + 3] - ticks[tick * 3]) * fraction;
		double y = ticks[tick * 3 + 1] + (ticks[tick * 3 + 4] - ticks[tick * 3 + 1]) * fraction;
		double z = ticks[tick * 3 + 2] + (ticks[tick * 3 + 5] - ticks[tick * 3 + 2]) * fraction;

		if (count >= 2) {
			int nearest = 0;
			double best = Double.MAX_VALUE;

			for (int index = 0; index < count; index++) {
				double dx = curve[index * 3] - x;
				double dy = curve[index * 3 + 1] - y;
				double dz = curve[index * 3 + 2] - z;
				double squared = dx * dx + dy * dy + dz * dz;

				if (squared < best) {
					best = squared;
					nearest = index;
				}
			}

			int next = Math.min(nearest + 1, count - 1);
			double along = (curve[next * 3] - curve[nearest * 3]) * (x - curve[nearest * 3])
					+ (curve[next * 3 + 1] - curve[nearest * 3 + 1]) * (y - curve[nearest * 3 + 1])
					+ (curve[next * 3 + 2] - curve[nearest * 3 + 2]) * (z - curve[nearest * 3 + 2]);

			int first = Math.max(1, along > 0.0 ? nearest + 1 : nearest);
			int visible = count - first + 1;

			if (visible >= 2) {
				this.drawn[0] = x;
				this.drawn[1] = y;
				this.drawn[2] = z;

				System.arraycopy(curve, first * 3, this.drawn, 3, (visible - 1) * 3);

				if (this.lineStyle.is(STYLE_GLOW)) {
					this.drawGlow(frame, this.drawn, visible, x, y, z, color, style);
				} else {
					frame.polyline(this.drawn, visible, x, y, z,
							style.thickness(), color, style.throughWalls());
				}
			}
		}

		if (path.hit() != null && this.marker.getValue()) {
			this.drawMarker(frame, camera, path.hit(), color, style);
		}
	}

	private void easeMotion(LocalPlayer player) {
		Vec3 target = Predictions.motionOf(player, MAX_SHOOTER_SPEED);
		double ty = player.onGround() ? 0.0 : target.y;

		long now = System.nanoTime();
		double seconds = this.frameTime == 0L ? 0.0 : (now - this.frameTime) / 1.0E9;
		this.frameTime = now;

		if (!this.shooterMotionKnown || seconds <= 0.0) {
			this.shooterMotion[0] = target.x;
			this.shooterMotion[1] = ty;
			this.shooterMotion[2] = target.z;
			this.shooterMotionKnown = true;

			return;
		}

		double fraction = 1.0 - Math.exp(-Math.min(seconds, MAX_FRAME_TIME) / MOTION_SMOOTH_TIME);

		this.shooterMotion[0] += (target.x - this.shooterMotion[0]) * fraction;
		this.shooterMotion[1] += (ty - this.shooterMotion[1]) * fraction;
		this.shooterMotion[2] += (target.z - this.shooterMotion[2]) * fraction;
	}

	private void forgetMotion() {
		this.shooterMotionKnown = false;
		this.frameTime = 0L;
	}

	private Vec3 shooterMotion() {
		return new Vec3(this.shooterMotion[0], this.shooterMotion[1], this.shooterMotion[2]);
	}

	private static Vec3 eyeOf(LocalPlayer player) {
		float partial = MathUtil.partialTick();

		return new Vec3(
				MathUtil.lerp(player.xo, player.getX(), partial),
				MathUtil.lerp(player.yo, player.getY(), partial) + player.getEyeHeight() - EYE_DROP,
				MathUtil.lerp(player.zo, player.getZ(), partial));
	}

	private static Vec3 handOf(LocalPlayer player, boolean mainHand) {
		float partial = MathUtil.partialTick();

		Vec3 forward = Predictions.direction(0.0F, player.getYRot());
		boolean right = (player.getMainArm() == HumanoidArm.RIGHT) == mainHand;
		double side = right ? HAND_SIDE : -HAND_SIDE;

		return new Vec3(
				MathUtil.lerp(player.xo, player.getX(), partial) - forward.z * side,
				MathUtil.lerp(player.yo, player.getY(), partial) + player.getEyeHeight() * HAND_SHARE,
				MathUtil.lerp(player.zo, player.getZ(), partial) + forward.x * side);
	}

	private void drawAim(
			Render3D.Frame frame,
			ClientLevel level,
			LocalPlayer player,
			Vec3 camera,
			Style style) {
		Launch launch = this.launchOf(player.getMainHandItem(), player);
		boolean mainHand = launch != null;

		if (launch == null) {
			launch = this.launchOf(player.getOffhandItem(), player);
		}

		if (launch == null) {
			return;
		}

		Vec3 start = Predictions.eyeOf(player);

		Vec3 velocity = Predictions.direction(
						player.getXRot(),
						player.getYRot(),
						launch.pitchOffset())
				.scale(launch.velocity());

		Vec3 motion = this.shooterMotion();

		if (launch.inheritsMotion()) {
			velocity = velocity.add(motion);
		}

		Path path = this.simulate(level, player, launch.kind(), start, velocity,
				start.subtract(motion));

		if (path == null) {
			return;
		}

		int color = withAlpha(launch.kind().rgb, style.alpha());

		double[] curve = path.curve();
		int count = curve.length / 3;

		Anchor anchor = Anchor.at(start);

		if (count > 2) {
			Vec3 exit = Predictions.direction(0.0F, player.getYRot());

			Vec3 arm = Predictions.handOf(player, mainHand)
					.add(exit.scale(HAND_DISTANCE));
			double length = Predictions.spacing(curve) * (count - 1);

			Vec3 shift = arm.subtract(start);
			double wanted = shift.length() * SHEAR_PER_SHIFT;
			double room = Math.max(0.0, length - MIN_HAND_PATH) * SHEAR_LIMIT;

			double facing = (Predictions.forward(curve, exit) - HAND_FADE_MIN)
					/ (HAND_FADE_FULL - HAND_FADE_MIN);
			double scale = MathUtil.smoothStep((float) Mth.clamp(facing, 0.0, 1.0));

			scale *= wanted > 0.0 ? Math.min(1.0, room / wanted) : 0.0;

			if (scale > 0.0) {
				anchor = Anchor.from(start.add(shift.scale(scale)), start, wanted * scale);
			}
		}

		this.draw(frame, path, anchor, color, style);

		if (path.hit() != null && this.marker.getValue()) {
			this.drawMarker(frame, camera, path.hit(), color, style);
		}
	}

	private Launch launchOf(ItemStack stack, Player player) {
		if (stack.isEmpty()) {
			return null;
		}

		if (stack.getItem() instanceof BowItem) {
			if (!this.projectiles.isSelected(PROJECTILE_ARROW) || !player.isUsingItem()) {
				return null;
			}

			float power = BowItem.getPowerForTime(player.getTicksUsingItem());

			return power < MIN_DRAW_POWER
					? null
					: new Launch(Kind.ARROW, power * VELOCITY_BOW, 0.0F, INHERITS_MOTION);
		}

		if (stack.getItem() instanceof CrossbowItem) {
			if (!this.projectiles.isSelected(PROJECTILE_ARROW) || !CrossbowItem.isCharged(stack)) {
				return null;
			}

			ChargedProjectiles charge = stack.get(DataComponents.CHARGED_PROJECTILES);

			float velocity = charge != null && charge.contains(Items.FIREWORK_ROCKET)
					? VELOCITY_CROSSBOW_FIREWORK
					: VELOCITY_CROSSBOW;

			return new Launch(Kind.ARROW, velocity, 0.0F, IGNORES_MOTION);
		}

		if (stack.getItem() == Items.TRIDENT) {
			return this.projectiles.isSelected(PROJECTILE_TRIDENT)
					? new Launch(Kind.TRIDENT, VELOCITY_TRIDENT, 0.0F, INHERITS_MOTION)
					: null;
		}

		if (stack.getItem() == Items.ENDER_PEARL) {
			return this.projectiles.isSelected(PROJECTILE_PEARL)
					? new Launch(Kind.PEARL, VELOCITY_THROWN, 0.0F, INHERITS_MOTION)
					: null;
		}

		if (stack.getItem() == Items.SNOWBALL) {
			return this.projectiles.isSelected(PROJECTILE_SNOWBALL)
					? new Launch(Kind.SNOWBALL, VELOCITY_THROWN, 0.0F, INHERITS_MOTION)
					: null;
		}

		if (stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.LINGERING_POTION) {
			return this.projectiles.isSelected(PROJECTILE_POTION)
					? new Launch(Kind.POTION, VELOCITY_POTION, PITCH_OFFSET_LOBBED, INHERITS_MOTION)
					: null;
		}

		return null;
	}

	private Kind kindOf(Entity entity) {
		if (entity instanceof ThrownTrident) {
			return this.projectiles.isSelected(PROJECTILE_TRIDENT) ? Kind.TRIDENT : null;
		}

		if (entity instanceof AbstractArrow) {
			return this.projectiles.isSelected(PROJECTILE_ARROW) ? Kind.ARROW : null;
		}

		if (entity instanceof ThrownEnderpearl) {
			return this.projectiles.isSelected(PROJECTILE_PEARL) ? Kind.PEARL : null;
		}

		if (entity instanceof Snowball) {
			return this.projectiles.isSelected(PROJECTILE_SNOWBALL) ? Kind.SNOWBALL : null;
		}

		if (entity instanceof AbstractThrownPotion) {
			return this.projectiles.isSelected(PROJECTILE_POTION) ? Kind.POTION : null;
		}

		return null;
	}

	private Path simulate(
			ClientLevel level,
			Entity context,
			Kind kind,
			Vec3 start,
			Vec3 velocity,
			Vec3 before) {
		if (velocity.lengthSqr() < MIN_SPEED_SQUARED) {
			return null;
		}

		this.scratch.clear();
		this.scratch.add(start);

		Vec3 pos = start;
		double gravity = kind.gravity;
		int minY = level.getMinY();
		Vec3 block = null;

		for (int tick = 0; tick < MAX_TICKS; tick++) {
			boolean water = Predictions.isWater(level, pos);
			float drag = water ? kind.waterDrag : DRAG_AIR;
			Vec3 next;

			if (kind.arrowPhysics) {
				next = pos.add(velocity);

				velocity = velocity.scale(drag);
				velocity = velocity.add(0.0, -gravity, 0.0);
			} else {
				velocity = velocity.add(0.0, -gravity, 0.0).scale(drag);
				next = pos.add(velocity);
			}

			BlockHitResult hit = level.clipIncludingBorder(new ClipContext(
					pos, next,
					ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
					context));

			boolean blocked = hit.getType() != HitResult.Type.MISS;
			Vec3 end = blocked ? hit.getLocation() : next;

			this.scratch.add(end);

			if (blocked) {
				block = end;
				break;
			}

			if (end.y < minY) {
				break;
			}

			pos = next;
		}

		int age = context instanceof Projectile projectile ? projectile.tickCount : 0;

		Hit entity = this.firstEntityOnPath(level, context, this.scratch, age);

		Vec3 hit = entity != null ? entity.point() : block;

		double[] ticks = Predictions.array(this.scratch);
		double[] curve = Predictions.curve(this.scratch, before);

		return new Path(curve, ticks, hit);
	}

	private void draw(Render3D.Frame frame, Path path, Anchor anchor, int color, Style style) {
		double[] curve = path.curve();
		int count = curve.length / 3;

		int head = anchor.head(curve, count);
		int visible = count - head;

		if (visible >= 2) {
			anchor.fill(this.drawn, curve, head, visible);

			Vec3 start = anchor.start();

			if (this.lineStyle.is(STYLE_GLOW)) {
				this.drawGlow(frame, this.drawn, visible, start.x, start.y, start.z, color, style);
			} else {
				frame.polyline(this.drawn, visible, start.x, start.y, start.z,
						style.thickness(), color, style.throughWalls());
			}
		}

	}

	private void drawGlow(
			Render3D.Frame frame,
			double[] points,
			int count,
			double sx,
			double sy,
			double sz,
			int color,
			Style style) {
		double width = style.glow() * GLOW_WIDTH;
		Vec3 camera = style.camera();

		double along = 0.0;
		int out = 0;

		double px = points[0];
		double py = points[1];
		double pz = points[2];
		double previousSize = this.glowSize(px, py, pz, 0.0, width, camera);

		this.glowPoints[0] = px;
		this.glowPoints[1] = py;
		this.glowPoints[2] = pz;
		this.glowSizes[0] = (float) previousSize;
		out = 1;

		for (int index = 1; index < count; index++) {
			double x = points[index * 3];
			double y = points[index * 3 + 1];
			double z = points[index * 3 + 2];

			double dx = x - px;
			double dy = y - py;
			double dz = z - pz;
			double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

			double endSize = this.glowSize(x, y, z, along + length, width, camera);
			int steps = (int) Math.ceil(length / Math.max(Math.min(previousSize, endSize) * GLOW_OVERLAP, MIN_LENGTH));
			steps = Mth.clamp(steps, 1, GLOW_SUBDIVISIONS);

			for (int step = 1; step <= steps; step++) {
				double t = (double) step / steps;
				double qx = px + dx * t;
				double qy = py + dy * t;
				double qz = pz + dz * t;
				double size = step == steps
						? endSize
						: this.glowSize(qx, qy, qz, along + length * t, width, camera);

				this.glowPoints[out * 3] = qx;
				this.glowPoints[out * 3 + 1] = qy;
				this.glowPoints[out * 3 + 2] = qz;
				this.glowSizes[out] = (float) size;
				out++;
			}

			along += length;
			previousSize = endSize;
			px = x;
			py = y;
			pz = z;
		}

		frame.glowLine(this.glowPoints, out, sx, sy, sz, this.glowSizes,
				color & 0xFFFFFF, style.alpha(), style.throughWalls());
	}

	private double glowSize(double x, double y, double z, double along, double width, Vec3 camera) {
		double dx = x - camera.x;
		double dy = y - camera.y;
		double dz = z - camera.z;
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

		double ramp = GLOW_RAMP_MIN
				+ (1.0 - GLOW_RAMP_MIN) * MathUtil.smoothStep((float) Math.min(along / GLOW_RAMP_LENGTH, 1.0));

		return Math.min(Math.max(distance * width, GLOW_MIN_SIZE), GLOW_MAX_SIZE) * ramp;
	}

	private Hit firstEntityOnPath(ClientLevel level, Entity context, List<Vec3> path, int age) {
		if (path.size() < 2) {
			return null;
		}

		AABB span = Predictions.spanOf(path)
				.inflate(Math.min(path.size() * LEAD_SPAN_PER_TICK, LEAD_SPAN_MAX));

		List<Entity> candidates = level.getEntities(
				context, span, entity -> Predictions.canBeHit(entity, context));

		if (candidates.isEmpty()) {
			return null;
		}

		int bestSegment = Integer.MAX_VALUE;
		double bestDistance = Double.MAX_VALUE;
		Vec3 bestPoint = null;
		Vec3 bestShift = null;
		Entity bestTarget = null;

		for (Entity candidate : candidates) {
			AABB box = Predictions.boxOf(candidate);

			Vec3 motion = Predictions.motionOf(candidate, MAX_TARGET_SPEED);
			boolean moving = motion.lengthSqr() > 0.0;

			for (int index = 1; index < path.size(); index++) {
				double margin = Predictions.marginAt(age + index);

				Vec3 previous = path.get(index - 1);
				Vec3 current = path.get(index);

				double ax = previous.x;
				double ay = previous.y;
				double az = previous.z;

				double bx = current.x;
				double by = current.y;
				double bz = current.z;

				if (moving) {
					ax -= motion.x * (index - 1);
					ay -= motion.y * (index - 1);
					az -= motion.z * (index - 1);

					bx -= motion.x * index;
					by -= motion.y * index;
					bz -= motion.z * index;
				}

				if (!Predictions.overlaps(box, margin, ax, ay, az, bx, by, bz)) {
					continue;
				}

				Optional<Vec3> clip = (margin > 0.0 ? box.inflate(margin) : box).clip(
						moving ? new Vec3(ax, ay, az) : previous,
						moving ? new Vec3(bx, by, bz) : current);

				if (clip.isEmpty()) {
					continue;
				}

				Vec3 point = clip.get();

				double dx = point.x - ax;
				double dy = point.y - ay;
				double dz = point.z - az;
				double distance = dx * dx + dy * dy + dz * dz;

				if (index > bestSegment || (index == bestSegment && distance >= bestDistance)) {
					continue;
				}

				double spanLength = (bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az);

				double fraction = spanLength > MIN_SPEED_SQUARED
						? Math.sqrt(distance / spanLength)
						: 0.0;

				double when = index - 1 + fraction;

				bestSegment = index;
				bestDistance = distance;
				bestPoint = point.add(motion.scale(when));
				bestShift = motion.scale(when);
				bestTarget = candidate;
				break;
			}
		}

		if (bestTarget == null) {
			return null;
		}

		while (path.size() > bestSegment) {
			path.remove(path.size() - 1);
		}

		path.add(bestPoint);

		return new Hit(bestPoint, bestTarget, bestShift);
	}

	private static double marginAt(int tick) {
		double margin = (tick - 2) / 20.0;

		return Math.max(0.0, Math.min(ProjectileUtil.DEFAULT_ENTITY_HIT_RESULT_MARGIN, margin));
	}

	private static boolean overlaps(
			AABB box,
			double margin,
			double ax,
			double ay,
			double az,
			double bx,
			double by,
			double bz) {
		return box.minX - margin < Math.max(ax, bx) && box.maxX + margin > Math.min(ax, bx)
				&& box.minY - margin < Math.max(ay, by) && box.maxY + margin > Math.min(ay, by)
				&& box.minZ - margin < Math.max(az, bz) && box.maxZ + margin > Math.min(az, bz);
	}

	private static Vec3 motionOf(Entity entity, double maxSpeed) {
		Vec3 motion = new Vec3(
				entity.getX() - entity.xo,
				entity.getY() - entity.yo,
				entity.getZ() - entity.zo);

		double speed = motion.length();

		return speed > maxSpeed ? motion.scale(maxSpeed / speed) : motion;
	}

	private static boolean canBeHit(Entity entity, Entity context) {
		if (entity == context || entity instanceof Projectile || !entity.canBeHitByProjectile()) {
			return false;
		}

		return !(context instanceof Projectile projectile) || entity != projectile.getOwner();
	}

	private static AABB boxOf(Entity entity) {
		Vec3 interpolated = MathUtil.interpolate(entity);

		return entity.getBoundingBox().move(
				interpolated.x - entity.getX(),
				interpolated.y - entity.getY(),
				interpolated.z - entity.getZ());
	}

	private static double[] curve(List<Vec3> path, Vec3 before) {
		int size = path.size();

		if (size < 2) {
			return Predictions.array(path);
		}

		boolean reserve = before.distanceToSqr(path.get(0)) > MIN_LENGTH * MIN_LENGTH;
		int offset = reserve ? 1 : 0;
		int controls = size + offset;

		if (reserve) {
			CONTROL[0] = before.x;
			CONTROL[1] = before.y;
			CONTROL[2] = before.z;
		}

		for (int index = 0; index < size; index++) {
			Vec3 point = path.get(index);
			int at = (index + offset) * 3;

			CONTROL[at] = point.x;
			CONTROL[at + 1] = point.y;
			CONTROL[at + 2] = point.z;
		}

		Predictions.tangents(controls);

		int dense = (controls - 1) * ARC_STEPS + 1;

		for (int index = 0; index < dense; index++) {
			int span = index / ARC_STEPS;
			float t = (float) (index % ARC_STEPS) / ARC_STEPS;

			Predictions.hermite(span, Math.min(span + 1, controls - 1), t, DENSE, index * 3);
		}

		double total = 0.0;

		ARC[0] = 0.0;

		for (int index = 1; index < dense; index++) {
			int at = index * 3;
			int previous = at - 3;

			double dx = DENSE[at] - DENSE[previous];
			double dy = DENSE[at + 1] - DENSE[previous + 1];
			double dz = DENSE[at + 2] - DENSE[previous + 2];

			total += Math.sqrt(dx * dx + dy * dy + dz * dz);

			ARC[index] = total;
		}

		int points = Math.min(
				Math.max((int) Math.ceil(total / CURVE_SPACING) + 1, 2),
				CURVE_MAX_POINTS);

		double[] result = new double[points * 3];
		double step = total / (points - 1);

		int span = 0;

		for (int index = 0; index < points; index++) {
			double target = index * step;

			while (span < dense - 1 && ARC[span + 1] < target) {
				span++;
			}

			double length = ARC[span + 1] - ARC[span];
			double part = length > MIN_LENGTH ? (target - ARC[span]) / length : 0.0;

			int at = index * 3;
			int from = span * 3;
			int to = from + 3;

			result[at] = DENSE[from] + (DENSE[to] - DENSE[from]) * part;
			result[at + 1] = DENSE[from + 1] + (DENSE[to + 1] - DENSE[from + 1]) * part;
			result[at + 2] = DENSE[from + 2] + (DENSE[to + 2] - DENSE[from + 2]) * part;
		}

		Vec3 end = path.get(size - 1);

		result[(points - 1) * 3] = end.x;
		result[(points - 1) * 3 + 1] = end.y;
		result[(points - 1) * 3 + 2] = end.z;

		return result;
	}

	private static int head(double[] curve, int count, Vec3 start) {
		double dx = curve[3] - curve[0];
		double dy = curve[4] - curve[1];
		double dz = curve[5] - curve[2];

		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

		if (length < MIN_LENGTH) {
			return 0;
		}

		dx /= length;
		dy /= length;
		dz /= length;

		double along = (start.x - curve[0]) * dx
				+ (start.y - curve[1]) * dy
				+ (start.z - curve[2]) * dz;

		if (along <= 0.0) {
			return 0;
		}

		for (int index = 1; index < count; index++) {
			double at = (curve[index * 3] - curve[0]) * dx
					+ (curve[index * 3 + 1] - curve[1]) * dy
					+ (curve[index * 3 + 2] - curve[2]) * dz;

			if (at >= along) {
				return index;
			}
		}

		return count - 1;
	}

	private static double forward(double[] curve, Vec3 exit) {
		double dx = curve[3] - curve[0];
		double dy = curve[4] - curve[1];
		double dz = curve[5] - curve[2];

		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

		if (length < MIN_LENGTH) {
			return 0.0;
		}

		return (dx * exit.x + dy * exit.y + dz * exit.z) / length;
	}

	private static double spacing(double[] curve) {
		int last = curve.length - 3;

		double dx = curve[last] - curve[last - 3];
		double dy = curve[last + 1] - curve[last - 2];
		double dz = curve[last + 2] - curve[last - 1];

		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static double weight(double distance, double span) {
		if (span <= 0.0) {
			return 0.0;
		}

		return 1.0 - MathUtil.smoothStep((float) Math.min(distance / span, 1.0));
	}

	private static double[] array(List<Vec3> path) {
		double[] result = new double[path.size() * 3];

		for (int index = 0; index < path.size(); index++) {
			Vec3 point = path.get(index);

			result[index * 3] = point.x;
			result[index * 3 + 1] = point.y;
			result[index * 3 + 2] = point.z;
		}

		return result;
	}

	private static void tangents(int controls) {
		for (int index = 0; index < controls; index++) {
			int at = index * 3;
			int before = index > 0 ? at - 3 : -1;
			int after = index + 1 < controls ? at + 3 : -1;

			double px = before >= 0 ? CONTROL[before] : 2.0 * CONTROL[at] - CONTROL[after];
			double py = before >= 0 ? CONTROL[before + 1] : 2.0 * CONTROL[at + 1] - CONTROL[after + 1];
			double pz = before >= 0 ? CONTROL[before + 2] : 2.0 * CONTROL[at + 2] - CONTROL[after + 2];

			double nx = after >= 0 ? CONTROL[after] : 2.0 * CONTROL[at] - px;
			double ny = after >= 0 ? CONTROL[after + 1] : 2.0 * CONTROL[at + 1] - py;
			double nz = after >= 0 ? CONTROL[after + 2] : 2.0 * CONTROL[at + 2] - pz;

			double back = Math.sqrt(
					(CONTROL[at] - px) * (CONTROL[at] - px)
							+ (CONTROL[at + 1] - py) * (CONTROL[at + 1] - py)
							+ (CONTROL[at + 2] - pz) * (CONTROL[at + 2] - pz));
			double ahead = Math.sqrt(
					(nx - CONTROL[at]) * (nx - CONTROL[at])
							+ (ny - CONTROL[at + 1]) * (ny - CONTROL[at + 1])
							+ (nz - CONTROL[at + 2]) * (nz - CONTROL[at + 2]));
			double total = back + ahead;

			if (total < MIN_LENGTH) {
				TANGENT[at] = 0.0;
				TANGENT[at + 1] = 0.0;
				TANGENT[at + 2] = 0.0;

				continue;
			}

			TANGENT[at] = ((nx - CONTROL[at]) * back + (CONTROL[at] - px) * ahead) / total;
			TANGENT[at + 1] = ((ny - CONTROL[at + 1]) * back + (CONTROL[at + 1] - py) * ahead) / total;
			TANGENT[at + 2] = ((nz - CONTROL[at + 2]) * back + (CONTROL[at + 2] - pz) * ahead) / total;
		}
	}

	private static void hermite(int from, int to, float t, double[] target, int at) {
		float t2 = t * t;
		float t3 = t2 * t;

		int f = from * 3;
		int o = to * 3;

		for (int axis = 0; axis < 3; axis++) {
			double p1 = CONTROL[f + axis];
			double m1 = TANGENT[f + axis];
			double p2 = CONTROL[o + axis];
			double m2 = TANGENT[o + axis];

			target[at + axis] = (2.0 * t3 - 3.0 * t2 + 1.0) * p1
					+ (t3 - 2.0 * t2 + t) * m1
					+ (-2.0 * t3 + 3.0 * t2) * p2
					+ (t3 - t2) * m2;
		}
	}

	private static AABB spanOf(List<Vec3> path) {
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;

		for (Vec3 point : path) {
			minX = Math.min(minX, point.x);
			minY = Math.min(minY, point.y);
			minZ = Math.min(minZ, point.z);
			maxX = Math.max(maxX, point.x);
			maxY = Math.max(maxY, point.y);
			maxZ = Math.max(maxZ, point.z);
		}

		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private void drawMarker(
			Render3D.Frame frame,
			Vec3 camera,
			Vec3 point,
			int color,
			Style style) {
		double size = Math.min(
				MARKER_SIZE_NEAR + point.distanceTo(camera) * MARKER_GROWTH_PER_BLOCK,
				MARKER_MAX_SIZE);
		double half = size * 0.5;

		frame.filledBox(
				point.x - half, point.y - half, point.z - half,
				point.x + half, point.y + half, point.z + half,
				color, style.throughWalls());

		frame.boxOutline(
				point.x - half, point.y - half, point.z - half,
				point.x + half, point.y + half, point.z + half,
				style.thickness(),
				withAlpha(color, Math.min(alphaOf(color) * MARKER_ALPHA_BOOST, 1.0F)),
				style.throughWalls());
	}

	private static Vec3 direction(float xRot, float yRot) {
		return Predictions.direction(xRot, yRot, 0.0F);
	}

	private static Vec3 direction(float xRot, float yRot, float roll) {
		float x = -Mth.sin(yRot * DEG_TO_RAD) * Mth.cos(xRot * DEG_TO_RAD);
		float y = -Mth.sin((xRot + roll) * DEG_TO_RAD);
		float z = Mth.cos(yRot * DEG_TO_RAD) * Mth.cos(xRot * DEG_TO_RAD);

		return new Vec3(x, y, z).normalize();
	}

	private static boolean isWater(Level level, Vec3 pos) {
		return level.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
	}

	private static float alphaOf(int color) {
		return ((color >>> 24) & 0xFF) / 255.0F;
	}

	private static int withAlpha(int rgb, float alpha) {
		return ColorUtil.withAlpha(rgb, Math.round(alpha * 255.0F));
	}

	private record Style(
			double thickness,
			float alpha,
			boolean throughWalls,
			float glow,
			Vec3 camera) {
	}

	private record Anchor(Vec3 start, Vec3 shift, double span) {

		static Anchor at(Vec3 start) {
			return new Anchor(start, Vec3.ZERO, 0.0);
		}

		static Anchor from(Vec3 start, Vec3 origin, double span) {
			return new Anchor(start, start.subtract(origin), span);
		}

		private boolean sheared() {
			return this.span > 0.0;
		}

		int head(double[] curve, int count) {
			return Predictions.head(curve, count, this.start.subtract(this.shift));
		}

		void fill(double[] target, double[] curve, int head, int visible) {
			if (!this.sheared()) {
				System.arraycopy(curve, head * 3, target, 0, visible * 3);
				return;
			}

			double step = Predictions.spacing(curve);

			for (int index = 0; index < visible; index++) {
				int to = index * 3;
				int from = (head + index) * 3;

				double weight = Predictions.weight(index * step, this.span);

				target[to] = curve[from] + this.shift.x * weight;
				target[to + 1] = curve[from + 1] + this.shift.y * weight;
				target[to + 2] = curve[from + 2] + this.shift.z * weight;
			}
		}
	}

	private record Path(double[] curve, double[] ticks, Vec3 hit) {
	}

	private record Hit(Vec3 point, Entity entity, Vec3 shift) {
	}

	private record Ghost(Path path, Kind kind, long time, double distance) {
	}

	private record Launch(Kind kind, float velocity, float pitchOffset, boolean inheritsMotion) {
	}

	private enum Kind {

		ARROW(COLOR_ARROW, GRAVITY_ARROW, DRAG_WATER_ARROW, true),
		TRIDENT(COLOR_TRIDENT, GRAVITY_ARROW, DRAG_WATER_TRIDENT, true),
		PEARL(COLOR_PEARL, GRAVITY_THROWN, DRAG_WATER_THROWN, false),
		SNOWBALL(COLOR_SNOWBALL, GRAVITY_THROWN, DRAG_WATER_THROWN, false),
		POTION(COLOR_POTION, GRAVITY_POTION, DRAG_WATER_THROWN, false);

		private final int rgb;
		private final double gravity;
		private final float waterDrag;
		private final boolean arrowPhysics;

		Kind(int rgb, double gravity, float waterDrag, boolean arrowPhysics) {
			this.rgb = rgb;
			this.gravity = gravity;
			this.waterDrag = waterDrag;
			this.arrowPhysics = arrowPhysics;
		}
	}
}