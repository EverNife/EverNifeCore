package br.com.finalcraft.evernifecore.booster;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.MultiplierProvider;
import br.com.finalcraft.evernifecore.logic.Multipliers;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.ProgressOwnerProvider;
import br.com.finalcraft.evernifecore.logic.ProgressOwners;
import br.com.finalcraft.evernifecore.logic.audit.LogicTrace;
import br.com.finalcraft.evernifecore.time.ECClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The booster engine: the {@link BoosterSource}s, how each gain joins its boosters, and the multiplier a context gets.
 * The core registers it as a {@link MultiplierProvider}, so {@link ExecutionContext#getMultiplier} answers
 * {@link #resolve}'s multiplier. A booster multiplies at the source of the gain (the {@code money} effect with
 * {@code boosted: true}, a job's pay), never inside a {@code Currency}. Any thread.
 */
public final class Boosters {

    /** The owner of an id written bare. */
    private static final String CORE = "evernifecore";

    private static final List<Registered> SOURCES = new CopyOnWriteArrayList<>();
    private static final Map<String, Declared> DECLARED = new ConcurrentHashMap<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static volatile Map<String, BoosterJoin> configured = Collections.emptyMap();
    private static volatile List<BigDecimal> permissionFactors = PermissionBoosterSource.DEFAULT_FACTORS;

    private static final MultiplierProvider PROVIDER = new MultiplierProvider() {
        @Override
        public BigDecimal getMultiplier(ExecutionContext ctx, String variableId) {
            return resolve(ctx, variableId).getMultiplier();
        }

        @Override
        public String toString() {
            return "boosters";
        }
    };

    private Boosters() {
    }

    /** Adds the stock sources (permission, region flag) and makes {@code getMultiplier} read the engine; called once by the core. */
    public static void registerCore(ECPluginData core) {
        addSource(core, new PermissionBoosterSource(core));
        addSource(core, new RegionBoosterSource(core));
        Multipliers.register(core, PROVIDER);
    }

    /** Adds {@code source} under {@code owner}; {@link #unregisterAll} removes it. */
    public static void addSource(ECPluginData owner, BoosterSource source) {
        SOURCES.add(new Registered(owner, source));
    }

    static void removeSource(BoosterSource source) {
        SOURCES.removeIf(registered -> registered.source == source);
    }

    /** Removes {@code owner}'s sources and join declarations; called on its shutdown. */
    public static void unregisterAll(ECPluginData owner) {
        SOURCES.removeIf(registered -> registered.owner == owner);
        DECLARED.values().removeIf(declared -> declared.owner == owner);
    }

    /** Every source added, in order. */
    public static List<BoosterSource> getSources() {
        List<BoosterSource> sources = new ArrayList<>();
        for (Registered registered : SOURCES) {
            sources.add(registered.source);
        }
        return sources;
    }

    /**
     * Declares how the boosters of {@code variableId} join, and which strategies {@code config.yml}
     * ({@code Boosters.join.<variable>}) may swap it for; {@code defaultJoin}'s is always accepted. Whoever declares the
     * gain declares this; an undeclared gain joins by {@link BoosterJoin#sumBonus()}.
     */
    public static void declareJoin(ECPluginData owner, String variableId, BoosterJoin defaultJoin, BoosterJoin... accepted) {
        Set<BoosterJoin.Strategy> strategies = EnumSet.of(defaultJoin.getStrategy());
        for (BoosterJoin join : accepted) {
            strategies.add(join.getStrategy());
        }
        DECLARED.put(normalize(variableId), new Declared(owner, defaultJoin, strategies));
    }

    /** The join {@code variableId} uses now: the configured one when its declaration accepts it, else the declared one. */
    public static BoosterJoin getJoin(String variableId) {
        String id = normalize(variableId);
        Declared declared = DECLARED.get(id);
        BoosterJoin wanted = configured.get(id);
        if (wanted != null) {
            if (declared == null || declared.accepted.contains(wanted.getStrategy())) {
                return wanted;
            }
            if (WARNED.add(id + "=" + wanted)) {
                EverNifeCore.getLog().warning("config.yml Boosters.join." + shortForm(id) + " asks for " + wanted + ", but "
                        + declared.owner.getMetaInfo().getName() + " lets the boosters of " + id + " join only by " + names(declared.accepted)
                        + ": keeping " + declared.join + ". Write one of those strategies there.");
            }
        }
        return declared != null ? declared.join : BoosterJoin.sumBonus();
    }

    /**
     * The multiplier every source yields for {@code variableId} in {@code ctx}, joined by {@link #getJoin}, each parcel
     * traced. A source that throws is skipped with a warning. Any thread.
     */
    public static BoosterResult resolve(ExecutionContext ctx, String variableId) {
        String id = normalize(variableId);
        Instant now = ECClock.now();
        List<Booster> parcels = new ArrayList<>();
        for (Registered registered : SOURCES) {
            Collection<Booster> offered;
            try {
                offered = registered.source.boostersFor(ctx, id);
            } catch (RuntimeException broken) {
                EverNifeCore.getLog().warning("The booster source " + registered.source.getId() + " of "
                        + registered.owner.getMetaInfo().getName() + " failed for " + id + " and was skipped: " + broken);
                continue;
            }
            for (Booster booster : offered) {
                if (booster.getVariableId().equals(id) && booster.isActiveAt(now)) {
                    parcels.add(booster);
                    LogicTrace.publish(ctx, () -> "booster " + booster + " (" + registered.source.getId() + ")");
                }
            }
        }
        BoosterJoin join = getJoin(id);
        return new BoosterResult(id, join.join(parcels), parcels, join);
    }

    /** Whether a booster of {@code owner} counts for {@code ctx}: its own owner, everyone, or a group/region/world the player is in. */
    static boolean appliesTo(ProgressOwner owner, ExecutionContext ctx) {
        if (owner.equals(ctx.getOwner()) || owner.isKind(ProgressOwner.GLOBAL)) {
            return true;
        }
        FPlayer player = ctx.getPlayer();
        if (player == null) {
            return false;
        }
        if (owner.isKind(ProgressOwner.PLAYER)) {
            return owner.getId().equals(player.getUniqueId().toString());
        }
        Optional<ProgressOwnerProvider> provider = ProgressOwners.find(owner.getKind());
        if (!provider.isPresent()) {
            return false;
        }
        for (FPlayer member : provider.get().getMembers(owner)) {
            if (member.getUniqueId().equals(player.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    /** {@code money} -> {@code evernifecore:money}; an {@code owner:name} id as it is, lowercase. */
    public static String normalize(String variableId) {
        String id = variableId.trim().toLowerCase(Locale.ROOT);
        return id.indexOf(':') < 0 ? CORE + ":" + id : id;
    }

    /** The bare name for the core's own ids, the full id otherwise - how a permission node or a config key writes it. */
    public static String shortForm(String variableId) {
        String id = normalize(variableId);
        return id.startsWith(CORE + ":") ? id.substring(CORE.length() + 1) : id;
    }

    /** Applies {@code Boosters.*} of the core's {@code config.yml}; keys may be written bare ({@code money}). */
    public static void applySettings(List<BigDecimal> factors, Map<String, BoosterJoin> joins) {
        Map<String, BoosterJoin> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, BoosterJoin> entry : joins.entrySet()) {
            normalized.put(normalize(entry.getKey()), entry.getValue());
        }
        List<BigDecimal> sorted = new ArrayList<>(factors);
        Collections.sort(sorted);
        permissionFactors = Collections.unmodifiableList(sorted);
        configured = Collections.unmodifiableMap(normalized);
        WARNED.clear();
    }

    static List<BigDecimal> getPermissionFactors() {
        return permissionFactors;
    }

    private static String names(Set<BoosterJoin.Strategy> strategies) {
        List<String> names = new ArrayList<>();
        for (BoosterJoin.Strategy strategy : strategies) {
            names.add(strategy.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        }
        return String.join(", ", names);
    }

    private static final class Registered {
        final ECPluginData owner;
        final BoosterSource source;

        Registered(ECPluginData owner, BoosterSource source) {
            this.owner = owner;
            this.source = source;
        }
    }

    private static final class Declared {
        final ECPluginData owner;
        final BoosterJoin join;
        final Set<BoosterJoin.Strategy> accepted;

        Declared(ECPluginData owner, BoosterJoin join, Set<BoosterJoin.Strategy> accepted) {
            this.owner = owner;
            this.join = join;
            this.accepted = accepted;
        }
    }
}
