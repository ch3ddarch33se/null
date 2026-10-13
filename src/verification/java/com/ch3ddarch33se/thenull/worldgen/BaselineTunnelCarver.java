package com.ch3ddarch33se.thenull.worldgen;

import java.util.Random;
import java.util.function.IntBinaryOperator;

/**
 * Jittered spatial graph with swept, winding Bezier paths. Every edge belongs
 * to one world cell and is reconstructed identically by every intersected
 * chunk. No writes into neighbours, global mutable cache, or per-voxel noise.
 * AI-assisted original implementation, Apache-2.0.
 */
public final class BaselineTunnelCarver {
    public static final int CELL = 48;
    public static final int LEVELS = 8;
    public static final int MIN_Y = -763;
    public static final int MAX_Y = -257;
    private static final int DEPTH = MAX_Y - MIN_Y + 1;
    private final long seed;
    private final IntBinaryOperator surface;

    public BaselineTunnelCarver(long seed) { this(seed, (x, z) -> -258); }

    public BaselineTunnelCarver(long seed, IntBinaryOperator surface) {
        this.seed = seed;
        this.surface = surface;
    }

    /** Packed mask: ((y - MIN_Y) * sizeZ + localZ) * sizeX + localX. */
    public boolean[] carve(int startX, int startZ, int sizeX, int sizeZ) {
        boolean[] air = new boolean[Math.multiplyExact(Math.multiplyExact(sizeX, sizeZ), DEPTH)];
        var target = new Target(startX, startZ, sizeX, sizeZ, air);
        // All control points lie within two cells of their owner. This fixed
        // conservative reach includes edges whose owner is outside the chunk.
        for (int cz = Math.floorDiv(startZ, CELL) - 2; cz <= Math.floorDiv(startZ + sizeZ - 1, CELL) + 2; cz++) {
            for (int cx = Math.floorDiv(startX, CELL) - 2; cx <= Math.floorDiv(startX + sizeX - 1, CELL) + 2; cx++) {
                for (int layer = 0; layer < LEVELS; layer++) {
                    Point node = node(cx, cz, layer);
                    path(target, node, node(cx + 1, cz, layer), cx, cz, layer * 16);
                    path(target, node, node(cx, cz + 1, layer), cx, cz, layer * 16 + 1);
                    // Horizontal edges form loops. Each 2x2 group has one
                    // vertical edge per level, linking the horizontal layers.
                    long group = SeedHash.hash(seed, Math.floorDiv(cx, 2), Math.floorDiv(cz, 2), layer + 900);
                    if (layer + 1 < LEVELS && Math.floorMod(cx, 2) == (int) (group & 1)
                        && Math.floorMod(cz, 2) == (int) ((group >>> 1) & 1)) {
                        path(target, node, node(cx, cz, layer + 1), cx, cz, layer * 16 + 2);
                    }
                    Random random = new Random(SeedHash.hash(seed, cx, cz, layer * 16 + 3));
                    Point tip = new Point(node.x + random.nextDouble(-30, 30),
                        Math.clamp(node.y + random.nextDouble(-15, 15), -750, -355),
                        node.z + random.nextDouble(-30, 30));
                    path(target, node, tip, cx, cz, layer * 16 + 3);
                    if (layer == 0 && (SeedHash.hash(seed, cx, cz, 911) & 3) == 0) {
                        // Sloping surface access, attached to the upper graph.
                        int ex = (int) Math.floor(node.x + random.nextDouble(-32, 32));
                        int ez = (int) Math.floor(node.z + random.nextDouble(-32, 32));
                        path(target, node, new Point(ex + .5, surface.applyAsInt(ex, ez) + 2, ez + .5),
                            cx, cz, 912);
                    }
                }
            }
        }
        return air;
    }

    private Point node(int cx, int cz, int layer) {
        long a = SeedHash.hash(seed, cx, cz, layer * 7 + 100);
        long b = SeedHash.hash(seed, cx, cz, layer * 7 + 101);
        long c = SeedHash.hash(seed, cx, cz, layer * 7 + 102);
        return new Point(cx * (double) CELL + CELL / 2.0 + 16 * SeedHash.signed(a),
            -370 - 52 * layer + 7 * SeedHash.signed(b),
            cz * (double) CELL + CELL / 2.0 + 16 * SeedHash.signed(c));
    }

    private void path(Target target, Point from, Point to, int cx, int cz, int salt) {
        Random random = new Random(SeedHash.hash(seed, cx, cz, salt + 4000));
        double dx = to.x - from.x, dz = to.z - from.z;
        double horizontal = Math.hypot(dx, dz);
        double sideX = horizontal > 1 ? -dz / horizontal : 1;
        double sideZ = horizontal > 1 ? dx / horizontal : 0;
        double bend1 = random.nextDouble(-17, 17), bend2 = random.nextDouble(-17, 17);
        Point c1 = new Point(from.x + dx / 3 + sideX * bend1,
            from.y + (to.y - from.y) / 3 + random.nextDouble(-8, 8),
            from.z + dz / 3 + sideZ * bend1);
        Point c2 = new Point(from.x + dx * 2 / 3 + sideX * bend2,
            from.y + (to.y - from.y) * 2 / 3 + random.nextDouble(-8, 8),
            from.z + dz * 2 / 3 + sideZ * bend2);
        if (!target.overlaps(from, c1, c2, to)) return;
        // Control-polygon length is an upper bound on arc length. Sub-block
        // spacing prevents diagonal voxel gaps at chunk boundaries and bends.
        int steps = Math.max(1, (int) Math.ceil((from.distance(c1) + c1.distance(c2) + c2.distance(to)) / .65));
        double radius = random.nextDouble(1.25, 1.65);
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps, u = 1 - t;
            double a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, d = t * t * t;
            target.ball(a * from.x + b * c1.x + c * c2.x + d * to.x,
                a * from.y + b * c1.y + c * c2.y + d * to.y,
                a * from.z + b * c1.z + c * c2.z + d * to.z, radius);
        }
    }

    private record Point(double x, double y, double z) {
        double distance(Point other) {
            return Math.sqrt((x - other.x) * (x - other.x) + (y - other.y) * (y - other.y) + (z - other.z) * (z - other.z));
        }
    }

    private static final class Target {
        private final int x0, z0, nx, nz;
        private final boolean[] air;
        private Target(int x0, int z0, int nx, int nz, boolean[] air) {
            this.x0 = x0; this.z0 = z0; this.nx = nx; this.nz = nz; this.air = air;
        }
        boolean overlaps(Point... points) {
            double minX = Double.POSITIVE_INFINITY, minZ = minX, maxX = -minX, maxZ = -minX;
            for (Point p : points) {
                minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
                minZ = Math.min(minZ, p.z); maxZ = Math.max(maxZ, p.z);
            }
            return maxX + 2 >= x0 && minX - 2 <= x0 + nx && maxZ + 2 >= z0 && minZ - 2 <= z0 + nz;
        }
        void ball(double x, double y, double z, double radius) {
            if (x + radius < x0 || x - radius >= x0 + nx || z + radius < z0 || z - radius >= z0 + nz) return;
            // Slightly taller than wide to keep two-block player headroom.
            double verticalRadius = 1.8;
            int lowX = Math.max(x0, (int) Math.floor(x - radius));
            int highX = Math.min(x0 + nx - 1, (int) Math.floor(x + radius));
            int lowZ = Math.max(z0, (int) Math.floor(z - radius));
            int highZ = Math.min(z0 + nz - 1, (int) Math.floor(z + radius));
            int lowY = Math.max(MIN_Y, (int) Math.floor(y - verticalRadius));
            int highY = Math.min(MAX_Y, (int) Math.floor(y + verticalRadius));
            for (int by = lowY; by <= highY; by++) {
                double dy = (by + .5 - y) / verticalRadius;
                for (int bz = lowZ; bz <= highZ; bz++) {
                    double dz = (bz + .5 - z) / radius;
                    for (int bx = lowX; bx <= highX; bx++) {
                        double dx = (bx + .5 - x) / radius;
                        if (dx * dx + dy * dy + dz * dz <= 1) {
                            air[((by - MIN_Y) * nz + bz - z0) * nx + bx - x0] = true;
                        }
                    }
                }
            }
        }
    }
}
