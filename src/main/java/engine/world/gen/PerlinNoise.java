package engine.world.gen;

import java.util.Random;

public class PerlinNoise {
    
    private final int[] p = new int[512];
    
    public PerlinNoise(long seed) {
        int[] perm = new int[256];
        for (int i = 0; i < 256; i++) {
            perm[i] = i;
        }
        
        Random r = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = perm[i];
            perm[i] = perm[j];
            perm[j] = t;
        }
        
        for (int i = 0; i < 512; i++) {
            p[i] = perm[i & 255];
        }
    }
    
    private double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
    
    private double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }
    
    private double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
    
    public double noise3(double x, double y, double z) {
        int X = (int) Math.floor(x) & 255;
        int Y = (int) Math.floor(y) & 255;
        int Z = (int) Math.floor(z) & 255;
        
        x -= Math.floor(x);
        y -= Math.floor(y);
        z -= Math.floor(z);
        
        double u = fade(x);
        double v = fade(y);
        double w = fade(z);
        
        int A = p[X] + Y;
        int AA = p[A] + Z;
        int AB = p[A + 1] + Z;
        int B = p[X + 1] + Y;
        int BA = p[B] + Z;
        int BB = p[B + 1] + Z;
        
        return lerp(w,
            lerp(v, 
                lerp(u, grad(p[AA], x, y, z), grad(p[BA], x - 1, y, z)),
                lerp(u, grad(p[AB], x, y - 1, z), grad(p[BB], x - 1, y - 1, z))),
            lerp(v,
                lerp(u, grad(p[AA + 1], x, y, z - 1), grad(p[BA + 1], x - 1, y, z - 1)),
                lerp(u, grad(p[AB + 1], x, y - 1, z - 1), grad(p[BB + 1], x - 1, y - 1, z - 1))));
    }
    
    public double noise2(double x, double y) {
        return noise3(x, y, 0);
    }
}
