package com.ch3ddarch33se.thenull.worldgen;

/** Deterministic, block-coordinate geometry for the Null's central chamber. */
public final class CentralChamber {
    public static final int MIN_Y = -1760;
    public static final int BEDROCK_MAX_Y = -1757;
    public static final int FLOOR_Y = -1756;
    public static final int AIR_MIN_Y = FLOOR_Y + 1;
    public static final int AIR_MAX_Y = -300;
    public static final int RADIUS = 350;
    public static final int RADIUS_SQUARED = RADIUS * RADIUS;

    // A sphere cannot be both 700 blocks wide and 1,160 blocks tall. This
    // vanilla-glass ellipsoid keeps circular horizontal slices, spans the
    // requested floor-to-ceiling interval, and reaches the chamber walls.
    public static final double ORB_CENTER_X = 0.5;
    public static final double ORB_CENTER_Z = 0.5;
    public static final double ORB_CENTER_Y = -879.5;
    public static final double ORB_RADIUS_XZ = 350.0;
    public static final double ORB_RADIUS_Y = 580.0;
    public static final double ORB_SHELL_INNER = 0.985;
    public static final double ORB_SHELL_OUTER = 1.015;

    private CentralChamber() { }

    public static boolean containsColumn(int x, int z) {
        double dx = x + 0.5 - ORB_CENTER_X;
        double dz = z + 0.5 - ORB_CENTER_Z;
        return dx * dx + dz * dz <= RADIUS_SQUARED;
    }

    public static boolean sectionIsAir(int sectionY) {
        return sectionY >= AIR_MIN_Y && sectionY + 15 <= AIR_MAX_Y;
    }

    public static boolean sectionFullyInside(int x0, int z0) {
        double farX = Math.max(Math.abs(x0 + 0.5 - ORB_CENTER_X), Math.abs(x0 + 15.5 - ORB_CENTER_X));
        double farZ = Math.max(Math.abs(z0 + 0.5 - ORB_CENTER_Z), Math.abs(z0 + 15.5 - ORB_CENTER_Z));
        return farX * farX + farZ * farZ <= RADIUS_SQUARED;
    }

    public static boolean sectionOutside(int x0, int z0) {
        double nearX = nearest(ORB_CENTER_X, x0 + 0.5, x0 + 15.5);
        double nearZ = nearest(ORB_CENTER_Z, z0 + 0.5, z0 + 15.5);
        double dx = nearX - ORB_CENTER_X;
        double dz = nearZ - ORB_CENTER_Z;
        return dx * dx + dz * dz > RADIUS_SQUARED;
    }

    public static boolean orbMayIntersect(int x0, int y0, int z0) {
        double min = axisMin(x0 + 0.5, x0 + 15.5, ORB_CENTER_X, ORB_RADIUS_XZ)
            + axisMin(z0 + 0.5, z0 + 15.5, ORB_CENTER_Z, ORB_RADIUS_XZ)
            + axisMin(y0 + 0.5, y0 + 15.5, ORB_CENTER_Y, ORB_RADIUS_Y);
        double max = axisMax(x0 + 0.5, x0 + 15.5, ORB_CENTER_X, ORB_RADIUS_XZ)
            + axisMax(z0 + 0.5, z0 + 15.5, ORB_CENTER_Z, ORB_RADIUS_XZ)
            + axisMax(y0 + 0.5, y0 + 15.5, ORB_CENTER_Y, ORB_RADIUS_Y);
        return max >= ORB_SHELL_INNER * ORB_SHELL_INNER
            && min <= ORB_SHELL_OUTER * ORB_SHELL_OUTER;
    }

    public static boolean isOrbShell(int x, int y, int z) {
        double dx = (x + 0.5 - ORB_CENTER_X) / ORB_RADIUS_XZ;
        double dy = (y + 0.5 - ORB_CENTER_Y) / ORB_RADIUS_Y;
        double dz = (z + 0.5 - ORB_CENTER_Z) / ORB_RADIUS_XZ;
        double distance = dx * dx + dy * dy + dz * dz;
        return distance >= ORB_SHELL_INNER * ORB_SHELL_INNER
            && distance <= ORB_SHELL_OUTER * ORB_SHELL_OUTER;
    }

    private static double axisMin(double min, double max, double center, double radius) {
        return square((nearest(center, min, max) - center) / radius);
    }

    private static double axisMax(double min, double max, double center, double radius) {
        return square((Math.max(Math.abs(min - center), Math.abs(max - center))) / radius);
    }

    private static double nearest(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double square(double value) { return value * value; }
}
