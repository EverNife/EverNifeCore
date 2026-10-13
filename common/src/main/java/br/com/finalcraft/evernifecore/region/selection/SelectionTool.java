package br.com.finalcraft.evernifecore.region.selection;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.region.RegionScheduler;
import br.com.finalcraft.evernifecore.region.RegionClock;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import br.com.finalcraft.evernifecore.region.usage.UsageHold;
import br.com.finalcraft.evernifecore.region.visual.RegionVisualizer;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Hands players the selection tool and turns the clicks the platform reports into points. One session per player;
 * a new {@link #start} replaces the old one. A session ends on confirm, cancel, a click in another world, quit, or
 * {@link SelectionSpec#getTimeout()} without a touch.
 *
 * <p>While any session is open, interaction events are needed ({@link EventSourceUsage#acquire}), so the platform
 * listens to them only then. Call {@link #start} and {@link #handleClick} on the player's world thread - the main thread
 * on Bukkit, the world's own on Hytale.</p>
 */
public final class SelectionTool {

    @FCLocale(lang = LocaleType.EN_US, text = "§a§l ▶ §aSelection started: §eleft click§a adds a point, §eright click§a removes the last one, §esneak + right click§a confirms.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§l ▶ §aSeleção iniciada: §eclique esquerdo§a marca um ponto, §eclique direito§a remove o último, §eagachado + clique direito§a confirma.")
    public static LocaleMessage SELECTION_STARTED;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §eYour previous selection was replaced by a new one.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §eSua seleção anterior foi trocada por uma nova.")
    public static LocaleMessage SELECTION_REPLACED;

    @FCLocale(lang = LocaleType.EN_US, text = "§c§l ▶ §cSelection cancelled: you changed world.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§c§l ▶ §cSeleção cancelada: você mudou de mundo.")
    public static LocaleMessage SELECTION_CANCELLED_WORLD_CHANGED;

    @FCLocale(lang = LocaleType.EN_US, text = "§c§l ▶ §cSelection cancelled: it sat untouched for too long.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§c§l ▶ §cSeleção cancelada: ficou parada tempo demais.")
    public static LocaleMessage SELECTION_EXPIRED;

    private static final Map<UUID, SelectionSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Object LOCK = new Object();
    private static final AtomicBoolean WARNED_START_OFF_THREAD = new AtomicBoolean();
    private static final AtomicBoolean WARNED_CLICK_OFF_THREAD = new AtomicBoolean();
    private static @Nullable UsageHold interactHold;
    private static @Nullable ECEventSubscription<ECPlayerQuitEvent> quitSubscription;

    private SelectionTool() {
    }

    /**
     * Opens a selection for {@code player} in the world they stand in, cancelling the one they had, gives them the
     * tool of {@code spec} through the platform and, when the spec asks for it and the visualizer is on, draws it live.
     *
     * @param onConfirm receives the shape when the player confirms; it runs on the player's world thread
     */
    public static SelectionSession start(FPlayer player, SelectionSpec spec, Consumer<Shape> onConfirm) {
        FLocation location = player.getLocation();
        if (location == null) {
            throw new IllegalStateException("The platform has no location for " + player.getName() + " - a selection belongs to the"
                    + " world the player stands in, so start it once the player is in one.");
        }
        String world = location.getWorldName();
        warnIfOffThread(world, WARNED_START_OFF_THREAD, "SelectionTool.start");

        SelectionSession previous = SESSIONS.get(player.getUniqueId());
        if (previous != null) {
            previous.close(SELECTION_REPLACED);
        }

        SelectionSession session = new SelectionSession(player, spec, world, onConfirm);
        synchronized (LOCK) {
            if (SESSIONS.isEmpty()) {
                interactHold = EventSourceUsage.get().acquire(EventSource.INTERACT);
            }
            if (quitSubscription == null) {
                quitSubscription = ECEventBus.global().subscribe(ECPlayerQuitEvent.class,
                        event -> onQuit(event.getPlayerData().getUniqueId()));
            }
            SESSIONS.put(player.getUniqueId(), session);
        }

        EverNifeCore.getPlatform().getSelectionItems().give(player, spec.getTool());
        SELECTION_STARTED.send(player);
        scheduleExpiry(session, spec.getTimeout());
        if (spec.isLivePreview() && RegionVisualizer.get().isEnabled()) {
            RegionVisualizer.get().showLive(session);
        }
        return session;
    }

    /** The open selection of {@code player}, or null. */
    public static @Nullable SelectionSession getSession(FPlayer player) {
        return SESSIONS.get(player.getUniqueId());
    }

    /**
     * What the platform's listener calls when a player clicks a block holding the selection tool.
     *
     * @return true when the click belonged to a selection - the platform then cancels the native event
     */
    public static boolean handleClick(FPlayer player, WorldBlockPos block, SelectionClick click) {
        SelectionSession session = SESSIONS.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        warnIfOffThread(block.getWorldName(), WARNED_CLICK_OFF_THREAD, "SelectionTool.handleClick");
        if (!session.getWorld().equals(block.getWorldName())) {
            session.close(SELECTION_CANCELLED_WORLD_CHANGED);   //a selection is one world's worth of points
            return true;
        }
        switch (click) {
            case LEFT:
                session.addPoint(block.getBlockPos());
                break;
            case RIGHT:
                session.removeLastPoint();
                break;
            case SNEAK_RIGHT:
                session.confirm();
                break;
        }
        return true;
    }

    /** Ends every open selection without a word - the core's shutdown, and tests. */
    public static void cancelAll() {
        for (SelectionSession session : new ArrayList<>(SESSIONS.values())) {
            session.close(null);
        }
    }

    static void closed(SelectionSession session) {
        synchronized (LOCK) {
            if (!SESSIONS.remove(session.getPlayer().getUniqueId(), session)) {
                return;
            }
            if (SESSIONS.isEmpty() && interactHold != null) {
                interactHold.release();
                interactHold = null;
            }
        }
    }

    private static void onQuit(UUID player) {
        SelectionSession session = SESSIONS.get(player);
        if (session != null) {
            session.close(null);
        }
    }

    private static void scheduleExpiry(SelectionSession session, Duration timeout) {
        Instant deadline = session.getLastTouch().plus(timeout);
        long delayMillis = Math.max(0, Duration.between(RegionClock.now(), deadline).toMillis());
        RegionScheduler.scheduleAsync(() -> checkExpiry(session, timeout), delayMillis);
    }

    private static void checkExpiry(SelectionSession session, Duration timeout) {
        if (!session.isActive()) {
            return;
        }
        if (RegionClock.now().isBefore(session.getLastTouch().plus(timeout))) {
            scheduleExpiry(session, timeout);   //touched since: wait out what is left of the new deadline
            return;
        }
        //the scheduler's thread is nobody's: only the world's thread may talk to the player
        EverNifeCore.getPlatform().runOnWorldThread(session.getWorld(), () -> session.close(SELECTION_EXPIRED))
                .exceptionally(error -> {
                    ECDebugModule.REGIONS.debug("Dropped the expired selection of {}: world {} has no thread ({}).",
                            session.getPlayer().getName(), session.getWorld(), error.getMessage());
                    session.close(null);
                    return null;
                });
    }

    private static void warnIfOffThread(String world, AtomicBoolean warned, String site) {
        if (!EverNifeCore.getPlatform().isWorldThread(world) && warned.compareAndSet(false, true)) {
            ECDebugModule.REGIONS.debug("{} was called off the thread of world {} - call it from the player's world thread.", site, world);
        }
    }
}
