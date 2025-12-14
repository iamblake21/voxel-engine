package engine.utils;

/**
 * 3D math utilities - vectors, matrices, etc.
 */
public class Math3D {

    /**
     * 4x4 Matrix for transformations
     */
    public static class Mat4 {
        public final float[] m = new float[16];

        public float[] toArray() {
            return m;
        }

        public static Mat4 ortho(float left, float right,
                float bottom, float top,
                float near, float far) {
            Mat4 r = new Mat4();

            r.m[0] = 2.0f / (right - left);
            r.m[5] = 2.0f / (top - bottom);
            r.m[10] = -2.0f / (far - near);
            r.m[15] = 1.0f;

            r.m[12] = -(right + left) / (right - left);
            r.m[13] = -(top + bottom) / (top - bottom);
            r.m[14] = -(far + near) / (far - near);

            return r;
        }

        public static Mat4 mul(Mat4 a, Mat4 b) {
            Mat4 r = new Mat4();
            for (int col = 0; col < 4; col++) {
                for (int row = 0; row < 4; row++) {
                    r.m[col * 4 + row] = a.m[0 * 4 + row] * b.m[col * 4 + 0] +
                            a.m[1 * 4 + row] * b.m[col * 4 + 1] +
                            a.m[2 * 4 + row] * b.m[col * 4 + 2] +
                            a.m[3 * 4 + row] * b.m[col * 4 + 3];
                }
            }
            return r;
        }

        public Mat4() {
        }

        public static Mat4 identity() {
            Mat4 r = new Mat4();
            r.m[0] = r.m[5] = r.m[10] = r.m[15] = 1;
            return r;
        }

        public static Mat4 perspective(float fov, float aspect, float near, float far) {
            Mat4 r = new Mat4();
            float f = (float) (1.0 / Math.tan(Math.toRadians(fov) / 2));
            r.m[0] = f / aspect;
            r.m[5] = f;
            r.m[10] = (far + near) / (near - far);
            r.m[11] = -1;
            r.m[14] = (2 * far * near) / (near - far);
            return r;
        }

        public static Mat4 lookAt(float px, float py, float pz,
                float cx, float cy, float cz,
                float ux, float uy, float uz) {
            float zx = px - cx, zy = py - cy, zz = pz - cz;
            float zl = (float) Math.sqrt(zx * zx + zy * zy + zz * zz);
            zx /= zl;
            zy /= zl;
            zz /= zl;

            float xx = uy * zz - uz * zy;
            float xy = uz * zx - ux * zz;
            float xz = ux * zy - uy * zx;
            float xl = (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
            xx /= xl;
            xy /= xl;
            xz /= xl;

            float yx = zy * xz - zz * xy;
            float yy = zz * xx - zx * xz;
            float yz = zx * xy - zy * xx;

            Mat4 r = new Mat4();
            r.m[0] = xx;
            r.m[4] = xy;
            r.m[8] = xz;
            r.m[1] = yx;
            r.m[5] = yy;
            r.m[9] = yz;
            r.m[2] = zx;
            r.m[6] = zy;
            r.m[10] = zz;
            r.m[12] = -(xx * px + xy * py + xz * pz);
            r.m[13] = -(yx * px + yy * py + yz * pz);
            r.m[14] = -(zx * px + zy * py + zz * pz);
            r.m[15] = 1;
            return r;
        }

        public static Mat4 translate(float x, float y, float z) {
            Mat4 r = identity();
            r.m[12] = x;
            r.m[13] = y;
            r.m[14] = z;
            return r;
        }

        public static Mat4 scale(float x, float y, float z) {
            Mat4 r = identity();
            r.m[0] = x;
            r.m[5] = y;
            r.m[10] = z;
            return r;
        }
    }

    /**
     * 3D Vector
     */
    public static class Vec3i {
        public int x, y, z;

        public Vec3i(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public String toString() {
            return "(" + x + ", " + y + ", " + z + ")";
        }
    }

    public static class Vec3 {
        public float x, y, z;

        public Vec3() {
        }

        public Vec3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Vec3 add(Vec3 other) {
            return new Vec3(x + other.x, y + other.y, z + other.z);
        }

        public Vec3 sub(Vec3 other) {
            return new Vec3(x - other.x, y - other.y, z - other.z);
        }

        public Vec3 mul(float scalar) {
            return new Vec3(x * scalar, y * scalar, z * scalar);
        }

        public float length() {
            return (float) Math.sqrt(x * x + y * y + z * z);
        }

        public Vec3 normalize() {
            float len = length();
            if (len > 0)
                return new Vec3(x / len, y / len, z / len);
            return new Vec3(0, 0, 0);
        }

        public float dot(Vec3 other) {
            return x * other.x + y * other.y + z * other.z;
        }

        public Vec3 cross(Vec3 other) {
            return new Vec3(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x);
        }

        public Vec3 copy() {
            return new Vec3(x, y, z);
        }
    }

    // ======================================================================================
    // NUOVA CLASSE AABB (Axis Aligned Bounding Box) PER LA FISICA
    // ======================================================================================

    public static class AABB {
        public float minX, minY, minZ;
        public float maxX, maxY, maxZ;

        public AABB(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            this.set(minX, minY, minZ, maxX, maxY, maxZ);
        }

        // Helper per creare da centro
        public static AABB fromCenterAndSize(float cx, float cy, float cz, float hw, float hh) {
            return new AABB(cx - hw, cy, cz - hw, cx + hw, cy + hh, cz + hw);
        }

        public void set(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public float getWidth() {
            return maxX - minX;
        }

        public float getHeight() {
            return maxY - minY;
        }

        public float getDepth() {
            return maxZ - minZ;
        }

        public float getCenterX() {
            return minX + (getWidth() / 2f);
        }

        public float getCenterY() {
            return minY + (getHeight() / 2f);
        }

        public float getCenterZ() {
            return minZ + (getDepth() / 2f);
        }

        public AABB expand(float dx, float dy, float dz) {
            float nx0 = minX, ny0 = minY, nz0 = minZ;
            float nx1 = maxX, ny1 = maxY, nz1 = maxZ;
            if (dx < 0)
                nx0 += dx;
            else
                nx1 += dx;
            if (dy < 0)
                ny0 += dy;
            else
                ny1 += dy;
            if (dz < 0)
                nz0 += dz;
            else
                nz1 += dz;
            return new AABB(nx0, ny0, nz0, nx1, ny1, nz1);
        }

        public boolean intersects(AABB other) {
            return this.minX < other.maxX && this.maxX > other.minX &&
                    this.minY < other.maxY && this.maxY > other.minY &&
                    this.minZ < other.maxZ && this.maxZ > other.minZ;
        }

        // Calcolo offset collisioni (stile Minecraft)
        public float calculateXOffset(AABB other, float offsetX) {
            if (other.maxY > this.minY && other.minY < this.maxY && other.maxZ > this.minZ && other.minZ < this.maxZ) {
                if (offsetX > 0 && other.maxX <= this.minX) {
                    float d = this.minX - other.maxX;
                    if (d < offsetX)
                        offsetX = d;
                } else if (offsetX < 0 && other.minX >= this.maxX) {
                    float d = this.maxX - other.minX;
                    if (d > offsetX)
                        offsetX = d;
                }
            }
            return offsetX;
        }

        public float calculateYOffset(AABB other, float offsetY) {
            if (other.maxX > this.minX && other.minX < this.maxX && other.maxZ > this.minZ && other.minZ < this.maxZ) {
                if (offsetY > 0 && other.maxY <= this.minY) {
                    float d = this.minY - other.maxY;
                    if (d < offsetY)
                        offsetY = d;
                } else if (offsetY < 0 && other.minY >= this.maxY) {
                    float d = this.maxY - other.minY;
                    if (d > offsetY)
                        offsetY = d;
                }
            }
            return offsetY;
        }

        public float calculateZOffset(AABB other, float offsetZ) {
            if (other.maxX > this.minX && other.minX < this.maxX && other.maxY > this.minY && other.minY < this.maxY) {
                if (offsetZ > 0 && other.maxZ <= this.minZ) {
                    float d = this.minZ - other.maxZ;
                    if (d < offsetZ)
                        offsetZ = d;
                } else if (offsetZ < 0 && other.minZ >= this.maxZ) {
                    float d = this.maxZ - other.minZ;
                    if (d > offsetZ)
                        offsetZ = d;
                }
            }
            return offsetZ;
        }
    }

    // ======================================================================================

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }

    public static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}