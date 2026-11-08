package com.mathgulag.game;

import com.mathgulag.net.StatePacket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Logica del gulag (solo servidor). */
public class GulagManager {
    /** Tiempo para responder la pregunta (12 s) y para pasar la bomba despues de acertar (10 s). */
    public static final int QUESTION_TICKS = 12 * 20;
    public static final int PASS_TICKS = 10 * 20;

    private static boolean active;
    private static final List<UUID> players = new ArrayList<>();
    private static UUID holder;
    private static int phase, ticksLeft, ticksTotal, answer, lastAnswer = -1;
    private static String question = "";
    private static final StringBuilder typed = new StringBuilder();
    private static final Random RNG = new Random();

    // ---------------------------------------------------------------- inicio / fin

    public static int start(MinecraftServer server, Collection<ServerPlayer> targets) {
        if (active) end(server);
        players.clear();
        StringBuilder names = new StringBuilder();
        for (ServerPlayer p : targets) {
            players.add(p.getUUID());
            if (names.length() > 0) names.append(", ");
            names.append(p.getGameProfile().getName());
        }
        if (players.isEmpty()) return 0;
        active = true;
        broadcast(server, Component.literal("\u2620 GULAG \u2620 ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal(names.toString()).withStyle(ChatFormatting.RED)));
        holder = players.get(RNG.nextInt(players.size()));
        startQuestion(server);
        return players.size();
    }

    public static void end(MinecraftServer server) {
        for (UUID id : new ArrayList<>(players)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                p.removeEffect(MobEffects.GLOWING);
                send(p, new StatePacket(0, "", 0, 0, ""));
            }
        }
        players.clear();
        holder = null;
        phase = 0;
        active = false;
    }

    // ---------------------------------------------------------------- turnos

    private static void startQuestion(MinecraftServer server) {
        ServerPlayer h = server.getPlayerList().getPlayer(holder);
        if (h == null) {
            eliminate(server, holder, false);
            return;
        }
        phase = 1;
        typed.setLength(0);
        ticksLeft = ticksTotal = QUESTION_TICKS;
        generateQuestion();
        syncAll(server);
    }

    private static void generateQuestion() {
        int a = 0, b = 0;
        char op = '+';
        do {
            int k = RNG.nextInt(3);
            if (k == 0) {
                op = '+';
                a = 1 + RNG.nextInt(15);
                b = 1 + RNG.nextInt(15);
                answer = a + b;
            } else if (k == 1) {
                op = '-';
                a = 2 + RNG.nextInt(18);
                b = 1 + RNG.nextInt(a);
                answer = a - b;
            } else {
                op = 'X';
                a = 2 + RNG.nextInt(8);
                b = 2 + RNG.nextInt(8);
                answer = a * b;
            }
        } while (answer == lastAnswer);
        lastAnswer = answer;
        question = a + " " + op + " " + b;
    }

    private static void syncAll(MinecraftServer server) {
        ServerPlayer h = server.getPlayerList().getPlayer(holder);
        String holderName = h != null ? h.getGameProfile().getName() : "?";
        for (UUID id : new ArrayList<>(players)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            if (id.equals(holder)) send(p, new StatePacket(phase, question, ticksLeft, ticksTotal, holderName));
            else send(p, new StatePacket(3, "", ticksLeft, ticksTotal, holderName));
        }
    }

    public static void tick(MinecraftServer server) {
        if (!active || holder == null) return;
        ServerPlayer h = server.getPlayerList().getPlayer(holder);
        if (h == null) {
            eliminate(server, holder, false);
            return;
        }
        ticksLeft--;
        if (ticksLeft <= 0) {
            eliminate(server, holder, true);
            return;
        }
        ServerLevel lvl = (ServerLevel) h.level();
        boolean urgent = ticksLeft < 60;
        if (ticksLeft % (urgent ? 10 : 20) == 0) {
            lvl.playSound(null, h.getX(), h.getY(), h.getZ(),
                    urgent ? SoundEvents.HONEY_BLOCK_HIT : SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
                    1.2f, urgent ? 0.5f : 1f);
        }
        if (ticksLeft % 4 == 0) {
            lvl.sendParticles(ParticleTypes.SMOKE, h.getX(), h.getY() + 2.1, h.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
        }
        if (ticksLeft % 20 == 0) {
            h.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, false));
        }
    }

    // ---------------------------------------------------------------- acciones del jugador

    public static void onAnswer(ServerPlayer sp, int digit) {
        if (!active || phase != 1 || !sp.getUUID().equals(holder)) return;
        if (digit < 0 || digit > 9) return;
        MinecraftServer server = sp.getServer();
        if (server == null) return;
        typed.append(digit);
        if (typed.length() < String.valueOf(answer).length()) return;
        if (Integer.parseInt(typed.toString()) == answer) {
            phase = 2;
            ticksLeft = ticksTotal = PASS_TICKS;
            ((ServerLevel) sp.level()).playSound(null, sp.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1f, 1f);
            ((ServerLevel) sp.level()).playSound(null, sp.blockPosition(),
                    SoundEvents.HONEY_BLOCK_PLACE, SoundSource.PLAYERS, 1f, 0.7f);
            syncAll(server);
        } else {
            eliminate(server, holder, true);
        }
    }

    /** @return true si hay que cancelar el golpe. */
    public static boolean onAttack(Player attacker, Entity target) {
        if (!active || !(attacker instanceof ServerPlayer sp) || !sp.getUUID().equals(holder)) return false;
        MinecraftServer server = sp.getServer();
        if (server == null) return false;
        if (phase == 1) {
            sp.displayClientMessage(Component.literal("Primero responde la pregunta").withStyle(ChatFormatting.RED), true);
            return true;
        }
        if (phase == 2) {
            UUID next;
            if (players.size() >= 2) {
                if (target instanceof ServerPlayer tp && tp != sp && players.contains(tp.getUUID())) {
                    next = tp.getUUID();
                } else {
                    sp.displayClientMessage(Component.literal("Golpea a otro jugador del gulag").withStyle(ChatFormatting.RED), true);
                    return true;
                }
            } else if (target instanceof LivingEntity) {
                next = sp.getUUID(); // modo prueba con una sola persona
            } else {
                return true;
            }
            sp.removeEffect(MobEffects.GLOWING);
            holder = next;
            startQuestion(server);
        }
        return true;
    }

    public static void onDeath(ServerPlayer sp) {
        if (active && players.contains(sp.getUUID()) && sp.getServer() != null) {
            eliminate(sp.getServer(), sp.getUUID(), false);
        }
    }

    public static void onLogout(ServerPlayer sp, MinecraftServer server) {
        if (active && players.contains(sp.getUUID())) {
            eliminate(server, sp.getUUID(), false);
        }
    }

    // ---------------------------------------------------------------- eliminacion

    private static void eliminate(MinecraftServer server, UUID id, boolean boom) {
        boolean wasHolder = id.equals(holder);
        players.remove(id);
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        String name = "Jugador";
        if (p != null) {
            name = p.getGameProfile().getName();
            p.removeEffect(MobEffects.GLOWING);
            send(p, new StatePacket(0, "", 0, 0, ""));
            if (boom) {
                ServerLevel lvl = (ServerLevel) p.level();
                lvl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 140, 0.4, 0.9, 0.4, 0.7);
                lvl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
                lvl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2f, 1f);
                lvl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 1f);
                lvl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.HONEY_BLOCK_BREAK, SoundSource.PLAYERS, 1.5f, 0.5f);
                p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
            }
        }
        broadcast(server, Component.literal("\u2620 ").withStyle(ChatFormatting.DARK_RED)
                .append(Component.literal(name).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal(" qued\u00f3 eliminado").withStyle(ChatFormatting.DARK_RED)));
        if (!active) return;
        if (players.size() >= 2) {
            if (wasHolder) {
                holder = players.get(RNG.nextInt(players.size()));
                startQuestion(server);
            } else {
                syncAll(server);
            }
        } else {
            if (players.size() == 1) {
                ServerPlayer w = server.getPlayerList().getPlayer(players.get(0));
                if (w != null) {
                    broadcast(server, Component.literal("\u2605 " + w.getGameProfile().getName() + " sobrevivi\u00f3 al gulag")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                }
            }
            end(server);
        }
    }

    // ---------------------------------------------------------------- utilidades

    private static void send(ServerPlayer p, StatePacket pk) {
        ServerPlayNetworking.send(p, pk);
    }

    private static void broadcast(MinecraftServer server, Component c) {
        server.getPlayerList().broadcastSystemMessage(c, false);
    }
}
