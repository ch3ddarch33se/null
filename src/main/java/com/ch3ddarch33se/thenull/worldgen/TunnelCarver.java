package com.ch3ddarch33se.thenull.worldgen;

import java.util.Arrays;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.IntBinaryOperator;

/**
 * Seeded, jittered spatial graph swept with winding Bezier passages. Edges
 * have one owner and fixed spatial bounds, so every chunk reconstructs the
 * same geometry without neighbour writes or a shared mutable cache.
 * AI-assisted original implementation, Apache-2.0.
 */
public final class TunnelCarver {
    public static final int CELL = 40;
    public static final int LEVELS = 10;
    public static final int MIN_Y = -763;
    public static final int MAX_Y = -257;
    private static final int DEPTH = MAX_Y - MIN_Y + 1;
    private static final double MAX_RADIUS = 4.5;
    private static final double STEP = 0.9;
    public enum Profile {
        ORIGINAL(40, 10, 40, -378, 4.5, 2, MAX_Y, MIN_Y),
        ECOSYSTEM(32, 12, 30, -398, 10.0, 3, -369, -748);
        final int cell, levels, spacing, top, reach, ceiling, floor;
        final double radius;
        Profile(int cell, int levels, int spacing, int top, double radius, int reach, int ceiling, int floor) {
            this.cell=cell; this.levels=levels; this.spacing=spacing; this.top=top;
            this.radius=radius; this.reach=reach; this.ceiling=ceiling; this.floor=floor;
        }
    }
    private final Profile profile;
    private final long seed;
    private final IntBinaryOperator surface;

    public TunnelCarver(long seed) { this(seed, (x, z) -> -258); }

    public TunnelCarver(long seed, IntBinaryOperator surface) { this(seed, surface, Profile.ORIGINAL); }

    public TunnelCarver(long seed, Profile profile) { this(seed, (x,z) -> -258, profile); }

    private TunnelCarver(long seed, IntBinaryOperator surface, Profile profile) {
        this.seed=seed; this.surface=surface; this.profile=profile;
    }

    /** Packed mask: ((y - MIN_Y) * sizeZ + localZ) * sizeX + localX. */
    public boolean[] carve(int startX, int startZ, int sizeX, int sizeZ) {
        return carve(startX, startZ, sizeX, sizeZ, null);
    }

    /** Optional path observations for development checks; absent in generation. */
    public boolean[] carve(int startX, int startZ, int sizeX, int sizeZ, Consumer<CavePath> observer) {
        boolean[] air = new boolean[Math.multiplyExact(Math.multiplyExact(sizeX, sizeZ), DEPTH)];
        carveInto(air,startX,startZ,sizeX,sizeZ,observer);
        return air;
    }

    /** Adds this graph to a same-sized mask, avoiding a second full-height allocation. */
    public void carveInto(boolean[] air,int startX,int startZ,int sizeX,int sizeZ,Consumer<CavePath> observer) {
        carveInto(air,startX,startZ,sizeX,sizeZ,MIN_Y,MAX_Y,observer);
    }

    /** Adds only the requested vertical slice to a compact mask. */
    public void carveInto(boolean[] air,int startX,int startZ,int sizeX,int sizeZ,
        int minY,int maxY,Consumer<CavePath> observer) {
        if(minY<MIN_Y||maxY>MAX_Y||minY>maxY)throw new IllegalArgumentException("Invalid cave slice "+minY+".."+maxY);
        int expected=Math.multiplyExact(Math.multiplyExact(sizeX,sizeZ),maxY-minY+1);
        if(air.length!=expected)throw new IllegalArgumentException("Expected cave mask length "+expected);
        int targetCeiling=Math.min(maxY,profile.ceiling),targetFloor=Math.max(minY,profile.floor);
        var target = new Target(startX, startZ, sizeX, sizeZ, air, observer, profile.radius,
            minY,targetCeiling,targetFloor);
        // Nodes, endpoints and controls plus maximum radius are confined to
        // owner offsets -2..2. The same conservative owner set works for a
        // single chunk, a large region and negative world coordinates.
        for (int cz = Math.floorDiv(startZ, profile.cell) - profile.reach; cz <= Math.floorDiv(startZ + sizeZ - 1, profile.cell) + profile.reach; cz++) {
            for (int cx = Math.floorDiv(startX, profile.cell) - profile.reach; cx <= Math.floorDiv(startX + sizeX - 1, profile.cell) + profile.reach; cx++) {
                for (int layer = 0; layer < profile.levels; layer++) {
                    Point node = node(cx, cz, layer);
                    int salt = layer * 32;
                    // The connected horizontal backbone remains intact; node
                    // jitter and independent bends conceal the owner lattice.
                    path(target, node, node(cx + 1, cz, layer), cx, cz, layer, salt, Kind.PASSAGE);
                    path(target, node, node(cx, cz + 1, layer), cx, cz, layer, salt + 1, Kind.PASSAGE);
                    long group = SeedHash.hash(seed, Math.floorDiv(cx, 2), Math.floorDiv(cz, 2), layer + 900);
                    // Two links per 2x2 owner group give alternate vertical routes.
                    if (layer + 1 < profile.levels && Math.floorMod(cx, 2) == (int) (group & 1)) {
                        path(target, node, node(cx, cz, layer + 1), cx, cz, layer, salt + 2, Kind.VERTICAL);
                    }
                    Random random = new Random(SeedHash.hash(seed, cx, cz, salt + 3));
                    branch(target, node, cx, cz, layer, salt + 3, random);
                    double depth = layer / (double) (profile.levels - 1);
                    if (random.nextDouble() < 0.15 + 0.35 * depth) {
                        branch(target, node, cx, cz, layer, salt + 4, random);
                    }
                    if (random.nextDouble() < 0.08 + 0.08 * depth) {
                        path(target, node, node(cx + 2, cz, layer), cx, cz, layer, salt + 5, Kind.LONG_CONNECTION);
                    }
                    if (random.nextDouble() < 0.08 + 0.08 * depth) {
                        // Modest attached ellipsoids, never separate noise pockets.
                        double radius = random.nextDouble(3.1, 4.5);
                        target.ball(node.x, node.y, node.z, radius, random.nextDouble(2.6, 3.6));
                    }
                    if (profile != Profile.ECOSYSTEM && layer == 0 && (SeedHash.hash(seed, cx, cz, 911) & 3) == 0) {
                        int ex = (int) Math.floor(node.x + random.nextDouble(-32, 32));
                        int ez = (int) Math.floor(node.z + random.nextDouble(-32, 32));
                        path(target, node, new Point(ex + .5, surface.applyAsInt(ex, ez) + 2, ez + .5),
                            cx, cz, layer, 912, Kind.ENTRANCE);
                    }
                }
            }
        }
    }

    private void branch(Target target, Point node, int cx, int cz, int layer, int salt, Random random) {
        double choice = random.nextDouble();
        double length = choice < .4 ? random.nextDouble(8, 22)
            : choice < .8 ? random.nextDouble(22, 40) : random.nextDouble(40, 64);
        double angle = random.nextDouble(Math.PI * 2);
        Point tip = new Point(node.x + Math.cos(angle) * length,
            Math.clamp(node.y + random.nextDouble(-20, 20),
                profile == Profile.ORIGINAL ? MIN_Y + 8 : profile.floor + 18,
                profile == Profile.ECOSYSTEM ? -384 : -350),
            node.z + Math.sin(angle) * length);
        path(target, node, tip, cx, cz, layer, salt, Kind.BRANCH);
    }

    private Point node(int cx, int cz, int layer) {
        long a = SeedHash.hash(seed, cx, cz, layer * 7 + 100);
        long b = SeedHash.hash(seed, cx, cz, layer * 7 + 101);
        long c = SeedHash.hash(seed, cx, cz, layer * 7 + 102);
        return new Point(cx * (double) profile.cell + profile.cell / 2.0 + 14 * SeedHash.signed(a),
            profile.top - profile.spacing * layer + 10 * SeedHash.signed(b),
            cz * (double) profile.cell + profile.cell / 2.0 + 14 * SeedHash.signed(c));
    }

    private void path(Target target, Point from, Point to, int cx, int cz, int layer, int salt, Kind kind) {
        if(Math.max(from.y,to.y)+11+profile.radius<target.floor
            ||Math.min(from.y,to.y)-11-profile.radius>target.ceiling)return;
        // Reject distant endpoints before allocating random/control objects.
        if (!target.couldReach(from, to, 43 + profile.radius)) return;
        Random random = new Random(SeedHash.hash(seed, cx, cz, salt + 4000));
        double dx = to.x - from.x, dz = to.z - from.z;
        double horizontal = Math.hypot(dx, dz);
        double sideX = horizontal > 1 ? -dz / horizontal : 1;
        double sideZ = horizontal > 1 ? dx / horizontal : 0;
        double bend = kind == Kind.BRANCH ? 20 : kind == Kind.VERTICAL ? 38 : 32;
        double bend1 = random.nextDouble(-bend, bend), bend2 = random.nextDouble(-bend, bend);
        Point c1 = bounded(new Point(from.x + dx / 3 + sideX * bend1,
            from.y + (to.y - from.y) / 3 + random.nextDouble(-11, 11),
            from.z + dz / 3 + sideZ * bend1), cx, cz);
        Point c2 = bounded(new Point(from.x + dx * 2 / 3 + sideX * bend2,
            from.y + (to.y - from.y) * 2 / 3 + random.nextDouble(-11, 11),
            from.z + dz * 2 / 3 + sideZ * bend2), cx, cz);
        if (!target.overlaps(from, c1, c2, to)) return;
        // A conservative arc-length bound keeps neighbouring samples joined.
        int steps = Math.max(1, (int) Math.ceil((from.distance(c1) + c1.distance(c2) + c2.distance(to)) / STEP));
        double widthChoice=random.nextDouble();
        boolean wide = widthChoice < .09 + .04 * layer / (profile.levels - 1.0);
        double[] radii = new double[5];
        for (int i = 0; i < radii.length; i++) {
            radii[i] = profile != Profile.ECOSYSTEM
                ? (wide ? random.nextDouble(3.0, profile.radius) : random.nextDouble(1.35, 2.5))
                : widthChoice < .5 ? random.nextDouble(1.6, 2.5)
                : widthChoice < .75 ? random.nextDouble(2.5, 5.0) : random.nextDouble(4.0, 10.0);
        }
        Point previous = from;
        double length = 0, minRadius = profile.radius, maxRadius = 0;
        for (int first = 0; first <= steps; first += 16) {
            int last = Math.min(first + 15, steps);
            // Exact subcurve control bounds reject batches that cannot touch
            // this chunk. Observed region carves retain every sample, providing
            // an independent reference for the chunk-mask comparisons.
            if (target.observer == null && !target.segmentOverlaps(from, c1, c2, to,
                first / (double) steps, last / (double) steps)) continue;
            for (int step = first; step <= last; step++) {
                double t = step / (double) steps, u = 1 - t;
                double a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, d = t * t * t;
                double x = a * from.x + b * c1.x + c * c2.x + d * to.x;
                double y = a * from.y + b * c1.y + c * c2.y + d * to.y;
                double z = a * from.z + b * c1.z + c * c2.z + d * to.z;
                double knot = t * (radii.length - 1);
                int index = Math.min(radii.length - 2, (int) knot);
                double blend = knot - index;
                blend = blend * blend * (3 - 2 * blend);
                double radius = radii[index] + (radii[index + 1] - radii[index]) * blend;
                target.ball(x, y, z, radius, Math.max(1.9, radius * .85));
                if (target.observer != null) {
                    Point current = new Point(x, y, z);
                    length += previous.distance(current); previous = current;
                    minRadius = Math.min(minRadius, radius); maxRadius = Math.max(maxRadius, radius);
                }
            }
        }
        if (target.observer != null) target.observer.accept(new CavePath(from, to,
            new Point(to.x - c2.x, to.y - c2.y, to.z - c2.z), radii[4],
            minRadius * 2, maxRadius * 2, length, kind));
    }

    private Point bounded(Point point, int cx, int cz) {
        double margin = profile.radius + 1;
        return new Point(Math.clamp(point.x, (cx - profile.reach) * (double) profile.cell + margin, (cx + profile.reach + 1.0) * profile.cell - margin),
            Math.clamp(point.y, MIN_Y + 5, profile.ceiling - 3),
            Math.clamp(point.z, (cz - profile.reach) * (double) profile.cell + margin, (cz + profile.reach + 1.0) * profile.cell - margin));
    }

    public enum Kind { PASSAGE, BRANCH, VERTICAL, ENTRANCE, LONG_CONNECTION }
    public record CavePath(Point start, Point end, Point endDirection, double endRadius,
                           double minWidth, double maxWidth, double length, Kind kind) { }
    public record Point(double x, double y, double z) {
        double distance(Point other) {
            return Math.sqrt((x - other.x) * (x - other.x) + (y - other.y) * (y - other.y) + (z - other.z) * (z - other.z));
        }
    }

    private static final class Target {
        private final int x0, z0, nx, nz;
        private final boolean[] air;
        private final Consumer<CavePath> observer;
        private final double radiusLimit;
        private final int baseY,ceiling,floor;
        private Target(int x0,int z0,int nx,int nz,boolean[] air,Consumer<CavePath> observer,
            double radiusLimit,int baseY,int ceiling,int floor) {
            this.x0=x0;this.z0=z0;this.nx=nx;this.nz=nz;this.air=air;this.observer=observer;
            this.radiusLimit=radiusLimit;this.baseY=baseY;this.ceiling=ceiling;this.floor=floor;
        }
        boolean couldReach(Point from, Point to, double margin) {
            return Math.max(from.x, to.x) + margin >= x0 && Math.min(from.x, to.x) - margin <= x0 + nx
                && Math.max(from.z, to.z) + margin >= z0 && Math.min(from.z, to.z) - margin <= z0 + nz;
        }
        boolean overlaps(Point p0, Point p1, Point p2, Point p3) {
            double minX = Math.min(Math.min(p0.x,p1.x),Math.min(p2.x,p3.x));
            double maxX = Math.max(Math.max(p0.x,p1.x),Math.max(p2.x,p3.x));
            double minZ = Math.min(Math.min(p0.z,p1.z),Math.min(p2.z,p3.z));
            double maxZ = Math.max(Math.max(p0.z,p1.z),Math.max(p2.z,p3.z));
            return maxX + radiusLimit >= x0 && minX - radiusLimit <= x0 + nx
                && maxZ + radiusLimit >= z0 && minZ - radiusLimit <= z0 + nz;
        }
        boolean segmentOverlaps(Point p0, Point p1, Point p2, Point p3, double start, double end) {
            return axisOverlaps(p0.x,p1.x,p2.x,p3.x,start,end,x0,x0+nx,radiusLimit)
                && axisOverlaps(p0.z,p1.z,p2.z,p3.z,start,end,z0,z0+nz,radiusLimit);
        }
        private static boolean axisOverlaps(double p0,double p1,double p2,double p3,
            double start,double end,double minimum,double maximum,double radiusLimit) {
            double a=value(p0,p1,p2,p3,start),b=value(p0,p1,p2,p3,end),scale=(end-start)/3;
            double c=a+derivative(p0,p1,p2,p3,start)*scale;
            double d=b-derivative(p0,p1,p2,p3,end)*scale;
            double margin=radiusLimit+1e-7;
            return Math.max(Math.max(a,b),Math.max(c,d))+margin>=minimum
                && Math.min(Math.min(a,b),Math.min(c,d))-margin<=maximum;
        }
        private static double value(double p0,double p1,double p2,double p3,double t) {
            double u=1-t;
            return u*u*u*p0+3*u*u*t*p1+3*u*t*t*p2+t*t*t*p3;
        }
        private static double derivative(double p0,double p1,double p2,double p3,double t) {
            double u=1-t;
            return 3*u*u*(p1-p0)+6*u*t*(p2-p1)+3*t*t*(p3-p2);
        }
        void ball(double x, double y, double z, double radius, double verticalRadius) {
            if (x + radius < x0 || x - radius >= x0 + nx || z + radius < z0 || z - radius >= z0 + nz) return;
            int lowZ = Math.max(z0, (int) Math.ceil(z - radius - .5));
            int highZ = Math.min(z0 + nz - 1, (int) Math.floor(z + radius - .5));
            int lowY = Math.max(floor, (int) Math.ceil(y - verticalRadius - .5));
            int highY = Math.min(ceiling, (int) Math.floor(y + verticalRadius - .5));
            for (int by = lowY; by <= highY; by++) {
                double dy = (by + .5 - y) / verticalRadius;
                for (int bz = lowZ; bz <= highZ; bz++) {
                    double dz = (bz + .5 - z) / radius;
                    double remaining = 1 - dy * dy - dz * dz;
                    if (remaining < 0) continue;
                    double extent = radius * Math.sqrt(remaining);
                    int lowX = Math.max(x0, (int) Math.ceil(x - extent - .5));
                    int highX = Math.min(x0 + nx - 1, (int) Math.floor(x + extent - .5));
                    if (lowX <= highX) {
                int row = ((by - baseY) * nz + bz - z0) * nx;
                        Arrays.fill(air, row + lowX - x0, row + highX - x0 + 1, true);
                    }
                }
            }
        }
    }
}
