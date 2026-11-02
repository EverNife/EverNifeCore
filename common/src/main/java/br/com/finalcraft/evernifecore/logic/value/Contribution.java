package br.com.finalcraft.evernifecore.logic.value;

import br.com.finalcraft.evernifecore.logic.audit.AuditEntry;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.logic.audit.AuditLog;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A goal several players fill together toward a {@link #getTarget() target owner}: each {@link #deposit} adds what the
 * depositor gave (or holds, for a checking requirement) up to what is still missing, by member; the total never passes
 * the target, and reaching it applies the completion effects once.
 *
 * <p>Any thread. The requirement runs while the contribution is not locked, so a reader on the player's thread never
 * waits behind a deposit that is itself waiting for that thread; deposits racing for the last of the target are
 * counted one at a time, and what one took past the target is given back through
 * {@link ContributionRequirement#giveBack}.
 */
public final class Contribution<T> {

    private final Contributions contributions;
    private final String key;
    private final String id;
    private final ProgressOwner target;
    private final ContributionRequirement<T> requirement;
    private volatile List<Effect> onComplete = Collections.emptyList();

    Contribution(Contributions contributions, String key, String id, ProgressOwner target, ContributionRequirement<T> requirement) {
        this.contributions = contributions;
        this.key = key;
        this.id = id;
        this.target = target;
        this.requirement = requirement;
    }

    /** The effects applied, with the completing deposit's context, when the target is reached. */
    public Contribution<T> onComplete(Effect... effects) {
        this.onComplete = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(effects)));
        return this;
    }

    /**
     * Takes from the context's player up to what is missing (consuming) or counts what they hold beyond what they
     * already counted (checking), posts a {@link ContributionDepositEvent} when something counted, and applies the
     * completion effects once, posting a {@link ContributionCompletedEvent}, when the target is reached. What counted is
     * recorded as a {@code DEPOSIT} in the {@link AuditLog} of the contributions' plugin, when it has one. What a racing
     * deposit left no room for is given back; a give-back that fails is audited {@code REFUND_FAILED} and logged severe,
     * never thrown.
     *
     * @throws IllegalStateException when the context has no player - a deposit is always somebody's
     */
    public DepositResult deposit(ExecutionContext ctx) {
        FPlayer player = depositor(ctx);
        String member = player.getUniqueId().toString();
        Contributions.Row row = contributions.rows.computeIfAbsent(key, Contributions.Row::new);
        BigDecimal missing;
        synchronized (row) {
            if (row.complete) {
                return new DepositResult(BigDecimal.ZERO, new BigDecimal(row.total), false);
            }
            missing = requirement.getTarget().subtract(new BigDecimal(row.total));
        }
        //a wallet or an inventory may wait for the player's thread, which may be reading this row: never under its monitor
        BigDecimal reachedOut = requirement.isConsuming() ? requirement.take(ctx, missing) : requirement.countHeld(ctx);
        BigDecimal counted;
        BigDecimal total;
        boolean completedNow = false;
        Contributions.Row snapshot = null;
        synchronized (row) {
            total = new BigDecimal(row.total);
            BigDecimal room = requirement.getTarget().subtract(total).max(BigDecimal.ZERO);
            BigDecimal given = new BigDecimal(row.members.getOrDefault(member, "0"));
            counted = (requirement.isConsuming() ? reachedOut : reachedOut.subtract(given)).max(BigDecimal.ZERO).min(room);
            if (counted.signum() > 0) {
                total = total.add(counted);
                row.total = total.toPlainString();
                row.members.put(member, given.add(counted).toPlainString());
                completedNow = total.compareTo(requirement.getTarget()) >= 0;
                row.complete = completedNow;
                snapshot = row.copy();
            }
        }
        if (requirement.isConsuming() && reachedOut.compareTo(counted) > 0) {
            giveBack(ctx, player, reachedOut.subtract(counted));
        }
        if (snapshot == null) {
            return new DepositResult(BigDecimal.ZERO, total, false);
        }
        contributions.write(snapshot);
        BigDecimal deposited = counted;
        BigDecimal reached = total;
        ECEventBus.global().postIfListened(ContributionDepositEvent.class, () -> new ContributionDepositEvent(this, ctx, deposited, reached));
        AuditLog.ofIfPresent(contributions.getPlugin()).ifPresent(log -> log.record(AuditEntry.builder(AuditKind.DEPOSIT, ctx.getCaller())
                .owner(target)
                .player(player.getUniqueId())
                .subject(id)
                .amount(deposited)
                .text(deposited.toPlainString() + " of " + requirement + " toward " + target + ", " + reached.toPlainString() + " in all")
                .build()));
        if (completedNow) {
            ECEventBus.global().postIfListened(ContributionCompletedEvent.class, () -> new ContributionCompletedEvent(this, ctx));
            for (Effect effect : onComplete) {
                Effects.apply(ctx, effect);
            }
        }
        return new DepositResult(counted, total, completedNow);
    }

    private void giveBack(ExecutionContext ctx, FPlayer player, BigDecimal surplus) {
        try {
            requirement.giveBack(ctx, surplus);
        } catch (RuntimeException failure) {
            String text = surplus.toPlainString() + " of " + requirement + " taken from " + player.getName() + " (uuid " + player.getUniqueId()
                    + ") toward " + target + " found the target already filled, and giving it back failed (" + failure + ") - give it back by hand.";
            AuditLog.ofIfPresent(contributions.getPlugin()).ifPresent(log -> log.record(AuditEntry.builder(AuditKind.REFUND_FAILED, ctx.getCaller())
                    .owner(target)
                    .player(player.getUniqueId())
                    .subject(id)
                    .amount(surplus)
                    .text(text)
                    .build()));
            contributions.getPlugin().getLog().severe("[" + ctx.getCaller() + "] " + text);
        }
    }

    static FPlayer depositor(ExecutionContext ctx) {
        FPlayer player = ctx.getPlayer();
        if (player == null) {
            throw new IllegalStateException("A deposit is a player's, and the context of " + ctx.getCaller() + " has none: build it with"
                    + " .player(player).");
        }
        return player;
    }

    public String getId() {
        return id;
    }

    /** Who the goal is for. */
    public ProgressOwner getTarget() {
        return target;
    }

    public ContributionRequirement<T> getRequirement() {
        return requirement;
    }

    public BigDecimal getTotal() {
        Contributions.Row row = contributions.rows.get(key);
        if (row == null) {
            return BigDecimal.ZERO;
        }
        synchronized (row) {
            return new BigDecimal(row.total);
        }
    }

    /** What each member counted, in the order they first deposited. */
    public Map<UUID, BigDecimal> getByMember() {
        Map<UUID, BigDecimal> members = new LinkedHashMap<>();
        Contributions.Row row = contributions.rows.get(key);
        if (row != null) {
            synchronized (row) {
                for (Map.Entry<String, String> entry : row.members.entrySet()) {
                    members.put(UUID.fromString(entry.getKey()), new BigDecimal(entry.getValue()));
                }
            }
        }
        return members;
    }

    public boolean isComplete() {
        Contributions.Row row = contributions.rows.get(key);
        return row != null && row.complete;
    }

    @Override
    public String toString() {
        return "Contribution{" + id + " for " + target + ", " + requirement + "}";
    }
}
