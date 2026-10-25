package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.cooldown.Cooldowns;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.time.Window;
import br.com.finalcraft.everylibs.util.FCTimeUtil;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;

/**
 * How often something may happen: a limit of uses (per {@link Window} when one is set, forever otherwise) and a minimum
 * gap between two uses. Immutable - every wither returns a new policy. The counter it governs is a {@link Cooldown},
 * resolved per owner by {@link Cooldowns#resolve(Caller, ProgressOwner, String, RepeatPolicy)}.
 */
public final class RepeatPolicy {

    /** Where a player's or the server's counter lives: this server only, or every server of the network. */
    public enum Reach { LOCAL, NETWORK }

    private static final int UNLIMITED = -1;

    private final int limit;
    private final @Nullable Duration cooldown;
    private final @Nullable Window window;
    private final Reach reach;
    private final @Nullable Boolean persistent;

    private RepeatPolicy(int limit, @Nullable Duration cooldown, @Nullable Window window, Reach reach, @Nullable Boolean persistent) {
        this.limit = limit;
        this.cooldown = cooldown;
        this.window = window;
        this.reach = reach;
        this.persistent = persistent;
    }

    /** One use, forever (or once per window). */
    public static RepeatPolicy once() {
        return limited(1);
    }

    /** {@code uses} uses, forever or per window when one is set. */
    public static RepeatPolicy limited(int uses) {
        if (uses < 1) {
            throw new IllegalArgumentException("A limit of " + uses + " uses lets nothing through: write at least 1, or use unlimited().");
        }
        return new RepeatPolicy(uses, null, null, Reach.LOCAL, null);
    }

    /** No limit of uses: only a gap, when one is set, holds a use back. */
    public static RepeatPolicy unlimited() {
        return new RepeatPolicy(UNLIMITED, null, null, Reach.LOCAL, null);
    }

    /** The minimum gap between two uses. */
    public RepeatPolicy cooldown(Duration between) {
        if (between.isNegative()) {
            throw new IllegalArgumentException("A cooldown of " + between + " is negative: write a gap of zero or more.");
        }
        return new RepeatPolicy(limit, between, window, reach, persistent);
    }

    /** The count of uses resets at each start of {@code window}. */
    public RepeatPolicy window(Window window) {
        return new RepeatPolicy(limit, cooldown, Objects.requireNonNull(window, "window"), reach, persistent);
    }

    /** Where a player's or the server's counter lives; {@link Reach#LOCAL} by default. */
    public RepeatPolicy reach(Reach reach) {
        return new RepeatPolicy(limit, cooldown, window, Objects.requireNonNull(reach, "reach"), persistent);
    }

    /** Whether the counter outlives a restart; by default a limit does and a bare cooldown does not. */
    public RepeatPolicy persistent(boolean persistent) {
        return new RepeatPolicy(limit, cooldown, window, reach, persistent);
    }

    public boolean isLimited() {
        return limit != UNLIMITED;
    }

    /** The limit of uses; {@link Integer#MAX_VALUE} when unlimited. */
    public int getLimit() {
        return isLimited() ? limit : Integer.MAX_VALUE;
    }

    public @Nullable Duration getCooldown() {
        return cooldown;
    }

    public @Nullable Window getWindow() {
        return window;
    }

    public Reach getReach() {
        return reach;
    }

    public boolean isPersistent() {
        return persistent != null ? persistent : isLimited();
    }

    /**
     * A cost that takes one use of the counter {@code counterId} of the context's owner: the gate takes it with the other
     * costs and gives it back when a later one does not come out.
     */
    public Cost asCost(String counterId) {
        RepeatPolicy policy = this;
        return new Cost() {
            private Cooldown counter(ExecutionContext ctx) {
                return Cooldowns.resolve(ctx.getCaller(), ctx.getOwner(), counterId, policy);
            }

            @Override
            public ConditionResult test(ExecutionContext ctx) {
                Cooldown counter = counter(ctx);
                return counter.canUse() ? ConditionResult.pass() : ConditionResult.fail(counter.explain(ctx.getLocale()));
            }

            @Override
            public boolean take(ExecutionContext ctx) {
                return counter(ctx).tryUse();
            }

            @Override
            public void refund(ExecutionContext ctx) {
                counter(ctx).giveBack();
            }

            @Override
            public FancyText describe(ExecutionContext ctx) {
                Cooldown counter = counter(ctx);
                return FancyText.of(policy.isLimited()
                        ? "1 use of " + counterId + " (" + counter.getUsesLeft() + " of " + policy.getLimit() + " left)"
                        : "1 use of " + counterId);
            }

            @Override
            public String toString() {
                return "repeat " + counterId + " " + policy;
            }
        };
    }

    /**
     * Reads {@code {limit: 3, cooldown: 10m, window: "daily at 04:00", reach: network, persistent: true}}; every key is
     * optional, and no {@code limit} is unlimited. A {@code window} without its own zone uses {@code zone}.
     */
    public static RepeatPolicy parse(JsonNode node, ZoneId zone) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("A repeat policy is a map, e.g. {limit: 3, cooldown: 10m, window: \"daily at 04:00\"};"
                    + " got " + node + ".");
        }
        for (Iterator<String> keys = node.fieldNames(); keys.hasNext(); ) {
            String key = keys.next();
            if (!key.equals("limit") && !key.equals("cooldown") && !key.equals("window") && !key.equals("reach") && !key.equals("persistent")) {
                throw new IllegalArgumentException("A repeat policy has no key '" + key + "': write limit, cooldown, window, reach or persistent.");
            }
        }
        RepeatPolicy policy;
        if (node.has("limit")) {
            JsonNode limit = node.get("limit");
            if (!limit.canConvertToInt() || !limit.isIntegralNumber()) {
                throw new IllegalArgumentException("The repeat policy's limit '" + limit.asText() + "' is not a whole number: write e.g. limit: 3.");
            }
            policy = limited(limit.asInt());
        } else {
            policy = unlimited();
        }
        if (node.has("cooldown")) {
            String text = node.get("cooldown").asText().trim();
            long millis;
            try {
                millis = text.matches("\\d+") ? Long.parseLong(text) * 1000L : FCTimeUtil.toMillis(text);
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("The repeat policy's cooldown '" + text + "' is not a duration: write e.g. 10m, 1h30m,"
                        + " 90s or a plain number of seconds.", invalid);
            }
            policy = policy.cooldown(Duration.ofMillis(millis));
        }
        if (node.has("window")) {
            policy = policy.window(Window.parse(node.get("window"), zone));
        }
        if (node.has("reach")) {
            String text = node.get("reach").asText().trim();
            try {
                policy = policy.reach(Reach.valueOf(text.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("The repeat policy's reach '" + text + "' is not one: write local or network.", invalid);
            }
        }
        if (node.has("persistent")) {
            policy = policy.persistent(node.get("persistent").asBoolean());
        }
        return policy;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof RepeatPolicy)) {
            return false;
        }
        RepeatPolicy that = (RepeatPolicy) other;
        return limit == that.limit && Objects.equals(cooldown, that.cooldown) && Objects.equals(window, that.window)
                && reach == that.reach && Objects.equals(persistent, that.persistent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(limit, cooldown, window, reach, persistent);
    }

    @Override
    public String toString() {
        return "RepeatPolicy{limit=" + (isLimited() ? String.valueOf(limit) : "unlimited") + ", cooldown=" + cooldown
                + ", window=" + window + ", reach=" + reach + ", persistent=" + isPersistent() + "}";
    }
}
