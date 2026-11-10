package com.hawkeye.gui;

import com.hawkeye.config.Settings;
import com.hawkeye.module.Modules;
import com.hawkeye.scan.Targets;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** The menu. Everything is drawn by hand so it looks the same on every GUI scale. */
public final class ClickGui extends Screen {
	// ---- palette (ARGB) ----
	private static final int SCRIM_TOP = 0xAA0A0A12;
	private static final int SCRIM_BOTTOM = 0xDD000000;
	private static final int PANEL = 0xF2121219;
	private static final int PANEL_BORDER = 0xFF2A2A3C;
	private static final int SIDEBAR = 0xFF0F0F16;
	private static final int ROW = 0xFF1B1B27;
	private static final int ROW_HOVER = 0xFF232334;
	private static final int ACCENT = 0xFF8B6BFF;
	private static final int ACCENT2 = 0xFF38BDF8;
	private static final int TEXT = 0xFFECECF6;
	private static final int DIM = 0xFF8E8EA8;
	private static final int ON = 0xFF4ADE80;
	private static final int OFF = 0xFF3A3A50;
	private static final int DANGER = 0xFFFF5470;

	private static final int[] PALETTE = {
			0xFF4D4D, 0xFF8A3D, 0xF2A93B, 0xFFE14A, 0x4AE86A, 0x1FE3C0, 0x4DD2FF,
			0x3C6EFF, 0x8B6BFF, 0xFF6BFF, 0xFF4DDB, 0xFFFFFF, 0xA0A0A0, 0xCB8A4A
	};

	private static final int SHOW_OFFSET = 80;
	private static final int TRACER_OFFSET = 36;

	private enum Tab {
		ORES("Ore ESP"), BLOCKS("Block ESP"), STORAGE("Storage"), BASES("Bases"), ENTITIES("Entities"), PLAYER("Player"), SETTINGS("Settings");

		final String label;

		Tab(String label) {
			this.label = label;
		}

		boolean active() {
			Settings s = Settings.get();
			return switch (this) {
				case ORES -> s.oreEsp || s.xray;
				case BLOCKS -> s.blockEsp;
				case STORAGE -> s.storageEsp;
				case BASES -> s.baseFinder;
				case ENTITIES -> s.entityEsp;
				case PLAYER -> s.fullbright || s.autoSprint || s.flight;
				case SETTINGS -> false;
			};
		}
	}

	private Tab tab = Tab.ORES;
	private List<Row> rows = new ArrayList<>();
	private float scroll;
	private float targetScroll;
	private int contentHeight;
	private Row activeRow;
	private long lastFrameNanos = System.nanoTime();
	private float dt;

	// layout, recomputed every frame
	private int px, py, pw, ph, sideW;
	private int cx, cy, cw, ch;

	/** The block you were aiming at when the menu opened (offered as a one-click add). */
	private final String lookedId;

	public ClickGui() {
		super(Component.literal("Hawkeye"));
		lookedId = Modules.lookedBlockId(Minecraft.getInstance());
		rebuild();
	}

	private void rebuildKeepScroll() {
		float keep = targetScroll;
		rebuild();
		targetScroll = keep;
		scroll = keep;
	}

	@Override
	public void onClose() {
		Settings.save();
		super.onClose();
	}

	// =============================================================== rows

	private abstract class Row {
		final int h;
		int y; // absolute, set during layout

		Row(int h) {
			this.h = h;
		}

		abstract void render(GuiGraphicsExtractor c, int x, int w, int mx, int my);

		boolean click(int x, int w, double mx, double my) {
			return false;
		}

		void drag(int x, int w, double mx) {
		}

		boolean hover(int x, int w, int mx, int my) {
			return mx >= x && mx < x + w && my >= y && my < y + h && my >= cy && my < cy + ch;
		}
	}

	private final class Header extends Row {
		final String text;

		Header(String text) {
			super(20);
			this.text = text;
		}

		@Override
		void render(GuiGraphicsExtractor c, int x, int w, int mx, int my) {
			drawText(c, text.toUpperCase(), x + 2, y + 9, ACCENT);
			int tw = font.width(text.toUpperCase());
			c.fill(x + tw + 8, y + 13, x + w, y + 14, 0xFF2A2A3C);
		}
	}

	private final class Note extends Row {
		final String text;

		Note(String text) {
			super(14);
			this.text = text;
		}

		@Override
		void render(GuiGraphicsExtractor c, int x, int w, int mx, int my) {
			drawText(c, text, x + 4, y + 3, DIM);
		}
	}

	/** Small "SHOW / TRACER" captions above the two switch columns of the container list. */
	private final class Columns extends Row {
		Columns() {
			super(14);
		}

		@Override
		void render(GuiGraphicsExtractor c, int x, int w, int mx, int my) {
			String show = "SHOW";
			String tracer = "TRACER";
			drawText(c, show, x + w - SHOW_OFFSET + 14 - font.width(show) / 2, y + 3, DIM);
			drawText(c, tracer, x + w - TRACER_OFFSET + 14 - font.width(tracer) / 2, y + 3, DIM);
		}
	}

	private final class Toggle extends Row {
		final String label;
		final String desc;
		final BooleanSupplier get;
		final Consumer<Boolean> set;
		final IntSupplier color;
		final IntConsumer setColor;
		Runnable remove;
		float anim = -1;

		Toggle withRemove(Runnable action) {
			this.remove = action;
			return this;
		}

		Toggle(String label, String desc, BooleanSupplier get, Consumer<Boolean> set, IntSupplier color, IntConsumer setColor) {
			super(desc == null ? 22 : 30);
			this.label = label;
			this.desc = desc;
			this.get = get;
			this.set = set;
			this.color = color;
			this.setColor = setColor;
		}

		@Override
		void render(GuiGraphicsExtractor c, int x, int w, int mx, int my) {
			boolean on = get.getAsBoolean();
			if (anim < 0) {
				anim = on ? 1 : 0;
			}
			anim += ((on ? 1f : 0f) - anim) * Math.min(1f, dt * 16f);
			rrect(c, x, y, x + w, y + h - 2, 4, hover(x, w, mx, my) ? ROW_HOVER : ROW);

			int tx = x + 10;
			if (color != null) {
				int rgb = color.getAsInt();
				rrect(c, x + 8, y + (h - 2) / 2 - 5, x + 20, y + (h - 2) / 2 + 5, 3, 0xFF000000 | rgb);
				tx = x + 28;
			}
			if (desc == null) {
				drawText(c, label, tx, y + (h - 2 - 9) / 2, TEXT);
			} else {
				drawText(c, label, tx, y + 5, TEXT);
				drawText(c, desc, tx, y + 16, DIM);
			}
			drawSwitch(c, x + w - 36, y + (h - 2 - 14) / 2, anim);
			if (remove != null) {
				rrect(c, x + w - 62, y + (h - 2) / 2 - 6, x + w - 46, y + (h - 2) / 2 + 6, 3, withAlpha(DANGER, 0x44));
				drawText(c, "x", x + w - 56, y + (h - 2 - 9) / 2, DANGER);
			}
		}

		@Override
		boolean click(int x, int w, double mx, double my) {
			if (remove != null && mx >= x + w - 64 && mx < x + w - 44) {
				remove.run();
				rebuildKeepScroll();
				return true;
			}
			if (color != null && setColor != null && mx >= x + 6 && mx < x + 22) {
				setColor.accept(nextColor(color.getAsInt()));
				return true;
			}
			set.accept(!get.getAsBoolean());
			return true;
		}
	}

	/** One container type: colour dot, "show" switch and "tracer" switch. */
	private final class Dual extends Row {
		final String label;
		final IntSupplier color;
		final IntConsumer setColor;
		final BooleanSupplier getA, getB;
		final Consumer<Boolean> setA, setB;
		float animA = -1, animB = -1;

		Dual(String label, IntSupplier color, IntConsumer setColor, BooleanSupplier getA, Consumer<Boolean> setA,
				BooleanSupplier getB, Consumer<Boolean> setB) {
			super(22);
			this.label = label;
			this.color = color;
			this.setColor = setColor;
			this.getA = getA;
			this.setA = setA;
			this.getB = getB;
			this.setB = setB;
		}

		@Override
		void render(GuiGraphicsExtractor c, int x, int w, int mx, int my) {
			boolean a = getA.getAsBoolean();
			boolean b = getB.getAsBoolean();
			if (animA < 0) {
				animA = a ? 1 : 0;
				animB = b ? 1 : 0;
			}
			float k = Math.min(1f, dt * 16f);
			animA += ((a ? 1f : 0f) - animA) * k;
			animB += ((b ? 1f : 0f) - animB) * k;
			rrect(c, x, y, x + w, y + h - 2, 4, hover(x, w, mx, my) ? ROW_HOVER : ROW);
			rrect(c, x + 8, y + 5, x + 20, y + 15, 3, 0xFF000000 | color.getAsInt());
			drawText(c, label, x + 28, y + 6, a ? TEXT : DIM);
			drawSwitch(c, x + w - SHOW_OFFSET, y + 3, animA);
			drawSwitch(c, x + w - TRACER_OFFSET, y + 3, animB);
		}

		@Override
		boolean click(int x, int w, double mx, double my) {
			if (mx >= x + 6 && mx < x + 22) {
				setColor.accept(nextColor(color.getAsInt()));
			} else if (mx >= x + w - TRACER_OFFSET - 8) {
				setB.accept(!getB.getAsBoolean());
			} else {
				setA.accept(!getA.getAsBoolean());
			}
			return true;
		}
	}

	private final class Slider extends Row {
		final String label;
		final double min, max, step;
		final DoubleSupplier get;
		final DoubleConsumer set;
		final DoubleFunction<String> fmt;
		int lastX, lastW;

		Slider(String label, double min, double max, double step, DoubleSupplier get, DoubleConsumer set,
				DoubleFunction<String> fmt) {
			super(30);
			this.label = label;
			this.min = min;
			this.max = max;
			this.step = step;
			this.get = get;
			this.set = set;
			this.fmt = fmt;
		}

		@Override
		void render(GuiGraphicsExtractor c, int x, int w, int mx, int my) {
			lastX = x;
			lastW = w;
			rrect(c, x, y, x + w, y + h - 2, 4, (hover(x, w, mx, my) || activeRow == this) ? ROW_HOVER : ROW);
			double v = get.getAsDouble();
			String value = fmt.apply(v);
			drawText(c, label, x + 10, y + 5, TEXT);
			drawText(c, value, x + w - 10 - font.width(value), y + 5, ACCENT2);
			int tx1 = x + 10, tx2 = x + w - 10, ty = y + 19;
			rrect(c, tx1, ty, tx2, ty + 4, 2, 0xFF2C2C40);
			float frac = (float) ((v - min) / (max - min));
			frac = Math.max(0f, Math.min(1f, frac));
			int fillTo = tx1 + Math.round((tx2 - tx1) * frac);
			if (fillTo > tx1 + 1) {
				rrect(c, tx1, ty, fillTo, ty + 4, 2, ACCENT);
			}
			rrect(c, fillTo - 3, ty - 2, fillTo + 3, ty + 6, 3, 0xFFFFFFFF);
		}

		@Override
		boolean click(int x, int w, double mx, double my) {
			activeRow = this;
			drag(x, w, mx);
			return true;
		}

		@Override
		void drag(int x, int w, double mx) {
			double frac = (mx - (x + 10)) / (double) (w - 20);
			frac = Math.max(0, Math.min(1, frac));
			double v = min + (max - min) * frac;
			v = Math.round((v - min) / step) * step + min;
			v = Math.max(min, Math.min(max, v));
			set.accept(v);
		}
	}

	private final class Button extends Row {
		final String label;
		final int color;
		final Runnable action;

		Button(String label, int color, Runnable action) {
			super(24);
			this.label = label;
			this.color = color;
			this.action = action;
		}

		@Override
		void render(GuiGraphicsExtractor c, int x, int w, int mx, int my) {
			boolean hov = hover(x, w, mx, my);
			rrect(c, x, y, x + w, y + h - 2, 4, hov ? withAlpha(color, 0x55) : withAlpha(color, 0x33));
			rrect(c, x, y, x + 3, y + h - 2, 1, color);
			int tw = font.width(label);
			drawText(c, label, x + (w - tw) / 2, y + (h - 2 - 9) / 2, TEXT);
		}

		@Override
		boolean click(int x, int w, double mx, double my) {
			action.run();
			return true;
		}
	}

	// =============================================================== tab content

	private void rebuild() {
		List<Row> r = new ArrayList<>();
		Settings s = Settings.get();
		Minecraft mc = Minecraft.getInstance();

		switch (tab) {
			case ORES -> {
				r.add(new Header("Ore ESP & Xray"));
				r.add(new Toggle("Ore ESP", "Glowing boxes on ores, visible through walls",
						() -> s.oreEsp, v -> s.oreEsp = v, null, null));
				r.add(new Toggle("Terrain Xray", "Hides every other block (turn Sodium off for this one)",
						() -> s.xray, v -> Modules.setXray(mc, v), null, null));
				r.add(new Toggle("Xray shows containers", null,
						() -> s.xrayShowStorage, v -> {
							s.xrayShowStorage = v;
							if (s.xray) Modules.reloadTerrain(mc);
						}, null, null));
				r.add(new Slider("Range", 16, 128, 8, () -> s.oreRange, v -> s.oreRange = (int) Math.round(v),
						v -> (int) v + " blocks"));
				r.add(new Slider("Fill opacity", 0.05, 1.0, 0.05, () -> s.fillAlpha, v -> s.fillAlpha = (float) v,
						v -> Math.round(v * 100) + "%"));
				r.add(new Slider("Glow", 0, 1, 0.05, () -> s.glow, v -> s.glow = (float) v,
						v -> Math.round(v * 100) + "%"));
				r.add(new Slider("Line width", 1, 5, 0.5, () -> s.lineWidth, v -> s.lineWidth = (float) v,
						v -> String.format("%.1f px", v)));
				r.add(new Toggle("Fill", null, () -> s.oreFill, v -> s.oreFill = v, null, null));
				r.add(new Toggle("Outline", null, () -> s.oreOutline, v -> s.oreOutline = v, null, null));
				r.add(new Toggle("Pulse", "Gentle breathing glow", () -> s.pulse, v -> s.pulse = v, null, null));
				r.add(new Toggle("Tracers to ores", null, () -> s.oreTracers, v -> s.oreTracers = v, null, null));
				r.add(new Header("Ores (click the dot to change colour)"));
				for (Targets.Type t : Targets.ORES) {
					r.add(new Toggle(t.name, null, () -> s.oreEnabled(t), v -> {
						s.ores.put(t.key, v);
						if (s.xray) Modules.reloadTerrain(mc);
					}, () -> s.oreColor(t), c -> s.oreColors.put(t.key, c)));
				}
			}
			case BLOCKS -> {
				r.add(new Header("Block ESP"));
				r.add(new Toggle("Block ESP", "Highlight the blocks you pick, through walls",
						() -> s.blockEsp, v -> {
							s.blockEsp = v;
							if (s.xray) Modules.reloadTerrain(mc);
						}, null, null));
				r.add(new Toggle("Fill", null, () -> s.blockFill, v -> s.blockFill = v, null, null));
				r.add(new Toggle("Outline", null, () -> s.blockOutline, v -> s.blockOutline = v, null, null));
				r.add(new Toggle("Tracers", null, () -> s.blockTracers, v -> s.blockTracers = v, null, null));
				r.add(new Slider("Range", 16, 128, 8, () -> s.blockRange, v -> s.blockRange = (int) Math.round(v),
						v -> (int) v + " blocks"));
				if (lookedId != null) {
					r.add(new Button("Add the block you were looking at: " + Targets.prettyName(lookedId), ON, () -> {
						Modules.addBlock(mc, lookedId);
						rebuildKeepScroll();
					}));
				} else {
					r.add(new Note("Aim at a block, then open the menu to add it from here."));
				}
				r.add(new Note("Or bind \"Block ESP: add / toggle looked-at block\" in Controls."));
				List<Targets.Type> mine = new ArrayList<>();
				List<Targets.Type> presets = new ArrayList<>();
				for (Targets.Type t : Targets.blockTypes()) {
					(Targets.isCustomBlock(t.key) ? mine : presets).add(t);
				}
				if (!mine.isEmpty()) {
					r.add(new Header("Your blocks"));
					for (Targets.Type t : mine) {
						r.add(new Toggle(t.name, null, () -> s.blockEnabled(t), v -> {
							s.blocks.put(t.key, v);
							if (s.xray) Modules.reloadTerrain(mc);
						}, () -> s.blockColor(t), c -> s.blockColors.put(t.key, c))
								.withRemove(() -> Modules.removeBlock(mc, t.key)));
					}
				}
				r.add(new Header("Presets (click the dot for colour)"));
				for (Targets.Type t : presets) {
					r.add(new Toggle(t.name, null, () -> s.blockEnabled(t), v -> {
						s.blocks.put(t.key, v);
						if (s.xray) Modules.reloadTerrain(mc);
					}, () -> s.blockColor(t), c -> s.blockColors.put(t.key, c)));
				}
			}
			case STORAGE -> {
				r.add(new Header("Storage ESP"));
				r.add(new Toggle("Storage ESP", "Outline chests, shulkers and other base blocks",
						() -> s.storageEsp, v -> s.storageEsp = v, null, null));
				r.add(new Toggle("Fill", null, () -> s.storageFill, v -> s.storageFill = v, null, null));
				r.add(new Toggle("Outline", null, () -> s.storageOutline, v -> s.storageOutline = v, null, null));
				r.add(new Toggle("Tracers", "Master switch for tracer lines to containers",
						() -> s.storageTracers, v -> s.storageTracers = v, null, null));
				r.add(new Slider("Range", 32, 256, 16, () -> s.storageRange, v -> s.storageRange = (int) Math.round(v),
						v -> (int) v + " blocks"));
				r.add(new Slider("Tracer width", 1, 4, 0.5, () -> s.tracerWidth, v -> s.tracerWidth = (float) v,
						v -> String.format("%.1f px", v)));
				r.add(new Button("Show every type", ON, () -> {
					for (Targets.Type t : Targets.STORAGE) s.storage.put(t.key, true);
				}));
				r.add(new Button("Hide every type", DANGER, () -> {
					for (Targets.Type t : Targets.STORAGE) s.storage.put(t.key, false);
				}));
				r.add(new Header("Container types"));
				r.add(new Columns());
				for (Targets.Type t : Targets.STORAGE) {
					r.add(new Dual(t.name, () -> s.storageColor(t), c -> s.storageColors.put(t.key, c),
							() -> s.storageEnabled(t), v -> s.storage.put(t.key, v),
							() -> s.storageTracerOn(t), v -> s.storageTracer.put(t.key, v)));
				}
				r.add(new Note("Shulker boxes keep their dye colour unless you change the dot."));
			}
			case BASES -> {
				r.add(new Header("Base Finder"));
				r.add(new Toggle("Base Finder", "Groups nearby containers and marks likely bases",
						() -> s.baseFinder, v -> s.baseFinder = v, null, null));
				r.add(new Toggle("Light pillar", "Tall beam so you can spot it from far away",
						() -> s.basePillar, v -> s.basePillar = v, null, null));
				r.add(new Toggle("Footprint box", "Box around the whole cluster",
						() -> s.baseFootprint, v -> s.baseFootprint = v, null, null));
				r.add(new Toggle("Tracer", null, () -> s.baseTracer, v -> s.baseTracer = v, null, null));
				r.add(new Toggle("HUD list", "Distance and direction in the corner",
						() -> s.baseHudList, v -> s.baseHudList = v, null, null));
				r.add(new Slider("Min containers", 2, 20, 1, () -> s.baseMinContainers,
						v -> s.baseMinContainers = (int) Math.round(v), v -> String.valueOf((int) v)));
				r.add(new Slider("Cluster radius", 8, 64, 4, () -> s.baseRadius,
						v -> s.baseRadius = (int) Math.round(v), v -> (int) v + " blocks"));
				r.add(new Note("Uses the Storage range for how far it looks."));
			}
			case ENTITIES -> {
				r.add(new Header("Entity ESP"));
				r.add(new Toggle("Entity ESP", "Boxes through walls", () -> s.entityEsp, v -> s.entityEsp = v, null, null));
				r.add(new Toggle("Players", null, () -> s.espPlayers, v -> s.espPlayers = v, null, null));
				r.add(new Toggle("Hostile mobs", null, () -> s.espHostile, v -> s.espHostile = v, null, null));
				r.add(new Toggle("Animals & villagers", null, () -> s.espPassive, v -> s.espPassive = v, null, null));
				r.add(new Toggle("Dropped items", null, () -> s.espItems, v -> s.espItems = v, null, null));
				r.add(new Toggle("Fill", null, () -> s.entityFill, v -> s.entityFill = v, null, null));
				r.add(new Toggle("Tracers", null, () -> s.entityTracers, v -> s.entityTracers = v, null, null));
			}
			case PLAYER -> {
				r.add(new Header("Player"));
				r.add(new Toggle("Fullbright", "Permanent night vision", () -> s.fullbright, v -> s.fullbright = v, null, null));
				r.add(new Toggle("Auto sprint", "Sprint whenever you walk forward", () -> s.autoSprint, v -> s.autoSprint = v, null, null));
				r.add(new Toggle("Flight", "Creative-style flying (server must allow it)",
						() -> s.flight, v -> s.flight = v, null, null));
				r.add(new Slider("Fly speed", 0.05, 0.5, 0.01, () -> s.flySpeed, v -> s.flySpeed = (float) v,
						v -> String.format("%.1fx", v / 0.05)));
			}
			case SETTINGS -> {
				r.add(new Header("Interface"));
				r.add(new Toggle("Module list on HUD", null, () -> s.hudModuleList, v -> s.hudModuleList = v, null, null));
				r.add(new Slider("Max boxes drawn", 100, 8000, 100, () -> s.maxTargets,
						v -> s.maxTargets = (int) Math.round(v), v -> String.valueOf((int) v)));
				r.add(new Header("Config"));
				r.add(new Button("Save now", ACCENT, Settings::save));
				r.add(new Button("Reset everything to defaults", DANGER, () -> {
					boolean wasXray = Settings.get().xray;
					Settings.resetAll();
					if (wasXray) Modules.reloadTerrain(mc);
					rebuild();
				}));
				r.add(new Header("Keys"));
				r.add(new Note("Right Shift  -  open this menu"));
				r.add(new Note("X  -  toggle xray   (change in Controls)"));
				r.add(new Note("Ore ESP, Storage ESP and Block ESP keys are unbound by default."));
			}
		}
		rows = r;
		scroll = 0;
		targetScroll = 0;
		activeRow = null;
	}

	// =============================================================== rendering

	private void layout() {
		pw = Math.min(640, width - 16);
		ph = Math.min(372, height - 16);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		sideW = pw < 470 ? 88 : 122;
		cx = px + sideW + 10;
		cy = py + 42;
		cw = pw - sideW - 20;
		ch = ph - 42 - 10;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		dt = Math.min(0.05f, (now - lastFrameNanos) / 1.0e9f);
		lastFrameNanos = now;
		layout();

		c.fillGradient(0, 0, width, height, SCRIM_TOP, SCRIM_BOTTOM);

		// shadow + panel
		for (int i = 6; i >= 1; i--) {
			rrect(c, px - i, py - i, px + pw + i, py + ph + i, 8, withAlpha(0x000000, 0x0C));
		}
		rrect(c, px - 1, py - 1, px + pw + 1, py + ph + 1, 7, PANEL_BORDER);
		rrect(c, px, py, px + pw, py + ph, 6, PANEL);

		renderTitleBar(c);
		renderSidebar(c, mouseX, mouseY);
		renderContent(c, mouseX, mouseY);
	}

	private void renderTitleBar(GuiGraphicsExtractor c) {
		String name = "HAWKEYE";
		int x = px + 14;
		for (int i = 0; i < name.length(); i++) {
			String ch1 = String.valueOf(name.charAt(i));
			int col = lerpColor(ACCENT, ACCENT2, i / (float) (name.length() - 1));
			drawText(c, ch1, x, py + 12, col);
			x += font.width(ch1) + 1;
		}
		drawText(c, "1.21.11", x + 6, py + 12, DIM);
		String hint = "Right Shift / Esc to close";
		drawText(c, hint, px + pw - 14 - font.width(hint), py + 12, DIM);
		// gradient underline
		int ux1 = px + 14, ux2 = px + pw - 14;
		for (int i = ux1; i < ux2; i += 2) {
			float t = (i - ux1) / (float) (ux2 - ux1);
			c.fill(i, py + 28, Math.min(i + 2, ux2), py + 29, withAlpha(lerpColor(ACCENT, ACCENT2, t) & 0xFFFFFF, 0xAA));
		}
	}

	private void renderSidebar(GuiGraphicsExtractor c, int mx, int my) {
		int sx = px + 8, sy = py + 38, sw = sideW - 8;
		rrect(c, sx - 2, sy - 4, sx + sw, py + ph - 8, 5, SIDEBAR);
		Tab[] tabs = Tab.values();
		for (int i = 0; i < tabs.length; i++) {
			int ty = sy + i * 26;
			boolean sel = tabs[i] == tab;
			boolean hov = mx >= sx && mx < sx + sw - 2 && my >= ty && my < ty + 24;
			if (sel) {
				rrect(c, sx + 2, ty, sx + sw - 4, ty + 24, 4, 0xFF23233A);
				rrect(c, sx + 2, ty + 4, sx + 5, ty + 20, 1, ACCENT);
			} else if (hov) {
				rrect(c, sx + 2, ty, sx + sw - 4, ty + 24, 4, 0xFF1A1A28);
			}
			drawText(c, tabs[i].label, sx + 12, ty + 8, sel ? TEXT : DIM);
			if (tabs[i].active()) {
				rrect(c, sx + sw - 14, ty + 9, sx + sw - 8, ty + 15, 3, ON);
			}
		}
		// panic button
		int by = py + ph - 8 - 26;
		boolean hov = mx >= sx + 2 && mx < sx + sw - 4 && my >= by && my < by + 20;
		rrect(c, sx + 2, by, sx + sw - 4, by + 20, 4, hov ? withAlpha(DANGER, 0x66) : withAlpha(DANGER, 0x33));
		String label = "PANIC OFF";
		drawText(c, label, sx + 2 + (sw - 6 - font.width(label)) / 2, by + 6, DANGER);
	}

	private void renderContent(GuiGraphicsExtractor c, int mx, int my) {
		// layout rows
		int total = 0;
		for (Row row : rows) {
			total += row.h + 2;
		}
		contentHeight = total;
		float max = Math.max(0, contentHeight - ch);
		targetScroll = Math.max(0, Math.min(max, targetScroll));
		scroll += (targetScroll - scroll) * Math.min(1f, dt * 14f);
		if (Math.abs(targetScroll - scroll) < 0.2f) {
			scroll = targetScroll;
		}

		int rowW = cw - 8;
		c.enableScissor(cx - 2, cy, cx + cw, cy + ch);
		int y = cy - Math.round(scroll);
		for (Row row : rows) {
			row.y = y;
			if (y + row.h >= cy && y <= cy + ch) {
				row.render(c, cx, rowW, mx, my);
			}
			y += row.h + 2;
		}
		c.disableScissor();

		// scrollbar
		if (max > 0) {
			int barX = cx + cw - 4;
			int barH = Math.max(18, Math.round(ch * (ch / (float) contentHeight)));
			int barY = cy + Math.round((ch - barH) * (scroll / max));
			rrect(c, barX, cy, barX + 3, cy + ch, 1, 0xFF1A1A28);
			rrect(c, barX, barY, barX + 3, barY + barH, 1, ACCENT);
		}
	}

	// =============================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		double mx = event.x();
		double my = event.y();

		// sidebar
		int sx = px + 8, sy = py + 38, sw = sideW - 8;
		Tab[] tabs = Tab.values();
		for (int i = 0; i < tabs.length; i++) {
			int ty = sy + i * 26;
			if (mx >= sx && mx < sx + sw - 2 && my >= ty && my < ty + 24) {
				if (tab != tabs[i]) {
					tab = tabs[i];
					rebuild();
				}
				return true;
			}
		}
		int by = py + ph - 8 - 26;
		if (mx >= sx + 2 && mx < sx + sw - 4 && my >= by && my < by + 20) {
			Modules.panic(Minecraft.getInstance());
			return true;
		}

		// content
		if (mx >= cx && mx < cx + cw - 8 && my >= cy && my < cy + ch) {
			for (Row row : rows) {
				if (my >= row.y && my < row.y + row.h) {
					return row.click(cx, cw - 8, mx, my);
				}
			}
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_RSHIFT) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double offsetX, double offsetY) {
		if (activeRow != null) {
			activeRow.drag(cx, cw - 8, event.x());
			return true;
		}
		return super.mouseDragged(event, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		activeRow = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		targetScroll -= (float) (verticalAmount * 28.0);
		return true;
	}

	// =============================================================== helpers

	private void drawText(GuiGraphicsExtractor c, String s, int x, int y, int color) {
		c.text(font, s, x, y, color, true);
	}

	private void drawSwitch(GuiGraphicsExtractor c, int x, int y, float anim) {
		int track = lerpColor(OFF, ON, anim);
		rrect(c, x, y, x + 28, y + 14, 7, track);
		int kx = x + 2 + Math.round(anim * 14);
		rrect(c, kx, y + 2, kx + 10, y + 12, 5, 0xFFFFFFFF);
	}

	private static int nextColor(int current) {
		int best = 0;
		for (int i = 0; i < PALETTE.length; i++) {
			if (PALETTE[i] == (current & 0xFFFFFF)) {
				best = i;
				return PALETTE[(i + 1) % PALETTE.length];
			}
		}
		return PALETTE[best];
	}

	private static int withAlpha(int rgb, int alpha) {
		return (alpha << 24) | (rgb & 0xFFFFFF);
	}

	private static int lerpColor(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		int ra = Math.round(aa + (ba - aa) * t);
		int rr = Math.round(ar + (br - ar) * t);
		int rg = Math.round(ag + (bg - ag) * t);
		int rb = Math.round(ab + (bb - ab) * t);
		return (ra << 24) | (rr << 16) | (rg << 8) | rb;
	}

	/** Rounded rectangle from stacked fills. */
	private static void rrect(GuiGraphicsExtractor c, int x1, int y1, int x2, int y2, int r, int color) {
		if (x2 <= x1 || y2 <= y1) {
			return;
		}
		r = Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
		if (r <= 0) {
			c.fill(x1, y1, x2, y2, color);
			return;
		}
		c.fill(x1, y1 + r, x2, y2 - r, color);
		for (int i = 0; i < r; i++) {
			double d = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(r * r - d * d));
			c.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
			c.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
		}
	}
}
