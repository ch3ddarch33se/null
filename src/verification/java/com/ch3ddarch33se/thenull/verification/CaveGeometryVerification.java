package com.ch3ddarch33se.thenull.verification;

import com.ch3ddarch33se.thenull.worldgen.TunnelCarver;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;

/** Checks physical caps and two-block player floor paths in the carved voxels. */
final class CaveGeometryVerification {
    static String verify(boolean[] air, List<TunnelCarver.CavePath> paths, int x0, int z0, int size) {
        List<TunnelCarver.CavePath> ends = deadEnds(air, paths, x0, z0, size);
        long shortPaths = paths.stream().filter(p -> p.length() < 24).count();
        long longPaths = paths.stream().filter(p -> p.length() > 100).count();
        long widePaths = paths.stream().filter(p -> p.maxWidth() > 6).count();
        if ((size >= 240 && ends.isEmpty()) || shortPaths == 0 || longPaths == 0 || widePaths == 0) {
            throw new AssertionError("Cave variety missing: caps=" + ends.size() + ", short=" + shortPaths
                + ", long=" + longPaths + ", wide=" + widePaths);
        }
        int[] walk = walkable(air, size);
        if (walk[1] < 64) throw new AssertionError("No sufficiently long connected walking route: " + walk[1]);
        long shallow = 0, deep = 0, shallowVolume = 0, deepVolume = 0;
        int plane = size * size;
        for (int i = 0; i < air.length; i++) {
            int y = i / plane + TunnelCarver.MIN_Y;
            if (y >= -530 && y < -385) { shallowVolume++; if (air[i]) shallow++; }
            if (y >= -750 && y < -570) { deepVolume++; if (air[i]) deep++; }
        }
        return String.format(Locale.ROOT,
            "verified_physical_dead_ends=%d short_paths=%d long_paths_over_100=%d wide_paths_over_6=%d longest_path=%.1f standing_voxels=%d longest_walk_extent=%d air_within_2_of_player_route=%.2f%% air_within_8=%.2f%% air_beyond_8=%.2f%% shallow_air_percent=%.3f deep_air_percent=%.3f",
            ends.size(), shortPaths, longPaths, widePaths, paths.stream().mapToDouble(TunnelCarver.CavePath::length).max().orElseThrow(),
            walk[0], walk[1], walk[3]/100.0, walk[4]/100.0, walk[5]/100.0,
            100.0 * shallow / shallowVolume, 100.0 * deep / deepVolume);
    }

    static List<TunnelCarver.CavePath> deadEnds(boolean[] air, List<TunnelCarver.CavePath> paths, int x0, int z0, int size) {
        var result = new ArrayList<TunnelCarver.CavePath>();
        for (var path : paths) {
            var end = path.end();
            if (path.kind() != TunnelCarver.Kind.BRANCH || end.y() >= -385 || end.y() < -745) continue;
            int x = (int) Math.floor(end.x()) - x0, z = (int) Math.floor(end.z()) - z0;
            if (x < 10 || z < 10 || x >= size - 10 || z >= size - 10) continue;
            if (sealedCap(air, x0, z0, size, path)) result.add(path);
        }
        return result;
    }

    private static boolean sealedCap(boolean[] air, int x0, int z0, int size, TunnelCarver.CavePath path) {
        int radius = 8, side = radius * 2 + 1, plane = size * size;
        var end = path.end(); var direction = path.endDirection();
        double norm = Math.sqrt(direction.x()*direction.x() + direction.y()*direction.y() + direction.z()*direction.z());
        if (norm < .01) return false;
        int wx = (int) Math.floor(end.x()), wy = (int) Math.floor(end.y()), wz = (int) Math.floor(end.z());
        boolean[] visited = new boolean[side * side * side];
        int[] queue = new int[visited.length];
        int head = 0, tail = 1, exits = 0;
        int center = (radius * side + radius) * side + radius;
        int origin = ((wy - TunnelCarver.MIN_Y) * size + wz - z0) * size + wx - x0;
        if (!air[origin]) return false;
        queue[0] = center; visited[center] = true;
        while (head < tail) {
            int p = queue[head++], x = p % side, z = p / side % side, y = p / (side * side);
            int dx = x-radius, dy = y-radius, dz = z-radius;
            if (x == 0 || z == 0 || y == 0 || x == side-1 || z == side-1 || y == side-1) {
                exits++;
                // A real cap has reachable boundary air only behind its final
                // tangent. Any forward/lateral escape invalidates this probe.
                if ((dx*direction.x()+dy*direction.y()+dz*direction.z()) / norm > -3) return false;
            }
            for (int[] delta : NEIGHBOURS) {
                int nx = x+delta[0], ny = y+delta[1], nz = z+delta[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= side || ny >= side || nz >= side) continue;
                int next = (ny * side + nz) * side + nx;
                int worldIndex = (((wy+ny-radius)-TunnelCarver.MIN_Y)*size + wz+nz-radius-z0)*size + wx+nx-radius-x0;
                if (!visited[next] && air[worldIndex]) { visited[next] = true; queue[tail++] = next; }
            }
        }
        return exits > 0;
    }

    private static final int[][] NEIGHBOURS = {{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0},{0,-1,0}};

    private static int[] walkable(boolean[] air, int size) {
        int plane = size * size, depth = air.length / plane;
        boolean[] floor = new boolean[air.length], visited = new boolean[air.length];
        int standing = 0, extent = 0;
        for (int y = 1; y < depth-2; y++) for (int i = y*plane; i < (y+1)*plane; i++) {
            if (air[i] && air[i+plane] && !air[i-plane]) { floor[i] = true; standing++; }
        }
        int[] queue = new int[standing];
        for (int start = 0; start < floor.length; start++) {
            if (!floor[start] || visited[start]) continue;
            int head = 0, tail = 1, minX = size, maxX = 0, minZ = size, maxZ = 0;
            queue[0] = start; visited[start] = true;
            while (head < tail) {
                int current = queue[head++], x = current % size, z = current / size % size, y = current / plane;
                minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
                for (int[] delta : NEIGHBOURS) {
                    if (delta[1] != 0) continue;
                    int nx=x+delta[0],nz=z+delta[2];
                    if(nx<0||nz<0||nx>=size||nz>=size)continue;
                    for(int dy=-1;dy<=1;dy++) {
                        int ny=y+dy;if(ny<1||ny>=depth-2)continue;
                        int next=(ny*size+nz)*size+nx;
                        if(!floor[next]||visited[next])continue;
                        if(dy==1&&!air[current+2*plane])continue;
                        if(dy==-1&&!air[next+2*plane])continue;
                        visited[next]=true;queue[tail++]=next;
                    }
                }
            }
            extent=Math.max(extent,Math.max(maxX-minX,maxZ-minZ));
        }
        int open=0;
        for(boolean value:air)if(value)open++;
        byte[] distance=new byte[air.length];Arrays.fill(distance,(byte)-1);
        int[] routeQueue=new int[open];int head=0,tail=0;
        for(int i=0;i<floor.length;i++)if(floor[i]&&visited[i]) {distance[i]=0;routeQueue[tail++]=i;}
        while(head<tail) {
            int current=routeQueue[head++],d=distance[current];
            if(d>=8)continue;
            int x=current%size,z=current/size%size,y=current/plane;
            if(x>0)tail=distanceVisit(current-1,air,distance,routeQueue,tail,d);
            if(x+1<size)tail=distanceVisit(current+1,air,distance,routeQueue,tail,d);
            if(z>0)tail=distanceVisit(current-size,air,distance,routeQueue,tail,d);
            if(z+1<size)tail=distanceVisit(current+size,air,distance,routeQueue,tail,d);
            if(y>0)tail=distanceVisit(current-plane,air,distance,routeQueue,tail,d);
            if(y+1<depth)tail=distanceVisit(current+plane,air,distance,routeQueue,tail,d);
        }
        long within2=0,within8=0,beyond8=0;
        for(int i=0;i<air.length;i++)if(air[i]) {
            int d=distance[i];if(d>=0&&d<=2)within2++;
            if(d>=0)within8++;else beyond8++;
        }
        return new int[] {standing,extent,(int)open,
            (int)Math.round(10000.0*within2/open),(int)Math.round(10000.0*within8/open),
            (int)Math.round(10000.0*beyond8/open)};
    }

    private static int distanceVisit(int index,boolean[] air,byte[] distance,int[] queue,int tail,int d) {
        if(air[index]&&distance[index]==-1) {distance[index]=(byte)(d+1);queue[tail++]=index;}
        return tail;
    }
}
