package br.com.finalcraft.evernifecore.logic.core;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.group.GroupId;
import br.com.finalcraft.evernifecore.group.GroupLookup;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.ProgressOwners;
import br.com.finalcraft.evernifecore.logic.Variable;
import br.com.finalcraft.evernifecore.logic.Variables;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionId;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.session.RegionPlayerSession;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.ParsedParams;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The stock logic over regions: the conditions {@code in-region}, {@code region-flag} and {@code region-member}, and the
 * variable {@code region-time}. A region is read from where the player's region session says they stand, never from
 * a lookup of their position. A context without a player (a group's or a region's quest) asks every online member of
 * its owner: {@code in-region} passes when any of them is inside.
 */
public final class StockRegions {

    private StockRegions() {
    }

    static void register(ECPluginData core) {
        Conditions.registry().register(core, TypeDescriptor.<Condition>of("evernifecore:in-region")
                .describe("Passes while the player stands in a region (by id) or in any region with a tag; with for-at-least, only once"
                        + " they stood there that long without leaving.")
                .param(ParamSpec.optional("region", ParamSpec.Kind.ID, null, "The region, owner:name; a bare name is the caller's own."))
                .param(ParamSpec.optional("tag", ParamSpec.Kind.STRING, null, "Any region with this tag."))
                .param(ParamSpec.optional("for-at-least", ParamSpec.Kind.DURATION, null, "How long inside, without leaving, e.g. 5m."))
                .factory(params -> new InRegion(target(params), params.getDuration("for-at-least")))
                .build());
        Conditions.registry().register(core, TypeDescriptor.<Condition>of("evernifecore:region-flag")
                .describe("Passes when a flag resolves, where the player stands, to a value.")
                .param(ParamSpec.required("flag", ParamSpec.Kind.ID, "The flag, e.g. evernifecore:pvp."))
                .param(ParamSpec.required("equals", ParamSpec.Kind.STRING, "The value it resolves to, as written in a region, e.g. deny."))
                .factory(params -> new RegionFlagIs(params.getString("flag"), params.getString("equals")))
                .build());
        Conditions.registry().register(core, TypeDescriptor.<Condition>of("evernifecore:region-member")
                .describe("Passes when the player belongs to a group local to a region.")
                .param(ParamSpec.required("region", ParamSpec.Kind.ID, "The region, owner:name; a bare name is the caller's own."))
                .param(ParamSpec.required("group", ParamSpec.Kind.STRING, "The region's group, e.g. members."))
                .factory(params -> {
                    GroupId group = GroupId.regionLocal(regionId(params, params.getString("region")), params.getString("group"));
                    return new Condition() {
                        @Override
                        public ConditionResult test(ExecutionContext ctx) {
                            FPlayer player = ctx.getPlayer();
                            boolean member = player != null && GroupLookup.get().getMembers(group).contains(player.getUniqueId());
                            return ConditionResult.of(member, FancyText.of("Only for members of " + group + "."));
                        }

                        @Override
                        public String toString() {
                            return "region-member(" + group + ")";
                        }
                    };
                })
                .build());
        Variables.registry().register(core, TypeDescriptor.<Variable<?>>of("evernifecore:region-time")
                .describe("How long the player has stood in a region without leaving it; zero outside.")
                .param(ParamSpec.required("region", ParamSpec.Kind.ID, "The region, owner:name; a bare name is the caller's own."))
                .factory(params -> {
                    RegionId region = regionId(params, params.getString("region"));
                    return new Variable<Duration>() {
                        @Override
                        public String getId() {
                            return "region-time";
                        }

                        @Override
                        public Class<Duration> getType() {
                            return Duration.class;
                        }

                        @Override
                        public Duration read(ExecutionContext ctx) {
                            FPlayer player = ctx.getPlayer();
                            return player == null ? Duration.ZERO : timeIn(player.getUniqueId(), Target.of(region));
                        }
                    };
                })
                .build());
    }

    /** How long {@code player} has stood, without leaving, in the region {@code target} names (the longest of a tag's); zero outside. */
    public static Duration timeIn(UUID player, Target target) {
        RegionPlayerSession session = RegionLookup.get().getSessionIfPresent(player);
        if (session == null) {
            return Duration.ZERO;
        }
        Instant now = ECClock.now();
        Duration longest = null;
        for (Region region : session.getCurrent()) {
            if (!target.matches(region)) {
                continue;
            }
            Instant entered = session.getEnteredAt(region);
            Duration inside = entered == null ? Duration.ZERO : Duration.between(entered, now);
            if (longest == null || inside.compareTo(longest) > 0) {
                longest = inside;
            }
        }
        return longest == null ? Duration.ZERO : longest;
    }

    /** Whether {@code player}'s session stands in a region {@code target} names. */
    public static boolean isIn(UUID player, Target target) {
        RegionPlayerSession session = RegionLookup.get().getSessionIfPresent(player);
        if (session == null) {
            return false;
        }
        for (Region region : session.getCurrent()) {
            if (target.matches(region)) {
                return true;
            }
        }
        return false;
    }

    /** The players a condition asks: the context's player, else the online members of its owner. */
    static List<FPlayer> playersOf(ExecutionContext ctx) {
        if (ctx.getPlayer() != null) {
            return Collections.singletonList(ctx.getPlayer());
        }
        ProgressOwner owner = ctx.getOwner();
        List<FPlayer> players = new ArrayList<>();
        if (owner == null) {
            return players;
        }
        if (owner.isKind(ProgressOwner.GROUP)) {
            for (UUID member : GroupLookup.get().getMembers(GroupId.parse(owner.getId()))) {
                FPlayer player = EverNifeCore.getPlatform().getPlayer(member);
                if (player != null && player.isOnline()) {
                    players.add(player);
                }
            }
            return players;
        }
        ProgressOwners.find(owner.getKind()).ifPresent(provider -> players.addAll(provider.getMembers(owner)));
        return players;
    }

    private static Target target(ParsedParams params) {
        String region = params.getString("region");
        String tag = params.getString("tag");
        if ((region == null) == (tag == null)) {
            throw new TypeRegistryException("in-region names one region or one tag: write {type: in-region, region: theduel:arena} or"
                    + " {type: in-region, tag: checkpoint}.");
        }
        return region != null ? Target.of(regionId(params, region)) : Target.tagged(tag);
    }

    /** {@code text} as a region id; a bare name belongs to the caller. */
    public static RegionId regionId(ParsedParams params, String text) {
        try {
            return text.indexOf(':') >= 0 ? RegionId.parse(text) : RegionId.of(params.getCaller().getMetaInfo().getName(), text);
        } catch (IllegalArgumentException invalid) {
            throw new TypeRegistryException("'" + text + "' is not a region id: " + invalid.getMessage());
        }
    }

    /** One region by id, or every region with a tag. Immutable. */
    public static final class Target {

        private final @Nullable RegionId region;
        private final @Nullable String tag;

        private Target(@Nullable RegionId region, @Nullable String tag) {
            this.region = region;
            this.tag = tag;
        }

        public static Target of(RegionId region) {
            return new Target(Objects.requireNonNull(region, "region"), null);
        }

        public static Target tagged(String tag) {
            return new Target(null, Objects.requireNonNull(tag, "tag"));
        }

        public boolean matches(Region candidate) {
            if (region != null) {
                return candidate.getId().equals(region);
            }
            for (String own : candidate.getTags()) {
                if (own.equalsIgnoreCase(tag)) {
                    return true;
                }
            }
            return false;
        }

        public @Nullable RegionId getRegion() {
            return region;
        }

        public @Nullable String getTag() {
            return tag;
        }

        @Override
        public String toString() {
            return region != null ? region.toString() : "tag " + tag;
        }
    }

    private static final class InRegion implements Condition {

        private final Target target;
        private final @Nullable Duration atLeast;

        InRegion(Target target, @Nullable Duration atLeast) {
            this.target = target;
            this.atLeast = atLeast;
        }

        @Override
        public ConditionResult test(ExecutionContext ctx) {
            for (FPlayer player : playersOf(ctx)) {
                boolean inside = atLeast == null ? isIn(player.getUniqueId(), target)
                        : isIn(player.getUniqueId(), target) && timeIn(player.getUniqueId(), target).compareTo(atLeast) >= 0;
                if (inside) {
                    return ConditionResult.of(true, FancyText.of(""));
                }
            }
            return ConditionResult.of(false, FancyText.of(atLeast == null ? "Only inside " + target + "."
                    : "Only after " + atLeast.getSeconds() + "s inside " + target + "."));
        }

        @Override
        public Set<EventSource> getNeeds() {
            return Collections.singleton(EventSource.MOVE);
        }

        @Override
        public String toString() {
            return "in-region(" + target + (atLeast == null ? "" : ", for-at-least " + atLeast) + ")";
        }
    }

    private static final class RegionFlagIs implements Condition {

        private final String flagKey;
        private final String expected;

        RegionFlagIs(String flagKey, String expected) {
            this.flagKey = flagKey;
            this.expected = expected;
        }

        @Override
        public ConditionResult test(ExecutionContext ctx) {
            FPlayer player = ctx.getPlayer();
            Flag<?> flag = FlagRegistry.get().find(flagKey).orElse(null);
            Object value = player == null || flag == null ? null : RegionLookup.get().resolve(flag, player);
            boolean passed = value != null && String.valueOf(value).equalsIgnoreCase(expected);
            return ConditionResult.of(passed, FancyText.of("Only where " + flagKey + " is " + expected + "."));
        }

        @Override
        public Set<EventSource> getNeeds() {
            return Collections.singleton(EventSource.MOVE);
        }

        @Override
        public String toString() {
            return "region-flag(" + flagKey + " = " + expected + ")";
        }
    }
}
