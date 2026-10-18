package br.com.finalcraft.evernifecore.region.playerstate;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.region.flag.EffectLevel;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the session flags changed on one player, slot by slot, and what the player had there before: the first flag to
 * hold a slot saves the player's state, the last to let it go writes it back - so the player leaves with what they had
 * on entering, even when it equals what the region gave. A slot held with {@code persistent} also lands in the player's
 * {@link PlayerStateBackup}: a player who was inside when the server stopped gets it back on the next join
 * ({@link #restoreLeftovers}). One per movement session; calls run on the player's world thread.
 */
public final class HeldPlayerState {

    public static final String TIME = "time";
    public static final String WEATHER = "weather";
    /** Allowed to fly, and flying: the slot {@code allow-fly} and {@code force-fly} share. */
    public static final String FLIGHT = "flight";
    public static final String GAME_MODE = "game-mode";
    private static final String EFFECT = "effect:";

    private static final class Slot {
        final String before;
        final Map<FlagKey, Object> holders = new LinkedHashMap<>();

        Slot(String before) {
            this.before = before;
        }
    }

    private final UUID player;
    private final Map<String, Slot> slots = new HashMap<>();

    public HeldPlayerState(UUID player) {
        this.player = player;
    }

    /** The slot of one effect, by its platform id. */
    public static String effect(String id) {
        return EFFECT + id;
    }

    /**
     * {@code flag} holds {@code slot} at {@code value}. The first holder saves what the player has there now - or what the
     * backup kept, when the server stopped with the player inside and the slot was never given back.
     */
    public synchronized void hold(String slot, RegionFlag<?> flag, Object value, boolean persistent, FPlayer online) {
        Slot held = slots.get(slot);
        if (held == null) {
            PlayerStateBackup backup = backup();
            String kept = backup == null ? null : backup.get(slot);
            held = new Slot(kept != null ? kept : read(access(), online, slot));
            slots.put(slot, held);
            if (persistent && backup != null && kept == null) {
                backup.put(slot, held.before);
            }
        }
        held.holders.put(flag.getKey(), value);
    }

    /** The flags holding {@code slot} now, with their values, in the order they took it. */
    public synchronized Map<FlagKey, Object> getHolders(String slot) {
        Slot held = slots.get(slot);
        return held == null ? Collections.emptyMap() : new LinkedHashMap<>(held.holders);
    }

    /**
     * {@code flag} lets {@code slot} go. The last holder writes back what the player had.
     *
     * @return whether the slot was given back; false while another flag still holds it, or when {@code flag} never did
     */
    public synchronized boolean release(String slot, RegionFlag<?> flag, FPlayer online) {
        Slot held = slots.get(slot);
        if (held == null || held.holders.remove(flag.getKey()) == null || !held.holders.isEmpty()) {
            return false;
        }
        slots.remove(slot);
        give(online, slot, held.before);
        return true;
    }

    /** On join, once the session flags applied: gives back every slot the backup kept that no flag holds now. */
    public synchronized void restoreLeftovers(FPlayer online) {
        PlayerStateBackup backup = backup();
        if (backup == null) {
            return;
        }
        for (Map.Entry<String, String> kept : backup.getSlots().entrySet()) {
            if (!slots.containsKey(kept.getKey())) {
                give(online, kept.getKey(), kept.getValue());
            }
        }
    }

    private void give(FPlayer online, String slot, String before) {
        try {
            write(access(), online, slot, before);
        } catch (RuntimeException refused) {
            ECDebugModule.REGIONS.debug("Could not give {} back their {} ({}): {}", online.getName(), slot, before, refused.toString());
        }
        PlayerStateBackup backup = backup();
        if (backup != null) {
            backup.remove(slot);
        }
    }

    private @Nullable PlayerStateBackup backup() {
        //no PlayerData layer (a bare region test, a platform still booting): memory is all there is
        if (!PlayerController.getConfiguredPDSections().containsKey(PlayerStateBackup.class)) {
            return null;
        }
        //ONLINE: every login loads it, so a player in the world has it in memory
        return PlayerController.getLoadedSection(player, PlayerStateBackup.class);
    }

    private static IPlayerStateAccess access() {
        return EverNifeCore.getPlatform().getPlayerState();
    }

    /** {@code slot} as {@code online} has it now, encoded. */
    static String read(IPlayerStateAccess access, FPlayer online, String slot) {
        switch (slot) {
            case TIME:
                PlayerTime time = access.getTime(online);
                return time == null ? "" : time.encode();
            case WEATHER:
                String weather = access.getWeather(online);
                return weather == null ? "" : weather;
            case FLIGHT:
                return access.getAllowFlight(online) + "|" + access.isFlying(online);
            case GAME_MODE:
                return access.getGameMode(online).name();
            default:
                ActiveEffect effect = access.getEffect(online, effectId(slot));
                return effect == null ? "" : effect.getEffect().getLevel() + "|" + effect.getRemaining().toMillis();
        }
    }

    /** Puts {@code before} - what {@link #read} gave - back on {@code online}. */
    static void write(IPlayerStateAccess access, FPlayer online, String slot, String before) {
        switch (slot) {
            case TIME:
                access.setTime(online, before.isEmpty() ? null : PlayerTime.decode(before), Duration.ZERO);
                return;
            case WEATHER:
                access.setWeather(online, before.isEmpty() ? null : before);
                return;
            case FLIGHT:
                String[] flight = before.split("\\|", 2);
                boolean allow = Boolean.parseBoolean(flight[0]);
                access.setAllowFlight(online, allow);
                if (allow) {
                    access.setFlying(online, flight.length > 1 && Boolean.parseBoolean(flight[1]));
                }
                return;
            case GAME_MODE:
                access.setGameMode(online, ECGameMode.valueOf(before));
                return;
            default:
                String id = effectId(slot);
                access.removeEffect(online, id);
                if (!before.isEmpty()) {
                    String[] effect = before.split("\\|", 2);
                    access.addEffect(online, EffectLevel.of(id, Integer.parseInt(effect[0])), Duration.ofMillis(Long.parseLong(effect[1])));
                }
        }
    }

    private static String effectId(String slot) {
        if (!slot.startsWith(EFFECT)) {
            throw new IllegalArgumentException("Unknown player-state slot '" + slot + "': it is time, weather, flight, game-mode or effect:<id>.");
        }
        return slot.substring(EFFECT.length());
    }
}
