package com.ch3ddarch33se.thenull.verification;

import com.ch3ddarch33se.thenull.worldgen.TerrainShape;
import com.ch3ddarch33se.thenull.worldgen.TunnelCarver;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import javax.imageio.ImageIO;

/** Actual six-neighbour voxel flood fills, not a claim based on a path graph. */
public final class GeometryVerification {
    private static final int SIZE = 144;
    private static final int DEPTH = TunnelCarver.MAX_Y - TunnelCarver.MIN_Y + 1;
    private static final int PLANE = SIZE * SIZE;

    public static void main(String[] args) throws Exception {
        var report = new StringBuilder();
        for (long seed : new long[] {123456789, 0, -1, 987654321}) {
            int x0 = -96, z0 = -96;
            var carver = new TunnelCarver(seed);
            boolean[] whole = carver.carve(x0, z0, SIZE, SIZE);
            if (seed == 123456789) cavePlan(whole);
            List<int[]> chunks = new ArrayList<>();
            for (int z = 0; z < SIZE; z += 16) for (int x = 0; x < SIZE; x += 16) chunks.add(new int[] {x, z});
            long comparisons = 0;
            // Forward, reverse and shuffled chunk requests must all equal a
            // single large carve, including negative coordinates and borders.
            for (int order = 0; order < 3; order++) {
                if (order == 1) Collections.reverse(chunks);
                if (order == 2) Collections.shuffle(chunks, new Random(seed));
                for (int[] chunk : chunks) {
                    boolean[] part = carver.carve(x0 + chunk[0], z0 + chunk[1], 16, 16);
                    for (int y = 0; y < DEPTH; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        if (part[(y * 16 + z) * 16 + x] != whole[(y * SIZE + z + chunk[1]) * SIZE + x + chunk[0]]) {
                            throw new AssertionError("Chunk-border/order mismatch seed=" + seed);
                        }
                        comparisons++;
                    }
                }
            }
            report.append("seed=").append(seed).append(" chunk_order_comparisons=").append(comparisons)
                .append(" mismatches=0 ").append(connectivity(whole, new TerrainShape(seed), x0, z0, SIZE)).append('\n');
        }
        surfaceMap(report);
        report.append("connectivity_scope = six-neighbour cave AIR in four 144x144x507 voxel windows, boundary fragments censored; not proof of player traversal or every seed\n");
        report.append("automated_result = PASSED\n");
        Files.createDirectories(Path.of("build/reports"));
        Files.writeString(Path.of("build/reports/geometry.txt"), report);
        System.out.print(report);
    }

    static String connectivity(boolean[] air, TerrainShape shape, int x0, int z0, int size) {
        int plane = size * size;
        // Only underground air; exposed outside air cannot join separate caves.
        long rockCells = 0;
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
            int ceiling = shape.surface(x0 + x, z0 + z) - 10;
            for (int y = 0; y < DEPTH; y++) {
                if (y + TunnelCarver.MIN_Y >= ceiling) air[y * plane + z * size + x] = false;
                else rockCells++;
            }
        }
        boolean[] visited = new boolean[air.length];
        int[] queue = new int[air.length];
        int components = 0, isolatedComponents = 0, largest = 0, total = 0;
        long isolatedVoxels = 0;
        for (int index = 0; index < air.length; index++) {
            if (!air[index] || visited[index]) continue;
            int head = 0, tail = 1;
            queue[0] = index; visited[index] = true;
            boolean boundary = false;
            while (head < tail) {
                int current = queue[head++];
                int x = current % size, z = current / size % size, y = current / plane;
                if (x == 0 || z == 0 || x == size - 1 || z == size - 1
                    || y + TunnelCarver.MIN_Y >= shape.surface(x0 + x, z0 + z) - 11) boundary = true;
                if (x > 0) tail = visit(current - 1, air, visited, queue, tail);
                if (x + 1 < size) tail = visit(current + 1, air, visited, queue, tail);
                if (z > 0) tail = visit(current - size, air, visited, queue, tail);
                if (z + 1 < size) tail = visit(current + size, air, visited, queue, tail);
                if (y > 0) tail = visit(current - plane, air, visited, queue, tail);
                if (y + 1 < DEPTH) tail = visit(current + plane, air, visited, queue, tail);
            }
            components++; total += tail; largest = Math.max(largest, tail);
            if (!boundary) { isolatedComponents++; isolatedVoxels += tail; }
        }
        if (total == 0 || isolatedComponents > 0 || largest < total * .95) {
            throw new AssertionError("Isolated cave components=" + isolatedComponents + ", voxels=" + isolatedVoxels);
        }
        return String.format(Locale.ROOT,
            "air_voxels=%d rock_volume=%d air_percent=%.3f components=%d largest_percent=%.3f interior_isolated_components=%d",
            total, rockCells, 100.0 * total / rockCells, components, 100.0 * largest / total, isolatedComponents);
    }

    private static int visit(int index, boolean[] air, boolean[] visited, int[] queue, int tail) {
        if (air[index] && !visited[index]) { visited[index] = true; queue[tail++] = index; }
        return tail;
    }

    private static void cavePlan(boolean[] air) throws Exception {
        int scale = 4;
        var image = new BufferedImage(SIZE * scale, SIZE * scale, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < SIZE; z++) for (int x = 0; x < SIZE; x++) {
            boolean tunnel = false;
            for (int y = -390; y <= -350; y++) tunnel |= air[((y - TunnelCarver.MIN_Y) * SIZE + z) * SIZE + x];
            int color = tunnel ? 0x63c5ba : 0x141d25;
            for (int dz = 0; dz < scale; dz++) for (int dx = 0; dx < scale; dx++) image.setRGB(x * scale + dx, z * scale + dz, color);
        }
        Files.createDirectories(Path.of("build/reports"));
        ImageIO.write(image, "png", Path.of("build/reports/cave-plan.png").toFile());
    }

    private static void surfaceMap(StringBuilder report) throws Exception {
        var shape = new TerrainShape(123456789);
        int size = 768, min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        long sum = 0, steep = 0, clamped = 0;
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
            int wx = x - size / 2, wz = z - size / 2;
            int y = shape.surface(wx, wz);
            min = Math.min(min, y); max = Math.max(max, y); sum += y;
            if (y == -348 || y == -270) clamped++;
            int dx = shape.surface(wx + 1, wz) - y, dz = shape.surface(wx, wz + 1) - y;
            if (Math.abs(dx) > 4 || Math.abs(dz) > 4) steep++;
            int shade = Math.clamp(150 + dx * 22 + dz * 14, 0, 255);
            int elevation = Math.clamp((y + 348) * 3, 0, 255);
            image.setRGB(x, z, (shade << 16) | ((shade * 3 / 4 + elevation / 4) << 8) | (shade * 2 / 3));
        }
        Files.createDirectories(Path.of("build/reports"));
        ImageIO.write(image, "png", Path.of("build/reports/surface-relief.png").toFile());
        report.append(String.format(Locale.ROOT, "surface_768x768 min=%d max=%d mean=%.3f steep_columns=%d clamped_columns=%d%n",
            min, max, sum / (double) (size * size), steep, clamped));
    }
}
