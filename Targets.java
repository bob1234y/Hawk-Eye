package com.hawkeye.scan;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Static definitions of everything the ESP modules can look for. */
public final class Targets {
	public enum Kind {
		ORE, STORAGE, BLOCK
	}

	public static final class Type {
		public final Kind kind;
		public final String key;
		public final String name;
		public final int defaultColor;
		public final boolean defaultTracer;
		final String[] paths;
		final String suffix;
		/** Every block this type matches (filled by {@link #init()} or when a custom block is added). */
		public final Set<Block> blocks = Collections.newSetFromMap(new IdentityHashMap<>());

		Type(Kind kind, String key, String name, int color, boolean tracer, String suffix, String... paths) {
			this.kind = kind;
			this.key = key;
			this.name = name;
			this.defaultColor = color;
			this.defaultTracer = tracer;
			this.suffix = suffix;
			this.paths = paths;
		}
	}

	public static final List<Type> ORES = List.of(
			new Type(Kind.ORE, "coal", "Coal", 0xA0A0A0, false, null, "coal_ore", "deepslate_coal_ore"),
			new Type(Kind.ORE, "iron", "Iron", 0xE8B894, false, null, "iron_ore", "deepslate_iron_ore"),
			new Type(Kind.ORE, "copper", "Copper", 0xF0803C, false, null, "copper_ore", "deepslate_copper_ore"),
			new Type(Kind.ORE, "gold", "Gold", 0xFFE14A, false, null, "gold_ore", "deepslate_gold_ore", "nether_gold_ore"),
			new Type(Kind.ORE, "redstone", "Redstone", 0xFF3030, false, null, "redstone_ore", "deepslate_redstone_ore"),
			new Type(Kind.ORE, "lapis", "Lapis", 0x3C6EFF, false, null, "lapis_ore", "deepslate_lapis_ore"),
			new Type(Kind.ORE, "diamond", "Diamond", 0x4DF2E0, false, null, "diamond_ore", "deepslate_diamond_ore"),
			new Type(Kind.ORE, "emerald", "Emerald", 0x1FE36A, false, null, "emerald_ore", "deepslate_emerald_ore"),
			new Type(Kind.ORE, "quartz", "Nether Quartz", 0xF4ECDD, false, null, "nether_quartz_ore"),
			new Type(Kind.ORE, "debris", "Ancient Debris", 0xC8643C, true, null, "ancient_debris")
	);

	public static final List<Type> STORAGE = List.of(
			new Type(Kind.STORAGE, "chest", "Chest", 0xF2A93B, true, null, "chest"),
			new Type(Kind.STORAGE, "trapped_chest", "Trapped Chest", 0xFF4D4D, true, null, "trapped_chest"),
			new Type(Kind.STORAGE, "ender_chest", "Ender Chest", 0xB368FF, false, null, "ender_chest"),
			new Type(Kind.STORAGE, "barrel", "Barrel", 0xCB8A4A, true, null, "barrel"),
			new Type(Kind.STORAGE, "shulker", "Shulker Box", 0xFF6BFF, true, "shulker_box"),
			new Type(Kind.STORAGE, "hopper", "Hopper", 0x9AA0A6, false, null, "hopper"),
			new Type(Kind.STORAGE, "dispenser", "Dispenser / Dropper", 0x8A8F98, false, null, "dispenser", "dropper"),
			new Type(Kind.STORAGE, "furnace", "Furnaces", 0xFF8A3D, false, null, "furnace", "blast_furnace", "smoker"),
			new Type(Kind.STORAGE, "brewing", "Brewing Stand", 0xFFD27A, false, null, "brewing_stand"),
			new Type(Kind.STORAGE, "beacon", "Beacon", 0x7DFFFA, true, null, "beacon"),
			new Type(Kind.STORAGE, "enchanting", "Enchanting Table", 0x6A8CFF, false, null, "enchanting_table"),
			new Type(Kind.STORAGE, "spawner", "Spawner", 0x3AFF7A, true, null, "spawner"),
			new Type(Kind.STORAGE, "crafter", "Crafter", 0xB4B4D2, false, null, "crafter"),
			new Type(Kind.STORAGE, "pot", "Decorated Pot", 0xD98A66, false, null, "decorated_pot"),
			new Type(Kind.STORAGE, "bookshelf", "Chiseled Bookshelf", 0xB8905C, false, null, "chiseled_bookshelf")
	);

	/** Ready-made picks for the Block ESP. Keys are full block ids. */
	public static final List<Type> BLOCK_PRESETS = List.of(
			preset("obsidian", "Obsidian", 0xB368FF),
			preset("crying_obsidian", "Crying Obsidian", 0xD070FF),
			preset("tnt", "TNT", 0xFF4D4D),
			preset("end_portal_frame", "End Portal Frame", 0x5CFF8A),
			preset("trial_spawner", "Trial Spawner", 0x3AFF7A),
			preset("vault", "Vault", 0xFFE14A),
			preset("reinforced_deepslate", "Reinforced Deepslate", 0x8A8F98),
			preset("sculk_shrieker", "Sculk Shrieker", 0x1FE3C0),
			preset("respawn_anchor", "Respawn Anchor", 0xFF6BFF),
			preset("netherite_block", "Netherite Block", 0xC8643C),
			preset("diamond_block", "Diamond Block", 0x4DF2E0),
			preset("emerald_block", "Emerald Block", 0x1FE36A),
			preset("gold_block", "Gold Block", 0xFFE14A),
			preset("iron_block", "Iron Block", 0xE8B894),
			preset("lodestone", "Lodestone", 0xB4B4D2),
			preset("bell", "Bell", 0xF2A93B),
			preset("command_block", "Command Block", 0xFF8A3D),
			preset("barrier", "Barrier", 0xFF3030),
			preset("light", "Light Block", 0xFFFFFF),
			preset("cobweb", "Cobweb", 0xCCCCCC),
			preset("magma_block", "Magma Block", 0xFF6A2A),
			preset("lava", "Lava", 0xFF8A3D),
			preset("bookshelf", "Bookshelf", 0xB8905C),
			preset("crafting_table", "Crafting Table", 0xCB8A4A),
			preset("piston", "Piston", 0x9AA0A6),
			preset("sticky_piston", "Sticky Piston", 0x80C71F),
			preset("observer", "Observer", 0x8A8F98),
			preset("tripwire_hook", "Tripwire Hook", 0xCCCCCC),
			preset("heavy_core", "Heavy Core", 0x4DD2FF),
			preset("creaking_heart", "Creaking Heart", 0xFF8A3D),
			preset("dragon_egg", "Dragon Egg", 0xB368FF)
	);

	private static final int[] CUSTOM_COLORS = {
			0xFF4D4D, 0x4DD2FF, 0xFFE14A, 0x4AE86A, 0xFF6BFF, 0xFF8A3D, 0x8B6BFF, 0x1FE3C0
	};

	private static final Map<Block, Type> ORE_LOOKUP = new IdentityHashMap<>();
	private static final Map<Block, Type> STORAGE_LOOKUP = new IdentityHashMap<>();
	private static final Map<Block, Type> BLOCK_LOOKUP = new IdentityHashMap<>();
	private static final Map<String, Integer> DYE = new HashMap<>();
	/** Block ESP types by id: presets first, then the user's custom ones in the order they were added. */
	private static final Map<String, Type> BLOCK_TYPES = new LinkedHashMap<>();
	private static final Set<String> CUSTOM_IDS = new java.util.LinkedHashSet<>();

	private Targets() {
	}

	private static Type preset(String path, String name, int color) {
		return new Type(Kind.BLOCK, "minecraft:" + path, name, color, false, null, path);
	}

	public static void init() {
		for (Type t : ORES) {
			resolve(t);
			addToLookup(t, ORE_LOOKUP);
		}
		for (Type t : STORAGE) {
			resolve(t);
			addToLookup(t, STORAGE_LOOKUP);
		}
		for (Type t : BLOCK_PRESETS) {
			resolve(t);
			BLOCK_TYPES.put(t.key, t);
		}
		rebuildBlockLookup();

		DYE.put("white", 0xF9FFFE);
		DYE.put("orange", 0xF9801D);
		DYE.put("magenta", 0xC74EBD);
		DYE.put("light_blue", 0x3AB3DA);
		DYE.put("yellow", 0xFED83D);
		DYE.put("lime", 0x80C71F);
		DYE.put("pink", 0xF38BAA);
		DYE.put("gray", 0x9AA0A6);
		DYE.put("light_gray", 0xC8C8C0);
		DYE.put("cyan", 0x169C9C);
		DYE.put("purple", 0x8932B8);
		DYE.put("blue", 0x3C44AA);
		DYE.put("brown", 0x9B6A3C);
		DYE.put("green", 0x5E7C16);
		DYE.put("red", 0xFF3B3B);
		DYE.put("black", 0x6B6B7B);
	}

	private static void resolve(Type type) {
		for (String path : type.paths) {
			Block block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(path));
			if (block != Blocks.AIR) {
				type.blocks.add(block);
			}
		}
		if (type.suffix != null) {
			for (Block block : BuiltInRegistries.BLOCK) {
				Identifier id = BuiltInRegistries.BLOCK.getKey(block);
				if (id.getNamespace().equals("minecraft") && id.getPath().endsWith(type.suffix)) {
					type.blocks.add(block);
				}
			}
		}
	}

	private static void addToLookup(Type type, Map<Block, Type> lookup) {
		for (Block block : type.blocks) {
			lookup.put(block, type);
		}
	}

	private static void rebuildBlockLookup() {
		BLOCK_LOOKUP.clear();
		for (Type t : BLOCK_TYPES.values()) {
			addToLookup(t, BLOCK_LOOKUP);
		}
	}

	// ------------------------------------------------------------------ lookups

	public static Type oreOf(Block block) {
		return ORE_LOOKUP.get(block);
	}

	public static Type storageOf(Block block) {
		return STORAGE_LOOKUP.get(block);
	}

	public static Type blockOf(Block block) {
		return BLOCK_LOOKUP.get(block);
	}

	// ------------------------------------------------------------------ Block ESP list

	/** All Block ESP types in display order (presets, then custom). */
	public static List<Type> blockTypes() {
		return new ArrayList<>(BLOCK_TYPES.values());
	}

	public static Type blockType(String id) {
		return BLOCK_TYPES.get(id);
	}

	public static boolean isCustomBlock(String id) {
		return CUSTOM_IDS.contains(id);
	}

	/** Turns "obsidian" or "Minecraft:Obsidian" into "minecraft:obsidian". */
	public static String normalizeId(String raw) {
		String id = raw.trim().toLowerCase();
		return id.contains(":") ? id : "minecraft:" + id;
	}

	/** Adds a block to the Block ESP list. Returns the type, or null if no such block exists. */
	public static Type addCustomBlock(String rawId) {
		String id = normalizeId(rawId);
		Type existing = BLOCK_TYPES.get(id);
		if (existing != null) {
			return existing;
		}
		Block block;
		try {
			block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
		} catch (Exception ex) {
			return null;
		}
		if (block == null || block == Blocks.AIR) {
			return null;
		}
		int color = CUSTOM_COLORS[CUSTOM_IDS.size() % CUSTOM_COLORS.length];
		Type type = new Type(Kind.BLOCK, id, prettyName(id), color, false, null);
		type.blocks.add(block);
		BLOCK_TYPES.put(id, type);
		CUSTOM_IDS.add(id);
		rebuildBlockLookup();
		return type;
	}

	/** Forget every custom block (used by "reset to defaults"). */
	public static void clearCustomBlocks() {
		for (String id : new ArrayList<>(CUSTOM_IDS)) {
			removeCustomBlock(id);
		}
	}

	public static void removeCustomBlock(String id) {
		if (CUSTOM_IDS.remove(id)) {
			BLOCK_TYPES.remove(id);
			rebuildBlockLookup();
		}
	}

	/** "minecraft:crying_obsidian" -> "Crying Obsidian". */
	public static String prettyName(String id) {
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		StringBuilder sb = new StringBuilder();
		for (String part : path.split("_")) {
			if (part.isEmpty()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return sb.toString();
	}

	/** Shulker boxes keep their dye colour; everything else uses the colour chosen in the menu. */
	public static int dyeColor(Block block, int fallback) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		String path = id.getPath();
		if (path.endsWith("_shulker_box")) {
			Integer c = DYE.get(path.substring(0, path.length() - "_shulker_box".length()));
			if (c != null) {
				return c;
			}
		}
		return fallback;
	}
}
