package com.mathgulag.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/** Dibujo del gulag: filtro rojo, venas que laten, pinchos, numeros de sangre y caja de humo. */
public class GulagRenderer {
    private static float[][] veins;
    private static float[] veinW;
    private static float[] veinSeed;
    private static float beatPhase, lastT;

    // ------------------------------------------------------------------ utilidades

    private static float time() {
        return (System.currentTimeMillis() % 3600000L) / 1000f;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int clamp255(float v) {
        return Math.max(0, Math.min(255, (int) v));
    }

    private static int mixc(int red, int green, float G) {
        return clamp255(lerp(red, green, G));
    }

    private static int argb(int a, int r, int g, int b) {
        return (Math.max(0, Math.min(255, a)) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    private static float hash(float x) {
        float s = (float) Math.sin(x * 12.9898f) * 43758.5453f;
        return s - (float) Math.floor(s);
    }

    private static float heartbeat(float phase) {
        float c = phase % 1f;
        float a = (float) Math.exp(-Math.pow((c - 0.08f) / 0.05f, 2));
        float b = 0.7f * (float) Math.exp(-Math.pow((c - 0.30f) / 0.06f, 2));
        return Math.max(a, b);
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float p = x - 1f;
        return 1f + c3 * p * p * p + c1 * p * p;
    }

    private static void prepare() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    /** Dibuja el buffer, que puede quedar vacio si ese pase no genero geometria. */
    private static void flush(BufferBuilder bb) {
        MeshData mesh = bb.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    /** Elipse con degradado radial (centro c0, borde c1). */
    private static void radial(Matrix4f m, float cx, float cy, float rx, float ry, int[] c0, int[] c1) {
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        bb.addVertex(m, cx, cy, 0f).setColor(c0[0], c0[1], c0[2], c0[3]);
        int n = 24;
        for (int i = 0; i <= n; i++) {
            double ang = i * Math.PI * 2 / n;
            bb.addVertex(m, cx + (float) Math.cos(ang) * rx, cy + (float) Math.sin(ang) * ry, 0f)
                    .setColor(c1[0], c1[1], c1[2], c1[3]);
        }
        flush(bb);
    }

    private static void ring(Matrix4f m, float cx, float cy, float radius, float width, int r, int g, int b, int a) {
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int n = 36;
        for (int i = 0; i <= n; i++) {
            double ang = i * Math.PI * 2 / n;
            float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
            bb.addVertex(m, cx + ca * (radius - width * 0.5f), cy + sa * (radius - width * 0.5f), 0f).setColor(r, g, b, a);
            bb.addVertex(m, cx + ca * (radius + width * 0.5f), cy + sa * (radius + width * 0.5f), 0f).setColor(r, g, b, a);
        }
        flush(bb);
    }

    private static void dot(BufferBuilder bb, Matrix4f m, float cx, float cy, float r,
                            int cr, int cg, int cb, int a) {
        bb.addVertex(m, cx, cy - r, 0f).setColor(cr, cg, cb, a);
        bb.addVertex(m, cx + r, cy, 0f).setColor(cr, cg, cb, a);
        bb.addVertex(m, cx, cy + r, 0f).setColor(cr, cg, cb, a);
        bb.addVertex(m, cx - r, cy, 0f).setColor(cr, cg, cb, a);
    }

    /** Actualiza el temblor de pantalla (lo usan el HUD, la pantalla y el clic). */
    private static void updateShake(float t, int h) {
        if (ClientState.intensity < 0.01f) {
            ClientState.shakeX = 0f;
            ClientState.shakeY = 0f;
            return;
        }
        float urgency = ClientState.phase != 0
                ? 1f - clamp01(ClientState.ticksLeft / (float) ClientState.ticksTotal) : 0f;
        float low = Math.max(0f, urgency - 0.72f) / 0.28f;
        float amp = (ClientState.shakeAmount * 7f + low * 2.4f) * Math.max(0.6f, h / 540f);
        ClientState.shakeX = amp * (float) Math.sin(t * 87.3f);
        ClientState.shakeY = amp * (float) Math.sin(t * 71.7f + 1.3f);
    }

    // ------------------------------------------------------------------ red de venas

    private static final float[] PULSE = new float[2];

    private static void init() {
        if (veins != null) return;
        Random r = new Random(20261027L);
        List<float[]> pts = new ArrayList<>();
        List<Float> widths = new ArrayList<>();
        List<Float> seeds = new ArrayList<>();
        // grietas interiores: rejilla 5x3 con jitter, cubren toda la pantalla
        for (int gy = 0; gy < 3; gy++) {
            for (int gx = 0; gx < 5; gx++) {
                float x = (gx + 0.2f + 0.6f * r.nextFloat()) / 5f;
                float y = (gy + 0.2f + 0.6f * r.nextFloat()) / 3f;
                float ang = r.nextFloat() * 6.283f;
                grow(pts, widths, seeds, r, x, y, ang, 0.75f + 0.6f * r.nextFloat(), 1);
                grow(pts, widths, seeds, r, x, y, ang + 2.4f + 1.2f * r.nextFloat(), 0.75f + 0.6f * r.nextFloat(), 1);
                if (r.nextFloat() < 0.45f) {
                    grow(pts, widths, seeds, r, x, y, ang + 4.4f + r.nextFloat(), 0.65f + 0.5f * r.nextFloat(), 1);
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            float x, y, ang;
            switch (i % 4) {
                case 0 -> {
                    x = -0.03f + 0.05f * r.nextFloat();
                    y = 0.05f + 0.9f * r.nextFloat();
                    ang = (float) Math.toRadians(-28 + 56 * r.nextFloat());
                }
                case 1 -> {
                    x = 0.98f + 0.05f * r.nextFloat();
                    y = 0.05f + 0.9f * r.nextFloat();
                    ang = (float) Math.toRadians(152 + 56 * r.nextFloat());
                }
                case 2 -> {
                    x = 0.04f + 0.92f * r.nextFloat();
                    y = -0.03f + 0.05f * r.nextFloat();
                    ang = (float) Math.toRadians(62 + 56 * r.nextFloat());
                }
                default -> {
                    x = 0.04f + 0.92f * r.nextFloat();
                    y = 0.98f + 0.05f * r.nextFloat();
                    ang = (float) Math.toRadians(-118 + 56 * r.nextFloat());
                }
            }
            grow(pts, widths, seeds, r, x, y, ang, 3.2f + 2.3f * r.nextFloat(), 0);
        }
        veins = pts.toArray(new float[0][]);
        veinW = new float[widths.size()];
        veinSeed = new float[seeds.size()];
        for (int i = 0; i < veinW.length; i++) {
            veinW[i] = widths.get(i);
            veinSeed[i] = seeds.get(i);
        }
    }

    /** Rama una vena desde el borde hacia dentro, con ramificaciones que se afinan. */
    private static void grow(List<float[]> pts, List<Float> widths, List<Float> seeds, Random r,
                             float x, float y, float ang, float width, int depth) {
        int n = 12 + r.nextInt(10);
        float step = 0.009f + 0.006f * r.nextFloat();
        float[] line = new float[(n + 1) * 2];
        line[0] = x;
        line[1] = y;
        float drift = (r.nextFloat() - 0.5f) * 0.45f;
        float cx = x, cy = y;
        for (int i = 1; i <= n; i++) {
            ang += drift * 0.3f + (r.nextFloat() - 0.5f) * 0.34f;
            float toX = 0.5f - cx, toY = 0.5f - cy;
            float dist2 = toX * toX + toY * toY;
            if (dist2 > 0.01f) {
                float diff = (float) Math.atan2(toY, toX) - ang;
                while (diff > Math.PI) diff -= (float) (Math.PI * 2);
                while (diff < -Math.PI) diff += (float) (Math.PI * 2);
                ang += diff * (depth == 0 ? 0.025f : -0.008f);
            }
            cx += (float) Math.cos(ang) * step * (0.85f + 0.3f * r.nextFloat());
            cy += (float) Math.sin(ang) * step * (0.85f + 0.3f * r.nextFloat());
            line[i * 2] = cx;
            line[i * 2 + 1] = cy;
        }
        pts.add(line);
        widths.add(width);
        seeds.add(r.nextFloat() * 6.283f);
        if (depth >= 3) return;
        int branches;
        if (depth == 0) {
            branches = r.nextFloat() < 0.35f ? 3 : 2;
        } else if (depth == 1) {
            branches = 1 + (r.nextFloat() < 0.55f ? 1 : 0);
        } else {
            branches = r.nextFloat() < 0.6f ? 1 : 0;
        }
        for (int b = 0; b < branches; b++) {
            int at = 2 + r.nextInt(Math.max(1, n - 6));
            float bx = line[at * 2], by = line[at * 2 + 1];
            float base = (float) Math.atan2(by - line[(at - 1) * 2 + 1], bx - line[(at - 1) * 2]);
            float bang = base + (r.nextBoolean() ? 1f : -1f) * (0.5f + 0.6f * r.nextFloat());
            grow(pts, widths, seeds, r, bx, by, bang, width * (0.5f + 0.18f * r.nextFloat()), depth + 1);
        }
    }

    private static void seg(BufferBuilder bb, Matrix4f m, float x0, float y0, float x1, float y1, float wd,
                            int r, int g, int b, int a) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        float nx = -dy / len * wd * 0.5f, ny = dx / len * wd * 0.5f;
        bb.addVertex(m, x0 + nx, y0 + ny, 0f).setColor(r, g, b, a);
        bb.addVertex(m, x1 + nx, y1 + ny, 0f).setColor(r, g, b, a);
        bb.addVertex(m, x1 - nx, y1 - ny, 0f).setColor(r, g, b, a);
        bb.addVertex(m, x0 - nx, y0 - ny, 0f).setColor(r, g, b, a);
    }

    private static void pointAt(float[] line, int segs, float frac, float breathe, float sx, float sy,
                                int w, int h) {
        float pos = clamp01(frac) * segs;
        int i = Math.min(segs - 1, (int) pos);
        float f = pos - i;
        float ax = 0.5f + (line[i * 2] - 0.5f) * breathe + sx;
        float ay = 0.5f + (line[i * 2 + 1] - 0.5f) * breathe + sy;
        float bx = 0.5f + (line[i * 2 + 2] - 0.5f) * breathe + sx;
        float by = 0.5f + (line[i * 2 + 3] - 0.5f) * breathe + sy;
        PULSE[0] = lerp(ax, bx, f) * w;
        PULSE[1] = lerp(ay, by, f) * h;
    }

    private static void drawVeins(GuiGraphics g, int w, int h, float t, float beat, float G, float I) {
        init();
        Matrix4f m = g.pose().last().pose();
        float s = Math.max(0.6f, h / 720f);
        float breathe = 1f + 0.012f * (float) Math.sin(t * 0.9f) + 0.006f * beat;
        float sway = 0.006f * (float) Math.sin(t * 0.27f);
        float swayY = 0.004f * (float) Math.sin(t * 0.21f + 1.1f);
        prepare();

        // sombreado celular: manchas oscuras detras de la red, estilo grietas de sangre seca
        float s2 = Math.max(0.6f, h / 720f);
        for (int cy = 0; cy < 3; cy++) {
            for (int cx = 0; cx < 5; cx++) {
                float hx = hash(cx * 3.3f + cy * 7.7f);
                float ccx = ((cx + 0.5f) / 5f) * w + (hash(cx + cy * 5f) - 0.5f) * 90f * s2;
                float ccy = ((cy + 0.5f) / 3f) * h + (hash(cx * 1.7f + cy * 2.9f) - 0.5f) * 70f * s2;
                float rad = (0.17f + 0.06f * hx) * w;
                radial(m, ccx, ccy, rad, rad * 1.25f,
                        new int[]{mixc(40, 8, G), mixc(2, 26, G), mixc(5, 10, G), (int) (46 * I)},
                        new int[]{mixc(40, 8, G), mixc(2, 26, G), mixc(5, 10, G), 0});
            }
        }

        for (int pass = 0; pass < 3; pass++) {
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int v = 0; v < veins.length; v++) {
                float[] line = veins[v];
                int segs = line.length / 2 - 1;
                float w0 = veinW[v] * s * (1f + 0.2f * beat);
                for (int i = 0; i < segs; i++) {
                    float f = i / (float) segs;
                    float taper = 1f - 0.7f * f;
                    float swell = 0.82f + 0.32f * (float) Math.sin(i * 1.15f + veinSeed[v] * 3f);
                    float x0 = (0.5f + (line[i * 2] - 0.5f) * breathe + sway) * w;
                    float y0 = (0.5f + (line[i * 2 + 1] - 0.5f) * breathe + swayY) * h;
                    float x1 = (0.5f + (line[i * 2 + 2] - 0.5f) * breathe + sway) * w;
                    float y1 = (0.5f + (line[i * 2 + 3] - 0.5f) * breathe + swayY) * h;
                    float mxn = (line[i * 2] + line[i * 2 + 2]) / 2f;
                    float myn = (line[i * 2 + 1] + line[i * 2 + 3]) / 2f;
                    float dx = (mxn - 0.5f) * 2f, dy = (myn - 0.5f) * 2f;
                    float d = Math.min(1.3f, (float) Math.sqrt(dx * dx + dy * dy));
                    float fade = clamp01(0.55f + 0.55f * d);
                    float wd = Math.max(1.0f, w0 * taper * swell);
                    if (pass == 0) {
                        int al = (int) (22 * fade * I * (0.75f + 0.5f * beat));
                        seg(bb, m, x0, y0, x1, y1, Math.max(2.5f, wd * 3.2f),
                                mixc(235, 15, G), mixc(25, 210, G), mixc(25, 70, G), al);
                    } else if (pass == 1) {
                        int al = (int) (92 * fade * I);
                        seg(bb, m, x0, y0, x1, y1, Math.max(1.8f, wd * 1.7f),
                                mixc(120, 0, G), mixc(0, 110, G), mixc(4, 40, G), al);
                    } else {
                        int al = (int) (235 * fade * I);
                        int r = clamp255(lerp(255, 80, G) + 25 * beat), gg = mixc(62, 255, G), bl = mixc(70, 120, G);
                        seg(bb, m, x0, y0, x1, y1, wd, r, gg, bl, al);
                    }
                }
                if (pass == 2) {
                    float tipX = line[line.length - 2], tipY = line[line.length - 1];
                    float dx = (tipX - 0.5f) * 2f, dy = (tipY - 0.5f) * 2f;
                    float d = Math.min(1.3f, (float) Math.sqrt(dx * dx + dy * dy));
                    float fade = clamp01(0.55f + 0.55f * d);
                    float ex = (0.5f + (tipX - 0.5f) * breathe + sway) * w;
                    float ey = (0.5f + (tipY - 0.5f) * breathe + swayY) * h;
                    dot(bb, m, ex, ey, Math.max(1.4f, w0 * 0.4f),
                            clamp255(lerp(240, 80, G)), mixc(30, 200, G), mixc(35, 100, G), (int) (200 * fade * I));
                }
            }
            flush(bb);
        }

        // pulsos que viajan por las venas, con una pequena cola
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int v = 0; v < veins.length; v += 3) {
            float[] line = veins[v];
            int segs = line.length / 2 - 1;
            float sp = (t * 0.30f + hash(v * 4.7f + veinSeed[v]) * 5f) % 1f;
            float alpha = (float) Math.sin(Math.PI * sp);
            int al = (int) (190 * alpha * I);
            if (al < 4) continue;
            float baseR = (2.2f + 0.55f * veinW[v]) * s * (0.75f + 0.5f * alpha);
            for (int k = 0; k < 3; k++) {
                float f = sp - 0.05f * k;
                if (f < 0f) continue;
                int aa = (int) (al * (1f - 0.28f * k));
                if (aa < 4) continue;
                pointAt(line, segs, f, breathe, sway, swayY, w, h);
                dot(bb, m, PULSE[0], PULSE[1], baseR * (1f - 0.2f * k),
                        clamp255(lerp(255, 90, G)), mixc(50, 255, G), mixc(40, 120, G), aa);
            }
        }
        flush(bb);
        RenderSystem.enableCull();
    }

    // ------------------------------------------------------------------ manchas en los bordes

    private static void edgeSplatters(GuiGraphics g, int w, int h, float t, float I, float beat) {
        Matrix4f m = g.pose().last().pose();
        float s = Math.max(0.6f, h / 720f);
        prepare();
        for (int i = 0; i < 7; i++) {
            boolean left = i % 2 == 0;
            float nx = left ? 0.01f + 0.10f * hash(i * 3.1f) : 0.89f + 0.10f * hash(i * 5.7f);
            float ny = 0.08f + 0.55f * hash(i * 7.3f + 2f);
            float radius = (16f + 34f * hash(i * 9.1f)) * s * (1f + 0.08f * beat);
            float cx = nx * w + 2f * s * (float) Math.sin(t * 0.4f + i);
            float cy = ny * h + 2f * s * (float) Math.cos(t * 0.33f + i * 1.4f);
            radial(m, cx, cy, radius * 1.4f, radius,
                    new int[]{140, 4, 6, (int) (56 * I)},
                    new int[]{110, 2, 5, 0});
            radial(m, cx, cy, radius * 0.5f, radius * 0.42f,
                    new int[]{190, 22, 20, (int) (40 * I)},
                    new int[]{150, 8, 9, 0});
        }
        RenderSystem.enableCull();
    }

    // ------------------------------------------------------------------ pinchos

    private static void spikes(Matrix4f m, float cx, float cy, float R, float t, float seed, float G, float I) {
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        bb.addVertex(m, cx, cy, 0f).setColor(mixc(165, 20, G), mixc(6, 140, G), mixc(12, 40, G), (int) (245 * I));
        int n = 16;
        float rot = seed + t * 0.04f;
        for (int i = 0; i <= n * 2; i++) {
            int k = i % (n * 2);
            double ang = k * Math.PI / n + rot + 0.14 * (hash(k * 5.3f + seed) - 0.5f);
            boolean tip = k % 2 == 0;
            float rr;
            int r, g2, b, a;
            if (tip) {
                rr = R * (0.78f + 0.42f * hash(k * 3.1f + seed * 7f)) * (1f + 0.05f * (float) Math.sin(t * 1.4f + k + seed));
                r = mixc(255, 90, G);
                g2 = mixc(64, 245, G);
                b = mixc(70, 110, G);
                a = (int) (185 * I);
            } else {
                rr = R * 0.34f;
                r = mixc(158, 0, G);
                g2 = mixc(4, 90, G);
                b = mixc(10, 25, G);
                a = (int) (245 * I);
            }
            bb.addVertex(m, cx + (float) Math.cos(ang) * rr, cy + (float) Math.sin(ang) * rr, 0f).setColor(r, g2, b, a);
        }
        flush(bb);
    }

    private static void spikeBlob(GuiGraphics g, float cx, float cy, float R, float t, float seed, float beat,
                                  float G, float I, float hover) {
        Matrix4f m = g.pose().last().pose();
        float rr = R * (1f + 0.03f * beat) * (1f + 0.10f * hover);
        radial(m, cx, cy, rr * 1.35f, rr * 1.35f,
                new int[]{mixc(255, 70, G), mixc(50, 255, G), mixc(55, 100, G), (int) (120 * I)},
                new int[]{mixc(255, 70, G), mixc(50, 255, G), mixc(55, 100, G), 0});
        spikes(m, cx, cy, rr, t, seed, G, I);
        radial(m, cx, cy, rr * 0.36f, rr * 0.36f,
                new int[]{mixc(225, 50, G), mixc(30, 215, G), mixc(35, 70, G), (int) (245 * I)},
                new int[]{mixc(115, 0, G), mixc(0, 80, G), mixc(10, 20, G), (int) (245 * I)});
    }

    private static void pressSplash(GuiGraphics g, float cx, float cy, float R, float press, float I) {
        Matrix4f m = g.pose().last().pose();
        float age = 1f - press;
        for (int k = 0; k < 10; k++) {
            float ang = (float) (k * 2 * Math.PI / 10 + hash(k * 3.7f) * 0.8f);
            float dist = R * (0.55f + 1.05f * age) * (0.7f + 0.55f * hash(k * 7.1f));
            float rad = R * 0.14f * (0.5f + 0.8f * hash(k * 5.3f)) * (1f - 0.55f * age);
            float px = cx + (float) Math.cos(ang) * dist;
            float py = cy + (float) Math.sin(ang) * dist;
            radial(m, px, py, rad * 1.4f, rad,
                    new int[]{235, 45, 38, (int) (190 * press * I)},
                    new int[]{140, 10, 12, (int) (90 * press * I)});
        }
    }

    // ------------------------------------------------------------------ texto de sangre

    private static void bloodText(GuiGraphics g, String text, float cx, float cy, float scale, float G, float I,
                                  float tilt, boolean drips, float t, float seed, float whiteMix) {
        Font font = Minecraft.getInstance().font;
        Component c = Component.literal(text).withStyle(ChatFormatting.BOLD);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(tilt));
        ps.scale(scale, scale, 1f);
        float x = -font.width(c) / 2f;
        int a = (int) (255 * I);
        int outline = argb(a, 255, 235, 232);
        int shade = argb(a, (int) lerp(90, 0, G), (int) lerp(0, 90, G), 5);
        int fill = argb(a,
                (int) lerp(lerp(235, 60, G), 255, whiteMix),
                (int) lerp(lerp(20, 255, G), 242, whiteMix),
                (int) lerp(lerp(30, 90, G), 232, whiteMix));
        int sheen = argb((int) ((70 + 90 * whiteMix) * I), 255, 238, 232);
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4;
            ps.pushPose();
            ps.translate((float) Math.cos(ang) * 1.25f, (float) Math.sin(ang) * 1.25f, 0f);
            g.drawString(font, c, (int) x, -4, outline, false);
            ps.popPose();
        }
        ps.pushPose();
        ps.translate(0f, 0.7f, 0f);
        g.drawString(font, c, (int) x, -4, shade, false);
        ps.popPose();
        g.drawString(font, c, (int) x, -4, fill, false);
        ps.pushPose();
        ps.translate(-0.4f, -0.5f, 0f);
        g.drawString(font, c, (int) x, -4, sheen, false);
        ps.popPose();
        ps.popPose();

        if (drips) {
            float tw = font.width(c) * scale;
            float y0 = cy + 4.2f * scale;
            for (int j = 0; j < 4; j++) {
                float dx = cx + (hash(seed * 3f + j * 1.7f) - 0.5f) * tw * 0.85f;
                float len = scale * (1.5f + 4.5f * hash(seed * 7f + j))
                        * (0.6f + 0.4f * (0.5f + 0.5f * (float) Math.sin(t * 0.5f + j * 2f + seed)));
                int wd = Math.max(2, (int) (scale * 0.5f));
                g.fillGradient((int) dx, (int) y0, (int) dx + wd, (int) (y0 + len),
                        argb((int) (235 * I), (int) lerp(215, 40, G), (int) lerp(15, 220, G), 25),
                        argb((int) (90 * I), (int) lerp(150, 0, G), (int) lerp(0, 150, G), 10));
            }
        }
    }

    // ------------------------------------------------------------------ caja de humo

    private static void smokyBox(GuiGraphics g, int x1, int y1, int x2, int y2, float t, float I, float s) {
        Matrix4f m = g.pose().last().pose();
        prepare();
        int[] c0 = {6, 0, 0, (int) (38 * I)};
        int[] c1 = {6, 0, 0, 0};
        for (int i = 0; i < 46; i++) {
            float hx = hash(i * 1.7f), hy = hash(i * 2.9f + 5f), hr = hash(i * 4.1f + 9f);
            float cx = x1 + (x2 - x1) * (0.05f + 0.9f * hx) + 5f * s * (float) Math.sin(t * 0.4f + i);
            float cy = y1 + (y2 - y1) * (0.05f + 0.9f * hy) + 3f * s * (float) Math.cos(t * 0.35f + i * 1.3f);
            radial(m, cx, cy, (60f + 110f * hr) * s, (40f + 70f * hr) * s, c0, c1);
        }
        for (int i = 0; i < 14; i++) {
            float cx = x1 + (x2 - x1) * (0.06f + 0.88f * hash(i * 6.7f + 2f));
            float len = (40f + 90f * hash(i * 3.3f)) * s * (1f + 0.2f * (float) Math.sin(t * 0.6f + i));
            radial(m, cx, y2 - 20f * s + len * 0.3f, 16f * s, len, new int[]{6, 0, 0, (int) (46 * I)}, c1);
        }
        RenderSystem.enableCull();
    }

    // ------------------------------------------------------------------ banner de aviso

    private static void passBanner(GuiGraphics g, String text, float cx, float cy, float scale,
                                   float I, float beat, float G) {
        Font font = Minecraft.getInstance().font;
        Component c = Component.literal(text).withStyle(ChatFormatting.BOLD);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 0);
        float pop = 1f + 0.05f * beat;
        ps.scale(scale * pop, scale * pop, 1f);
        int wd = font.width(c);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                g.drawString(font, c, -wd / 2 + dx, dy,
                        argb((int) (170 * I), (int) lerp(76, 8, G), (int) lerp(30, 64, G), (int) lerp(6, 18, G)), false);
            }
        }
        g.drawString(font, c, -wd / 2, 0,
                argb((int) (255 * I), (int) lerp(255, 70, G), (int) lerp(176, 255, G), (int) lerp(32, 105, G)), false);
        g.drawString(font, c, -wd / 2, -1,
                argb((int) (110 * I), (int) lerp(255, 185, G), (int) lerp(224, 255, G), (int) lerp(140, 190, G)), false);
        ps.popPose();
    }

    // ------------------------------------------------------------------ manos en primera persona

    private static void quad(BufferBuilder bb, Matrix4f m, float x0, float y0, float x1, float y1,
                             float x2, float y2, float x3, float y3, int r, int g2, int b, int a) {
        bb.addVertex(m, x0, y0, 0f).setColor(r, g2, b, a);
        bb.addVertex(m, x1, y1, 0f).setColor(r, g2, b, a);
        bb.addVertex(m, x2, y2, 0f).setColor(r, g2, b, a);
        bb.addVertex(m, x3, y3, 0f).setColor(r, g2, b, a);
    }

    /** Brazo/mano rosado estilo POV que sube desde el borde inferior. dir=1 mano izquierda, -1 derecha. */
    private static void drawHand(GuiGraphics g, float bx, float by, float dir, float s, float I, float t) {
        Matrix4f m = g.pose().last().pose();
        float lean = dir * 78f * s;
        float topX = bx + lean;
        float topY = by - 360f * s;
        // contorno oscuro
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        quad(bb, m, bx - 92f * s, by + 14f * s, bx + 92f * s, by + 14f * s,
                topX + 66f * s, topY - 12f * s, topX - 66f * s, topY - 12f * s, 142, 74, 70, (int) (235 * I));
        // antebrazo
        bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        quad(bb, m, bx - 84f * s, by + 10f * s, bx + 84f * s, by + 10f * s,
                topX + 58f * s, topY, topX - 58f * s, topY, 235, 152, 142, (int) (250 * I));
        // sombreado lateral
        quad(bb, m, bx + dir * 10f * s, by + 10f * s, bx + dir * 84f * s, by + 10f * s,
                topX + dir * 58f * s, topY, topX + dir * 8f * s, topY, 206, 118, 110, (int) (250 * I));
        flush(bb);
        // nudillos
        prepare();
        for (int f = 0; f < 4; f++) {
            float fx = topX + dir * (-45f + f * 30f) * s;
            float fy = topY - (6f + 4f * (float) Math.sin(t * 1.1f + f)) * s;
            radial(m, fx, fy, 26f * s, 24f * s,
                    new int[]{240, 158, 148, (int) (252 * I)}, new int[]{205, 118, 110, (int) (252 * I)});
        }
        radial(m, topX + dir * 62f * s, topY + 34f * s, 24f * s, 20f * s,
                new int[]{232, 148, 138, (int) (252 * I)}, new int[]{198, 112, 104, (int) (252 * I)});
        RenderSystem.enableCull();
    }

    private static void drawHands(GuiGraphics g, int w, int h, float t, float beat, float I) {
        float s = h / 1080f;
        float bob = (2.2f * (float) Math.sin(t * 0.85f) + 5f * beat) * s;
        drawHand(g, w * 0.5f - 330f * s, h + 50f * s + bob, 1f, s, I, t);
        drawHand(g, w * 0.5f + 330f * s, h + 50f * s + bob, -1f, s, I, t + 2.4f);
    }

    // ------------------------------------------------------------------ corazon gigante

    /** Corazon rojo gigante que late al recibir la bomba. age01: 0..1 desde que llego la bomba. */
    private static void drawHeart(GuiGraphics g, int w, int h, float t, float I, float age01, float beat) {
        float pop = easeOutBack(clamp01(age01 * 4f));
        float fade = clamp01(age01 * 8f) * clamp01((1f - age01) * 2.2f);
        if (fade <= 0.01f) return;
        float size = Math.min(w, h) * (0.24f + 0.13f * pop) * (1f + 0.15f * beat);
        float cx = w / 2f;
        float cy = h * 0.42f;
        Matrix4f m = g.pose().last().pose();
        prepare();
        // contorno
        heartFan(m, cx, cy, size * 1.08f, 128, 6, 14, (int) (240 * I * fade));
        // relleno
        heartFan(m, cx, cy, size, 216, 22, 34, (int) (252 * I * fade));
        // brillo del lobulo izquierdo
        radial(m, cx - size * 0.34f, cy - size * 0.22f, size * 0.30f, size * 0.24f,
                new int[]{252, 96, 104, (int) (170 * I * fade)}, new int[]{230, 50, 60, 0});
        // gotas que caen del corazon
        for (int j = 0; j < 3; j++) {
            float ph = (t * 0.55f + j * 0.37f) % 1f;
            float dx = cx + (hash(j * 7.7f) - 0.5f) * size * 0.9f;
            float dy = cy + size * 0.75f + ph * size * 0.8f;
            radial(m, dx, dy, size * 0.07f * (1f - ph * 0.5f), size * 0.10f * (1f - ph * 0.5f),
                    new int[]{205, 18, 28, (int) (200 * (1f - ph) * I * fade)},
                    new int[]{150, 8, 14, 0});
        }
        RenderSystem.enableCull();
    }

    /** Abanico del contorno de un corazon usando la curva parametrica clasica. */
    private static void heartFan(Matrix4f m, float cx, float cy, float size, int r, int g2, int b, int a) {
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        bb.addVertex(m, cx, cy - size * 0.18f, 0f).setColor(r, g2, b, a);
        int n = 40;
        for (int i = 0; i <= n; i++) {
            double th = i * Math.PI * 2 / n;
            double hx = 16 * Math.pow(Math.sin(th), 3);
            double hy = 13 * Math.cos(th) - 5 * Math.cos(2 * th) - 2 * Math.cos(3 * th) - Math.cos(4 * th);
            bb.addVertex(m, cx + (float) hx * size / 34f, cy - (float) hy * size / 34f, 0f)
                    .setColor(r, g2, b, a);
        }
        flush(bb);
    }

    // ------------------------------------------------------------------ capas

    public static void renderOverlay(GuiGraphics g, float partialTick, int w, int h) {
        float I = ClientState.intensity;
        float t = time();
        if (I < 0.01f) {
            ClientState.shakeX = 0f;
            ClientState.shakeY = 0f;
            lastT = t;
            beatPhase = 0f;
            return;
        }
        float G = ClientState.green;
        float dt = lastT == 0f ? 0f : Math.max(0f, Math.min(0.1f, t - lastT));
        lastT = t;
        float urgency = 1f - clamp01(ClientState.ticksLeft / (float) ClientState.ticksTotal);
        beatPhase += dt * (1.2f + 2.6f * urgency * (ClientState.phase == 3 ? 0.45f : 1f));
        float beat = heartbeat(beatPhase);
        updateShake(t, h);

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(ClientState.shakeX, ClientState.shakeY, 0f);

        g.fill(0, 0, w, h, argb((int) (56 * I), (int) lerp(150, 0, G), (int) lerp(0, 150, G), (int) lerp(8, 28, G)));
        int strips = 26;
        float edgeW = w * 0.28f;
        for (int i = 0; i < strips; i++) {
            float f = 1f - i / (float) strips;
            int al = (int) (120 * I * f * f);
            int sw = (int) Math.ceil(edgeW / strips);
            g.fill(i * sw, 0, (i + 1) * sw, h, argb(al, 4, 0, 0));
            g.fill(w - (i + 1) * sw, 0, w - i * sw, h, argb(al, 4, 0, 0));
        }
        g.fillGradient(0, 0, w, (int) (h * 0.28f), argb((int) (120 * I), 4, 0, 0), argb(0, 4, 0, 0));
        g.fillGradient(0, (int) (h * 0.72f), w, h, argb(0, 4, 0, 0), argb((int) (120 * I), 4, 0, 0));
        g.fill(0, 0, w, h, argb((int) (24 * I * beat), (int) lerp(255, 0, G), (int) lerp(0, 255, G), 20));

        // destellos de daño / fallo / acierto
        if (ClientState.hurtFlash > 0.015f) {
            float f = ClientState.hurtFlash;
            g.fill(0, 0, w, h, argb((int) (60 * f), 130, 0, 0));
            g.fillGradient(0, 0, w, (int) (h * 0.4f), argb((int) (170 * f), 150, 0, 0), argb(0, 120, 0, 0));
            g.fillGradient(0, (int) (h * 0.6f), w, h, argb(0, 120, 0, 0), argb((int) (170 * f), 150, 0, 0));
        }
        if (ClientState.wrongFlash > 0.015f) {
            g.fill(0, 0, w, h, argb((int) (70 * ClientState.wrongFlash), 210, 0, 0));
        }
        if (ClientState.correctFlash > 0.015f) {
            g.fill(0, 0, w, h, argb((int) (46 * ClientState.correctFlash), 0, 190, 55));
        }

        edgeSplatters(g, w, h, t, I, beat);
        drawVeins(g, w, h, t, beat, G, I);

        float s = h / 1080f;

        if (ClientState.phase == 1 || ClientState.phase == 2) {
            drawHands(g, w, h, t, beat, I);
        }
        if (ClientState.phase == 1) {
            float age = (ClientState.tickCount - ClientState.holderSince) / 55f;
            if (age >= 0f && age < 1f) {
                drawHeart(g, w, h, t, I, age, beat);
            }
        }

        if (ClientState.phase == 2) {
            // pregunta resuelta en verde arriba (sin bloquear el ataque)
            String solved = ClientState.question + " = " + ClientState.typed;
            bloodText(g, solved, w / 2f, h * 0.10f, 5.4f * s, 1f, I, 0f, false, t, 7f, 0.15f);
            float secs = Math.max(0f, ClientState.ticksLeft / 20f);
            String count = String.format(java.util.Locale.ROOT, "%.1f", secs);
            passBanner(g, "\u00a1CORRECTO!  Golpea a otro jugador para pasar la bomba  \u00b7  " + count + "s",
                    w / 2f, h - 86 * Math.max(0.8f, s), 1.55f * Math.max(0.75f, s * 1.6f), I, beat, 1f);
        } else if (ClientState.phase == 3) {
            passBanner(g, "BOMBA: " + ClientState.holder,
                    w / 2f, h - 40 * Math.max(0.8f, s), 1.15f * Math.max(0.75f, s * 1.6f), I, beat, 0f);
        }
        ps.popPose();
    }

    public static void renderQuestionLayer(GuiGraphics g, int w, int h, int mouseX, int mouseY) {
        float I = clamp01(ClientState.intensity);
        if (I < 0.01f) return;
        float G = ClientState.green;
        float t = time();
        float beat = heartbeat(beatPhase);
        float s = h / 1080f;
        updateShake(t, h);

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(ClientState.shakeX, ClientState.shakeY, 0f);

        smokyBox(g, (int) (w * 0.34f), (int) (h * 0.13f), (int) (w * 0.66f), (int) (h * 0.50f), t, I, s);

        float R = 125f * s;
        float[] posX = new float[10];
        float[] posY = new float[10];
        float[] spawnA = new float[10];
        float[] hoverA = new float[10];
        for (int d = 0; d < 10; d++) {
            float spawn = clamp01((ClientState.tickCount - ClientState.questionTick - d * 1.2f) / 14f);
            posX[d] = ClientState.cur[d][0] * w
                    + 3.5f * s * (float) Math.sin(t * 0.85f + d * 1.9f);
            posY[d] = ClientState.cur[d][1] * h
                    + 3.0f * s * (float) Math.cos(t * 0.7f + d * 2.3f);
            spawnA[d] = clamp01(spawn * 1.5f);
            double dx = mouseX - posX[d], dy = mouseY - posY[d];
            hoverA[d] = (Math.sqrt(dx * dx + dy * dy) < 66 * s && spawn > 0.4f) ? 1f : 0f;
        }

        Matrix4f m = ps.last().pose();
        prepare();
        // estelas
        for (int d = 0; d < 10; d++) {
            if (ClientState.ghostA[d] < 0.03f) continue;
            float ga = ClientState.ghostA[d] * I * spawnA[d];
            float gx = ClientState.ghost[d][0] * w + 3.5f * s * (float) Math.sin(t * 0.85f + d * 1.9f);
            float gy = ClientState.ghost[d][1] * h + 3.0f * s * (float) Math.cos(t * 0.7f + d * 2.3f);
            radial(m, gx, gy, R * 0.85f, R * 0.85f,
                    new int[]{200, 16, 18, (int) (70 * ga)},
                    new int[]{120, 6, 9, 0});
        }
        // salpicaduras al pulsar
        for (int d = 0; d < 10; d++) {
            if (ClientState.press[d] > 0.02f) {
                pressSplash(g, posX[d], posY[d], R, ClientState.press[d], I * spawnA[d]);
            }
        }
        // anillos de entrada / pulsacion
        for (int d = 0; d < 10; d++) {
            float spawn = clamp01((ClientState.tickCount - ClientState.questionTick - d * 1.2f) / 14f);
            if (spawn < 1f) {
                float rad = R * (1.7f - 0.7f * spawn);
                ring(m, posX[d], posY[d], rad, 3f * Math.max(0.6f, s * 2f),
                        255, (int) lerp(90, 200, ClientState.green), 70,
                        (int) (130 * (1f - spawn) * I));
            }
            if (ClientState.press[d] > 0.02f) {
                float age = 1f - ClientState.press[d];
                ring(m, posX[d], posY[d], R * (1f + 0.9f * age), 2.5f * Math.max(0.6f, s * 2f),
                        255, 80, 70, (int) (130 * ClientState.press[d] * I));
            }
        }
        RenderSystem.enableCull();

        for (int d = 0; d < 10; d++) {
            float spawn = clamp01((ClientState.tickCount - ClientState.questionTick - d * 1.2f) / 14f);
            float ease = easeOutBack(spawn);
            float press = ClientState.press[d];
            float hover = hoverA[d];
            float rd = R * (0.35f + 0.65f * Math.max(0.01f, ease))
                    * (1f + 0.12f * hover + 0.26f * press);
            float alpha = I * spawnA[d];
            if (alpha < 0.01f) continue;
            spikeBlob(g, posX[d], posY[d], rd, t, d * 1.37f, beat, G, alpha, hover);
            float tilt = (hash(d * 1.37f) - 0.5f) * 10f + 1.5f * (float) Math.sin(t * 0.7f + d);
            float ts = 6.8f * s * (0.4f + 0.6f * Math.max(0.02f, ease))
                    * (1f + 0.10f * hover + 0.18f * press);
            bloodText(g, String.valueOf(d), posX[d], posY[d], ts, G, alpha, tilt, false, t, d, hover * 0.25f);
        }

        // pregunta como en el video: "2   X   8" con los simbolos bien separados y la respuesta
        // escrita debajo, dentro de la caja de humo
        int qAge = ClientState.tickCount - ClientState.questionTick;
        float qEase = Math.max(0.05f, easeOutBack(clamp01(qAge / 10f)));
        float qa = I * clamp01(qAge / 6f);
        float y = h * 0.27f;
        float qs = 13.5f * s * qEase;
        float spread = 150f * s;
        String[] parts = ClientState.question.trim().split("\\s+");
        if (parts.length == 3) {
            for (int i = 0; i < 3; i++) {
                float px = w / 2f + (i - 1) * spread;
                float tilt = (i - 1) * 1.8f + 0.8f * (float) Math.sin(t * 0.8f + i);
                bloodText(g, parts[i].toUpperCase(java.util.Locale.ROOT), px, y, qs, G, qa, tilt, true, t,
                        99f + i * 3f, 0f);
            }
        } else {
            bloodText(g, ClientState.question, w / 2f, y, qs, G, qa, 0f, true, t, 99f, 0f);
        }

        String typedText = ClientState.typed.isEmpty() ? "?" : ClientState.typed;
        float tScale = 11f * s * Math.max(0.05f, qEase) * (1f + 0.09f * ClientState.answerFlash);
        float white = ClientState.typed.isEmpty() ? 0.1f : 0.35f;
        float yt = h * 0.41f;
        bloodText(g, typedText, w / 2f, yt, tScale, G, qa, 1f, !ClientState.typed.isEmpty(), t, 50f, white);

        if (ClientState.answerFlash > 0.02f) {
            Font font = Minecraft.getInstance().font;
            Component tc = Component.literal(typedText).withStyle(ChatFormatting.BOLD);
            ps.pushPose();
            ps.translate(w / 2f, yt, 0);
            ps.scale(tScale, tScale, 1f);
            g.drawCenteredString(font, tc, 0, -4,
                    argb((int) (220 * ClientState.answerFlash), 90, 255, 95));
            ps.popPose();
        }
        ps.popPose();
    }
}
