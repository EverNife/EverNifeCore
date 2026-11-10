package br.com.finalcraft.evernifecore.logic.reward;

import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who got what from which step of a {@link RewardTable}: what {@link RewardTable#pay} paid, or what
 * {@link RewardTable#preview} would pay. Immutable; any thread.
 */
public final class RewardReceipt {

    /** What became of a member's share. */
    public enum Status {
        /** Applied to the member, online. */
        ONLINE,
        /** Kept as a pending delivery of the offline member ({@code absent: deliver}); what cannot wait follows its {@code offline}. */
        DELIVERED,
        /** Not paid: the member was offline and the step forfeits, or redistributes with nobody eligible online. */
        LOST,
        /** Not paid to the offline member: split among the online eligible, whose lines carry it. */
        REDISTRIBUTED;

        /** Whether the member got it, now or on login. */
        public boolean isPaid() {
            return this == ONLINE || this == DELIVERED;
        }
    }

    private final List<Step> steps;

    RewardReceipt(List<Step> steps) {
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
    }

    /** One per step of the table, in its order. Read-only. */
    public List<Step> getSteps() {
        return steps;
    }

    /** @throws IndexOutOfBoundsException for an index the table has no step at */
    public Step getStep(int index) {
        return steps.get(index);
    }

    /** What every step paid {@code member} ({@link Status#isPaid() paid} lines only); ZERO when nothing. */
    public BigDecimal getAmount(UUID member) {
        BigDecimal total = BigDecimal.ZERO;
        for (Step step : steps) {
            Line line = step.getLine(member);
            if (line != null && line.getStatus().isPaid()) {
                total = total.add(line.getAmount());
            }
        }
        return total;
    }

    @Override
    public String toString() {
        return "RewardReceipt" + steps;
    }

    /** One step of the table: who it paid, or, {@link #isOnce() once}, whether its effects ran. */
    public static final class Step {
        private final int index;
        private final @Nullable String name;
        private final boolean once;
        private final boolean run;
        private final Map<UUID, Line> lines;

        Step(int index, @Nullable String name, boolean once, boolean run, List<Line> lines) {
            this.index = index;
            this.name = name;
            this.once = once;
            this.run = run;
            Map<UUID, Line> byMember = new LinkedHashMap<>();
            for (Line line : lines) {
                byMember.put(line.getMember(), line);
            }
            this.lines = Collections.unmodifiableMap(byMember);
        }

        /** Its position in the table, from 0. */
        public int getIndex() {
            return index;
        }

        /** The step's {@code name}; null when it has none. */
        public @Nullable String getName() {
            return name;
        }

        public boolean isOnce() {
            return once;
        }

        /** Whether its moment took the payment's trigger; false = it paid nothing and ran nothing. */
        public boolean isRun() {
            return run;
        }

        /** One per member it settled, in the order paid; empty for a once step. Read-only. */
        public List<Line> getLines() {
            return new ArrayList<>(lines.values());
        }

        /** Null when the step settled nothing for {@code member}. */
        public @Nullable Line getLine(UUID member) {
            return lines.get(member);
        }

        /** The share of every member it paid (delivered included), in its order. */
        Map<UUID, BigDecimal> getPaidShares() {
            Map<UUID, BigDecimal> paid = new LinkedHashMap<>();
            for (Line line : lines.values()) {
                if (line.getStatus().isPaid()) {
                    paid.put(line.getMember(), line.getShare());
                }
            }
            return Collections.unmodifiableMap(paid);
        }

        @Override
        public String toString() {
            return "Step{" + index + (name == null ? "" : " '" + name + "'") + (once ? ", once" : "") + (run ? "" : ", not run")
                    + ", " + lines.values() + "}";
        }
    }

    /** One member in one step. */
    public static final class Line {
        private final UUID member;
        private final BigDecimal share;
        private final BigDecimal amount;
        private final Status status;

        Line(UUID member, BigDecimal share, BigDecimal amount, Status status) {
            this.member = member;
            this.share = share;
            this.amount = amount;
            this.status = status;
        }

        public UUID getMember() {
            return member;
        }

        /** The part of the step it got, 0-1 - a redistributed share included; for a line not paid, the part it had. */
        public BigDecimal getShare() {
            return share;
        }

        /** What the step's effects with an amount give for that share ({@code {amount}}); ZERO when none has one. */
        public BigDecimal getAmount() {
            return amount;
        }

        public Status getStatus() {
            return status;
        }

        @Override
        public String toString() {
            return member + " " + status + " share " + share.stripTrailingZeros().toPlainString() + " amount "
                    + amount.stripTrailingZeros().toPlainString();
        }
    }
}
