package com.hawkeye.module;

import com.hawkeye.client.HawkeyeClient;
import com.hawkeye.config.Settings;
import com.hawkeye.scan.BaseFinder;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Enabled-module list (top right) and the base finder readout (top left). */
public final class Hud {
	private static final int ACCENT = 0xFF8B6BFF;

	private Hud() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(HawkeyeClient.MOD_ID, "overlay"), Hud::render);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		Settings s = Settings.get();
		Font font = mc.font;

		if (s.hudModuleList) {
			List<String> active = new ArrayList<>();
			if (s.xray) active.add("Xray");
			if (s.oreEsp) active.add("Ore ESP");
			if (s.blockEsp) active.add("Block ESP");
			if (s.storageEsp) active.add("Storage ESP");
			if (s.baseFinder) active.add("Base Finder");
			if (s.entityEsp) active.add("Entity ESP");
			if (s.fullbright) active.add("Fullbright");
			if (s.autoSprint) active.add("Auto Sprint");
			if (s.flight) active.add("Flight");
			active.sort(Comparator.comparingInt((String n) -> font.width(n)).reversed());

			int screenW = graphics.guiWidth();
			int y = 4;
			for (String name : active) {
				int w = font.width(name);
				int x = screenW - w - 8;
				graphics.fill(x - 3, y - 1, screenW - 2, y + 10, 0x99101018);
				graphics.fill(screenW - 3, y - 1, screenW - 2, y + 10, ACCENT);
				graphics.text(font, name, x, y + 1, 0xFFE8E8F4, true);
				y += 11;
			}
		}

		if (s.baseFinder && s.baseHudList && !BaseFinder.clusters().isEmpty()) {
			int y = 4;
			graphics.fill(4, y, 150, y + 13, 0xAA101018);
			graphics.fill(4, y, 6, y + 13, 0xFFFF4DDB);
			graphics.text(font, "BASES NEARBY", 10, y + 3, 0xFFFF8AE8, true);
			y += 15;
			int shown = 0;
			for (BaseFinder.Cluster c : BaseFinder.clusters()) {
				String line = c.count + " containers  " + Math.round(c.distance) + "m  " + compass(c, mc);
				int w = font.width(line) + 12;
				graphics.fill(4, y, 4 + w, y + 12, 0x88101018);
				graphics.text(font, line, 10, y + 2, 0xFFE8E8F4, true);
				y += 13;
				if (++shown >= 5) {
					break;
				}
			}
		}
	}

	private static String compass(BaseFinder.Cluster c, Minecraft mc) {
		double dx = c.cx() - mc.player.getX();
		double dz = c.cz() - mc.player.getZ();
		double angle = Math.toDegrees(Math.atan2(dz, dx)); // 0 = +x (east), 90 = +z (south)
		String[] names = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};
		int idx = (int) Math.round(((angle + 360) % 360) / 45.0) % 8;
		return names[idx];
	}
}
