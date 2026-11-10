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
 * The engine that pays a step ({@link RewardRule}) of a {@link RewardTable} to its members, and the {@code reward-piece}
 * registry a plugin adds its own pieces to. The core registers its stock ({@code CoreRewardPieces}) on enable.
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
     * Pays one step, {@code rule}, for {@code ctx} - the engine under {@link RewardTable#pay}, which every payment goes
     * through. Nothing is paid or posted when the rule's moment does not take the context's trigger. Otherwise the
     * candidates (a {@link RewardScene}'s participants; without one, the owner's members, offline included, plus whoever
     * contributed while the leaver keeps) pass every eligibility, the distribution gives each a share, and each eligible
     * member gets the rule's effects multiplied by its share, reading {@code {amount}} and {@code {share}}: applied on the
     * member's thread when online; when offline, as the absence says - a pending delivery, nothing, or split among the
     * online eligible. Every payment posts a {@link RewardPaidEvent} and lands in the caller plugin's {@link AuditLog}
     * when it has one open. A {@link RewardRule#isOnce() once} rule skips all of that: its effects apply one time on the
     * main thread, from the console, with the reward's attributes and the scene's variables, and only the audit line is
     * written. Any thread.
     *
     * @return completes, once every effect applied or was kept, with the step's receipt
     * @throws IllegalArgumentException when the rule asks for a group role and the owner is not a group
     */
    static CompletableFuture<RewardReceipt.Step> payStep(RewardRule rule, RewardContext ctx, int index) {
        RewardReceipt.Step step = settle(rule, ctx, index);
        if (!step.isRun()) {
            return CompletableFuture.completedFuture(step);
        }
        if (step.isOnce()) {
            return payOnce(rule, ctx).thenApply(done -> step);
        }
        List<CompletableFuture<?>> applied = new ArrayList<>();
        for (RewardReceipt.Line line : step.getLines()) {
            if (line.getStatus().isPaid()) {
                applied.add(payOne(rule, ctx, line));
            }
        }
        return CompletableFuture.allOf(applied.toArray(new CompletableFuture<?>[0])).thenApply(done -> step);
    }

    /** {@link #payStep} alone, answering the share each paid member got (delivered included, forfeited left out); empty for a once rule. */
    static CompletableFuture<Map<UUID, BigDecimal>> pay(RewardRule rule, RewardContext ctx) {
        return payStep(rule, ctx, 0).thenApply(step -> step.getPaidShares());
    }

    /** @throws IllegalArgumentException when {@code rule} asks for a group role and the owner of {@code ctx} is not a group */
    static void checkRoles(RewardRule rule, RewardContext ctx) {
        for (Eligibility eligibility : rule.getEligibility()) {
            if (CoreRewardPieces.asksForAGroupRole(eligibility) && !ctx.getOwner().isKind(ProgressOwner.GROUP)) {
                throw new IllegalArgumentException("The reward of " + ctx.getCaller() + " is for " + ctx.getOwner() + ", which has no roles:"
                        + " " + eligibility + " only reads a group's leader and roles. Pay it to ProgressOwner.group(id), or pick another"
                        + " eligibility (online, any-member, last-actor...).");
            }
        }
    }

    /**
     * What {@link #payStep} would pay, applying nothing: who is eligible, each one's share and amount, and what becomes of
     * the offline. Posts no event, keeps no delivery and writes no audit line.
     */
    static RewardReceipt.Step settle(RewardRule rule, RewardContext ctx, int index) {
        if (!rule.getMoment().paysAt(ctx.getTrigger())) {
            return new RewardReceipt.Step(index, rule.getName(), rule.isOnce(), false, Collections.<RewardReceipt.Line>emptyList());
        }
        if (rule.isOnce()) {
            return new RewardReceipt.Step(index, rule.getName(), true, true, Collections.<RewardReceipt.Line>emptyList());
        }
        checkRoles(rule, ctx);

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
            return new RewardReceipt.Step(index, rule.getName(), false, true, Collections.<RewardReceipt.Line>emptyList());
        }

        Map<UUID, BigDecimal> shares = rule.getDistribution().sharesOf(Collections.unmodifiableSet(eligible), ctx);
        Map<UUID, BigDecimal> paid = new LinkedHashMap<>();
        Set<UUID> delivered = new LinkedHashSet<>();
        Map<UUID, RewardReceipt.Status> unpaid = new LinkedHashMap<>();
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
                unpaid.put(member, RewardReceipt.Status.REDISTRIBUTED);
            } else {
                unpaid.put(member, RewardReceipt.Status.LOST);
            }
        }
        if (pooled.signum() > 0 && !redistribute(rule, ctx, eligible, pooled, paid)) {
            unpaid.replaceAll((member, status) -> RewardReceipt.Status.LOST);
        }

        List<RewardReceipt.Line> lines = new ArrayList<>();
        Set<UUID> order = new LinkedHashSet<>(shares.keySet());
        order.addAll(paid.keySet());
        for (UUID member : order) {
            if (paid.containsKey(member)) {
                boolean waits = delivered.contains(member);
                lines.add(new RewardReceipt.Line(member, paid.get(member), amountOf(rule, ctx, member, paid.get(member), waits),
                        waits ? RewardReceipt.Status.DELIVERED : RewardReceipt.Status.ONLINE));
            } else if (unpaid.containsKey(member)) {
                lines.add(new RewardReceipt.Line(member, shares.get(member), amountOf(rule, ctx, member, shares.get(member), true), unpaid.get(member)));
            }
        }
        return new RewardReceipt.Step(index, rule.getName(), false, true, lines);
    }

    /** What the rule's effects with an amount give {@code member} for {@code share}; ZERO when none has one. */
    private static BigDecimal amountOf(RewardRule rule, RewardContext ctx, UUID member, BigDecimal share, boolean delivered) {
        BigDecimal amount = rule.getEffects().withShare(share).getAmount(contextOf(ctx, member, delivered));
        return amount == null ? BigDecimal.ZERO : amount;
    }

    /**
     * A scene's participants; otherwise the owner's members (offline included where the owner knows them), then the
     * contributors the leaver rule keeps.
     */
    private static Set<UUID> candidatesOf(RewardRule rule, RewardContext ctx) {
        if (ctx.getScene() != null) {
            return new LinkedHashSet<>(ctx.getContributions().keySet());
        }
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

    /**
     * Splits {@code pooled} among the online eligible by the rule's distribution, normalized so the pool is paid once.
     *
     * @return false when nobody took it: the pool is lost
     */
    private static boolean redistribute(RewardRule rule, RewardContext ctx, Set<UUID> eligible, BigDecimal pooled, Map<UUID, BigDecimal> paid) {
        Set<UUID> present = new LinkedHashSet<>();
        for (UUID member : eligible) {
            if (online(member) != null) {
                present.add(member);
            }
        }
        if (present.isEmpty()) {
            return false;
        }
        Map<UUID, BigDecimal> again = rule.getDistribution().sharesOf(Collections.unmodifiableSet(present), ctx);
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal share : again.values()) {
            total = total.add(share.max(BigDecimal.ZERO));
        }
        if (total.signum() == 0) {
            return false;
        }
        for (Map.Entry<UUID, BigDecimal> share : again.entrySet()) {
            if (share.getValue().signum() <= 0) {
                continue;
            }
            BigDecimal extra = pooled.multiply(share.getValue()).divide(total, CoreRewardPieces.SHARE_SCALE, RoundingMode.DOWN);
            BigDecimal before = paid.get(share.getKey());
            paid.put(share.getKey(), before == null ? extra : before.add(extra));
        }
        return true;
    }

    private static CompletableFuture<Void> payOne(RewardRule rule, RewardContext ctx, RewardReceipt.Line line) {
        UUID member = line.getMember();
        BigDecimal share = line.getShare();
        boolean delivered = line.getStatus() == RewardReceipt.Status.DELIVERED;
        EffectList shared = (EffectList) rule.getEffects().withShare(share);
        Runnable task = () -> {
            ExecutionContext paying = contextOf(ctx, member, delivered).derive()
                    .variable("amount", plain(line.getAmount()))
                    .variable("share", plain(share))
                    .build();
            shared.apply(paying);
            ECEventBus.global().postIfListened(RewardPaidEvent.class,
                    () -> new RewardPaidEvent(ctx.getCaller(), ctx.getOwner(), member, share, rule, delivered));
            AuditLog.ofIfPresent(ctx.getCaller().getPlugin()).ifPresent(log -> {
                //nobody else ever reads this context: a share that failed or skipped would vanish with its trail
                log.record(paying.getAudit());
                log.record(AuditEntry.builder(AuditKind.REWARD_PAID, ctx.getCaller())
                        .owner(ProgressOwner.player(member))
                        .amount(share)
                        .subject(ctx.getOwner().toString())
                        .text(titleOf(rule) + " share " + share.stripTrailingZeros().toPlainString() + " of " + ctx.getOwner()
                                + (delivered ? ", what can wait is a pending delivery" : "") + ": " + shared)
                        .build());
            });
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
     * destination: an amount or an item becomes a delivery, an effect with {@code offline: skip|on-login} follows it, a
     * command without one runs now (or waits under {@code as: player}), and anything else applies without a player. Either way it carries the reward's attributes and, under a scene, the
     * scene's variables and the participant's name as {@code {player}}. Every effect and {@code condition} of a payment
     * reads this context. Internal.
     */
    public static ExecutionContext contextOf(RewardContext ctx, UUID member, boolean delivered) {
        ExecutionContext.Builder builder = sceneContext(ctx);
        RewardScene scene = ctx.getScene();
        if (scene != null) {
            RewardScene.Participant participant = scene.getParticipant(member);
            if (participant != null) {
                //the plugin's name wins over the last name the server saw
                builder.variable("player", participant.getName());
            }
        }
        FPlayer player = delivered ? null : online(member);
        if (player != null) {
            return builder.player(player).build();
        }
        return builder.owner(ProgressOwner.player(member)).attribute(EffectModifiers.DESTINATION_ATTRIBUTE, PendingDestination.ID).build();
    }

    /** The caller's context with the reward's attributes and the scene's variables, before any member. */
    private static ExecutionContext.Builder sceneContext(RewardContext ctx) {
        ExecutionContext.Builder builder = ExecutionContext.builder(ctx.getCaller());
        for (Map.Entry<String, Object> attribute : ctx.getAttributes().entrySet()) {
            builder.attribute(attribute.getKey(), attribute.getValue());
        }
        RewardScene scene = ctx.getScene();
        if (scene != null) {
            for (Map.Entry<String, String> variable : scene.getVariables().entrySet()) {
                builder.variable(variable.getKey(), variable.getValue());
            }
        }
        return builder;
    }

    private static CompletableFuture<Map<UUID, BigDecimal>> payOnce(RewardRule rule, RewardContext ctx) {
        return ECScheduler.onMainThread(() -> {
            ExecutionContext console = sceneContext(ctx).build();
            rule.getEffects().apply(console);
            AuditLog.ofIfPresent(ctx.getCaller().getPlugin()).ifPresent(log -> {
                log.record(console.getAudit());
                log.record(AuditEntry.builder(AuditKind.REWARD_PAID, ctx.getCaller())
                        .owner(ctx.getOwner())
                        .subject(ctx.getOwner().toString())
                        .text(titleOf(rule) + " ran once for " + ctx.getOwner() + ": " + rule.getEffects())
                        .build());
            });
        }).thenApply(done -> Collections.<UUID, BigDecimal>emptyMap());
    }

    private static String plain(BigDecimal number) {
        return number.signum() == 0 ? "0" : number.stripTrailingZeros().toPlainString();
    }

    private static String titleOf(RewardRule rule) {
        return rule.getName() == null ? "reward" : "reward '" + rule.getName() + "'";
    }

    /** The member when it is online; null otherwise. Internal. */
    public static @Nullable FPlayer online(UUID member) {
        FPlayer player = EverNifeCore.getPlatform().getPlayer(member);
        return player != null && player.isOnline() ? player : null;
    }
}
