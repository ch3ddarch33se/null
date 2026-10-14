package com.ch3ddarch33se.thenull.verification;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import javax.imageio.ImageIO;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/** Regional survey of the real compiled native density, before cave carving. */
final class SurfaceGeometryVerification {
    static String run(RandomState random, NoiseGeneratorSettings settings, Path output, Properties sites) throws Exception {
        int size = 129, origin = -2048, spacing = 32;
        var sampler = random.getSampler(settings.noiseRouter().finalDensity());
        int[] heights = new int[size * size];
        int min = 0, max = -768, low = 0, high = 0;
        long sum = 0;
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
            int index = z * size + x, top = -352;
            for (int y = -270; y >= -352; y--) {
                if (sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, origin + x * spacing, y, origin + z * spacing) > 0) {
                    top = y; break;
                }
            }
            heights[index] = top; sum += top;
            if (top < min) { min = top; low = index; }
            if (top > max) { max = top; high = index; }
        }
        int flat = 0, rolling = 0, steep = 0;
        var image = new BufferedImage(size * 4, size * 4, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
            int h = heights[z * size + x];
            int dx = heights[z * size + Math.min(size-1,x+1)] - h;
            int dz = heights[Math.min(size-1,z+1) * size + x] - h;
            int slope = Math.max(Math.abs(dx),Math.abs(dz));
            if(slope<=1)flat++;else if(slope<=8)rolling++;else steep++;
            int shade = Math.clamp(120 + dx*6 + dz*4,25,220);
            int elevation = Math.clamp((h+352)*3,0,255);
            int color = (shade/3 << 16) | ((shade*2/3+elevation/5) << 8) | Math.min(255,shade+elevation/6);
            for(int iz=0;iz<4;iz++)for(int ix=0;ix<4;ix++)image.setRGB(x*4+ix,z*4+iz,color);
        }
        ImageIO.write(image,"png",output.resolve("null-surface-native.png").toFile());
        site(sites,"surface_low",origin+(low%size)*spacing,heights[low]+18,origin+(low/size)*spacing);
        site(sites,"surface_high",origin+(high%size)*spacing,heights[high]+18,origin+(high/size)*spacing);
        if (max-min < 35 || flat == 0 || rolling == 0 || steep == 0) {
            throw new AssertionError("Surface lacks regional variety: range="+min+".."+max+", flat="+flat+", rolling="+rolling+", steep="+steep);
        }
        return String.format(Locale.ROOT,"PASSED: native 4096x4096 survey at 32-block spacing; columns=%d range=%d..%d mean=%.3f flat_neighbours=%d rolling_neighbours=%d steep_neighbours=%d; geometry-only preview=%s",
            heights.length,min,max,sum/(double)heights.length,flat,rolling,steep,output.resolve("null-surface-native.png"));
    }

    static void site(Properties sites,String name,double x,double y,double z) {
        sites.setProperty(name+".x",Double.toString(x));sites.setProperty(name+".y",Double.toString(y));sites.setProperty(name+".z",Double.toString(z));
        sites.setProperty(name+".yaw","45");sites.setProperty(name+".pitch","35");
    }
}
