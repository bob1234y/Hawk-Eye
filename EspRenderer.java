package com.hawkeye.render;

import com.hawkeye.config.Settings;
import com.hawkeye.scan.BaseFinder;
import com.hawkeye.scan.Scanner;
import com.hawkeye.scan.Target;
import com.hawkeye.scan.Targets;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws all through-the-wall overlays using Minecraft's own gizmo system with "always on top"
 * set, which is what makes them visible through terrain. The list of things to draw is rebuilt
 * a few times per second in {@link #tick}; every frame {@link #onBeforeGizmos} just submits it.
 */
public final class EspRenderer {
	private static final int BASE_COLOR = 0xFF4DDB;

	private static List<Target> shown = List.of();
	/** x, y, z, rgb of every point that should get a tracer. */
	private static final List<double[]> TRACERS = new ArrayList<>();

	private static ClientLevel lastLevel;
	private static long ticks;
	private static long lastRebuildTick = Long.MIN_VALUE;
	private static int lastHash;
	private static double lastPx, lastPz;

	private EspRenderer() {
	}

	public static void init() {
		LevelRenderEvents.BEFORE_GIZMOS.register(EspRenderer::onBeforeGizmos);
	}

	/** Ask for the target list to be rebuilt on the next tick. */
	public static void invalidate() {
		lastRebuildTick = Long.MIN_VALUE;
	}

	// ------------------------------------------------------------------ target list (per tick)

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if (level == null || player == null) {
			shown = List.of();
			TRACERS.clear();
			BaseFinder.clear();
			return;
		}
		ticks++;
		Settings s = Settings.get();
		int hash = s.geometryHash();
		double dx = player.getX() - lastPx;
		double dz = player.getZ() - lastPz;
		boolean moved = dx * dx + dz * dz > 36.0;
		if (level != lastLevel || hash != lastHash || ticks - lastRebuildTick >= 10 || moved) {
			lastLevel = level;
			lastHash = hash;
			lastRebuildTick = ticks;
			lastPx = player.getX();
			lastPz = player.getZ();
			rebuild(level, player, s);
		}
	}

	private static void rebuild(ClientLevel level, LocalPlayer player, Settings s) {
		final double px = player.getX();
		final double py = player.getY();
		final double pz = player.getZ();

		final List<Target> targets = new ArrayList<>();
		final List<Target> basePoints = new ArrayList<>();

		if (s.oreEsp || s.blockEsp) {
			final double oreR2 = (double) s.oreRange * s.oreRange;
			final double blockR2 = (double) s.blockRange * s.blockRange;
			final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
			Scanner.INSTANCE.forEachHit(hit -> {
				boolean ore = hit.type.kind == Targets.Kind.ORE;
				if (ore ? !s.oreEsp || !s.oreEnabled(hit.type) : !s.blockEsp || !s.blockEnabled(hit.type)) {
					return;
				}
				double ddx = hit.x + 0.5 - px;
				double ddy = hit.y + 0.5 - py;
				double ddz = hit.z + 0.5 - pz;
				double d2 = ddx * ddx + ddy * ddy + ddz * ddz;
				if (d2 > (ore ? oreR2 : blockR2)) {
					return;
				}
				mp.set(hit.x, hit.y, hit.z);
				BlockState state = level.getBlockState(mp);
				if (Scanner.INSTANCE.activeType(state.getBlock()) != hit.type) {
					return; // mined away or replaced
				}
				Target t = new Target(hit.x - 0.002, hit.y - 0.002, hit.z - 0.002,
						hit.x + 1.002, hit.y + 1.002, hit.z + 1.002,
						ore ? s.oreColor(hit.type) : s.blockColor(hit.type), hit.type, d2);
				t.tracer = ore ? s.oreTracers : s.blockTracers;
				targets.add(t);
			});
		}

		if (s.storageEsp || s.baseFinder) {
			final double r2 = (double) s.storageRange * s.storageRange;
			int pcx = ((int) Math.floor(px)) >> 4;
			int pcz = ((int) Math.floor(pz)) >> 4;
			int cr = (s.storageRange >> 4) + 1;
			for (LevelChunk chunk : Scanner.INSTANCE.loadedChunks()) {
				int cx = chunk.getPos().getMinBlockX() >> 4;
				int cz = chunk.getPos().getMinBlockZ() >> 4;
				if (Math.abs(cx - pcx) > cr || Math.abs(cz - pcz) > cr) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					BlockState state = be.getBlockState();
					Targets.Type type = Targets.storageOf(state.getBlock());
					if (type == null) {
						continue;
					}
					BlockPos bp = be.getBlockPos();
					double ddx = bp.getX() + 0.5 - px;
					double ddy = bp.getY() + 0.5 - py;
					double ddz = bp.getZ() + 0.5 - pz;
					double d2 = ddx * ddx + ddy * ddy + ddz * ddz;
					if (d2 > r2) {
						continue;
					}
					AABB box = shapeBox(level, state, bp);
					int color = s.storageColor(type);
					if (type.key.equals("shulker") && color == type.defaultColor) {
						color = Targets.dyeColor(state.getBlock(), color);
					}
					Target t = new Target(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
							color, type, d2);
					t.tracer = s.storageTracers && s.storageTracerOn(type);
					if (BaseFinder.countsTowardsBase(type)) {
						basePoints.add(t);
					}
					if (s.storageEsp && s.storageEnabled(type)) {
						targets.add(t);
					}
				}
			}
		}

		targets.sort(Comparator.comparingDouble(t -> t.dist2));
		List<Target> capped = targets.size() > s.maxTargets
				? new ArrayList<>(targets.subList(0, s.maxTargets)) : targets;

		if (s.baseFinder) {
			BaseFinder.compute(basePoints, s.baseRadius, s.baseMinContainers, px, py, pz);
		} else {
			BaseFinder.clear();
		}

		TRACERS.clear();
		if (s.baseFinder && s.baseTracer) {
			for (BaseFinder.Cluster c : BaseFinder.clusters()) {
				TRACERS.add(new double[] {c.cx(), c.cy(), c.cz(), BASE_COLOR});
			}
		}
		int tracerBudget = 300;
		for (Target t : capped) {
			if (t.tracer && tracerBudget > 0) {
				TRACERS.add(new double[] {t.cx(), t.cy(), t.cz(), t.rgb});
				tracerBudget--;
			}
		}
		shown = capped;
	}

	private static AABB shapeBox(ClientLevel level, BlockState state, BlockPos pos) {
		VoxelShape shape = state.getShape(level, pos);
		if (shape.isEmpty()) {
			return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
		}
		AABB b = shape.bounds();
		return new AABB(pos.getX() + b.minX, pos.getY() + b.minY, pos.getZ() + b.minZ,
				pos.getX() + b.maxX, pos.getY() + b.maxY, pos.getZ() + b.maxZ);
	}

	// ------------------------------------------------------------------ per-frame submission

	private static int argb(int rgb, float alpha) {
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	private static void onBeforeGizmos(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (mc.level == null || player == null) {
			return;
		}
		Settings s = Settings.get();
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 eye = player.getEyePosition(partial);
		// Tracers start just in front of the eye so they come out of the crosshair.
		Vec3 start = eye.add(player.getViewVector(partial).scale(0.6));
		float pulse = s.pulse ? 0.82f + 0.18f * (float) Math.sin(System.currentTimeMillis() / 320.0) : 1.0f;

		// ---- base finder clusters
		if (s.baseFinder) {
			for (BaseFinder.Cluster c : BaseFinder.clusters()) {
				if (s.baseFootprint) {
					AABB footprint = new AABB(c.minX - 1, c.minY - 1, c.minZ - 1, c.maxX + 1, c.maxY + 1, c.maxZ + 1);
					Gizmos.cuboid(footprint,
							GizmoStyle.strokeAndFill(argb(BASE_COLOR, 0.95f), s.lineWidth * 1.4f, argb(BASE_COLOR, 0.07f)))
							.setAlwaysOnTop();
				}
				if (s.basePillar) {
					double cx = c.cx();
					double cz = c.cz();
					AABB pillar = new AABB(cx - 0.35, c.minY - 6, cz - 0.35, cx + 0.35, c.maxY + 70, cz + 0.35);
					Gizmos.cuboid(pillar, GizmoStyle.fill(argb(BASE_COLOR, 0.30f))).setAlwaysOnTop();
				}
			}
		}

		// ---- ores and containers
		for (Target t : shown) {
			boolean fillOn = switch (t.kind) {
				case ORE -> s.oreFill;
				case BLOCK -> s.blockFill;
				case STORAGE -> s.storageFill;
			};
			boolean outlineOn = switch (t.kind) {
				case ORE -> s.oreOutline;
				case BLOCK -> s.blockOutline;
				case STORAGE -> s.storageOutline;
			};
			int stroke = argb(t.rgb, 0.95f);
			int fill = argb(t.rgb, s.fillAlpha * pulse);
			if (fillOn && outlineOn) {
				Gizmos.cuboid(t.box, GizmoStyle.strokeAndFill(stroke, s.lineWidth, fill)).setAlwaysOnTop();
			} else if (fillOn) {
				Gizmos.cuboid(t.box, GizmoStyle.fill(fill)).setAlwaysOnTop();
			} else if (outlineOn) {
				Gizmos.cuboid(t.box, GizmoStyle.stroke(stroke, s.lineWidth)).setAlwaysOnTop();
			}
			// Glow: a larger, fainter box around the real one.
			if (fillOn && s.glow > 0.01f) {
				double e = t.kind == Targets.Kind.STORAGE ? 0.07 : 0.10;
				Gizmos.cuboid(t.box.inflate(e), GizmoStyle.fill(argb(t.rgb, s.fillAlpha * 0.45f * s.glow * pulse)))
						.setAlwaysOnTop();
			}
		}

		// ---- tracers
		for (double[] p : TRACERS) {
			Gizmos.line(start, new Vec3(p[0], p[1], p[2]), argb((int) p[3], 0.85f), s.tracerWidth).setAlwaysOnTop();
		}

		// ---- entities
		if (s.entityEsp) {
			drawEntities(mc, player, s, partial, eye, start);
		}
	}

	private static void drawEntities(Minecraft mc, LocalPlayer player, Settings s, float partial, Vec3 eye, Vec3 start) {
		int drawn = 0;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == player || e.isRemoved()) {
				continue;
			}
			int rgb;
			if (e instanceof Player) {
				if (!s.espPlayers) continue;
				rgb = 0x5CC8FF;
			} else if (e instanceof Monster) {
				if (!s.espHostile) continue;
				rgb = 0xFF4D4D;
			} else if (e instanceof ItemEntity) {
				if (!s.espItems) continue;
				rgb = 0xFFE14A;
			} else if (e instanceof AgeableMob) {
				if (!s.espPassive) continue;
				rgb = 0x5CFF8A;
			} else {
				continue;
			}
			Vec3 now = e.position();
			Vec3 lerped = e.getPosition(partial);
			AABB box = e.getBoundingBox().move(lerped.x - now.x, lerped.y - now.y, lerped.z - now.z);
			Vec3 center = box.getCenter();
			if (center.distanceToSqr(eye) > 160.0 * 160.0) {
				continue;
			}
			int stroke = argb(rgb, 0.95f);
			if (s.entityFill) {
				Gizmos.cuboid(box, GizmoStyle.strokeAndFill(stroke, s.lineWidth, argb(rgb, 0.16f))).setAlwaysOnTop();
			} else {
				Gizmos.cuboid(box, GizmoStyle.stroke(stroke, s.lineWidth)).setAlwaysOnTop();
			}
			if (s.entityTracers) {
				Gizmos.line(start, center, argb(rgb, 0.8f), s.tracerWidth).setAlwaysOnTop();
			}
			if (++drawn >= 400) {
				break;
			}
		}
	}
}
