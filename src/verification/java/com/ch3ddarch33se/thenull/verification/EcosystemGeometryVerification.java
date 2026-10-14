package com.ch3ddarch33se.thenull.verification;

import com.ch3ddarch33se.thenull.worldgen.CaveSettings;
import com.ch3ddarch33se.thenull.worldgen.CaveSystem;
import com.ch3ddarch33se.thenull.worldgen.CavernCarver;
import com.ch3ddarch33se.thenull.worldgen.TunnelCarver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fast geometry milestone; actual native rock volume is measured on the server. */
public final class EcosystemGeometryVerification {
    public static void main(String[] args) throws Exception {
        int size=Integer.getInteger("thenull.ecosystemSize",128),origin=-size;
        var report=new StringBuilder();
        double scale=Double.parseDouble(System.getProperty("thenull.ecosystemScale","1.00"));
        var settings=new CaveSettings(scale,CaveSettings.DEFAULT.wallLichen(),CaveSettings.DEFAULT.floorLichen());
        report.append("cavern_scale=").append(scale).append('\n');
        for(long seed:new long[]{123456789,0,-1,987654321}) {
            var system=new CaveSystem(seed,settings);
            var paths=new ArrayList<TunnelCarver.CavePath>();
            var rooms=new ArrayList<CavernCarver.Room>();
            long start=System.nanoTime();
            boolean[] whole=system.carve(origin,origin,size,size,paths::add,rooms::add,List.of());
            report.append("seed=").append(seed).append(" carve_ms=").append((System.nanoTime()-start)/1e6).append(' ')
                .append(GeometryVerification.connectivity(whole.clone(),(x,z)->-300,origin,origin,size)).append('\n');
            report.append("rooms=").append(rooms.size()).append(" small=").append(rooms.stream().filter(r->r.kind()==0).count())
                .append(" medium=").append(rooms.stream().filter(r->r.kind()==1).count())
                .append(" large=").append(rooms.stream().filter(r->r.kind()==2).count())
                .append(" diameter_min=").append(String.format(java.util.Locale.ROOT,"%.1f",rooms.stream().mapToDouble(r->2*Math.max(r.rx(),r.rz())).min().orElse(0)))
                .append(" diameter_max=").append(String.format(java.util.Locale.ROOT,"%.1f",rooms.stream().mapToDouble(r->2*Math.max(r.rx(),r.rz())).max().orElse(0))).append('\n');
            report.append(CaveGeometryVerification.verify(whole,paths,origin,origin,size)).append('\n');
            long comparisons=0;
            for(int z=0;z<size;z+=16)for(int x=0;x<size;x+=16) {
                boolean[] part=system.carve(origin+x,origin+z,16,16);
                for(int y=0;y<453;y++)for(int iz=0;iz<16;iz++)for(int ix=0;ix<16;ix++) {
                    if(part[(y*16+iz)*16+ix]!=whole[(y*size+z+iz)*size+x+ix])throw new AssertionError("Seam mismatch seed="+seed);
                    comparisons++;
                }
            }
            report.append("border_comparisons=").append(comparisons).append(" mismatches=0\n");
        }
        report.append("scope = provisional constant Y=-310 underground ceiling; includes protected roof; no gap/sky; server measures actual density-defined rock volume\nautomated_result = PASSED\n");
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/ecosystem-geometry.txt"),report);
        System.out.print(report);
    }
}
