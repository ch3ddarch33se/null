package com.ch3ddarch33se.thenull.verification;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import com.ch3ddarch33se.thenull.worldgen.TunnelCarver;
import com.ch3ddarch33se.thenull.worldgen.CaveSettings;
import com.ch3ddarch33se.thenull.worldgen.CaveSystem;
import com.ch3ddarch33se.thenull.worldgen.CavernCarver;
import com.ch3ddarch33se.thenull.worldgen.CentralChamber;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/** Checks actual native terrain, not the legacy expected heightmap. */
final class NativeVerification {
    static void run(MinecraftServer server, ResourceKey<Level> key) {
        var report = new LinkedHashMap<String, String>();
        try {
            var level = server.getLevel(key);
            require(level != null, "Missing world provider");
            require(level.getMinY() == -1760 && level.getMaxY() == 255, "Incorrect dimension bounds");
            boolean standalone=level.getChunkSource().getGenerator() instanceof com.ch3ddarch33se.thenull.worldgen.LayeredChunkGenerator;
            var caveSettings=CaveSettings.load();
            var caveSystem=new CaveSystem(level.getSeed(),caveSettings);
            if(standalone) {
                require(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sift_backport"),"Sift Backport present in standalone verification");
                require(server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.Identifier.parse("sift:sift")))==null,"Unexpected legacy dimension");
                report.put("standalone_server","PASSED: native thenull:sift loaded without Sift Backport or legacy dimension");
            }
            long solid = 0, caveAir = 0;
            int min = 0, max = -1760;
            int upperMin=256,upperMax=0;
            var upperBiomes=new java.util.HashSet<String>();
            long decorations=0, materialSamples=0;
            var pos = new BlockPos.MutableBlockPos();
            for (int[] cp : new int[][] {{0,0},{1,0},{0,1},{-1,-1},{128,128},{-128,-128},{256,-256}}) {
                var chunk = level.getChunk(cp[0], cp[1]);
                int x0 = cp[0]*16, z0 = cp[1]*16;
                boolean[] caves = caveSystem.carve(x0-1,z0-1,18,18);
                for (int z=0;z<16;z++) for (int x=0;x<16;x++) {
                    int top = -1760;
                    boolean centralColumn = CentralChamber.containsColumn(x0 + x, z0 + z);
                    for (int y=-1760;y<0;y++) {
                        var state = chunk.getBlockState(pos.set(x0+x,y,z0+z));
                        if (y <= CentralChamber.BEDROCK_MAX_Y || y >= -4) require(state.is(Blocks.BEDROCK), "Bedrock hole at "+pos);
                        else if (y >= -256) require(state.isAir(), "Gap is not empty at "+pos);
                        else {
                            require(state.isAir() || state.is(Blocks.SCULK) || state.is(Blocks.GLOW_LICHEN)
                                || state.is(Blocks.TINTED_GLASS), "Unexpected lower material at "+pos);
                            if (state.is(Blocks.SCULK)) { solid++; top=y; }
                            if (y >= TunnelCarver.MIN_Y && y < -368
                                && !(centralColumn && y >= CentralChamber.AIR_MIN_Y && y <= CentralChamber.AIR_MAX_Y)) {
                                boolean expected = caves[((y+763)*18+z+1)*18+x+1];
                                boolean open=state.isAir()||state.is(Blocks.GLOW_LICHEN);
                                require(open==expected, "Deep connected graph changed at "+pos);
                                if (expected) caveAir++;
                            }
                        }
                    }
                    if (!centralColumn) {
                        require(top >= -352 && top <= -270, "Surface outside preserved envelope at "+(x0+x)+","+(z0+z)+": "+top);
                        min=Math.min(min,top); max=Math.max(max,top);
                    }
                    require(level.getBrightness(LightLayer.SKY,pos.set(x0+x,-128,z0+z))==0,"Skylight below barrier");
                    if(standalone) {
                        require(level.getBiome(pos.set(x0+x,-400,z0+z)).unwrapKey().orElseThrow().identifier().toString().equals("thenull:null_depths"),"Lower biome is not native Null depths");
                        int upperTop=0;
                        for(int y=0;y<256;y++) {
                            var state=chunk.getBlockState(pos.set(x0+x,y,z0+z));
                            if(!state.isAir())upperTop=y;
                            if(state.is(Blocks.WARPED_ROOTS)||state.is(Blocks.WARPED_FUNGUS)||state.is(Blocks.CRIMSON_ROOTS)
                                ||state.is(Blocks.AMETHYST_CLUSTER)||state.is(Blocks.SMALL_AMETHYST_BUD)||state.is(Blocks.PEARLESCENT_FROGLIGHT))decorations++;
                            if(state.is(Blocks.WARPED_NYLIUM)||state.is(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("minecraft:purple_terracotta")))||state.is(Blocks.CALCITE))materialSamples++;
                        }
                        require(upperTop>28,"Upper world missing normal underground rock");
                        upperMin=Math.min(upperMin,upperTop);upperMax=Math.max(upperMax,upperTop);
                        upperBiomes.add(level.getBiome(pos.set(x0+x,upperTop,z0+z)).unwrapKey().orElseThrow().identifier().toString());
                    }
                }
            }
            if (standalone) verifyCentralChamber(level);
            require(max-min>8, "Flat lower surface");
            report.put("native_geology", "PASSED: actual chunks; surface range="+min+".."+max+", sculk="+solid+", deep graph air="+caveAir);
            report.put("constraints", "PASSED: bottom bedrock, continuous separator, air gap, zero gap skylight, exact deep graph voxels");
            if(standalone) {
                require(upperMax-upperMin>10,"Flat upper surface");
                require(upperBiomes.contains("thenull:tideglass_grove")&&upperBiomes.contains("thenull:prism_heath"),"Missing upper prototype biome: "+upperBiomes);
                require(decorations>0&&materialSamples>0,"Native features or material rules did not generate");
                report.put("native_upper","PASSED: range="+upperMin+".."+upperMax+", biomes="+upperBiomes+", decorations="+decorations+", surface_materials="+materialSamples);
                Path output=Path.of(System.getProperty("thenull.verificationReport","../../build/reports/verification.txt")).toAbsolutePath().getParent();
                var sites = new java.util.Properties();
                sites.setProperty("seed",Long.toString(level.getSeed()));
                var ecosystem=verifyEcosystem(level,caveSystem,report,pos,sites);
                saveCaveSites(level,ecosystem.underground(),ecosystem.paths(),ecosystem.origin(),ecosystem.size(),sites);
                var settings = level.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(
                    ResourceKey.create(Registries.NOISE_SETTINGS,Identifier.parse("thenull:null_surface"))).value();
                var random = RandomState.create(level.registryAccess().lookupOrThrow(Registries.NOISE),level.getSeed(),settings);
                report.put("native_surface_survey",SurfaceGeometryVerification.run(random,settings,output,sites));
                try(var writer=Files.newBufferedWriter(output.resolve("terrain-inspection.properties"))) {
                    sites.store(writer,"Verified cave caps and regional density survey; fresh seed 123456789 only");
                }
            }
            report.putAll(TeleportVerification.run(server));
            report.put("block_timers", com.ch3ddarch33se.thenull.BlockTimerVerification.run(level));
            report.put("native_density", verifyDensity(level));
            report.put("automated_result", "PASSED");
        } catch (Throwable e) {
            report.put("automated_result", "FAILED");
            report.put("error",e.toString());
            e.printStackTrace();
        } finally {
            try {
                Path output=Path.of(System.getProperty("thenull.verificationReport","../../build/reports/verification.txt"));
                Files.createDirectories(output.toAbsolutePath().getParent());
                var text=new StringBuilder();
                report.forEach((k,v)->text.append(k).append(" = ").append(v).append('\n'));
                Files.writeString(output,text);
                System.out.print(text);
            } catch(Exception e) { throw new RuntimeException(e); }
            finally { server.halt(false); }
        }
    }

    private static void require(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }

    private static void verifyCentralChamber(ServerLevel level) {
        level.getChunk(0, 0);
        level.getChunk(350 >> 4, 0);
        for (int y = CentralChamber.MIN_Y; y <= CentralChamber.BEDROCK_MAX_Y; y++) {
            require(level.getBlockState(new BlockPos(0, y, 0)).is(Blocks.BEDROCK), "Bottom bedrock boundary is open at Y=" + y);
        }
        require(level.getBlockState(new BlockPos(0, CentralChamber.FLOOR_Y, 0)).is(Blocks.SCULK), "Central chamber floor is not solid Sculk");
        require(level.getBlockState(new BlockPos(0, -1700, 0)).isAir(), "Central chamber did not carve its lower volume");
        require(level.getBlockState(new BlockPos(0, -600, 0)).isAir(), "Central chamber did not reach the Null surface");
        require(level.getBlockState(new BlockPos(0, -1460, 0)).is(Blocks.TINTED_GLASS), "Energy Orb lower tip is missing");
        require(level.getBlockState(new BlockPos(0, -880, 0)).isAir(), "Energy Orb incorrectly filled its interior");
        require(level.getBlockState(new BlockPos(350, -880, 0)).is(Blocks.TINTED_GLASS), "Energy Orb does not fill the chamber width");
        require(level.getBlockState(new BlockPos(0, -300, 0)).is(Blocks.TINTED_GLASS), "Energy Orb upper tip is missing");
        require(level.getBlockState(new BlockPos(351, -880, 0)).is(Blocks.SCULK), "Chamber exceeded its 700-block diameter");
    }

    private record EcosystemSample(boolean[] underground,java.util.List<TunnelCarver.CavePath> paths,int origin,int size) { }

    private static EcosystemSample verifyEcosystem(ServerLevel level,CaveSystem caves,LinkedHashMap<String,String> report,
        BlockPos.MutableBlockPos pos,java.util.Properties sites) {
        final int size=240,origin=400,depth=TunnelCarver.MAX_Y-TunnelCarver.MIN_Y+1,plane=size*size;
        boolean[] actual=new boolean[plane*depth];int[] undergroundCeiling=new int[plane];
        long lichen=0,wallLichen=0,floorLichen=0,checkedFaces=0,brightLichen=0;
        BlockPos litSource=null,litLanding=null;int brightestLanding=0,validated=0;
        for(int cz=0;cz<size;cz+=16)for(int cx=0;cx<size;cx+=16) {
            var chunk=level.getChunk((origin+cx)/16,(origin+cz)/16);
            for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
                int wx=origin+cx+x,wz=origin+cz+z,column=(cz+z)*size+cx+x,top=-1760;
                for(int y=-368;y<=-270;y++)if(chunk.getBlockState(pos.set(wx,y,wz)).is(Blocks.SCULK))top=y;
                require(top>=-352&&top<=-270,"Lower surface not found in ecosystem sample at "+wx+","+wz+": "+top);
                undergroundCeiling[column]=top-10;
                for(int y=TunnelCarver.MIN_Y;y<=TunnelCarver.MAX_Y;y++) {
                    var state=chunk.getBlockState(pos.set(wx,y,wz));
                    int index=((y-TunnelCarver.MIN_Y)*size+cz+z)*size+cx+x;
                    actual[index]=state.isAir()||state.is(Blocks.GLOW_LICHEN);
                    if(y>=CavernCarver.FLOOR&&y<=CavernCarver.CEILING&&state.is(Blocks.GLOW_LICHEN)) {
                        lichen++;
                        if(state.getValue(MultifaceBlock.getFaceProperty(net.minecraft.core.Direction.DOWN)))floorLichen++;
                        else wallLichen++;
                        if((lichen&127)==1) {
                            boolean supported=false;
                            for(var face:net.minecraft.core.Direction.values()) {
                                var property=MultifaceBlock.getFaceProperty(face);
                                if(!state.getValue(property))continue;
                                var support=level.getBlockState(pos.set(wx,y,wz).move(face));
                                require(!support.isAir()&&!support.is(Blocks.GLOW_LICHEN),"Glow Lichen face lacks solid support at "+wx+","+y+","+wz+" "+face);
                                supported=true;checkedFaces++;
                            }
                            require(supported,"Glow Lichen has no attachment faces at "+wx+","+y+","+wz);validated++;
                            int emitted=level.getBrightness(LightLayer.BLOCK,pos.set(wx,y,wz));
                            if(emitted>=6)brightLichen++;
                            if(litLanding==null&&emitted>=6) {
                                BlockPos source=new BlockPos(wx,y,wz),landing=litLanding(level,source);
                                if(landing!=null) {litSource=source;litLanding=landing;brightestLanding=level.getBrightness(LightLayer.BLOCK,landing.above());}
                            }
                        }
                    }
                }
            }
        }
        require(lichen>0,"No vanilla Glow Lichen generated in the measured cave network");
        require(validated>=100&&brightLichen>0,"Glow Lichen support or block lighting was not validated");
        require(litLanding!=null&&brightestLanding>=2,"No player-sized cave position sees nearby natural Glow Lichen light");
        sites.setProperty("lichen_view.x",Double.toString(litLanding.getX()+.5));
        sites.setProperty("lichen_view.y",Integer.toString(litLanding.getY()));
        sites.setProperty("lichen_view.z",Double.toString(litLanding.getZ()+.5));
        double dx=litSource.getX()+.5-(litLanding.getX()+.5),dz=litSource.getZ()+.5-(litLanding.getZ()+.5);
        double horizontal=Math.hypot(dx,dz),eyeY=litLanding.getY()+1.62,dy=litSource.getY()+.5-eyeY;
        sites.setProperty("lichen_view.yaw",Double.toString(Math.toDegrees(Math.atan2(-dx,dz))));
        sites.setProperty("lichen_view.pitch",Double.toString(-Math.toDegrees(Math.atan2(dy,horizontal))));

        var paths=new java.util.ArrayList<TunnelCarver.CavePath>();
        var rooms=new java.util.ArrayList<CavernCarver.Room>();
        boolean[] expected=caves.carve(origin,origin,size,size,paths::add,rooms::add,java.util.List.of());
        long mismatches=0,rock=0,undergroundAir=0;
        boolean[] underground=actual.clone();
        for(int z=0;z<size;z++)for(int x=0;x<size;x++) {
            int ceiling=undergroundCeiling[z*size+x];
            for(int y=TunnelCarver.MIN_Y;y<=CavernCarver.CEILING;y++) {
                int index=(y-TunnelCarver.MIN_Y)*plane+z*size+x;
                boolean actualAir=actual[index],expectedAir=expected[index];
                if(actualAir!=expectedAir)mismatches++;
                if(y<ceiling) {rock++;if(actualAir)undergroundAir++;}
            }
            for(int y=ceiling-TunnelCarver.MIN_Y;y<depth;y++)underground[y*plane+z*size+x]=false;
        }
        require(mismatches==0,"Native chunks differ from deterministic cave/carver mask in "+mismatches+" deep voxels");
        report.put("native_cave_volume","PASSED: "+GeometryVerification.connectivity(underground.clone(),
            (x,z)->undergroundCeiling[(z-origin)*size+x-origin],origin,origin,size)+"; exact native mask matches="+(size*(long)size*(CavernCarver.CEILING-TunnelCarver.MIN_Y+1)-mismatches));
        report.put("native_player_navigation",CaveGeometryVerification.verify(underground,paths,origin,origin,size));
        report.put("native_cavern_distribution","rooms="+rooms.size()+", small="+rooms.stream().filter(r->r.kind()==0).count()
            +", medium="+rooms.stream().filter(r->r.kind()==1).count()+", large="+rooms.stream().filter(r->r.kind()==2).count()
            +", cave_core_air_percent="+String.format(java.util.Locale.ROOT,"%.3f",100.0*undergroundAir/rock));
        report.put("native_glow_lichen","PASSED: blocks="+lichen+", wall="+wallLichen+", floor="+floorLichen
            +", sampled_valid_faces="+validated+", supported_faces="+checkedFaces+", samples_with_light_level_6_plus="+brightLichen
            +", safe_player_landing_light="+brightestLanding+", lichen_site="+litSource+", no night vision required for light query");
        return new EcosystemSample(underground,paths,origin,size);
    }

    private static BlockPos litLanding(ServerLevel level,BlockPos source) {
        for(int radius=1;radius<=8;radius++)for(int y=-2;y<=2;y++)
            for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++) {
                if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
                var feet=source.offset(dx,y,dz);var floor=feet.below();
                if(!level.getBlockState(feet).isAir()||!level.getBlockState(feet.above()).isAir()
                    ||!level.getBlockState(floor).is(Blocks.SCULK)
                    ||!level.getBlockState(feet).getFluidState().isEmpty())continue;
                if(level.getBrightness(LightLayer.BLOCK,feet.above())>=3)return feet.immutable();
            }
        return null;
    }

    private static void saveCaveSites(ServerLevel level, boolean[] air, java.util.List<TunnelCarver.CavePath> paths,
        int origin, int size, java.util.Properties sites) {
        var order = java.util.Comparator.comparingDouble((TunnelCarver.CavePath p) -> p.end().x()*p.end().x()+p.end().z()*p.end().z());
        var ends=CaveGeometryVerification.deadEnds(air,paths,origin,origin,size);
        ends.sort(order);
        require(ends.stream().anyMatch(p -> caveSite(level,p,"dead_end",sites,true)),"No safe verified dead-end inspection site");
        var wide=paths.stream().filter(p -> p.maxWidth()>6 && p.end().y()<-385
            && p.end().x()>origin+10 && p.end().x()<origin+size-10
            && p.end().z()>origin+10 && p.end().z()<origin+size-10).sorted(order).toList();
        require(wide.stream().anyMatch(p -> caveSite(level,p,"wide_passage",sites,true)),"No safe wide inspection site");
        require(wide.stream().anyMatch(p -> caveSite(level,p,"junction",sites,false)),"No safe junction inspection site");
    }

    private static boolean caveSite(ServerLevel level, TunnelCarver.CavePath path, String name, java.util.Properties sites, boolean end) {
        var direction=path.endDirection();
        double norm=Math.sqrt(direction.x()*direction.x()+direction.y()*direction.y()+direction.z()*direction.z());
        var point=end ? path.end() : path.start();
        int px=(int)Math.floor(point.x()-(end ? direction.x()/norm*4 : 0));
        int pz=(int)Math.floor(point.z()-(end ? direction.z()/norm*4 : 0));
        for(int radius=0;radius<=3;radius++)for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++) {
            for(int y=(int)Math.floor(point.y())+4;y>point.y()-7;y--) {
                var feet=new BlockPos(px+dx,y,pz+dz);
                if(level.getBlockState(feet).isAir()&&level.getBlockState(feet.above()).isAir()&&level.getBlockState(feet.below()).is(Blocks.SCULK)) {
                    SurfaceGeometryVerification.site(sites,name,feet.getX()+.5,feet.getY(),feet.getZ()+.5);
                    double vx=end ? direction.x() : path.end().x()-point.x();
                    double vz=end ? direction.z() : path.end().z()-point.z();
                    sites.setProperty(name+".yaw",Double.toString(Math.toDegrees(Math.atan2(-vx,vz))));
                    sites.setProperty(name+".pitch","0");
                    return true;
                }
            }
        }
        return false;
    }

    private static String verifyDensity(ServerLevel level) {
        var registry = level.registryAccess();
        NoiseGeneratorSettings lower = registry.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(
            ResourceKey.create(Registries.NOISE_SETTINGS, Identifier.parse("thenull:null_surface"))).value();
        NoiseGeneratorSettings upper = registry.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(
            ResourceKey.create(Registries.NOISE_SETTINGS, Identifier.parse("thenull:upper"))).value();
        RandomState lowerRandom = RandomState.create(registry.lookupOrThrow(Registries.NOISE), level.getSeed(), lower);
        RandomState upperRandom = RandomState.create(registry.lookupOrThrow(Registries.NOISE), level.getSeed(), upper);
        var actualClimate = level.getChunkSource().randomState().createClimateSampler(
            net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED);
        var expectedClimate = upperRandom.createClimateSampler(
            net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED);
        long samples = 0;
        int lowerOverhangs = 0, upperOverhangs = 0;
        var pos = new BlockPos.MutableBlockPos();
        for (int[] cp : new int[][] {{0,0},{1,0},{0,1},{-1,-1},{128,128},{-128,-128},{256,-256}}) {
            var chunk = level.getChunk(cp[0], cp[1]);
            int x0 = cp[0] * 16, z0 = cp[1] * 16;
            for (int y : new int[] {-400, 100}) {
                require(actualClimate.sample(Math.floorDiv(x0,4),Math.floorDiv(y,4),Math.floorDiv(z0,4))
                    .equals(expectedClimate.sample(Math.floorDiv(x0,4),Math.floorDiv(y,4),Math.floorDiv(z0,4))),
                    "Vanilla climate queries use the wrong provider router");
            }
            boolean[] caves = new TunnelCarver(level.getSeed()).carve(x0, z0, 16, 16);
            for (int x : new int[] {0,8,15}) for (int z : new int[] {0,8,15}) {
                for (int y = -368; y < -256; y++) {
                    if (CentralChamber.containsColumn(x0 + x, z0 + z)
                        && y >= CentralChamber.AIR_MIN_Y && y <= CentralChamber.AIR_MAX_Y) continue;
                    float density = lowerRandom.sampleBlockValueUncached(lower.noiseRouter().finalDensity(), x0+x, y, z0+z);
                    boolean expectedAir = density <= 0 || caves[((y - TunnelCarver.MIN_Y) * 16 + z) * 16 + x];
                    require(chunk.getBlockState(pos.set(x0+x, y, z0+z)).isAir() == expectedAir,
                        "Actual block disagrees with native density and connected graph at " + pos);
                    samples++;
                }
                if (rockIntervals(lowerRandom, lower.noiseRouter().finalDensity(), x0+x, z0+z, -352, -269) > 1) lowerOverhangs++;
                if (rockIntervals(upperRandom, upper.noiseRouter().finalDensity(), x0+x, z0+z, 0, 256) > 1) upperOverhangs++;
            }
        }
        return "PASSED: 14 vanilla climate-query comparisons and " + samples + " independent native density/block comparisons; multiple rock intervals in "
            + lowerOverhangs + " lower and " + upperOverhangs + " upper columns of 63 sampled (pre-carving)";
    }

    private static int rockIntervals(RandomState random, DensityFunction density, int x, int z, int bottom, int top) {
        int intervals = 0;
        boolean wasRock = false;
        for (int y = bottom; y < top; y++) {
            boolean rock = random.sampleBlockValueUncached(density, x, y, z) > 0;
            if (rock && !wasRock) intervals++;
            wasRock = rock;
        }
        return intervals;
    }
}
