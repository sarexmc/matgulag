package com.mathgulag.client;

import com.mathgulag.net.AnswerPacket;
import com.mathgulag.net.StatePacket;
import java.util.Random;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Estado y animaciones del cliente (solo se carga en el cliente). */
public class ClientState {
    public static int phase, ticksLeft, ticksTotal = 1, tickCount, nextShuffle, layout = 1;
    public static String question = "", holder = "", typed = "";
    public static float green, intensity;

    /** Tick del cliente en que empezo la pregunta (para animar la entrada). */
    public static int questionTick = Integer.MIN_VALUE / 2;
    /** Tick en que este cliente recibio la bomba (para el corazon gigante). */
    public static int holderSince = Integer.MIN_VALUE / 2;
    /** Respuesta correcta deducida de la pregunta (feedback rapido). */
    public static int expected = -1;

    /** Animaciones por digito. */
    public static final float[] press = new float[10];
    public static final float[] ghostA = new float[10];
    public static final float[][] ghost = new float[10][2];
    /** Destellos y temblor. */
    public static float correctFlash, wrongFlash, hurtFlash, shakeAmount, barPulse, answerFlash;
    /** Desplazamiento de temblor calculado por el renderer cada frame. */
    public static float shakeX, shakeY;

    private static float lastHealth = Float.NaN;

    /** Distribucion A (primera captura, 1920x1080). */
    public static final float[][] SLOT_A = {
            {520 / 1920f, 405 / 1080f}, {1405 / 1920f, 400 / 1080f}, {245 / 1920f, 600 / 1080f},
            {1570 / 1920f, 610 / 1080f}, {1030 / 1920f, 755 / 1080f}, {1345 / 1920f, 755 / 1080f},
            {725 / 1920f, 870 / 1080f}, {1690 / 1920f, 810 / 1080f}, {420 / 1920f, 925 / 1080f},
            {1500 / 1920f, 930 / 1080f}};
    private static final int[] DIGIT_A = {5, 3, 4, 1, 9, 2, 0, 8, 6, 7};
    /** Distribucion B (segunda captura). */
    public static final float[][] SLOT_B = {
            {.164f, .536f}, {.283f, .580f}, {.183f, .749f}, {.381f, .767f}, {.499f, .641f},
            {.579f, .765f}, {.702f, .600f}, {.821f, .482f}, {.774f, .791f}, {.873f, .856f}};
    private static final int[] DIGIT_B = {2, 3, 4, 0, 7, 1, 6, 5, 9, 8};

    public static final float[][] cur = new float[10][2];
    public static final int[] perm = new int[10];
    private static final Random RNG = new Random();

    private static float[][] slots() {
        return layout == 0 ? SLOT_A : SLOT_B;
    }

    public static void apply(StatePacket p) {
        int oldPhase = phase;
        String oldQuestion = question;
        String oldHolder = holder;
        phase = p.phase();
        question = p.question();
        holder = p.holder();
        ticksLeft = p.ticksLeft();
        ticksTotal = Math.max(1, p.ticksTotal());

        boolean newQuestion = phase == 1
                && (oldPhase != 1 || !question.equals(oldQuestion) || !holder.equals(oldHolder));
        if (newQuestion) {
            typed = "";
            questionTick = tickCount;
            holderSince = tickCount;
            expected = parseQuestion(question);
            answerFlash = 0f;
            for (int d = 0; d < 10; d++) {
                press[d] = 0f;
                ghostA[d] = 0f;
            }
        }

        if (oldPhase == 0 && phase != 0) {
            layout = 1;
            int[] init = DIGIT_B;
            for (int s = 0; s < 10; s++) perm[init[s]] = s;
            for (int d = 0; d < 10; d++) {
                cur[d][0] = SLOT_B[perm[d]][0];
                cur[d][1] = SLOT_B[perm[d]][1];
                ghost[d][0] = cur[d][0];
                ghost[d][1] = cur[d][1];
                ghostA[d] = 0f;
                press[d] = 0f;
            }
            nextShuffle = tickCount + 100;
            play(SoundEvents.WARDEN_HEARTBEAT, 1.0f, 1.0f);
            play(SoundEvents.SKELETON_HURT, 0.35f, 0.8f);
            play(SoundEvents.HONEY_BLOCK_SLIDE, 0.9f, 0.55f);
            play(SoundEvents.HONEY_BLOCK_PLACE, 0.8f, 0.5f);
        } else if (phase == 2 && oldPhase != 2) {
            play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
            play(SoundEvents.HONEY_BLOCK_PLACE, 0.8f, 0.7f);
        } else if (phase == 0 && oldPhase != 0) {
            play(SoundEvents.HONEY_BLOCK_SLIDE, 0.8f, 0.6f);
            play(SoundEvents.HONEY_BLOCK_BREAK, 0.6f, 0.5f);
            typed = "";
        }
    }

    private static void shuffle() {
        layout = RNG.nextInt(2);
        int[] slotIdx = new int[10];
        for (int i = 0; i < 10; i++) slotIdx[i] = i;
        for (int i = 9; i > 0; i--) {
            int j = RNG.nextInt(i + 1);
            int t = slotIdx[i];
            slotIdx[i] = slotIdx[j];
            slotIdx[j] = t;
        }
        System.arraycopy(slotIdx, 0, perm, 0, 10);
    }

    /** Llamado al pulsar un digito: efecto local + paquete al servidor. */
    public static void answer(int d) {
        if (phase != 1 || typed.length() >= 3) return;
        typed += d;
        press[d] = 1f;
        float gx = cur[d][0] - ghost[d][0];
        float gy = cur[d][1] - ghost[d][1];
        ghostA[d] = Math.max(ghostA[d], 0.4f + 8f * (Math.abs(gx) + Math.abs(gy)));
        // cada digito suena mas agudo: como pulsar teclas de un organo, pero de miel / carne
        play(SoundEvents.HONEY_BLOCK_STEP, 1.0f, 0.55f + d * 0.06f);
        play(SoundEvents.HONEY_BLOCK_SLIDE, 0.35f, 0.7f + d * 0.05f);
        if (ClientPlayNetworking.canSend(AnswerPacket.TYPE)) {
            ClientPlayNetworking.send(new AnswerPacket(d));
        }
        if (expected >= 0 && typed.length() == String.valueOf(expected).length()) {
            int value = typedValue();
            if (value == expected) {
                correctFlash = 1f;
                answerFlash = 1f;
                play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
                play(SoundEvents.HONEY_DRINK, 0.8f, 0.8f);
            } else {
                wrongFlash = 1f;
                shakeAmount = Math.max(shakeAmount, 1f);
                hurtFlash = Math.max(hurtFlash, 0.8f);
                play(SoundEvents.SKELETON_HURT, 1.0f, 0.6f);
                play(SoundEvents.HONEY_BLOCK_BREAK, 1.0f, 0.5f);
            }
        }
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        tickCount++;
        if (mc.level == null || mc.player == null) {
            phase = 0;
            intensity = 0;
            green = 0;
            typed = "";
            lastHealth = Float.NaN;
            return;
        }
        intensity += ((phase != 0 ? 1f : 0f) - intensity) * 0.12f;
        if (intensity < 0.005f && phase == 0) intensity = 0;
        green += ((phase == 2 ? 1f : 0f) - green) * 0.18f;
        if (phase != 0 && ticksLeft > 0) {
            ticksLeft--;
            if (ticksLeft % 10 == 0) barPulse = 1f;
        }

        for (int d = 0; d < 10; d++) {
            press[d] = Math.max(0f, press[d] - 0.075f);
            ghostA[d] *= 0.90f;
            float dx = cur[d][0] - ghost[d][0];
            float dy = cur[d][1] - ghost[d][1];
            if (Math.abs(dx) + Math.abs(dy) > 0.004f) {
                ghostA[d] = Math.min(1f, ghostA[d] + 0.09f);
            }
            ghost[d][0] += dx * 0.15f;
            ghost[d][1] += dy * 0.15f;
        }
        correctFlash *= 0.90f;
        wrongFlash *= 0.88f;
        hurtFlash *= 0.90f;
        shakeAmount *= 0.86f;
        if (shakeAmount < 0.001f) shakeAmount = 0f;
        barPulse *= 0.85f;
        answerFlash *= 0.94f;

        float hp = mc.player.getHealth();
        if (!Float.isNaN(lastHealth) && hp < lastHealth - 0.05f && phase != 0) {
            hurtFlash = 1f;
            shakeAmount = Math.max(shakeAmount, 0.85f);
            play(SoundEvents.SKELETON_HURT, 1.0f, 0.5f);
            play(SoundEvents.HONEY_BLOCK_HIT, 0.8f, 0.45f);
        }
        lastHealth = hp;

        if (phase != 0 && ticksLeft > 0 && ticksLeft <= 60 && ticksLeft % 10 == 0) {
            play(SoundEvents.HONEY_BLOCK_HIT, 0.6f, 0.4f + (60 - ticksLeft) / 60f * 0.5f);
        }

        if (phase == 1 && tickCount >= nextShuffle) {
            shuffle();
            nextShuffle = tickCount + 70;
        }
        float[][] sl = slots();
        for (int d = 0; d < 10; d++) {
            cur[d][0] += (sl[perm[d]][0] - cur[d][0]) * 0.14f;
            cur[d][1] += (sl[perm[d]][1] - cur[d][1]) * 0.14f;
        }
        if (phase == 1 && mc.screen == null) {
            mc.setScreen(new GulagScreen());
        } else if (phase != 1 && mc.screen instanceof GulagScreen) {
            mc.setScreen(null);
        }
    }

    private static int typedValue() {
        try {
            return Integer.parseInt(typed);
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    private static int parseQuestion(String q) {
        try {
            String[] parts = q.trim().split("\\s+");
            if (parts.length != 3) return -1;
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[2]);
            return switch (parts[1]) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "X", "x", "*" -> a * b;
                default -> -1;
            };
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** Reproduce un sonido de interfaz. OJO: primero volumen y despues tono. */
    private static void play(SoundEvent s, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    /** True si este jugador es quien lleva la bomba (el que muestra la calavera). */
    public static boolean hasSkull(net.minecraft.world.entity.player.Player p) {
        return phase != 0 && !holder.isEmpty() && p.getGameProfile().getName().equals(holder);
    }
}
