package br.com.finalcraft.evernifecore.hytale.region;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import br.com.finalcraft.evernifecore.region.flag.EffectLevel;
import br.com.finalcraft.evernifecore.region.playerstate.ActiveEffect;
import br.com.finalcraft.evernifecore.region.playerstate.ECGameMode;
import br.com.finalcraft.evernifecore.region.playerstate.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.region.playerstate.PlayerTime;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.FlyMode;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.world.ClearEditorTimeOverride;
import com.hypixel.hytale.protocol.packets.world.UpdateEditorTimeOverride;
import com.hypixel.hytale.protocol.packets.world.UpdateTime;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Clock, flight, game mode ({@code ADVENTURE}, {@code CREATIVE}), entity effects, health and commands. Hytale has no
 * per-player weather the server can clear again and no food, so those two capabilities are missing. The clock is the
 * client's time override - the packet the server's own player-only time effect sends: a fixed time is a paused
 * override, a relative one an offset from the world's time that keeps running. An effect has no level here: the
 * level is ignored and an effect id names its strength ({@code Potion_Speed_2}).
 */
public final class HyPlayerStateAccess implements IPlayerStateAccess {

    static final long RAMP_STEP_MILLIS = 100;
    private static final long TICKS_PER_DAY = 24000;
    private static final long TICKS_AT_MIDNIGHT = 18000;   //Minecraft's day starts at 06:00
    private static final Set<Capability> SUPPORTED = EnumSet.of(Capability.TIME, Capability.FLIGHT, Capability.GAME_MODE,
            Capability.EFFECTS, Capability.HEALTH, Capability.COMMAND);

    /** What one call asks of the server; apart so the mapping above it is testable without a running world. */
    interface Ops {
        @Nullable Instant worldTime(FPlayer player);

        void send(UUID player, ToClientPacket packet);

        FlyMode flyMode(FPlayer player);

        void setFlyMode(FPlayer player, FlyMode mode);

        boolean isFlying(FPlayer player);

        void setFlying(FPlayer player, boolean flying);

        GameMode gameMode(FPlayer player);

        void setGameMode(FPlayer player, GameMode mode);

        /** The real id of the effect {@code lowercased} names, or null. */
        @Nullable String effectId(String lowercased);

        /** Effect id to seconds left. */
        Map<String, Float> effects(FPlayer player);

        void addEffect(FPlayer player, String effectId, float seconds);

        void removeEffect(FPlayer player, String effectId);

        float health(FPlayer player);

        float maxHealth(FPlayer player);

        void setHealth(FPlayer player, float health);

        boolean command(@Nullable FPlayer asPlayer, String command);
    }

    private final Ops ops;
    private final ScheduledExecutorService scheduler;
    private final Map<UUID, PlayerTime> times = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> shown = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> ramps = new ConcurrentHashMap<>();

    public HyPlayerStateAccess() {
        this(new EcsOps(), HytaleServer.SCHEDULED_EXECUTOR);
    }

    HyPlayerStateAccess(Ops ops, ScheduledExecutorService scheduler) {
        this.ops = ops;
        this.scheduler = scheduler;
    }

    @Override
    public boolean supports(Capability capability) {
        return SUPPORTED.contains(capability);
    }

    // ---- TIME ----

    /**
     * The instant a client shows for Minecraft day {@code ticks} (0 = 06:00, 24000 a day): that time of the world's
     * current day, or, {@code relative}, the world's time moved forward by {@code ticks}.
     */
    static Instant lockedInstant(Instant worldTime, long ticks, boolean relative) {
        if (relative) {
            return worldTime.plusNanos(nanosOf(ticks));
        }
        long sinceMidnight = Math.floorMod(ticks - TICKS_AT_MIDNIGHT, TICKS_PER_DAY);
        return worldTime.truncatedTo(ChronoUnit.DAYS).plusNanos(nanosOf(sinceMidnight));
    }

    private static long nanosOf(long ticks) {
        return Math.round(ticks * ((double) WorldTimeResource.NANOS_PER_DAY / TICKS_PER_DAY));
    }

    @Override
    public @Nullable PlayerTime getTime(FPlayer player) {
        return times.get(player.getUniqueId());
    }

    @Override
    public void setTime(FPlayer player, @Nullable PlayerTime time, Duration transition) {
        UUID uuid = player.getUniqueId();
        cancelRamp(uuid);
        Instant worldTime = ops.worldTime(player);
        if (time == null) {
            times.remove(uuid);
            shown.remove(uuid);
            ops.send(uuid, new ClearEditorTimeOverride());
            if (worldTime != null) {
                ops.send(uuid, new UpdateTime(WorldTimeResource.instantToInstantData(worldTime))); //not waiting for the next broadcast
            }
            return;
        }
        times.put(uuid, time);
        if (worldTime == null) {
            return;
        }
        Instant target = lockedInstant(worldTime, time.getTicks(), time.isRelative());
        boolean paused = !time.isRelative();
        int steps = (int) (transition.toMillis() / RAMP_STEP_MILLIS);
        if (steps <= 0) {
            show(uuid, target, paused);
            return;
        }
        //walk the client's clock from what it shows now to the target, the last step landing exactly on it
        Instant from = shown.getOrDefault(uuid, worldTime);
        long span = from.until(target, ChronoUnit.NANOS);
        int[] step = {0};
        ramps.put(uuid, scheduler.scheduleAtFixedRate(() -> {
            step[0]++;
            show(uuid, from.plusNanos(span / steps * step[0] + (step[0] == steps ? span % steps : 0)), paused);
            if (step[0] >= steps) {
                cancelRamp(uuid);
            }
        }, RAMP_STEP_MILLIS, RAMP_STEP_MILLIS, TimeUnit.MILLISECONDS));
    }

    /** Forgets a player who left the server, without sending him anything. */
    public void forget(UUID uuid) {
        cancelRamp(uuid);
        shown.remove(uuid);
        times.remove(uuid);
    }

    private void show(UUID uuid, Instant time, boolean paused) {
        shown.put(uuid, time);
        ops.send(uuid, new UpdateEditorTimeOverride(WorldTimeResource.instantToInstantData(time), paused));
    }

    private void cancelRamp(UUID uuid) {
        ScheduledFuture<?> ramp = ramps.remove(uuid);
        if (ramp != null) {
            ramp.cancel(false);
        }
    }

    // ---- FLIGHT ----

    @Override
    public boolean getAllowFlight(FPlayer player) {
        return ops.flyMode(player) != FlyMode.Disabled;
    }

    @Override
    public void setAllowFlight(FPlayer player, boolean allow) {
        if (!allow && ops.isFlying(player)) {
            ops.setFlying(player, false);
        }
        ops.setFlyMode(player, allow ? FlyMode.Allowed : FlyMode.Disabled);
    }

    @Override
    public boolean isFlying(FPlayer player) {
        return ops.isFlying(player);
    }

    @Override
    public void setFlying(FPlayer player, boolean flying) {
        ops.setFlying(player, flying);
    }

    // ---- GAME_MODE ----

    @Override
    public boolean supportsGameMode(ECGameMode mode) {
        return mode == ECGameMode.ADVENTURE || mode == ECGameMode.CREATIVE;
    }

    @Override
    public ECGameMode getGameMode(FPlayer player) {
        return ops.gameMode(player) == GameMode.Creative ? ECGameMode.CREATIVE : ECGameMode.ADVENTURE;
    }

    @Override
    public void setGameMode(FPlayer player, ECGameMode mode) {
        if (!supportsGameMode(mode)) {
            throw new IllegalArgumentException("Hytale has no " + mode + " game mode: use adventure or creative.");
        }
        ops.setGameMode(player, mode == ECGameMode.CREATIVE ? GameMode.Creative : GameMode.Adventure);
    }

    // ---- EFFECTS ----

    @Override
    public boolean isEffect(String effect) {
        return ops.effectId(effect.trim().toLowerCase(Locale.ROOT)) != null;
    }

    @Override
    public @Nullable ActiveEffect getEffect(FPlayer player, String effect) {
        String id = ops.effectId(effect.trim().toLowerCase(Locale.ROOT));
        Float seconds = id == null ? null : ops.effects(player).get(id);
        return seconds == null ? null : new ActiveEffect(EffectLevel.of(effect, 1), Duration.ofMillis((long) (seconds * 1000)));
    }

    @Override
    public void addEffect(FPlayer player, EffectLevel effect, Duration duration) {
        String id = ops.effectId(effect.getEffect());
        if (id == null) {
            throw new IllegalArgumentException("'" + effect.getEffect() + "' is no entity effect on this server: use an EntityEffect asset id.");
        }
        ops.addEffect(player, id, duration.toMillis() / 1000f);
    }

    @Override
    public void removeEffect(FPlayer player, String effect) {
        String id = ops.effectId(effect.trim().toLowerCase(Locale.ROOT));
        if (id != null) {
            ops.removeEffect(player, id);
        }
    }

    // ---- HEALTH ----

    @Override
    public double getHealth(FPlayer player) {
        return ops.health(player);
    }

    @Override
    public double getMaxHealth(FPlayer player) {
        return ops.maxHealth(player);
    }

    @Override
    public void setHealth(FPlayer player, double health) {
        ops.setHealth(player, (float) Math.max(0, Math.min(ops.maxHealth(player), health)));
    }

    // ponytail: lowers the health stat, skipping armor and the damage event; route through the damage systems if a
    // region ever needs armor to count
    @Override
    public void damage(FPlayer player, double amount) {
        setHealth(player, ops.health(player) - amount);
    }

    // ---- COMMAND ----

    @Override
    public boolean dispatchCommand(@Nullable FPlayer asPlayer, String command) {
        return ops.command(asPlayer, command);
    }

    /** The server's own components, read and written on the player's world thread. */
    static final class EcsOps implements Ops {

        private static Ref<EntityStore> ref(FPlayer player) {
            Ref<EntityStore> ref = ((HytaleFPlayer<?>) player).getPlayerRef().getReference();
            if (ref == null || !ref.isValid()) {
                throw new IllegalStateException(player.getName() + " is in no world now, so there is no entity to read or change.");
            }
            return ref;
        }

        private static <T extends Component<EntityStore>> T component(FPlayer player, ComponentType<EntityStore, T> type) {
            Ref<EntityStore> ref = ref(player);
            T component = ref.getStore().getComponent(ref, type);
            if (component == null) {
                throw new IllegalStateException(player.getName() + " has no " + type + " component.");
            }
            return component;
        }

        @Override
        public @Nullable Instant worldTime(FPlayer player) {
            World world = ((HytaleFPlayer<?>) player).getWorld();
            return world == null ? null : world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType()).getGameTime();
        }

        @Override
        public void send(UUID player, ToClientPacket packet) {
            PlayerRef ref = Universe.get().getPlayer(player);
            if (ref != null) {
                ref.getPacketHandler().write(packet);
            }
        }

        @Override
        public FlyMode flyMode(FPlayer player) {
            return component(player, MovementManager.getComponentType()).getSettings().fly;
        }

        @Override
        public void setFlyMode(FPlayer player, FlyMode mode) {
            MovementManager movement = component(player, MovementManager.getComponentType());
            movement.getSettings().fly = mode;
            movement.update(((HytaleFPlayer<?>) player).getPlayerRef().getPacketHandler());
        }

        @Override
        public boolean isFlying(FPlayer player) {
            Ref<EntityStore> ref = ref(player);
            return component(player, Player.getComponentType()).isFlying(ref, ref.getStore());
        }

        @Override
        public void setFlying(FPlayer player, boolean flying) {
            Ref<EntityStore> ref = ref(player);
            component(player, Player.getComponentType()).setFlying(ref, flying, ref.getStore());
        }

        @Override
        public GameMode gameMode(FPlayer player) {
            return component(player, Player.getComponentType()).getGameMode();
        }

        @Override
        public void setGameMode(FPlayer player, GameMode mode) {
            Ref<EntityStore> ref = ref(player);
            Player.setGameMode(ref, mode, ref.getStore());
        }

        @Override
        public @Nullable String effectId(String lowercased) {
            for (String id : EntityEffect.getAssetMap().getAssetMap().keySet()) {
                if (id.toLowerCase(Locale.ROOT).equals(lowercased)) {
                    return id;
                }
            }
            return null;
        }

        @Override
        public Map<String, Float> effects(FPlayer player) {
            Map<String, Float> effects = new LinkedHashMap<>();
            for (ActiveEntityEffect active : component(player, EffectControllerComponent.getComponentType()).getAllActiveEntityEffects()) {
                EntityEffect effect = EntityEffect.getAssetMap().getAsset(active.getEntityEffectIndex());
                if (effect != null) {
                    effects.put(effect.getId(), active.getRemainingDuration());
                }
            }
            return effects;
        }

        @Override
        public void addEffect(FPlayer player, String effectId, float seconds) {
            Ref<EntityStore> ref = ref(player);
            component(player, EffectControllerComponent.getComponentType())
                    .addEffect(ref, EntityEffect.getAssetMap().getAsset(effectId), seconds, OverlapBehavior.OVERWRITE, ref.getStore());
        }

        @Override
        public void removeEffect(FPlayer player, String effectId) {
            Ref<EntityStore> ref = ref(player);
            component(player, EffectControllerComponent.getComponentType())
                    .removeEffect(ref, EntityEffect.getAssetMap().getIndex(effectId), ref.getStore());
        }

        private static EntityStatValue healthOf(FPlayer player) {
            return component(player, EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getHealth());
        }

        @Override
        public float health(FPlayer player) {
            return healthOf(player).get();
        }

        @Override
        public float maxHealth(FPlayer player) {
            return healthOf(player).getMax();
        }

        @Override
        public void setHealth(FPlayer player, float health) {
            component(player, EntityStatMap.getComponentType()).setStatValue(DefaultEntityStatTypes.getHealth(), health);
        }

        @Override
        public boolean command(@Nullable FPlayer asPlayer, String command) {
            return asPlayer == null ? EverNifeCore.getPlatform().makeConsoleExecuteCommand(command)
                    : EverNifeCore.getPlatform().makePlayerExecuteCommand(asPlayer, command);
        }
    }
}
