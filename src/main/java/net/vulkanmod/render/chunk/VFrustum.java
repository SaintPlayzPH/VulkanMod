package net.vulkanmod.render.chunk;

import net.minecraft.world.phys.AABB;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class VFrustum {
    private Vector4f viewVector = new Vector4f();
    private double camX;
    private double camY;
    private double camZ;

    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Matrix4f matrix = new Matrix4f();

    public VFrustum offsetToFullyIncludeCameraCube(int offset) {
        double d0 = Math.floor(this.camX / (double) offset) * (double) offset;
        double d1 = Math.floor(this.camY / (double) offset) * (double) offset;
        double d2 = Math.floor(this.camZ / (double) offset) * (double) offset;
        double d3 = Math.ceil(this.camX / (double) offset) * (double) offset;
        double d4 = Math.ceil(this.camY / (double) offset) * (double) offset;
        double d5 = Math.ceil(this.camZ / (double) offset) * (double) offset;

        while (this.intersectAab(d0 - this.camX, d1 - this.camY, d2 - this.camZ, d3 - this.camX, d4 - this.camY, d5 - this.camZ) >= 0) {
            this.camZ -= (this.viewVector.z() * 4.0);
            this.camX -= (this.viewVector.x() * 4.0);
            this.camY -= (this.viewVector.y() * 4.0);
        }

        return this;
    }

    public void setCamOffset(double camX, double camY, double camZ) {
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
    }

    public void calculateFrustum(Matrix4f modelViewMatrix, Matrix4f projMatrix) {
        projMatrix.mul(modelViewMatrix, this.matrix);

        this.frustum.set(this.matrix, false);
        this.viewVector = this.matrix.transformTranspose(new Vector4f(0.0F, 0.0F, 1.0F, 0.0F));
    }

    public int cubeInFrustum(double x1, double y1, double z1, double x2, double y2, double z2) {
        double f = x1 - this.camX;
        double f1 = y1 - this.camY;
        double f2 = z1 - this.camZ;
        double f3 = x2 - this.camX;
        double f4 = y2 - this.camY;
        double f5 = z2 - this.camZ;
        return this.intersectAab(f, f1, f2, f3, f4, f5);
    }

    public boolean testFrustum(double x1, double y1, double z1, double x2, double y2, double z2) {
        double f = x1 - this.camX;
        double f1 = y1 - this.camY;
        double f2 = z1 - this.camZ;
        double f3 = x2 - this.camX;
        double f4 = y2 - this.camY;
        double f5 = z2 - this.camZ;
        return this.frustum.testAab((float) f, (float) f1, (float) f2, (float) f3, (float) f4, (float) f5);
    }

    private int intersectAab(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return this.frustum.intersectAab((float) minX, (float) minY, (float) minZ, (float) maxX, (float) maxY, (float) maxZ);
    }

    public boolean isVisible(AABB aABB) {
        return this.testFrustum(aABB.minX, aABB.minY, aABB.minZ, aABB.maxX, aABB.maxY, aABB.maxZ);
    }
}
