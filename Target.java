package com.hawkeye.scan;

import net.minecraft.world.phys.AABB;

/** One thing to draw: an ore, a container, a chosen block, ... Coordinates are absolute world coordinates. */
public final class Target {
	public final double minX, minY, minZ, maxX, maxY, maxZ;
	public final AABB box;
	public final int rgb;
	public final Targets.Kind kind;
	public final Targets.Type type;
	public final double dist2;
	public boolean tracer;

	public Target(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
			int rgb, Targets.Type type, double dist2) {
		this.minX = minX;
		this.minY = minY;
		this.minZ = minZ;
		this.maxX = maxX;
		this.maxY = maxY;
		this.maxZ = maxZ;
		this.box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
		this.rgb = rgb;
		this.kind = type.kind;
		this.type = type;
		this.dist2 = dist2;
	}

	public double cx() {
		return (minX + maxX) * 0.5;
	}

	public double cy() {
		return (minY + maxY) * 0.5;
	}

	public double cz() {
		return (minZ + maxZ) * 0.5;
	}
}
