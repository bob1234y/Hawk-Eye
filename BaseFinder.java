package com.hawkeye.scan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Groups nearby containers together; a big enough group is probably somebody's base. */
public final class BaseFinder {
	public static final class Cluster {
		public double sx, sy, sz;
		public int count;
		public double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
		public double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		public double distance;

		public double cx() {
			return sx / count;
		}

		public double cy() {
			return sy / count;
		}

		public double cz() {
			return sz / count;
		}

		void add(Target t) {
			sx += t.cx();
			sy += t.cy();
			sz += t.cz();
			count++;
			minX = Math.min(minX, t.minX);
			minY = Math.min(minY, t.minY);
			minZ = Math.min(minZ, t.minZ);
			maxX = Math.max(maxX, t.maxX);
			maxY = Math.max(maxY, t.maxY);
			maxZ = Math.max(maxZ, t.maxZ);
		}
	}

	private static volatile List<Cluster> clusters = List.of();

	private BaseFinder() {
	}

	public static List<Cluster> clusters() {
		return clusters;
	}

	public static void clear() {
		clusters = List.of();
	}

	public static boolean countsTowardsBase(Targets.Type t) {
		return !t.key.equals("spawner") && !t.key.equals("ender_chest");
	}

	public static void compute(List<Target> points, int radius, int minCount, double px, double py, double pz) {
		double r2 = (double) radius * radius;
		List<Cluster> list = new ArrayList<>();
		for (Target t : points) {
			Cluster best = null;
			double bestD = Double.MAX_VALUE;
			for (Cluster c : list) {
				double dx = c.cx() - t.cx();
				double dy = c.cy() - t.cy();
				double dz = c.cz() - t.cz();
				double d = dx * dx + dy * dy + dz * dz;
				if (d <= r2 && d < bestD) {
					bestD = d;
					best = c;
				}
			}
			if (best == null) {
				best = new Cluster();
				list.add(best);
			}
			best.add(t);
		}
		List<Cluster> result = new ArrayList<>();
		for (Cluster c : list) {
			if (c.count >= minCount) {
				double dx = c.cx() - px;
				double dy = c.cy() - py;
				double dz = c.cz() - pz;
				c.distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
				result.add(c);
			}
		}
		result.sort(Comparator.comparingDouble(c -> c.distance));
		clusters = result;
	}
}
