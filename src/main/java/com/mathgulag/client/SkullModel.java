package com.mathgulag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Calavera del gulag (modelo "calavera_desafio_4_gulag" de Blockbench) convertida a codigo.
 * Se dibuja a mano (cubos con rotacion en 3 ejes y UV por cara) porque el formato de modelos
 * de bloque de Minecraft no admite esas rotaciones.
 * Datos generados desde el .bbmodel; unidades en pixeles de Blockbench (16 = 1 bloque).
 */
public final class SkullModel {
    public static final int TEXTURE_COUNT = 7;
    private static final ResourceLocation[] TEX = new ResourceLocation[TEXTURE_COUNT];

    static {
        for (int i = 0; i < TEXTURE_COUNT; i++) {
            TEX[i] = ResourceLocation.fromNamespaceAndPath("mathgulag", "textures/skull/tex_" + i + ".png");
        }
    }

    /** {from xyz, to xyz, origin xyz, rotacion xyz en grados}. */
    private static final float[][] ELEMENTS = {
        {-6.00000f, 12.00000f, -8.00000f, 7.00000f, 21.00000f, 8.00000f, 0.00000f, 11.00000f, 1.00000f, 0.00000f, 0.00000f, 0.00000f},
        {-6.00000f, 9.00000f, -5.00000f, -4.00000f, 12.00000f, 5.00000f, -5.00000f, 4.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f},
        {-5.00000f, 10.00000f, -2.00000f, -1.00000f, 14.00000f, 9.00000f, -3.00000f, 11.00000f, 7.00000f, -141.79705f, -9.82234f, -10.46225f},
        {-5.00000f, 9.00000f, -15.00000f, -1.00000f, 13.00000f, -4.00000f, -3.00000f, 10.00000f, -6.00000f, -43.53961f, -2.61009f, -17.43504f},
        {3.00000f, 9.00000f, -14.00000f, 7.00000f, 13.00000f, -3.00000f, 5.00000f, 10.00000f, -6.00000f, -50.50962f, -26.91425f, 8.86722f},
        {3.00000f, 11.00000f, -2.00000f, 7.00000f, 15.00000f, 10.00000f, 5.00000f, 12.00000f, 7.00000f, -140.22766f, 17.72096f, 11.40133f},
        {-8.00000f, 1.00000f, -17.00000f, -2.00000f, 6.00000f, -11.00000f, -3.00000f, 3.00000f, -14.00000f, 34.76905f, 4.29357f, -6.15518f},
        {-8.00000f, 1.00000f, 12.00000f, -2.00000f, 6.00000f, 18.00000f, -3.00000f, 3.00000f, 15.00000f, -50.23095f, 4.29357f, -6.15518f},
        {6.00000f, 3.00000f, 10.00000f, 12.00000f, 8.00000f, 16.00000f, 11.00000f, 5.00000f, 13.00000f, -47.43833f, 19.42062f, 7.25895f},
        {5.00000f, 2.00000f, -16.00000f, 11.00000f, 7.00000f, -10.00000f, 10.00000f, 4.00000f, -13.00000f, 38.28679f, 10.42509f, 19.69842f}
    };

    /** {elemento, cara (0 down,1 up,2 north,3 south,4 west,5 east), textura, u0, v0, u1, v1} normalizados. */
    private static final float[][] FACES = {
        {0, 2, 0, 0.000000f, 0.390625f, 0.203125f, 0.531250f},
        {0, 5, 0, 0.000000f, 0.250000f, 0.250000f, 0.390625f},
        {0, 3, 0, 0.203125f, 0.390625f, 0.406250f, 0.531250f},
        {0, 4, 0, 0.250000f, 0.250000f, 0.500000f, 0.390625f},
        {0, 1, 0, 0.000000f, 0.000000f, 0.203125f, 0.250000f},
        {0, 0, 0, 0.203125f, 0.000000f, 0.406250f, 0.250000f},
        {1, 2, 5, 0.250000f, 0.375000f, 0.375000f, 0.562500f},
        {1, 5, 5, 0.000000f, 0.000000f, 0.625000f, 0.187500f},
        {1, 3, 5, 0.375000f, 0.375000f, 0.500000f, 0.562500f},
        {1, 4, 5, 0.000000f, 0.187500f, 0.625000f, 0.375000f},
        {1, 1, 5, 0.000000f, 0.375000f, 0.125000f, 1.000000f},
        {1, 0, 5, 0.125000f, 0.375000f, 0.250000f, 1.000000f},
        {2, 2, 2, 0.250000f, 0.250000f, 0.375000f, 0.375000f},
        {2, 5, 2, 0.000000f, 0.000000f, 0.343750f, 0.125000f},
        {2, 3, 2, 0.343750f, 0.000000f, 0.468750f, 0.125000f},
        {2, 4, 2, 0.000000f, 0.125000f, 0.343750f, 0.250000f},
        {2, 1, 2, 0.000000f, 0.250000f, 0.125000f, 0.593750f},
        {2, 0, 2, 0.125000f, 0.250000f, 0.250000f, 0.593750f},
        {3, 2, 3, 0.250000f, 0.250000f, 0.375000f, 0.375000f},
        {3, 5, 3, 0.000000f, 0.000000f, 0.343750f, 0.125000f},
        {3, 3, 3, 0.343750f, 0.000000f, 0.468750f, 0.125000f},
        {3, 4, 3, 0.000000f, 0.125000f, 0.343750f, 0.250000f},
        {3, 1, 3, 0.000000f, 0.250000f, 0.125000f, 0.593750f},
        {3, 0, 3, 0.125000f, 0.250000f, 0.250000f, 0.593750f},
        {4, 2, 4, 0.250000f, 0.250000f, 0.375000f, 0.375000f},
        {4, 5, 4, 0.000000f, 0.000000f, 0.343750f, 0.125000f},
        {4, 3, 4, 0.343750f, 0.000000f, 0.468750f, 0.125000f},
        {4, 4, 4, 0.000000f, 0.125000f, 0.343750f, 0.250000f},
        {4, 1, 4, 0.000000f, 0.250000f, 0.125000f, 0.593750f},
        {4, 0, 4, 0.125000f, 0.250000f, 0.250000f, 0.593750f},
        {5, 2, 1, 0.250000f, 0.250000f, 0.375000f, 0.375000f},
        {5, 5, 1, 0.000000f, 0.000000f, 0.375000f, 0.125000f},
        {5, 3, 1, 0.375000f, 0.000000f, 0.500000f, 0.125000f},
        {5, 4, 1, 0.000000f, 0.125000f, 0.375000f, 0.250000f},
        {5, 1, 1, 0.000000f, 0.250000f, 0.125000f, 0.625000f},
        {5, 0, 1, 0.125000f, 0.250000f, 0.250000f, 0.625000f},
        {6, 2, 6, 0.375000f, 0.000000f, 0.750000f, 0.187500f},
        {6, 5, 6, 0.375000f, 0.187500f, 0.750000f, 0.375000f},
        {6, 3, 6, 0.375000f, 0.375000f, 0.750000f, 0.562500f},
        {6, 4, 6, 0.375000f, 0.562500f, 0.750000f, 0.750000f},
        {6, 1, 6, 0.000000f, 0.000000f, 0.375000f, 0.375000f},
        {6, 0, 6, 0.000000f, 0.375000f, 0.375000f, 0.750000f},
        {7, 2, 6, 0.375000f, 0.000000f, 0.750000f, 0.187500f},
        {7, 5, 6, 0.375000f, 0.187500f, 0.750000f, 0.375000f},
        {7, 3, 6, 0.375000f, 0.375000f, 0.750000f, 0.562500f},
        {7, 4, 6, 0.375000f, 0.562500f, 0.750000f, 0.750000f},
        {7, 1, 6, 0.000000f, 0.000000f, 0.375000f, 0.375000f},
        {7, 0, 6, 0.000000f, 0.375000f, 0.375000f, 0.750000f},
        {8, 2, 6, 0.375000f, 0.000000f, 0.750000f, 0.187500f},
        {8, 5, 6, 0.375000f, 0.187500f, 0.750000f, 0.375000f},
        {8, 3, 6, 0.375000f, 0.375000f, 0.750000f, 0.562500f},
        {8, 4, 6, 0.375000f, 0.562500f, 0.750000f, 0.750000f},
        {8, 1, 6, 0.000000f, 0.000000f, 0.375000f, 0.375000f},
        {8, 0, 6, 0.000000f, 0.375000f, 0.375000f, 0.750000f},
        {9, 2, 6, 0.375000f, 0.000000f, 0.750000f, 0.187500f},
        {9, 5, 6, 0.375000f, 0.187500f, 0.750000f, 0.375000f},
        {9, 3, 6, 0.375000f, 0.375000f, 0.750000f, 0.562500f},
        {9, 4, 6, 0.375000f, 0.562500f, 0.750000f, 0.750000f},
        {9, 1, 6, 0.000000f, 0.000000f, 0.375000f, 0.375000f},
        {9, 0, 6, 0.000000f, 0.375000f, 0.375000f, 0.750000f}
    };

    /** Esquinas de cada cara (0 = min, 1 = max en x,y,z), mismo orden que usa Minecraft. */
    private static final int[][][] CORNERS = {
            {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
            {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
            {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
            {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
            {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}
    };
    private static final float[][] NORMALS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    private static Matrix4f[] rotations;

    private SkullModel() {
    }

    private static void init() {
        rotations = new Matrix4f[ELEMENTS.length];
        for (int i = 0; i < ELEMENTS.length; i++) {
            float[] e = ELEMENTS[i];
            // Blockbench: orden de rotacion ZYX (primero X, luego Y, luego Z) alrededor del origen del cubo.
            rotations[i] = new Matrix4f().rotateZYX(
                    (float) Math.toRadians(e[11]), (float) Math.toRadians(e[10]), (float) Math.toRadians(e[9]));
        }
    }

    /**
     * Dibuja la calavera en el espacio actual del PoseStack: 1 unidad = 1 bloque, y hacia arriba,
     * el origen en el centro de la base del modelo y el frente hacia -z (north).
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (rotations == null) init();
        PoseStack.Pose pose = poseStack.last();
        Vector3f pos = new Vector3f();
        Vector3f nrm = new Vector3f();
        for (int tex = 0; tex < TEXTURE_COUNT; tex++) {
            VertexConsumer vc = null;
            for (float[] f : FACES) {
                if ((int) f[2] != tex) continue;
                if (vc == null) vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX[tex]));
                int ei = (int) f[0];
                int dir = (int) f[1];
                float[] e = ELEMENTS[ei];
                Matrix4f rot = rotations[ei];
                nrm.set(NORMALS[dir][0], NORMALS[dir][1], NORMALS[dir][2]);
                rot.transformDirection(nrm);
                float[] us = {f[3], f[3], f[5], f[5]};
                float[] vs = {f[4], f[6], f[6], f[4]};
                for (int k = 0; k < 4; k++) {
                    int[] c = CORNERS[dir][k];
                    pos.set((c[0] == 1 ? e[3] : e[0]) - e[6],
                            (c[1] == 1 ? e[4] : e[1]) - e[7],
                            (c[2] == 1 ? e[5] : e[2]) - e[8]);
                    rot.transformPosition(pos);
                    pos.add(e[6], e[7], e[8]).div(16f);
                    vc.addVertex(pose, pos.x, pos.y, pos.z)
                            .setColor(255, 255, 255, 255)
                            .setUv(us[k], vs[k])
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light)
                            .setNormal(pose, nrm.x, nrm.y, nrm.z);
                }
            }
        }
    }
}
