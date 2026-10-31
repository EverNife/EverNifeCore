package br.com.finalcraft.evernifecore.logic.reward.core;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupId;
import br.com.finalcraft.evernifecore.group.GroupLookup;
import br.com.finalcraft.evernifecore.group.GroupMember;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.ProgressOwnerProvider;
import br.com.finalcraft.evernifecore.logic.ProgressOwners;
import br.com.finalcraft.evernifecore.logic.reward.Absence;
import br.com.finalcraft.evernifecore.logic.reward.Distribution;
import br.com.finalcraft.evernifecore.logic.reward.Eligibility;
import br.com.finalcraft.evernifecore.logic.reward.Moment;
import br.com.finalcraft.evernifecore.logic.reward.RewardContext;
import br.com.finalcraft.evernifecore.logic.reward.RewardMembership;
import br.com.finalcraft.evernifecore.logic.reward.RewardPiece;
import br.com.finalcraft.evernifecore.logic.reward.RewardTrigger;
import br.com.finalcraft.evernifecore.logic.reward.Rewards;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.ParsedParams;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The core's 21 reward pieces ({@code evernifecore:<name>} in {@link Rewards#registry()}), and the instances the static
 * factories of {@link Eligibility}, {@link Distribution}, {@link Absence}, {@link Moment} and {@link RewardMembership}
 * hand out. Stateless; any thread.
 */
public final class CoreRewardPieces {

    /** Decimal places of a split share; a share is cut there (never rounded up), so a split never pays more than the whole. */
    public static final int SHARE_SCALE = 10;

    public static final Eligibility ANY_MEMBER = named("any-member", (member, ctx) -> true);
    public static final Eligibility ONLINE = named("online", (member, ctx) -> Rewards.online(member) != null);
    public static final Eligibility PRESENT = named("present", CoreRewardPieces::isPresent);
    private static final String LAST_ACTOR_NAME = "last-actor";
    public static final Eligibility LAST_ACTOR = named(LAST_ACTOR_NAME, (member, ctx) -> member.equals(ctx.getLastActor()));

    public static final Distribution EACH = new NamedDistribution("each", (eligible, ctx) -> {
        Map<UUID, BigDecimal> shares = new LinkedHashMap<>();
        for (UUID member : eligible) {
            shares.put(member, BigDecimal.ONE);
        }
        return shares;
    });
    public static final Distribution SPLIT_EQUAL = new NamedDistribution("split-equal", (eligible, ctx) -> {
        BigDecimal each = BigDecimal.ONE.divide(BigDecimal.valueOf(eligible.size()), SHARE_SCALE, RoundingMode.DOWN);
        Map<UUID, BigDecimal> shares = new LinkedHashMap<>();
        for (UUID member : eligible) {
            shares.put(member, each);
        }
        return shares;
    });
    public static final Distribution PROPORTIONAL = new NamedDistribution("proportional", (eligible, ctx) -> {
        BigDecimal total = BigDecimal.ZERO;
        for (UUID member : eligible) {
            total = total.add(ctx.getContribution(member));
        }
        Map<UUID, BigDecimal> shares = new LinkedHashMap<>();
        if (total.signum() == 0) {
            return shares;
        }
        for (UUID member : eligible) {
            BigDecimal contribution = ctx.getContribution(member);
            if (contribution.signum() > 0) {
                shares.put(member, contribution.divide(total, SHARE_SCALE, RoundingMode.DOWN));
            }
        }
        return shares;
    });
    public static final Distribution TOP_TAKES_ALL = new NamedDistribution("top-takes-all", (eligible, ctx) -> {
        List<UUID> ranked = ranked(eligible, ctx);
        return ranked.isEmpty() ? Collections.<UUID, BigDecimal>emptyMap() : Collections.singletonMap(ranked.get(0), BigDecimal.ONE);
    });

    public static final Absence DELIVER = new NamedAbsence("deliver", Absence.Outcome.DELIVER);
    public static final Absence FORFEIT = new NamedAbsence("forfeit", Absence.Outcome.FORFEIT);
    public static final Absence REDISTRIBUTE = new NamedAbsence("redistribute", Absence.Outcome.REDISTRIBUTE);

    public static final Moment ON_COMPLETE = new NamedMoment("on-complete", RewardTrigger.ON_COMPLETE);
    public static final Moment ON_WINDOW_END = new NamedMoment("on-window-end", RewardTrigger.ON_WINDOW_END);
    public static final Moment ON_OWNER_GONE = new NamedMoment("on-owner-gone", RewardTrigger.ON_OWNER_GONE);

    private CoreRewardPieces() {
    }

    /** Registers the 21 pieces under {@code core}, the EverNifeCore plugin data. */
    public static void registerAll(ECPluginData core) {
        // eligibility (9)
        register(core, "any-member", "Every member of the owner, offline included, and whoever contributed while the leaver keeps.",
                params -> ANY_MEMBER);
        register(core, "online", "The members online when the reward pays.", params -> ONLINE);
        register(core, "present", "The members standing in the owner's region or world; the online ones for a group or a player.",
                params -> PRESENT);
        register(core, LAST_ACTOR_NAME, "Only whose action closed it, as the caller names it; nobody when it names none.", params -> LAST_ACTOR);
        register(core, "role", "The group's leader (role: leader) or the members holding a role of the group's kind. Group owners only.",
                params -> role(params.getString("role")),
                ParamSpec.required("role", ParamSpec.Kind.STRING, "leader, or the name of a role of the group's kind."));
        register(core, "contribution-at-least", "The members who contributed at least this much.",
                params -> contributionAtLeast(params.getNumber("min")),
                ParamSpec.required("min", ParamSpec.Kind.NUMBER, "The least contribution, e.g. 1."));
        register(core, "contribution-share", "The members who contributed at least this part of the fair share (total / contributors).",
                params -> contributionShare(fraction(params.getString("at-least"))),
                ParamSpec.required("at-least", ParamSpec.Kind.STRING, "A part of the fair share: 60% or 0.6."));
        register(core, "top", "The members with the highest contributions.",
                params -> top(params.getNumber("count").intValueExact()),
                ParamSpec.required("count", ParamSpec.Kind.NUMBER, "How many, e.g. 3."));
        register(core, "condition", "The members for whom a condition passes.",
                params -> condition(Conditions.of(params.<Condition>getPending("condition"))),
                ParamSpec.required("condition", ParamSpec.Kind.NODE, "The condition, run for the member.").nested(Conditions.registry()));
        // distribution (5)
        register(core, "each", "The whole reward to every eligible member.", params -> EACH);
        register(core, "split-equal", "The reward split equally among the eligible.", params -> SPLIT_EQUAL);
        register(core, "proportional", "The reward split by contribution.", params -> PROPORTIONAL);
        register(core, "top-takes-all", "The whole reward to the highest contribution.", params -> TOP_TAKES_ALL);
        register(core, "tiers", "A part of the reward per position, highest contribution first; positions past the list get nothing.",
                params -> tiers(fractions(params.getList("tiers"))),
                ParamSpec.required("tiers", ParamSpec.Kind.LIST, "The part of each position, e.g. [50%, 30%, 20%]."));
        // absence (3)
        register(core, "deliver", "An offline member's share waits as a pending delivery.", params -> DELIVER);
        register(core, "forfeit", "An offline member's share is lost.", params -> FORFEIT);
        register(core, "redistribute", "An offline member's share is split among the online eligible.", params -> REDISTRIBUTE);
        // moment (3)
        register(core, "on-complete", "Pays when what it was for is done.", params -> ON_COMPLETE);
        register(core, "on-window-end", "Pays when its time window closes.", params -> ON_WINDOW_END);
        register(core, "on-owner-gone", "Pays, in part, when its owner is gone.", params -> ON_OWNER_GONE);
        // leaver / joiner (1)
        register(core, "membership", "Whether a member who left keeps a contribution, and whether one who joined late inherits.",
                params -> membership("keeps".equals(params.getString("leaver")), "inherits-zero".equals(params.getString("joiner"))),
                ParamSpec.optional("leaver", ParamSpec.Kind.STRING, "keeps", "keeps or forfeits.").oneOf("keeps", "forfeits"),
                ParamSpec.optional("joiner", ParamSpec.Kind.STRING, "from-now", "from-now or inherits-zero.").oneOf("from-now", "inherits-zero"));
    }

    private static void register(ECPluginData core, String name, String description, Function<ParsedParams, RewardPiece> factory,
                                 ParamSpec... params) {
        TypeDescriptor.Builder<RewardPiece> descriptor = TypeDescriptor.<RewardPiece>of("evernifecore:" + name).describe(description);
        for (ParamSpec param : params) {
            descriptor.param(param);
        }
        Rewards.registry().register(core, descriptor.factory(factory).build());
    }

    /** Whether {@code eligibility} reads a group's leader or roles - refused on an owner that is not a group. */
    public static boolean asksForAGroupRole(Eligibility eligibility) {
        return eligibility instanceof RoleEligibility;
    }

    public static Eligibility role(String role) {
        return new RoleEligibility(role);
    }

    public static Eligibility contributionAtLeast(BigDecimal min) {
        Objects.requireNonNull(min, "min");
        return named("contribution-at-least: " + min.toPlainString(), (member, ctx) -> ctx.getContribution(member).compareTo(min) >= 0);
    }

    public static Eligibility contributionShare(BigDecimal ofFairShare) {
        Objects.requireNonNull(ofFairShare, "ofFairShare");
        return named("contribution-share: " + ofFairShare.toPlainString(), (member, ctx) -> {
            BigDecimal total = BigDecimal.ZERO;
            int contributors = 0;
            for (BigDecimal contribution : ctx.getContributions().values()) {
                if (contribution.signum() > 0) {
                    total = total.add(contribution);
                    contributors++;
                }
            }
            if (contributors == 0) {
                return false;
            }
            BigDecimal fair = total.divide(BigDecimal.valueOf(contributors), SHARE_SCALE, RoundingMode.HALF_EVEN);
            BigDecimal contribution = ctx.getContribution(member);
            return contribution.signum() > 0 && contribution.compareTo(fair.multiply(ofFairShare)) >= 0;
        });
    }

    public static Eligibility top(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("top: " + count + " takes nobody - write top: 1 or more.");
        }
        return named("top: " + count, (member, ctx) -> {
            List<UUID> ranked = ranked(ctx.getContributions().keySet(), ctx);
            return ranked.subList(0, Math.min(count, ranked.size())).contains(member);
        });
    }

    public static Eligibility condition(Condition condition) {
        Objects.requireNonNull(condition, "condition");
        return named("condition: " + condition, (member, ctx) -> {
            FPlayer player = Rewards.online(member);
            ExecutionContext.Builder run = ExecutionContext.builder(ctx.getCaller());
            ExecutionContext context = player != null ? run.player(player).build() : run.owner(ProgressOwner.player(member)).build();
            return condition.test(context).isPassed();
        });
    }

    public static Distribution tiers(BigDecimal... byPosition) {
        List<BigDecimal> parts = Arrays.asList(byPosition.clone());
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal part : parts) {
            if (part.signum() < 0) {
                throw new IllegalArgumentException("tiers " + parts + " has a negative part: every position takes zero or more.");
            }
            sum = sum.add(part);
        }
        if (sum.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("tiers " + parts + " add up to " + sum.toPlainString() + ", more than the whole reward:"
                    + " make them sum to 100% or less.");
        }
        return new NamedDistribution("tiers: " + parts, (eligible, ctx) -> {
            List<UUID> ranked = ranked(eligible, ctx);
            Map<UUID, BigDecimal> shares = new LinkedHashMap<>();
            for (int position = 0; position < Math.min(parts.size(), ranked.size()); position++) {
                shares.put(ranked.get(position), parts.get(position));
            }
            return shares;
        });
    }

    public static RewardMembership membership(boolean leaverKeeps, boolean joinerInherits) {
        return new Membership(leaverKeeps, joinerInherits);
    }

    /** {@code members} with a contribution above zero, highest first; a tie keeps the order of {@code members}. */
    private static List<UUID> ranked(Iterable<UUID> members, RewardContext ctx) {
        List<UUID> ranked = new ArrayList<>();
        for (UUID member : members) {
            if (ctx.getContribution(member).signum() > 0) {
                ranked.add(member);
            }
        }
        ranked.sort((a, b) -> ctx.getContribution(b).compareTo(ctx.getContribution(a)));
        return ranked;
    }

    private static boolean isPresent(UUID member, RewardContext ctx) {
        ProgressOwner owner = ctx.getOwner();
        if (owner.isKind(ProgressOwner.REGION) || owner.isKind(ProgressOwner.WORLD)) {
            ProgressOwnerProvider place = ProgressOwners.find(owner.getKind()).orElse(null);
            if (place != null) {
                for (FPlayer player : place.getMembers(owner)) {
                    if (player.getUniqueId().equals(member)) {
                        return true;
                    }
                }
                return false;
            }
        }
        return Rewards.online(member) != null;
    }

    /** {@code 60%} or {@code 0.6}. */
    static BigDecimal fraction(String written) {
        String text = written.trim();
        try {
            if (text.endsWith("%")) {
                return new BigDecimal(text.substring(0, text.length() - 1).trim()).movePointLeft(2).stripTrailingZeros();
            }
            return new BigDecimal(text);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("'" + written + "' is not a part: write a percentage like 60% or a fraction like 0.6.");
        }
    }

    private static BigDecimal[] fractions(List<String> written) {
        BigDecimal[] parts = new BigDecimal[written.size()];
        for (int i = 0; i < parts.length; i++) {
            parts[i] = fraction(written.get(i));
        }
        return parts;
    }

    private interface EligibilityTest {
        boolean test(UUID member, RewardContext ctx);
    }

    private static Eligibility named(String name, EligibilityTest test) {
        return new Eligibility() {
            @Override
            public boolean isEligible(UUID member, RewardContext ctx) {
                return test.test(member, ctx);
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    private interface Shares {
        Map<UUID, BigDecimal> of(Set<UUID> eligible, RewardContext ctx);
    }

    private static final class NamedDistribution implements Distribution {
        private final String name;
        private final Shares shares;

        NamedDistribution(String name, Shares shares) {
            this.name = name;
            this.shares = shares;
        }

        @Override
        public Map<UUID, BigDecimal> sharesOf(Set<UUID> eligible, RewardContext ctx) {
            return eligible.isEmpty() ? Collections.<UUID, BigDecimal>emptyMap() : shares.of(eligible, ctx);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class RoleEligibility implements Eligibility {
        private final String role;

        RoleEligibility(String role) {
            if (role == null || role.trim().isEmpty()) {
                throw new IllegalArgumentException("role: names nobody - write role: leader, or the name of a role of the group's kind.");
            }
            this.role = role.trim();
        }

        @Override
        public boolean isEligible(UUID member, RewardContext ctx) {
            GroupId id = GroupId.parse(ctx.getOwner().getId());
            if ("leader".equalsIgnoreCase(role)) {
                return member.equals(GroupLookup.get().getLeader(id));
            }
            //a provider's group (not managed here) answers only its leader
            Group group = GroupLookup.get().byId(id);
            GroupMember held = group == null ? null : group.getMember(member);
            return held != null && held.hasRole(role);
        }

        @Override
        public String toString() {
            return "role: " + role;
        }
    }

    private static final class NamedAbsence implements Absence {
        private final String name;
        private final Outcome outcome;

        NamedAbsence(String name, Outcome outcome) {
            this.name = name;
            this.outcome = outcome;
        }

        @Override
        public Outcome onAbsent(UUID member, RewardContext ctx) {
            return outcome;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class NamedMoment implements Moment {
        private final String name;
        private final RewardTrigger trigger;

        NamedMoment(String name, RewardTrigger trigger) {
            this.name = name;
            this.trigger = trigger;
        }

        @Override
        public boolean paysAt(RewardTrigger at) {
            return trigger == at;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class Membership implements RewardMembership {
        private final boolean leaverKeeps;
        private final boolean joinerInherits;

        Membership(boolean leaverKeeps, boolean joinerInherits) {
            this.leaverKeeps = leaverKeeps;
            this.joinerInherits = joinerInherits;
        }

        @Override
        public boolean leaverKeeps() {
            return leaverKeeps;
        }

        @Override
        public boolean joinerInherits() {
            return joinerInherits;
        }

        @Override
        public String toString() {
            return "leaver: " + (leaverKeeps ? "keeps" : "forfeits") + ", joiner: " + (joinerInherits ? "inherits-zero" : "from-now");
        }
    }
}
