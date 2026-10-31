package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.group.GroupId;
import br.com.finalcraft.evernifecore.group.GroupLookup;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.EffectList;
import br.com.finalcraft.evernifecore.logic.EffectModifiers;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.ProgressOwnerProvider;
import br.com.finalcraft.evernifecore.logic.ProgressOwners;
import br.com.finalcraft.evernifecore.logic.audit.AuditEntry;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.logic.audit.AuditLog;
import br.com.finalcraft.evernifecore.logic.delivery.PendingDestination;
import br.com.finalcraft.evernifecore.logic.reward.core.CoreRewardPieces;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import br.com.finalcraft.evernifecore.types.TypeRegistry;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pays a {@link RewardRule} to the members of a {@link ProgressOwner}, and holds the {@code reward-piece} registry a
 * plugin adds its own pieces to. The core registers its stock ({@code CoreRewardPieces}) on enable.
 */
public final class Rewards {

    private static final TypeRegistry<RewardPiece> REGISTRY = TypeRegistry.of(RewardPiece.class, "reward-piece");
    private static final Set<Caller> TOLD_NO_LAST_ACTOR = ConcurrentHashMap.newKeySet();

    private Rewards() {
    }

    /** {@code reward-piece}: every role of piece; a plugin registers {@code <plugin>:<name>} here. */
    public static TypeRegistry<RewardPiece> registry() {
        return REGISTRY;
    }

    /**
     * Pays {@code rule} for {@code ctx}. Nothing is paid or posted when the rule's moment does not take the context's
     * trigger. Otherwise the candidates (the owner's members, offline included, plus whoever contributed while the
     * leaver keeps) pass every eligibility, the distribution gives each a share, and each eligible member gets the
     * rule's effects multiplied by its share: applied on the member's thread when online; when offline, as the absence
     * says - a pending delivery, nothing, or split among the online eligible. Every payment posts a
     * {@link RewardPaidEvent} and lands in the caller plugin's {@link AuditLog} when it has one open. Any thread.
     *
     * @return completes, once every effect applied or was kept, with the share each paid member got (delivered
     *         included, forfeited left out), in the distribution's order
     * @throws IllegalArgumentException when the rule asks for a group role and the owner is not a group
     */
    public static CompletableFuture<Map<UUID, BigDecimal>> pay(RewardRule rule, RewardContext ctx) {
        if (!rule.getMoment().paysAt(ctx.getTrigger())) {
            return CompletableFuture.completedFuture(Collections.<UUID, BigDecimal>emptyMap());
        }
        for (Eligibility eligibility : rule.getEligibility()) {
            if (CoreRewardPieces.asksForAGroupRole(eligibility) && !ctx.getOwner().isKind(ProgressOwner.GROUP)) {
                throw new IllegalArgumentException("The reward of " + ctx.getCaller() + " is for " + ctx.getOwner() + ", which has no roles:"
                        + " " + eligibility + " only reads a group's leader and roles. Pay it to ProgressOwner.group(id), or pick another"
                        + " eligibility (online, any-member, last-actor...).");
            }
        }

        Set<UUID> eligible = new LinkedHashSet<>();
        for (UUID candidate : candidatesOf(rule, ctx)) {
            if (isEligible(rule, candidate, ctx)) {
                eligible.add(candidate);
            }
        }
        if (eligible.isEmpty()) {
            if (ctx.getLastActor() == null && rule.getEligibility().contains(Eligibility.lastActor()) && TOLD_NO_LAST_ACTOR.add(ctx.getCaller())) {
                ECDebugModule.GROUPS.debug("A last-actor reward of {} paid nobody: its RewardContext named no last actor.", ctx.getCaller());
            }
            return CompletableFuture.completedFuture(Collections.<UUID, BigDecimal>emptyMap());
        }

        Map<UUID, BigDecimal> shares = rule.getDistribution().sharesOf(Collections.unmodifiableSet(eligible), ctx);
        Map<UUID, BigDecimal> paid = new LinkedHashMap<>();
        Set<UUID> delivered = new LinkedHashSet<>();
        BigDecimal pooled = BigDecimal.ZERO;
        for (Map.Entry<UUID, BigDecimal> share : shares.entrySet()) {
            if (share.getValue().signum() <= 0) {
                continue;
            }
            UUID member = share.getKey();
            if (online(member) != null) {
                paid.put(member, share.getValue());
                continue;
            }
            Absence.Outcome outcome = rule.getAbsent().onAbsent(member, ctx);
            if (outcome == Absence.Outcome.DELIVER) {
                paid.put(member, share.getValue());
                delivered.add(member);
            } else if (outcome == Absence.Outcome.REDISTRIBUTE) {
                pooled = pooled.add(share.getValue());
            }
        }
        if (pooled.signum() > 0) {
            redistribute(rule, ctx, eligible, pooled, paid);
        }

        List<CompletableFuture<?>> applied = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> share : paid.entrySet()) {
            applied.add(payOne(rule, ctx, share.getKey(), share.getValue(), delivered.contains(share.getKey())));
        }
        Map<UUID, BigDecimal> result = Collections.unmodifiableMap(paid);
        return CompletableFuture.allOf(applied.toArray(new CompletableFuture<?>[0])).thenApply(done -> result);
    }

    /** The owner's members (offline included where the owner knows them), then the contributors the leaver rule keeps. */
    private static Set<UUID> candidatesOf(RewardRule rule, RewardContext ctx) {
        Set<UUID> members = membersOf(ctx.getOwner());
        Set<UUID> candidates = new LinkedHashSet<>();
        if (members != null) {
            candidates.addAll(members);
        }
        for (UUID contributor : ctx.getContributions().keySet()) {
            //an owner that knows no membership (global) has no leaver: every contributor is a candidate
            if (members == null || rule.getMembership().leaverKeeps() || members.contains(contributor)) {
                candidates.add(contributor);
            }
        }
        return candidates;
    }

    /** Null when the owner's kind has no notion of membership (global, or a kind nobody answers). */
    private static @Nullable Set<UUID> membersOf(ProgressOwner owner) {
        if (owner.isKind(ProgressOwner.GROUP)) {
            return new LinkedHashSet<>(GroupLookup.get().getMembers(GroupId.parse(owner.getId())));
        }
        if (owner.isKind(ProgressOwner.PLAYER)) {
            return Collections.singleton(UUID.fromString(owner.getId()));
        }
        if (owner.isKind(ProgressOwner.GLOBAL)) {
            return null;
        }
        Optional<ProgressOwnerProvider> provider = ProgressOwners.find(owner.getKind());
        if (!provider.isPresent()) {
            return null;
        }
        Set<UUID> online = new LinkedHashSet<>();
        for (FPlayer player : provider.get().getMembers(owner)) {
            online.add(player.getUniqueId());
        }
        return online;
    }

    private static boolean isEligible(RewardRule rule, UUID member, RewardContext ctx) {
        for (Eligibility eligibility : rule.getEligibility()) {
            if (!eligibility.isEligible(member, ctx)) {
                return false;
            }
        }
        return true;
    }

    /** Splits {@code pooled} among the online eligible by the rule's distribution, normalized so the pool is paid once. */
    private static void redistribute(RewardRule rule, RewardContext ctx, Set<UUID> eligible, BigDecimal pooled, Map<UUID, BigDecimal> paid) {
        Set<UUID> present = new LinkedHashSet<>();
        for (UUID member : eligible) {
            if (online(member) != null) {
                present.add(member);
            }
        }
        if (present.isEmpty()) {
            return; //nobody eligible is online to take it: the pool is lost
        }
        Map<UUID, BigDecimal> again = rule.getDistribution().sharesOf(Collections.unmodifiableSet(present), ctx);
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal share : again.values()) {
            total = total.add(share.max(BigDecimal.ZERO));
        }
        if (total.signum() == 0) {
            return;
        }
        for (Map.Entry<UUID, BigDecimal> share : again.entrySet()) {
            if (share.getValue().signum() <= 0) {
                continue;
            }
            BigDecimal extra = pooled.multiply(share.getValue()).divide(total, CoreRewardPieces.SHARE_SCALE, RoundingMode.DOWN);
            BigDecimal before = paid.get(share.getKey());
            paid.put(share.getKey(), before == null ? extra : before.add(extra));
        }
    }

    private static CompletableFuture<Void> payOne(RewardRule rule, RewardContext ctx, UUID member, BigDecimal share, boolean delivered) {
        EffectList shared = (EffectList) rule.getEffects().withShare(share);
        Runnable task = () -> {
            shared.apply(contextOf(ctx.getCaller(), member, delivered));
            ECEventBus.global().postIfListened(RewardPaidEvent.class,
                    () -> new RewardPaidEvent(ctx.getCaller(), ctx.getOwner(), member, share, rule, delivered));
            AuditLog.ofIfPresent(ctx.getCaller().getPlugin()).ifPresent(log -> log.record(AuditEntry.builder(AuditKind.REWARD_PAID, ctx.getCaller())
                    .owner(ProgressOwner.player(member))
                    .amount(share)
                    .subject(ctx.getOwner().toString())
                    .text("reward share " + share.stripTrailingZeros().toPlainString() + " of " + ctx.getOwner()
                            + (delivered ? ", kept as a pending delivery" : "") + ": " + shared)
                    .build()));
        };
        FPlayer player = delivered ? null : online(member);
        FLocation location = player == null ? null : player.getLocation();
        if (location == null) {
            return ECScheduler.onMainThread(task);
        }
        IPlatform platform = EverNifeCore.getPlatform();
        if (platform.isWorldThread(location.getWorldName())) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        platform.runOnWorldThread(location.getWorldName(), () -> {
            try {
                task.run();
                done.complete(null);
            } catch (RuntimeException failure) {
                done.completeExceptionally(failure);
            }
        });
        return done;
    }

    /**
     * The member online, or - offline - its uuid as owner with every effect that can wait sent to the pending
     * destination: an amount or an item becomes a delivery, what cannot wait (a message) applies without a player.
     */
    private static ExecutionContext contextOf(Caller caller, UUID member, boolean delivered) {
        ExecutionContext.Builder builder = ExecutionContext.builder(caller);
        FPlayer player = delivered ? null : online(member);
        if (player != null) {
            return builder.player(player).build();
        }
        return builder.owner(ProgressOwner.player(member)).attribute(EffectModifiers.DESTINATION_ATTRIBUTE, PendingDestination.ID).build();
    }

    /** The member when it is online; null otherwise. Internal. */
    public static @Nullable FPlayer online(UUID member) {
        FPlayer player = EverNifeCore.getPlatform().getPlayer(member);
        return player != null && player.isOnline() ? player : null;
    }
}
