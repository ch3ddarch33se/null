package com.ch3ddarch33se.thenull.worldgen;

/** Original deterministic graph-owner hashing, independent of terrain shape. */
final class SeedHash {
    private SeedHash() { }
    static long hash(long seed, int x, int z, int salt) {
        long value = seed ^ x * 0x632be59bd9b4e019L ^ z * 0x9e3779b97f4a7c15L ^ salt * 0xd1b54a32d192ed03L;
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
    static double unit(long value) { return (value >>> 11) * 0x1.0p-53; }
    static double signed(long value) { return unit(value) * 2 - 1; }
}
