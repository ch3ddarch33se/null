package com.ch3ddarch33se.thenull.worldgen;

/** Pure, seed-based geometry. AI-assisted original implementation, Apache-2.0. */
public final class TerrainShape {
    private final long seed;

    public TerrainShape(long seed) { this.seed = seed; }

    public int surface(int x, int z) {
        // Two independent, broad warps bend the coordinates of every scale.
        // Hash-based gradient noise has no trigonometric or tiled repetition.
        double wx = x + 73 * noise(x / 181.0, z / 181.0, 11)
            + 23 * noise(x / 67.0, z / 67.0, 12);
        double wz = z + 73 * noise(x / 181.0, z / 181.0, 13)
            + 23 * noise(x / 67.0, z / 67.0, 14);
        double broad = noise(wx / 137.0, wz / 137.0, 21);
        double ridge = 1 - Math.abs(noise(wx / 59.0, wz / 59.0, 22));
        double middle = noise(wx / 31.0, wz / 31.0, 23);
        double detail = noise(wx / 12.0, wz / 12.0, 24);
        double fine = noise(wx / 5.0, wz / 5.0, 25);
        int height = (int) Math.floor(-313 + 43 * broad + 27 * (ridge - .65)
            + 15 * middle + 6 * detail + 2.5 * fine);
        return Math.clamp(height, -348, -270);
    }

    private double noise(double x, double z, int salt) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double dx = x - ix, dz = z - iz;
        double u = fade(dx), v = fade(dz);
        double a = gradient(hash(seed, ix, iz, salt), dx, dz);
        double b = gradient(hash(seed, ix + 1, iz, salt), dx - 1, dz);
        double c = gradient(hash(seed, ix, iz + 1, salt), dx, dz - 1);
        double d = gradient(hash(seed, ix + 1, iz + 1, salt), dx - 1, dz - 1);
        return lerp(lerp(a, b, u), lerp(c, d, u), v);
    }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double gradient(long hash, double x, double z) {
        return switch ((int) hash & 7) {
            case 0 -> x; case 1 -> -x; case 2 -> z; case 3 -> -z;
            case 4 -> (x + z) * .7071067811865476;
            case 5 -> (x - z) * .7071067811865476;
            case 6 -> (-x + z) * .7071067811865476;
            default -> (-x - z) * .7071067811865476;
        };
    }

    static long hash(long seed, int x, int z, int salt) {
        long value = seed ^ x * 0x632be59bd9b4e019L ^ z * 0x9e3779b97f4a7c15L ^ salt * 0xd1b54a32d192ed03L;
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    static double unit(long value) { return (value >>> 11) * 0x1.0p-53; }
    static double signed(long value) { return unit(value) * 2 - 1; }
}
