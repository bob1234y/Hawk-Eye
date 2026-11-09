package com.hawkeye.scan;

import com.hawkeye.config.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Keeps track of loaded chunks and finds ore blocks in them without hitching the game: every
 * tick it scans a few 16x16x16 sections inside a small time budget and caches the hits.
 */
public final class Scanner {
	public static final Scanner INSTANCE = new Scanner();

	public static final class Hit {
		public final int x, y, z;
		public final Targets.Type type;

		Hit(int x, int y, int z, Targets.Type type) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.type = type;
		}
	}

	private static final class ChunkData {
		final List<List<Hit>> sections;
		final byte[] state; // 0 = needs scan, 1 = queued, 2 = done

		ChunkData(int sectionCount) {
			sections = new ArrayList<>(sectionCount);
			for (int i = 0; i < sectionCount; i++) {
				sections.add(null);
			}
			state = new byte[sectionCount];
		}
	}

	private record SectionTask(long chunkKey, int index) {
	}

	private static final long BUDGET_NANOS = 1_500_000L;
	private static final int DISCOVERY_INTERVAL = 20;
	private static final int FULL_REFRESH_INTERVAL = 20 * 45;

	/** Every chunk the client currently has loaded (filled by the Fabric chunk events). */
	private final Map<Long, LevelChunk> loaded = new HashMap<>();
	private final Map<Long, ChunkData> data = new HashMap<>();
	private final ArrayDeque<SectionTask> queue = new ArrayDeque<>();
	private ClientLevel level;
	private int ticks;
	private int lastSettingsKey = Integer.MIN_VALUE;
	/** Every block we are currently looking for (enabled ores + enabled Block ESP blocks) and what it counts as. */
	private final Map<Block, Targets.Type> active = new IdentityHashMap<>();
	private int scanRange = 64;

	private Scanner() {
	}

	private static long key(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}

	private static int chunkX(LevelChunk chunk) {
		return chunk.getPos().getMinBlockX() >> 4;
	}

	private static int chunkZ(LevelChunk chunk) {
		return chunk.getPos().getMinBlockZ() >> 4;
	}

	private static int keyX(long key) {
		return (int) (key >> 32);
	}

	private static int keyZ(long key) {
		return (int) key;
	}

	public Targets.Type activeType(Block block) {
		return active.get(block);
	}

	public Collection<LevelChunk> loadedChunks() {
		return loaded.values();
	}

	/** Visit every cached ore hit. Caller must be on the client thread. */
	public void forEachHit(Consumer<Hit> consumer) {
		for (ChunkData chunkData : data.values()) {
			for (List<Hit> list : chunkData.sections) {
				if (list != null) {
					for (Hit hit : list) {
						consumer.accept(hit);
					}
				}
			}
		}
	}

	public void reset() {
		loaded.clear();
		data.clear();
		queue.clear();
		level = null;
		ticks = 0;
		lastSettingsKey = Integer.MIN_VALUE;
	}

	public void chunkLoaded(ClientLevel l, LevelChunk chunk) {
		if (l != level) {
			reset();
			level = l;
		}
		long k = key(chunkX(chunk), chunkZ(chunk));
		loaded.put(k, chunk);
		data.remove(k);
		ticks = DISCOVERY_INTERVAL - 1;
	}

	public void chunkUnloaded(ClientLevel l, LevelChunk chunk) {
		if (l == level) {
			long k = key(chunkX(chunk), chunkZ(chunk));
			loaded.remove(k);
			data.remove(k);
		}
	}

	public void tick(Minecraft mc) {
		ClientLevel l = mc.level;
		if (l == null || mc.player == null) {
			if (level != null) {
				reset();
			}
			return;
		}
		if (l != level) {
			reset();
			level = l;
		}
		Settings s = Settings.get();
		if (!s.oreEsp && !s.blockEsp) {
			if (!data.isEmpty()) {
				data.clear();
				queue.clear();
			}
			active.clear();
			lastSettingsKey = Integer.MIN_VALUE;
			return;
		}

		int settingsKey = Objects.hash(s.oreEsp, s.blockEsp, s.ores, s.blocks, s.oreRange, s.blockRange);
		if (settingsKey != lastSettingsKey) {
			lastSettingsKey = settingsKey;
			data.clear();
			queue.clear();
			ticks = DISCOVERY_INTERVAL - 1;
			rebuildActive(s);
		}

		ticks++;
		BlockPos player = mc.player.blockPosition();
		if (ticks % DISCOVERY_INTERVAL == 0) {
			discover(l, player, scanRange);
		}
		if (ticks % FULL_REFRESH_INTERVAL == 0) {
			for (ChunkData chunkData : data.values()) {
				for (int i = 0; i < chunkData.state.length; i++) {
					if (chunkData.state[i] == 2) {
						chunkData.state[i] = 0;
					}
				}
			}
		}
		process(l);
	}

	private void rebuildActive(Settings s) {
		active.clear();
		scanRange = 16;
		if (s.oreEsp) {
			scanRange = Math.max(scanRange, s.oreRange);
			for (Targets.Type t : Targets.ORES) {
				if (s.oreEnabled(t)) {
					for (Block block : t.blocks) {
						active.put(block, t);
					}
				}
			}
		}
		if (s.blockEsp) {
			scanRange = Math.max(scanRange, s.blockRange);
			for (Targets.Type t : Targets.blockTypes()) {
				if (s.blockEnabled(t)) {
					for (Block block : t.blocks) {
						active.putIfAbsent(block, t);
					}
				}
			}
		}
	}

	private boolean matches(BlockState state) {
		return active.containsKey(state.getBlock());
	}

	private void discover(ClientLevel l, BlockPos player, int range) {
		int pcx = player.getX() >> 4;
		int pcz = player.getZ() >> 4;
		int chunkRadius = (range >> 4) + 1;
		int minY = l.getMinY();
		Predicate<BlockState> predicate = this::matches;

		List<long[]> wanted = new ArrayList<>();
		for (Map.Entry<Long, LevelChunk> entry : loaded.entrySet()) {
			int cx = keyX(entry.getKey());
			int cz = keyZ(entry.getKey());
			int dx = cx - pcx;
			int dz = cz - pcz;
			if (Math.abs(dx) <= chunkRadius && Math.abs(dz) <= chunkRadius) {
				wanted.add(new long[] {(long) dx * dx + (long) dz * dz, entry.getKey()});
			}
		}
		wanted.sort((a, b) -> Long.compare(a[0], b[0])); // nearest chunks first

		int lowY = Math.max(minY, player.getY() - range);
		int highY = player.getY() + range;
		for (long[] entry : wanted) {
			long k = entry[1];
			LevelChunk chunk = loaded.get(k);
			if (chunk == null) {
				continue;
			}
			LevelChunkSection[] sections = chunk.getSections();
			ChunkData chunkData = data.get(k);
			if (chunkData == null || chunkData.state.length != sections.length) {
				chunkData = new ChunkData(sections.length);
				data.put(k, chunkData);
			}
			int first = Math.max(0, (lowY - minY) >> 4);
			int last = Math.min(sections.length - 1, (highY - minY) >> 4);
			for (int i = first; i <= last; i++) {
				if (chunkData.state[i] != 0) {
					continue;
				}
				LevelChunkSection section = sections[i];
				if (section.hasOnlyAir() || !section.maybeHas(predicate)) {
					chunkData.sections.set(i, null);
					chunkData.state[i] = 2;
				} else {
					chunkData.state[i] = 1;
					queue.addLast(new SectionTask(k, i));
				}
			}
		}

		// Drop data for chunks that are now far away.
		Iterator<Map.Entry<Long, ChunkData>> it = data.entrySet().iterator();
		while (it.hasNext()) {
			long k = it.next().getKey();
			if (Math.abs(keyX(k) - pcx) > chunkRadius + 2 || Math.abs(keyZ(k) - pcz) > chunkRadius + 2) {
				it.remove();
			}
		}
	}

	private void process(ClientLevel l) {
		long deadline = System.nanoTime() + BUDGET_NANOS;
		int minY = l.getMinY();
		while (!queue.isEmpty() && System.nanoTime() < deadline) {
			SectionTask task = queue.pollFirst();
			ChunkData chunkData = data.get(task.chunkKey());
			LevelChunk chunk = loaded.get(task.chunkKey());
			if (chunkData == null || chunk == null) {
				continue;
			}
			LevelChunkSection[] sections = chunk.getSections();
			if (task.index() >= sections.length) {
				continue;
			}
			LevelChunkSection section = sections[task.index()];
			int baseX = keyX(task.chunkKey()) << 4;
			int baseZ = keyZ(task.chunkKey()) << 4;
			int baseY = minY + (task.index() << 4);
			List<Hit> found = null;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState state = section.getBlockState(x, y, z);
						Targets.Type t = active.get(state.getBlock());
						if (t != null) {
							if (found == null) {
								found = new ArrayList<>();
							}
							found.add(new Hit(baseX + x, baseY + y, baseZ + z, t));
						}
					}
				}
			}
			chunkData.sections.set(task.index(), found);
			chunkData.state[task.index()] = 2;
		}
	}
}
