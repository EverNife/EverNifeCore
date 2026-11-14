package br.com.finalcraft.evernifecore.booster;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.region.RegionId;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One gain of one plugin that boosters multiply - a duel win, a job's pay, the planks of a woodcutter - declared in code
 * by that plugin: {@code BoosterDomain.of(plugin, "win").join(BoosterJoin.sumBonus().cap(3)).permission("duels.booster.win", 2, 4).register()}.
 * Its id is {@code <plugin>:<name>}, lowercase. The core multiplies nothing by itself: whoever pays the gain asks
 * {@link #resolve} and applies the {@link BoosterResult}. It leaves with its plugin. Immutable; any thread.
 */
public final class BoosterDomain {

    private final ECPluginData owner;
    private final String name;
    private final String id;
    private final BoosterJoin join;
    private final Set<BoosterJoin.Strategy> accepted;
    private final @Nullable String permissionPrefix;
    private final List<BigDecimal> permissionFactors;
    private final Caller caller;

    private BoosterDomain(Builder builder) {
        this.owner = builder.owner;
        this.name = builder.name;
        this.id = builder.owner.getMetaInfo().getName().toLowerCase(Locale.ROOT) + ":" + builder.name;
        this.join = builder.join;
        this.accepted = Collections.unmodifiableSet(builder.accepted);
        this.permissionPrefix = builder.permissionPrefix;
        List<BigDecimal> factors = new ArrayList<>(builder.permissionFactors);
        Collections.sort(factors);
        this.permissionFactors = Collections.unmodifiableList(factors);
        this.caller = Caller.of(builder.owner, "booster/" + builder.name);
    }

    /**
     * A domain of {@code owner} named {@code name} ({@code [a-z0-9_-]{1,64}} once lowercased); {@link Builder#register()}
     * makes it count.
     *
     * @throws IllegalArgumentException when {@code name} breaks that rule
     */
    public static Builder of(ECPluginData owner, String name) {
        return new Builder(Objects.requireNonNull(owner, "owner"), RegionId.requireName("booster domain", name));
    }

    /** {@code <plugin>:<name>}, lowercase: what a region's {@code boosters} flag, a config and a stored booster name. */
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ECPluginData getOwner() {
        return owner;
    }

    /** The join the plugin declared; {@link #getJoin()} is the one in use. */
    public BoosterJoin getDefaultJoin() {
        return join;
    }

    /** The strategies {@code config.yml} {@code Boosters.join.<id>} may swap the declared join for; the declared one included. */
    public Set<BoosterJoin.Strategy> getAcceptedStrategies() {
        return accepted;
    }

    /** The configured join when the declaration accepts it, else the declared one. */
    public BoosterJoin getJoin() {
        return Boosters.getJoin(this);
    }

    /** {@code duels.booster.win}: {@code <prefix>.<factor>} boosts this domain; null when no permission does. */
    public @Nullable String getPermissionPrefix() {
        return permissionPrefix;
    }

    /** The factors a permission may grant, ascending; empty when no permission boosts this domain. */
    public List<BigDecimal> getPermissionFactors() {
        return permissionFactors;
    }

    /** Every active booster of this domain that counts for {@code ctx}, joined; each parcel traced. */
    public BoosterResult resolve(ExecutionContext ctx) {
        return Boosters.resolve(ctx, this);
    }

    /**
     * The boosters of this domain for the player {@code playerId}: as {@link #resolve(ExecutionContext)} for them when
     * online; offline, only the sources that need no player answer - the boosters a {@link BoosterManager} granted to
     * them or to everyone.
     */
    public BoosterResult resolve(UUID playerId) {
        FPlayer online = EverNifeCore.getPlatform().getPlayer(playerId);
        ExecutionContext.Builder ctx = ExecutionContext.builder(caller);
        if (online != null && online.isOnline()) {
            ctx.player(online);
        } else {
            // reason-detre: an offline player's permission nodes are not read, so a permission booster counts only online; read them offline through the permission plugin when one is present
            ctx.owner(ProgressOwner.player(playerId));
        }
        return resolve(ctx.build());
    }

    @Override
    public String toString() {
        return id;
    }

    public static final class Builder {
        private final ECPluginData owner;
        private final String name;
        private BoosterJoin join = BoosterJoin.sumBonus();
        private final Set<BoosterJoin.Strategy> accepted = EnumSet.of(BoosterJoin.Strategy.SUM_BONUS);
        private @Nullable String permissionPrefix;
        private final List<BigDecimal> permissionFactors = new ArrayList<>();

        private Builder(ECPluginData owner, String name) {
            this.owner = owner;
            this.name = name;
        }

        /**
         * How the boosters join (default {@link BoosterJoin#sumBonus()}), and which other strategies {@code config.yml}
         * ({@code Boosters.join.<id>}) may swap it for.
         */
        public Builder join(BoosterJoin defaultJoin, BoosterJoin... accepted) {
            this.join = Objects.requireNonNull(defaultJoin, "defaultJoin");
            this.accepted.clear();
            this.accepted.add(defaultJoin.getStrategy());
            for (BoosterJoin other : accepted) {
                this.accepted.add(other.getStrategy());
            }
            return this;
        }

        /**
         * The permission {@code <prefix>.<factor>} ({@code duels.booster.win.2}, or {@code .2.0}) boosts this domain by
         * that factor, for each of {@code factors}; the largest held counts. A platform cannot list a player's
         * permissions, so a factor not declared here is never seen - a plugin that wants them configurable reads its own
         * config and passes them. Without it (or with a null prefix) no permission boosts the domain.
         *
         * @throws IllegalArgumentException when a factor is negative
         */
        public Builder permission(@Nullable String prefix, double... factors) {
            String written = prefix == null ? null : prefix.trim();
            while (written != null && written.endsWith(".")) {
                written = written.substring(0, written.length() - 1);
            }
            this.permissionPrefix = written == null || written.isEmpty() ? null : written;
            this.permissionFactors.clear();
            for (double factor : factors) {
                if (factor < 0) {
                    throw new IllegalArgumentException("The booster domain " + name + " lists the permission factor " + factor
                            + ": a booster cannot turn a gain into a loss - list factors of 0 or more, like 2, 4, 6.");
                }
                BigDecimal exact = BigDecimal.valueOf(factor);
                if (!permissionFactors.contains(exact)) {
                    permissionFactors.add(exact);
                }
            }
            return this;
        }

        /**
         * Makes the domain count until its plugin shuts down.
         *
         * @throws IllegalArgumentException when a permission prefix lists no factor
         * @throws IllegalStateException    when a domain with the same id is already registered
         */
        public BoosterDomain register() {
            BoosterDomain domain = new BoosterDomain(this);
            if (permissionPrefix != null && permissionFactors.isEmpty()) {
                throw new IllegalArgumentException("The booster domain " + domain.getId() + " declares the permission prefix "
                        + permissionPrefix + " with no factor, so no node could ever boost it. List the factors a permission may"
                        + " grant: permission(\"" + permissionPrefix + "\", 2, 4, 6) makes " + permissionPrefix + ".2 double it;"
                        + " read them from your own config to make them configurable.");
            }
            Boosters.register(domain);
            return domain;
        }
    }
}
