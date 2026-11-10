package com.hawkeye.module;

import com.hawkeye.config.Settings;
import com.hawkeye.render.EspRenderer;
import com.hawkeye.scan.Targets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Small per-tick modules (fullbright, sprint, flight) plus xray helpers. */
public final class Modules {
	private static boolean nightVisionApplied;
	private static boolean flightApplied;

	private Modules() {
	}

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			nightVisionApplied = false;
			flightApplied = false;
			return;
		}
		Settings s = Settings.get();

		// Fullbright: a client-side night vision effect.
		if (s.fullbright) {
			if (!player.hasEffect(MobEffects.NIGHT_VISION)) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1_000_000, 0, false, false, false));
			}
			nightVisionApplied = true;
		} else if (nightVisionApplied) {
			player.removeEffect(MobEffects.NIGHT_VISION);
			nightVisionApplied = false;
		}

		// Auto sprint while walking forward.
		if (s.autoSprint && mc.gui.screen() == null && mc.options.keyUp.isDown()
				&& !player.isShiftKeyDown() && !player.horizontalCollision
				&& player.getFoodData().getFoodLevel() > 6) {
			player.setSprinting(true);
		}

		// Flight: creative-style abilities. Servers still have the final say.
		Abilities abilities = player.getAbilities();
		if (s.flight) {
			abilities.mayfly = true;
			abilities.setFlyingSpeed(s.flySpeed);
			flightApplied = true;
		} else if (flightApplied) {
			if (!player.isCreative() && !player.isSpectator()) {
				abilities.mayfly = false;
				abilities.flying = false;
			}
			abilities.setFlyingSpeed(0.05f);
			flightApplied = false;
		}
	}

	// ---- xray -------------------------------------------------------------

	/** Used by the terrain mixin: should this block still be drawn while xray is on? */
	public static boolean xrayShows(BlockState state) {
		Settings s = Settings.get();
		Block block = state.getBlock();
		Targets.Type ore = Targets.oreOf(block);
		if (ore != null) {
			return s.oreEnabled(ore);
		}
		if (s.blockEsp) {
			Targets.Type chosen = Targets.blockOf(block);
			if (chosen != null && s.blockEnabled(chosen)) {
				return true;
			}
		}
		if (s.xrayShowStorage) {
			Targets.Type storage = Targets.storageOf(block);
			return storage != null && s.storageEnabled(storage);
		}
		return false;
	}

	public static void setXray(Minecraft mc, boolean on) {
		Settings s = Settings.get();
		if (s.xray == on) {
			return;
		}
		s.xray = on;
		reloadTerrain(mc);
	}

	public static void toggleXray(Minecraft mc) {
		setXray(mc, !Settings.get().xray);
		notify(mc, "Xray " + (Settings.get().xray ? "ON" : "OFF"));
	}

	/** Chunks must be re-meshed for the terrain filter to take effect. */
	public static void reloadTerrain(Minecraft mc) {
		if (mc.levelExtractor != null && mc.level != null) {
			mc.levelExtractor.allChanged();
		}
		EspRenderer.invalidate();
	}

	public static void notify(Minecraft mc, String message) {
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal("[Hawkeye] " + message));
		}
	}

	// ---- block esp --------------------------------------------------------

	/** The id of the block under the crosshair, or null if you are not aiming at one. */
	public static String lookedBlockId(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return null;
		}
		HitResult hit = player.pick(64.0, 1.0f, false);
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			BlockState state = mc.level.getBlockState(blockHit.getBlockPos());
			return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
		}
		return null;
	}

	/** Adds a block to the Block ESP list (or switches it back on) and turns Block ESP on. */
	public static boolean addBlock(Minecraft mc, String id) {
		Settings s = Settings.get();
		Targets.Type type = Targets.addCustomBlock(id);
		if (type == null) {
			return false;
		}
		if (!Targets.BLOCK_PRESETS.contains(type) && !s.customBlocks.contains(type.key)) {
			s.customBlocks.add(type.key);
		}
		s.blocks.put(type.key, true);
		s.blockColors.putIfAbsent(type.key, type.defaultColor);
		s.blockEsp = true;
		if (s.xray) {
			reloadTerrain(mc);
		}
		return true;
	}

	public static void removeBlock(Minecraft mc, String id) {
		Settings s = Settings.get();
		Targets.removeCustomBlock(id);
		s.customBlocks.remove(id);
		s.blocks.remove(id);
		s.blockColors.remove(id);
		if (s.xray) {
			reloadTerrain(mc);
		}
	}

	/** Key handler: aim at a block and press the key to add it, or press again to switch it off. */
	public static void toggleLookedBlock(Minecraft mc) {
		String id = lookedBlockId(mc);
		if (id == null) {
			notify(mc, "Block ESP: aim at a block first");
			return;
		}
		Settings s = Settings.get();
		Targets.Type known = Targets.blockType(id);
		if (known != null && s.blockEsp && s.blockEnabled(known)) {
			s.blocks.put(id, false);
			notify(mc, "Block ESP: " + known.name + " OFF");
		} else if (addBlock(mc, id)) {
			notify(mc, "Block ESP: " + Targets.prettyName(id) + " ON");
		} else {
			notify(mc, "Block ESP: can't track " + id);
		}
		if (s.xray) {
			reloadTerrain(mc);
		}
		Settings.save();
	}

	public static void panic(Minecraft mc) {
		Settings s = Settings.get();
		boolean wasXray = s.xray;
		s.blockEsp = false;
		s.oreEsp = false;
		s.xray = false;
		s.storageEsp = false;
		s.baseFinder = false;
		s.entityEsp = false;
		s.fullbright = false;
		s.autoSprint = false;
		s.flight = false;
		if (wasXray) {
			reloadTerrain(mc);
		}
		Settings.save();
	}
}
