package br.com.finalcraft.evernifecore.player.state;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventPriority;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.eventbus.ECSubscribeOptions;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * What was changed on one player, slot by slot, by whom, and what the player had there before: the first
 * {@link StateHolder} - a session flag, a held effect - to hold a slot saves the player's state, the last to let it go
 * writes it back, so the player ends with what they had before, even when it equals what was given. While the slot is
 * shared, the latest holder's value is the one on the player; when it lets go, the holder now latest puts its own back.
 * A slot held with
 * {@code persistent} also lands in the player's {@link PlayerStateBackup}: a player who was inside when the server
 * stopped gets it back on the next join ({@link #restoreLeftovers}). One per player while online ({@link #of}); on quit
 * every slot still held is given back ({@link #forget}). Calls run on the player's world thread.
 */
public final class HeldPlayerState {

    public static final String TIME = "time";
    public static final String WEATHER = "weather";
    /** Allowed to fly, and flying: the slot {@code allow-fly} and {@code force-fly} share. */
    public static final String FLIGHT = "flight";
    public static final String GAME_MODE = "game-mode";
    private static final String EFFECT = "effect:";

    private static final Map<UUID, HeldPlayerState> PLAYERS = new ConcurrentHashMap<>();
    private static @Nullable ECEventSubscription<ECPlayerQuitEvent> quitSubscription;

    private static final class Slot {
        final String before;
        final Map<String, Object> holders = new LinkedHashMap<>();
        final Map<String, Consumer<FPlayer>> rewrites = new HashMap<>();

        Slot(String before) {
            this.before = before;
        }
    }

    private final UUID player;
    private final Map<String, Slot> slots = new HashMap<>();

    private HeldPlayerState(UUID player) {
        this.player = player;
    }

    /** The state held on {@code player}, created on first use; the same instance until {@link #forget}. */
    public static HeldPlayerState of(UUID player) {
        return PLAYERS.computeIfAbsent(player, HeldPlayerState::new);
    }

    /**
     * {@code player} quit: every slot still held is given back on {@code online} (when it is still there to write to)
     * and the instance is dropped. It also runs by itself on every quit after the region sessions let go of their
     * slots, so a slot held outside any region is given back too; calling it again does nothing.
     */
    public static void forget(UUID player, @Nullable FPlayer online) {
        HeldPlayerState state = PLAYERS.remove(player);
        if (state != null && online != null) {
            state.giveAllBack(online);
        }
        synchronized (HeldPlayerState.class) {
            if (PLAYERS.isEmpty() && quitSubscription != null) {
                quitSubscription.unsubscribe();
                quitSubscription = null;
            }
        }
    }

    private synchronized void giveAllBack(FPlayer online) {
        List<Map.Entry<String, Slot>> held = new ArrayList<>(slots.entrySet());
        slots.clear();
        for (Map.Entry<String, Slot> slot : held) {
            give(online, slot.getKey(), slot.getValue().before);
        }
    }

    private static void watchQuits() {
        synchronized (HeldPlayerState.class) {
            if (quitSubscription == null) {
                //late: the region sessions let go of their slots first, on the normal priority
                quitSubscription = ECEventBus.global().subscribe(ECPlayerQuitEvent.class, ECSubscribeOptions.defaults()
                        .withPriority(ECEventPriority.LATE), event -> forget(event.getPlayerData().getUniqueId(), event.getPlayer()));
            }
        }
    }

    /** The slot of one effect, by its platform id. */
    public static String effect(String id) {
        return EFFECT + id;
    }

    /**
     * {@code holder} holds {@code slot} at {@code value}; changing the player is the holder's part. The first holder
     * saves what the player has there now - or what the backup kept, when the server stopped with the player inside and
     * the slot was never given back.
     *
     * @param rewrite puts {@code value} on the player again, when a holder that came after lets the slot go
     */
    public synchronized void hold(String slot, StateHolder holder, Object value, Consumer<FPlayer> rewrite, boolean persistent,
                                  FPlayer online) {
        watchQuits();
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
        //a holder that takes the slot again just wrote its value: it is the latest now
        held.holders.remove(holder.getHolderId());
        held.holders.put(holder.getHolderId(), value);
        held.rewrites.put(holder.getHolderId(), rewrite);
    }

    /** The holders of {@code slot} now, by {@link StateHolder#getHolderId() id}, with their values, the latest to (re)take it last. */
    public synchronized Map<String, Object> getHolders(String slot) {
        Slot held = slots.get(slot);
        return held == null ? Collections.emptyMap() : new LinkedHashMap<>(held.holders);
    }

    /**
     * {@code holder} lets {@code slot} go. The last holder writes back what the player had; while others still hold it,
     * the latest of them puts its value back when {@code holder} was the latest (its value was the one on the player).
     *
     * @return whether the slot was given back; false while another holder still holds it, or when {@code holder} never did
     */
    public synchronized boolean release(String slot, StateHolder holder, FPlayer online) {
        Slot held = slots.get(slot);
        if (held == null || !held.holders.containsKey(holder.getHolderId())) {
            return false;
        }
        String latest = latest(held);
        held.holders.remove(holder.getHolderId());
        held.rewrites.remove(holder.getHolderId());
        if (!held.holders.isEmpty()) {
            if (latest.equals(holder.getHolderId())) {
                rewrite(online, slot, held.rewrites.get(latest(held)));
            }
            return false;
        }
        slots.remove(slot);
        give(online, slot, held.before);
        return true;
    }

    /** {@code holder} lets go of every slot it holds, each as {@link #release} does - for a holder that does not track its slots. */
    public synchronized void releaseAll(StateHolder holder, FPlayer online) {
        for (String slot : new ArrayList<>(slots.keySet())) {
            release(slot, holder, online);
        }
    }

    /** On join, once the session flags applied: gives back every slot the backup kept that nobody holds now. */
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

    private static String latest(Slot held) {
        String latest = null;
        for (String id : held.holders.keySet()) {
            latest = id;
        }
        return latest;
    }

    private static void rewrite(FPlayer online, String slot, Consumer<FPlayer> rewrite) {
        try {
            rewrite.accept(online);
        } catch (RuntimeException refused) {
            ECDebugModule.REGIONS.debug("Could not put {}'s {} back for the holder still there: {}", online.getName(), slot, refused.toString());
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
