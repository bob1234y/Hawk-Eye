package com.hawkeye.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hawkeye.client.HawkeyeClient;
import com.hawkeye.scan.Targets;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All user-facing options. Serialized to config/hawkeye.json. */
public final class Settings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("hawkeye.json");
	private static Settings instance = new Settings();

	// ---- module switches ----
	public boolean oreEsp = true;
	public boolean xray = false;
	public boolean storageEsp = true;
	public boolean baseFinder = true;
	public boolean entityEsp = false;
	public boolean blockEsp = false;
	public boolean fullbright = false;
	public boolean autoSprint = false;
	public boolean flight = false;

	// ---- shared look ----
	public float lineWidth = 2.0f;
	public float tracerWidth = 1.5f;
	public int maxTargets = 1500;

	// ---- ore esp / xray ----
	public int oreRange = 64;
	public float fillAlpha = 0.35f;
	public float glow = 0.7f;
	public boolean oreOutline = true;
	public boolean oreFill = true;
	public boolean pulse = true;
	public boolean oreTracers = false;
	public boolean xrayShowStorage = false;
	public Map<String, Boolean> ores = new LinkedHashMap<>();
	public Map<String, Integer> oreColors = new LinkedHashMap<>();

	// ---- block esp ----
	public int blockRange = 64;
	public boolean blockFill = true;
	public boolean blockOutline = true;
	public boolean blockTracers = false;
	/** Block id -> shown or not (presets and custom blocks). */
	public Map<String, Boolean> blocks = new LinkedHashMap<>();
	public Map<String, Integer> blockColors = new LinkedHashMap<>();
	/** Block ids the user added themselves (aim + key, or the menu button). */
	public List<String> customBlocks = new ArrayList<>();

	// ---- storage esp ----
	public int storageRange = 128;
	public boolean storageFill = true;
	public boolean storageOutline = true;
	public boolean storageTracers = true;
	public Map<String, Boolean> storage = new LinkedHashMap<>();
	public Map<String, Boolean> storageTracer = new LinkedHashMap<>();
	public Map<String, Integer> storageColors = new LinkedHashMap<>();

	// ---- base finder ----
	public int baseMinContainers = 4;
	public int baseRadius = 24;
	public boolean basePillar = true;
	public boolean baseFootprint = true;
	public boolean baseTracer = true;
	public boolean baseHudList = true;

	// ---- entity esp ----
	public boolean espPlayers = true;
	public boolean espHostile = false;
	public boolean espPassive = false;
	public boolean espItems = false;
	public boolean entityTracers = true;
	public boolean entityFill = true;

	// ---- player ----
	public float flySpeed = 0.05f;

	// ---- hud ----
	public boolean hudModuleList = true;

	public static Settings get() {
		return instance;
	}

	public static void load() {
		Settings loaded = null;
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				loaded = GSON.fromJson(reader, Settings.class);
			} catch (Exception ex) {
				HawkeyeClient.LOGGER.warn("Could not read hawkeye.json, using defaults", ex);
			}
		}
		instance = loaded != null ? loaded : new Settings();
		instance.fillDefaults();
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException ex) {
			HawkeyeClient.LOGGER.warn("Could not save hawkeye.json", ex);
		}
	}

	public static void resetAll() {
		Targets.clearCustomBlocks();
		instance = new Settings();
		instance.fillDefaults();
		save();
	}

	private void fillDefaults() {
		if (ores == null) ores = new LinkedHashMap<>();
		if (oreColors == null) oreColors = new LinkedHashMap<>();
		if (storage == null) storage = new LinkedHashMap<>();
		if (storageTracer == null) storageTracer = new LinkedHashMap<>();
		if (storageColors == null) storageColors = new LinkedHashMap<>();
		if (blocks == null) blocks = new LinkedHashMap<>();
		if (blockColors == null) blockColors = new LinkedHashMap<>();
		if (customBlocks == null) customBlocks = new ArrayList<>();
		for (Targets.Type t : Targets.ORES) {
			// Everything on by default except the very common ores, so the view is not a wall of colour.
			ores.putIfAbsent(t.key, !(t.key.equals("coal") || t.key.equals("copper") || t.key.equals("iron")));
			oreColors.putIfAbsent(t.key, t.defaultColor);
		}
		for (Targets.Type t : Targets.STORAGE) {
			storage.putIfAbsent(t.key, true);
			storageTracer.putIfAbsent(t.key, t.defaultTracer);
			storageColors.putIfAbsent(t.key, t.defaultColor);
		}
		for (Targets.Type t : Targets.BLOCK_PRESETS) {
			blocks.putIfAbsent(t.key, false);
			blockColors.putIfAbsent(t.key, t.defaultColor);
		}
		// Re-register the user's custom blocks (drop ids that no longer exist).
		List<String> valid = new ArrayList<>();
		for (String id : customBlocks) {
			Targets.Type t = Targets.addCustomBlock(id);
			if (t != null && !valid.contains(t.key)) {
				valid.add(t.key);
				blocks.putIfAbsent(t.key, true);
				blockColors.putIfAbsent(t.key, t.defaultColor);
			}
		}
		customBlocks = valid;
		oreRange = clamp(oreRange, 16, 128);
		blockRange = clamp(blockRange, 16, 128);
		storageRange = clamp(storageRange, 16, 256);
		maxTargets = clamp(maxTargets, 100, 5000);
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	public boolean oreEnabled(Targets.Type t) {
		return ores.getOrDefault(t.key, false);
	}

	public int oreColor(Targets.Type t) {
		return oreColors.getOrDefault(t.key, t.defaultColor);
	}

	public boolean blockEnabled(Targets.Type t) {
		return blocks.getOrDefault(t.key, false);
	}

	public int blockColor(Targets.Type t) {
		return blockColors.getOrDefault(t.key, t.defaultColor);
	}

	public boolean storageEnabled(Targets.Type t) {
		return storage.getOrDefault(t.key, true);
	}

	public boolean storageTracerOn(Targets.Type t) {
		return storageTracer.getOrDefault(t.key, false);
	}

	public int storageColor(Targets.Type t) {
		return storageColors.getOrDefault(t.key, t.defaultColor);
	}

	/** Cheap fingerprint of everything that changes the static geometry. */
	public int geometryHash() {
		int h = 17;
		h = 31 * h + (oreEsp ? 1 : 0) + (storageEsp ? 2 : 0) + (baseFinder ? 4 : 0) + (blockEsp ? 8 : 0);
		h = 31 * h + blockRange;
		h = 31 * h + (blockFill ? 1 : 0) + (blockOutline ? 2 : 0) + (blockTracers ? 4 : 0);
		h = 31 * h + blocks.hashCode();
		h = 31 * h + blockColors.hashCode();
		h = 31 * h + oreRange;
		h = 31 * h + storageRange;
		h = 31 * h + maxTargets;
		h = 31 * h + Float.floatToIntBits(fillAlpha);
		h = 31 * h + Float.floatToIntBits(glow);
		h = 31 * h + Float.floatToIntBits(lineWidth);
		h = 31 * h + (oreOutline ? 1 : 0) + (oreFill ? 2 : 0) + (storageFill ? 4 : 0) + (storageOutline ? 8 : 0);
		h = 31 * h + (basePillar ? 1 : 0) + (baseFootprint ? 2 : 0);
		h = 31 * h + baseMinContainers * 131 + baseRadius;
		h = 31 * h + ores.hashCode();
		h = 31 * h + oreColors.hashCode();
		h = 31 * h + storage.hashCode();
		h = 31 * h + storageColors.hashCode();
		return h;
	}
}
