package com.hawkeye.mixin;

import com.hawkeye.config.Settings;
import com.hawkeye.module.Modules;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Terrain xray. While xray is on, only blocks that pass {@link Modules#xrayShows} get faces,
 * everything else is skipped when chunks are meshed. The full descriptor is spelled out and
 * require = 0 so that if this method ever changes the mod still loads (xray just stops working).
 * Note: Sodium meshes chunks with its own code, so terrain xray needs Sodium removed; the ore
 * ESP glow works either way.
 */
@Mixin(Block.class)
public abstract class BlockMixin {
	@Inject(
			method = "shouldRenderFace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z",
			at = @At("HEAD"),
			cancellable = true,
			require = 0
	)
	private static void hawkeye$xray(BlockState state, BlockState neighborState, Direction direction,
			CallbackInfoReturnable<Boolean> cir) {
		if (Settings.get().xray) {
			cir.setReturnValue(Modules.xrayShows(state));
		}
	}
}
