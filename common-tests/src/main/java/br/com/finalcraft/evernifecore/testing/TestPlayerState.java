package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.region.flag.EffectLevel;
import br.com.finalcraft.evernifecore.region.playerstate.ActiveEffect;
import br.com.finalcraft.evernifecore.region.playerstate.ECGameMode;
import br.com.finalcraft.evernifecore.region.playerstate.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.region.playerstate.PlayerTime;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Players in memory, as {@link Platforms#playerState()} answers {@code getPlayerState()}: every capability by default,
 * each player starting in survival, on the world's clock and weather, not allowed to fly, with 20 health of 20 and 20
 * food of 20. Weathers are {@code clear}, {@code rain} and {@code thunder}; any non-blank effect id exists. Commands are
 * kept, in order, as {@code console: <command>} or {@code <player name>: <command>}.
 */
public final class TestPlayerState implements IPlayerStateAccess {

    /** One player's state; the fields are the test's to read and set. */
    public static final class State {
        public volatile @Nullable PlayerTime time;
        public volatile Duration lastTransition = Duration.ZERO;
        public volatile @Nullable String weather;
        public volatile boolean allowFlight;
        public volatile boolean flying;
        public volatile ECGameMode gameMode = ECGameMode.SURVIVAL;
        public final Map<String, ActiveEffect> effects = Collections.synchronizedMap(new LinkedHashMap<>());
        public volatile double health = 20;
        public volatile double maxHealth = 20;
        public volatile int food = 20;
        /** The share of a damage that armor absorbs, 0 to 1; a damage that ignores armor skips it. */
        public volatile double armor;
    }

    private static final List<String> WEATHERS = Arrays.asList(CLEAR, RAIN, "thunder");

    private final Map<UUID, State> players = new ConcurrentHashMap<>();
    private final Set<Capability> missing = EnumSet.noneOf(Capability.class);
    private final Set<ECGameMode> missingModes = EnumSet.noneOf(ECGameMode.class);
    private final List<String> commands = new CopyOnWriteArrayList<>();

    /** The state of {@code player}, created with the defaults on first use. */
    public State of(FPlayer player) {
        return players.computeIfAbsent(player.getUniqueId(), id -> new State());
    }

    /** From now on these capabilities are missing: {@link #supports} says so and their calls throw. */
    public TestPlayerState without(Capability... capabilities) {
        missing.addAll(Arrays.asList(capabilities));
        return this;
    }

    /** From now on {@link #supportsGameMode} says no to these. */
    public TestPlayerState withoutGameModes(ECGameMode... modes) {
        missingModes.addAll(Arrays.asList(modes));
        return this;
    }

    /** Every command dispatched so far, in order. */
    public List<String> getCommands() {
        return Collections.unmodifiableList(new ArrayList<>(commands));
    }

    @Override
    public boolean supports(Capability capability) {
        return !missing.contains(capability);
    }

    private State require(Capability capability, FPlayer player) {
        if (!supports(capability)) {
            throw IPlayerStateAccess.unsupported(capability);
        }
        return of(player);
    }

    @Override
    public @Nullable PlayerTime getTime(FPlayer player) {
        return require(Capability.TIME, player).time;
    }

    @Override
    public void setTime(FPlayer player, @Nullable PlayerTime time, Duration transition) {
        State state = require(Capability.TIME, player);
        state.time = time;
        state.lastTransition = transition;
    }

    @Override
    public @Nullable String getWeather(FPlayer player) {
        return require(Capability.WEATHER, player).weather;
    }

    @Override
    public void setWeather(FPlayer player, @Nullable String weather) {
        require(Capability.WEATHER, player).weather = weather;
    }

    @Override
    public @Nullable String toWeatherId(String text) {
        String id = text.trim().toLowerCase(Locale.ROOT);
        return supports(Capability.WEATHER) && WEATHERS.contains(id) ? id : null;
    }

    @Override
    public boolean getAllowFlight(FPlayer player) {
        return require(Capability.FLIGHT, player).allowFlight;
    }

    @Override
    public void setAllowFlight(FPlayer player, boolean allow) {
        State state = require(Capability.FLIGHT, player);
        state.allowFlight = allow;
        if (!allow) {
            state.flying = false;
        }
    }

    @Override
    public boolean isFlying(FPlayer player) {
        return require(Capability.FLIGHT, player).flying;
    }

    @Override
    public void setFlying(FPlayer player, boolean flying) {
        State state = require(Capability.FLIGHT, player);
        if (flying && !state.allowFlight) {
            throw new IllegalStateException(player.getName() + " cannot fly while flight is not allowed - Bukkit refuses it the same way");
        }
        state.flying = flying;
    }

    @Override
    public boolean supportsGameMode(ECGameMode mode) {
        return supports(Capability.GAME_MODE) && !missingModes.contains(mode);
    }

    @Override
    public ECGameMode getGameMode(FPlayer player) {
        return require(Capability.GAME_MODE, player).gameMode;
    }

    @Override
    public void setGameMode(FPlayer player, ECGameMode mode) {
        State state = require(Capability.GAME_MODE, player);
        state.gameMode = mode;
        boolean flies = mode == ECGameMode.CREATIVE || mode == ECGameMode.SPECTATOR;
        state.allowFlight = flies; //as the game does: a mode change resets flight
        state.flying = mode == ECGameMode.SPECTATOR;
    }

    @Override
    public boolean isEffect(String effect) {
        return supports(Capability.EFFECTS) && !effect.trim().isEmpty();
    }

    @Override
    public @Nullable ActiveEffect getEffect(FPlayer player, String effect) {
        return require(Capability.EFFECTS, player).effects.get(effect);
    }

    @Override
    public void addEffect(FPlayer player, EffectLevel effect, Duration duration) {
        require(Capability.EFFECTS, player).effects.put(effect.getEffect(), new ActiveEffect(effect, duration));
    }

    @Override
    public void removeEffect(FPlayer player, String effect) {
        require(Capability.EFFECTS, player).effects.remove(effect);
    }

    @Override
    public double getHealth(FPlayer player) {
        return require(Capability.HEALTH, player).health;
    }

    @Override
    public double getMaxHealth(FPlayer player) {
        return require(Capability.HEALTH, player).maxHealth;
    }

    @Override
    public void setHealth(FPlayer player, double health) {
        State state = require(Capability.HEALTH, player);
        state.health = Math.max(0, Math.min(state.maxHealth, health));
    }

    @Override
    public void damage(FPlayer player, double amount, boolean ignoreArmor) {
        State state = require(Capability.HEALTH, player);
        state.health = Math.max(0, state.health - (ignoreArmor ? amount : amount * (1 - state.armor)));
    }

    @Override
    public int getFood(FPlayer player) {
        return require(Capability.FOOD, player).food;
    }

    @Override
    public int getMaxFood(FPlayer player) {
        require(Capability.FOOD, player);
        return 20;
    }

    @Override
    public void setFood(FPlayer player, int food) {
        require(Capability.FOOD, player).food = Math.max(0, Math.min(20, food));
    }

    @Override
    public boolean dispatchCommand(@Nullable FPlayer asPlayer, String command) {
        if (!supports(Capability.COMMAND)) {
            throw IPlayerStateAccess.unsupported(Capability.COMMAND);
        }
        commands.add((asPlayer == null ? "console" : asPlayer.getName()) + ": " + command);
        return true;
    }
}
