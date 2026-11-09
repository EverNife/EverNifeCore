package br.com.finalcraft.evernifecore.player.state;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerFullyLoggedInEvent;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventPriority;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.eventbus.ECSubscribeOptions;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.time.ECScheduler;
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
 * A slot held {@link HoldOptions#persistent()} also lands in the player's {@link PlayerStateBackup}: a player who was inside
 * when the server stopped, or whose give-back the platform refused, gets it back on the next join ({@link #restoreLeftovers},
 * run on every login from {@link #start()}). An inventory whose give-back was refused lands there even when held
 * {@link HoldOptions#memory()}, so it is never lost in silence.
 *
 * <p>One per player while online ({@link #of}). On quit each holder's {@link QuitPolicy} decides ({@link #forget}): a slot
 * no holder keeps is given back, and one kept stays on the player, saved with them, until they reconnect or the last window
 * ends. Calls run on the player's world thread, except {@link #releaseAway}.</p>
 */
public final class HeldPlayerState {

    public static final String TIME = "time";
    public static final String WEATHER = "weather";
    /** Allowed to fly, and flying: the slot {@code allow-fly} and {@code force-fly} share. */
    public static final String FLIGHT = "flight";
    public static final String GAME_MODE = "game-mode";
    /**
     * Everything the player carries, as {@link IPlayerStateAccess#getInventory} encodes it: the holder empties it or
     * hands a kit, the last to let go gives the player's own back. A death while held drops what the player carries
     * then; a holder that leaves the own inventory in place clears those drops, or they come back twice.
     */
    public static final String INVENTORY = "inventory";
    private static final String EFFECT = "effect:";

    private static final Map<UUID, HeldPlayerState> PLAYERS = new ConcurrentHashMap<>();
    private static @Nullable ECEventSubscription<ECPlayerQuitEvent> quitSubscription;
    private static @Nullable ECEventSubscription<ECPlayerFullyLoggedInEvent> loginSubscription;

    private static final class Slot {
        final String before;
        final Map<String, Object> holders = new LinkedHashMap<>();
        final Map<String, Consumer<FPlayer>> rewrites = new HashMap<>();
        final Map<String, QuitPolicy> policies = new HashMap<>();
        //survived a quit: the next life of the player still has it, so a join does not take it for a past life's
        boolean kept;
        //the latest holder let go while the player was away, so its value is the one saved with them
        boolean rewriteOnReturn;

        Slot(String before) {
            this.before = before;
        }
    }

    private final UUID player;
    private final Map<String, Slot> slots = new HashMap<>();
    private boolean away;
    private long windowId;

    private HeldPlayerState(UUID player) {
        this.player = player;
    }

    /** The state held on {@code player}, created on first use; the same instance until {@link #forget}. */
    public static HeldPlayerState of(UUID player) {
        return PLAYERS.computeIfAbsent(player, HeldPlayerState::new);
    }

    /**
     * Gives back on every login what the backup kept, with or without a region around, until {@link #closeAll()}. The
     * crash this covers leaves no hold in this JVM, so it cannot wait for the first {@link #hold}.
     */
    public static synchronized void start() {
        if (loginSubscription == null) {
            loginSubscription = ECEventBus.global().subscribe(ECPlayerFullyLoggedInEvent.class, HeldPlayerState::onLogin);
        }
    }

    /** Stops the login watch and drops every state and window; what the backup kept waits for the next join. */
    public static void closeAll() {
        for (HeldPlayerState state : new ArrayList<>(PLAYERS.values())) {
            state.endWindow();
        }
        PLAYERS.clear();
        synchronized (HeldPlayerState.class) {
            if (loginSubscription != null) {
                loginSubscription.unsubscribe();
                loginSubscription = null;
            }
        }
        unwatchQuitsWhenNobodyIsLeft();
    }

    private static void onLogin(ECPlayerFullyLoggedInEvent event) {
        FPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        HeldPlayerState state = PLAYERS.get(id);
        PlayerStateBackup backup = backup(id);
        //every login passes here: nothing to give back asks the platform for nothing
        if ((state == null || !state.isAway()) && (backup == null || backup.getSlots().isEmpty())) {
            return;
        }
        access().runWhenWritable(player, () -> {
            HeldPlayerState now = PLAYERS.get(id);
            //no state in the registry: nothing is held, and a login must not leave an instance behind for no quit to drop
            (now != null ? now : new HeldPlayerState(id)).restoreLeftovers(player);
        });
    }

    /**
     * {@code player} quit, or with {@code online} null is joining. On a quit each holder's {@link QuitPolicy} decides: a slot
     * nobody keeps is given back on {@code online}, one kept stays on the player with a window for each keeper; it also runs
     * by itself on every quit after the region sessions let go of their slots, so a slot held outside any region is covered
     * too, and calling it again does nothing. On a join whatever memory has is a past life's and goes, except what a window kept.
     */
    public static void forget(UUID player, @Nullable FPlayer online) {
        HeldPlayerState state = PLAYERS.get(player);
        if (state != null) {
            if (online != null) {
                state.quit(online);
            } else {
                state.dropWhatNoWindowKept();
            }
            if (state.isEmpty()) {
                PLAYERS.remove(player, state);
            }
        }
        unwatchQuitsWhenNobodyIsLeft();
    }

    private static void unwatchQuitsWhenNobodyIsLeft() {
        synchronized (HeldPlayerState.class) {
            if (PLAYERS.isEmpty() && quitSubscription != null) {
                quitSubscription.unsubscribe();
                quitSubscription = null;
            }
        }
    }

    private synchronized boolean isAway() {
        return away;
    }

    private synchronized boolean isEmpty() {
        return slots.isEmpty();
    }

    private synchronized void dropWhatNoWindowKept() {
        slots.values().removeIf(held -> !held.kept);
    }

    private synchronized void endWindow() {
        away = false;
        windowId++; //the expiry tasks already scheduled find another id and do nothing
    }

    private synchronized void quit(FPlayer online) {
        if (away) {
            return; //the second call of the same quit
        }
        long window = ++windowId;
        for (Map.Entry<String, Slot> entry : new ArrayList<>(slots.entrySet())) {
            String slot = entry.getKey();
            Slot held = entry.getValue();
            Map<String, Duration> keepers = askHolders(online, slot, held);
            if (keepers.isEmpty() || !backupHolds(slot, held.before)) {
                slots.remove(slot);
                give(online, slot, held.before);
                continue;
            }
            String latestBefore = latest(held);
            held.holders.keySet().retainAll(keepers.keySet());
            held.rewrites.keySet().retainAll(keepers.keySet());
            held.policies.keySet().retainAll(keepers.keySet());
            held.kept = true;
            away = true;
            if (!latestBefore.equals(latest(held))) {
                rewrite(online, slot, held.rewrites.get(latest(held))); //still writable, and what is saved with the player now
            }
            for (Map.Entry<String, Duration> keeper : keepers.entrySet()) {
                String holderId = keeper.getKey();
                ECScheduler.scheduleAsync(() -> ECScheduler.onMainThread(() -> expire(window, slot, holderId)), keeper.getValue().toMillis());
            }
        }
    }

    /** The holders of {@code slot} that keep it through this quit, each with its window, in the order they came. */
    private Map<String, Duration> askHolders(FPlayer online, String slot, Slot held) {
        Map<String, Duration> keepers = new LinkedHashMap<>();
        for (String holderId : new ArrayList<>(held.holders.keySet())) {
            QuitPolicy policy = held.policies.get(holderId);
            Duration window = policy.isDelegate() ? askDelegate(online, slot, held, holderId) : policy.getWindow();
            if (window != null) {
                keepers.put(holderId, window);
            }
        }
        return keepers;
    }

    private @Nullable Duration askDelegate(FPlayer online, String slot, Slot held, String holderId) {
        String current;
        try {
            current = read(access(), online, slot);
        } catch (RuntimeException refused) {
            ECDebugModule.REGIONS.debug("Could not read {}'s {} for holder {}, so it is given back: {}", online.getName(), slot, holderId, refused.toString());
            return null;
        }
        HeldStateQuitEvent.Delegated asked = new HeldStateQuitEvent.Delegated(player, slot, holderId, held.before, current, online);
        ECEventBus.global().post(asked);
        return asked.getAnswer();
    }

    /** A window only exists with the {@code before} in the player's data: that is what the next join gives back. */
    private boolean backupHolds(String slot, String before) {
        PlayerStateBackup backup = backup();
        if (backup == null) {
            ECDebugModule.REGIONS.debug("No PlayerData layer to keep {} of {} through a quit: it is given back now.", slot, player);
            return false;
        }
        if (backup.get(slot) == null) {
            backup.put(slot, before); //a hold in memory whose delegate answered keepFor
        }
        return true;
    }

    private void expire(long window, String slot, String holderId) {
        HeldStateQuitEvent.Expired expired;
        synchronized (this) {
            Slot held = slots.get(slot);
            if (window != windowId || held == null || !held.holders.containsKey(holderId)) {
                return; //reconnected, released away, or closed
            }
            expired = new HeldStateQuitEvent.Expired(player, slot, holderId, held.before);
            dropAway(slot, held, holderId);
        }
        ECEventBus.global().post(expired);
    }

    /**
     * {@code holder} lets go of {@code slot} of a player who is away on a window: no write, no expiry event later, and when
     * it was the last holder the next join gives the player's own back from the backup. For an arena that ends during the window.
     * Any thread.
     *
     * @return false, touching nothing, when the player is not away or {@code holder} does not hold the slot
     */
    public static boolean releaseAway(UUID player, String slot, StateHolder holder) {
        HeldPlayerState state = PLAYERS.get(player);
        return state != null && state.letGoAway(slot, holder.getHolderId());
    }

    private synchronized boolean letGoAway(String slot, String holderId) {
        Slot held = slots.get(slot);
        if (!away || held == null || !held.holders.containsKey(holderId)) {
            return false;
        }
        dropAway(slot, held, holderId);
        return true;
    }

    private void dropAway(String slot, Slot held, String holderId) {
        String latest = latest(held);
        held.holders.remove(holderId);
        held.rewrites.remove(holderId);
        held.policies.remove(holderId);
        if (held.holders.isEmpty()) {
            slots.remove(slot);
        } else if (latest.equals(holderId)) {
            held.rewriteOnReturn = true;
        }
        if (slots.isEmpty()) {
            endWindow();
            PLAYERS.remove(player, this);
            unwatchQuitsWhenNobodyIsLeft();
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
     * @param options whether the slot survives a server stop, and what this holder does when the player quits
     */
    public synchronized void hold(String slot, StateHolder holder, Object value, Consumer<FPlayer> rewrite, HoldOptions options,
                                  FPlayer online) {
        watchQuits();
        Slot held = slots.get(slot);
        if (held == null) {
            PlayerStateBackup backup = backup();
            String kept = backup == null ? null : backup.get(slot);
            held = new Slot(kept != null ? kept : read(access(), online, slot));
            slots.put(slot, held);
        }
        if (options.isPersistent()) {
            PlayerStateBackup backup = backup();
            if (backup != null && backup.get(slot) == null) {
                backup.put(slot, held.before);
            }
        }
        //a holder that takes the slot again just wrote its value: it is the latest now
        held.holders.remove(holder.getHolderId());
        held.holders.put(holder.getHolderId(), value);
        held.rewrites.put(holder.getHolderId(), rewrite);
        held.policies.put(holder.getHolderId(), options.getQuitPolicy());
    }

    /** The holders of {@code slot} now, by {@link StateHolder#getHolderId() id}, with their values, the latest to (re)take it last. */
    public synchronized Map<String, Object> getHolders(String slot) {
        Slot held = slots.get(slot);
        return held == null ? Collections.emptyMap() : new LinkedHashMap<>(held.holders);
    }

    /**
     * {@code holder} lets {@code slot} go. The last holder writes back what the player had; while others still hold it,
     * the latest of them puts its value back when {@code holder} was the latest (its value was the one on the player).
     * While the player is away on a window this is {@link #releaseAway}: {@code online} is not there to write to.
     *
     * @return whether the slot was given back; false while another holder still holds it, or when {@code holder} never did
     */
    public synchronized boolean release(String slot, StateHolder holder, FPlayer online) {
        if (away) {
            return letGoAway(slot, holder.getHolderId()) && !slots.containsKey(slot);
        }
        Slot held = slots.get(slot);
        if (held == null || !held.holders.containsKey(holder.getHolderId())) {
            return false;
        }
        String latest = latest(held);
        held.holders.remove(holder.getHolderId());
        held.rewrites.remove(holder.getHolderId());
        held.policies.remove(holder.getHolderId());
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

    /**
     * On join, in whatever order with the session flags: a player back inside a window ends it, keeping what is still held,
     * and every slot the backup kept that nobody holds now is given back.
     */
    public synchronized void restoreLeftovers(FPlayer online) {
        endWindow();
        for (Map.Entry<String, Slot> entry : slots.entrySet()) {
            Slot held = entry.getValue();
            if (held.rewriteOnReturn) {
                held.rewriteOnReturn = false;
                rewrite(online, entry.getKey(), held.rewrites.get(latest(held)));
            }
        }
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
            if (INVENTORY.equals(slot)) {
                keepRefusedInventory(online, before);
            }
            return;
        }
        PlayerStateBackup backup = backup();
        if (backup != null) {
            backup.remove(slot);
        }
    }

    /** The next join tries again; the one thing a player cannot afford to lose, so it lands there even from a hold in memory. */
    private void keepRefusedInventory(FPlayer online, String before) {
        PlayerStateBackup backup = backup();
        if (backup == null) {
            EverNifeCore.getLog().warning("The inventory of " + online.getName() + " could not be given back and there is no"
                    + " PlayerData section to keep it for the next join, so what the holder left on them stays: " + before);
        } else if (backup.get(INVENTORY) == null) {
            backup.put(INVENTORY, before);
        }
    }

    private @Nullable PlayerStateBackup backup() {
        return backup(player);
    }

    private static @Nullable PlayerStateBackup backup(UUID player) {
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
            case INVENTORY:
                return access.getInventory(online);
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
            case INVENTORY:
                access.setInventory(online, before);
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
            throw new IllegalArgumentException("Unknown player-state slot '" + slot + "': it is time, weather, flight, game-mode, inventory or effect:<id>.");
        }
        return slot.substring(EFFECT.length());
    }
}
