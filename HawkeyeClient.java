package com.hawkeye.client;

import com.hawkeye.config.Settings;
import com.hawkeye.gui.ClickGui;
import com.hawkeye.module.Hud;
import com.hawkeye.module.Modules;
import com.hawkeye.render.EspRenderer;
import com.hawkeye.scan.Scanner;
import com.hawkeye.scan.Targets;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.sdl.SDLScancode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HawkeyeClient implements ClientModInitializer {
	public static final String MOD_ID = "hawkeye";
	public static final Logger LOGGER = LoggerFactory.getLogger("Hawkeye");

	private static KeyMapping menuKey;
	private static KeyMapping xrayKey;
	private static KeyMapping oreKey;
	private static KeyMapping storageKey;
	private static KeyMapping blockKey;

	@Override
	public void onInitializeClient() {
		Targets.init();
		Settings.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "controls"));
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.hawkeye.menu", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_RSHIFT, category));
		xrayKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.hawkeye.xray", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_X, category));
		oreKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.hawkeye.ore_esp", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_UNKNOWN, category));
		storageKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.hawkeye.storage_esp", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_UNKNOWN, category));
		blockKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.hawkeye.block_pick", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_UNKNOWN, category));

		EspRenderer.init();
		Hud.register();

		ClientTickEvents.END_CLIENT_TICK.register(HawkeyeClient::tick);
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> Scanner.INSTANCE.chunkLoaded(level, chunk));
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> Scanner.INSTANCE.chunkUnloaded(level, chunk));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> Scanner.INSTANCE.reset());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Settings.save());
		LOGGER.info("Hawkeye loaded. Press Right Shift in-game to open the menu.");
	}

	private static void tick(Minecraft mc) {
		while (menuKey.consumeClick()) {
			if (mc.gui.screen() == null) {
				mc.gui.setScreen(new ClickGui());
			}
		}
		while (xrayKey.consumeClick()) {
			if (mc.gui.screen() == null) {
				Modules.toggleXray(mc);
				Settings.save();
			}
		}
		while (oreKey.consumeClick()) {
			if (mc.gui.screen() == null) {
				Settings s = Settings.get();
				s.oreEsp = !s.oreEsp;
				Modules.notify(mc, "Ore ESP " + (s.oreEsp ? "ON" : "OFF"));
				Settings.save();
			}
		}
		while (storageKey.consumeClick()) {
			if (mc.gui.screen() == null) {
				Settings s = Settings.get();
				s.storageEsp = !s.storageEsp;
				Modules.notify(mc, "Storage ESP " + (s.storageEsp ? "ON" : "OFF"));
				Settings.save();
			}
		}
		while (blockKey.consumeClick()) {
			if (mc.gui.screen() == null) {
				Modules.toggleLookedBlock(mc);
			}
		}
		Modules.tick(mc);
		Scanner.INSTANCE.tick(mc);
		EspRenderer.tick(mc);
	}
}
