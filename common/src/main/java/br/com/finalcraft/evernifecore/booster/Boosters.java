package br.com.finalcraft.evernifecore.booster;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The booster engine: the {@link BoosterDomain}s the plugins declared, the {@link BoosterSource}s, how each domain
 * joins its boosters and the multiplier a context gets ({@link BoosterDomain#resolve}). A booster multiplies at the
 * source of the gain (a job's pay, a sale that names the domain), never inside a {@code Currency}. Any thread.
 */
public final class Boosters {

    private static final List<Registered> SOURCES = new CopyOnWriteArrayList<>();
    private static final Map<String, BoosterDomain> DOMAINS = new ConcurrentHashMap<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static volatile Map<String, BoosterJoin> configured = Collections.emptyMap();

    private Boosters() {
    }

    /** Adds the stock sources (permission, region flag); called once by the core. */
    public static void registerCore(ECPluginData core) {
        addSource(core, new PermissionBoosterSource(core));
        addSource(core, new RegionBoosterSource(core));
    }

    /** Adds {@code source} under {@code owner}; {@link #unregisterAll} removes it. */
    public static void addSource(ECPluginData owner, BoosterSource source) {
        SOURCES.add(new Registered(owner, source));
    }

    static void removeSource(BoosterSource source) {
        SOURCES.removeIf(registered -> registered.source == source);
    }

    /** Removes {@code owner}'s sources and domains; called on its shutdown. */
    public static void unregisterAll(ECPluginData owner) {
        SOURCES.removeIf(registered -> registered.owner == owner);
        DOMAINS.values().removeIf(domain -> domain.getOwner() == owner);
    }

    /** Every source added, in order. */
    public static List<BoosterSource> getSources() {
        List<BoosterSource> sources = new ArrayList<>();
        for (Registered registered : SOURCES) {
            sources.add(registered.source);
        }
        return sources;
    }

    static void register(BoosterDomain domain) {
        BoosterDomain existing = DOMAINS.putIfAbsent(domain.getId(), domain);
        if (existing != null) {
            throw new IllegalStateException("The booster domain " + domain.getId() + " is already registered by "
                    + existing.getOwner().getMetaInfo().getName() + ". Register each domain once, at the plugin's start, and keep the"
                    + " BoosterDomain register() returns in a static field; Boosters.findDomain(\"" + domain.getId() + "\") finds it again.");
        }
    }

    /** The domain registered under {@code id} ({@code duels:win}, any case). */
    public static Optional<BoosterDomain> findDomain(String id) {
        return Optional.ofNullable(DOMAINS.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    /** {@code owner}'s domains, sorted by id. */
    public static List<BoosterDomain> getDomains(ECPluginData owner) {
        List<BoosterDomain> owned = new ArrayList<>();
        for (BoosterDomain domain : DOMAINS.values()) {
            if (domain.getOwner() == owner) {
                owned.add(domain);
            }
        }
        owned.sort(Comparator.comparing(BoosterDomain::getId));
        return owned;
    }

    /**
     * The join {@code domain} uses now: the one {@code config.yml} {@code Boosters.join.<id>} names when the domain
     * accepts it, else the declared one. A key of a domain nobody registered is never read, so it warns nobody.
     */
    static BoosterJoin getJoin(BoosterDomain domain) {
        BoosterJoin wanted = configured.get(domain.getId());
        if (wanted == null) {
            return domain.getDefaultJoin();
        }
        if (domain.getAcceptedStrategies().contains(wanted.getStrategy())) {
            return wanted;
        }
        if (WARNED.add(domain.getId() + "=" + wanted)) {
            List<String> names = new ArrayList<>();
            for (BoosterJoin.Strategy strategy : domain.getAcceptedStrategies()) {
                names.add(strategy.name().toLowerCase(Locale.ROOT).replace('_', '-'));
            }
            EverNifeCore.getLog().warning("config.yml Boosters.join." + domain.getId() + " asks for " + wanted + ", but "
                    + domain.getOwner().getMetaInfo().getName() + " lets the boosters of " + domain.getId() + " join only by "
                    + String.join(", ", names) + ": keeping " + domain.getDefaultJoin() + ". Write one of those strategies there.");
        }
        return domain.getDefaultJoin();
    }

    /** Every source's active boosters of {@code domain} in {@code ctx}, joined, each parcel traced; a source that throws is skipped with a warning. */
    static BoosterResult resolve(ExecutionContext ctx, BoosterDomain domain) {
        Instant now = ECClock.now();
        List<Booster> parcels = new ArrayList<>();
        for (Registered registered : SOURCES) {
            Collection<Booster> offered;
            try {
                offered = registered.source.boostersFor(ctx, domain);
            } catch (RuntimeException broken) {
                EverNifeCore.getLog().warning("The booster source " + registered.source.getId() + " of "
                        + registered.owner.getMetaInfo().getName() + " failed for " + domain + " and was skipped: " + broken);
                continue;
            }
            for (Booster booster : offered) {
                if (booster.getDomainId().equals(domain.getId()) && booster.isActiveAt(now)) {
                    parcels.add(booster);
                    LogicTrace.publish(ctx, () -> "booster " + booster + " (" + registered.source.getId() + ")");
                }
            }
        }
        BoosterJoin join = getJoin(domain);
        return new BoosterResult(domain, join.join(parcels), parcels, join);
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

    /**
     * {@code Duels:Win} -> {@code duels:win}.
     *
     * @throws IllegalArgumentException when the id is not {@code <plugin>:<name>}
     */
    static String normalize(String domainId) {
        String id = domainId.trim().toLowerCase(Locale.ROOT);
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1 || id.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("'" + domainId + "' names no booster domain: write <plugin>:<domain>, e.g. duels:win."
                    + " A plugin's domains are listed by its export.");
        }
        return id;
    }

    /** Applies {@code Boosters.join} of the core's {@code config.yml}, keyed by domain id ({@code duels:win}). */
    public static void applySettings(Map<String, BoosterJoin> joins) {
        Map<String, BoosterJoin> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, BoosterJoin> entry : joins.entrySet()) {
            normalized.put(entry.getKey().trim().toLowerCase(Locale.ROOT), entry.getValue());
        }
        configured = Collections.unmodifiableMap(normalized);
        WARNED.clear();
    }

    private static final class Registered {
        final ECPluginData owner;
        final BoosterSource source;

        Registered(ECPluginData owner, BoosterSource source) {
            this.owner = owner;
            this.source = source;
        }
    }
}
