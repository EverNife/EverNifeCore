package br.com.finalcraft.evernifecore.region.session;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.flag.FlagParseException;
import br.com.finalcraft.evernifecore.region.flag.FlagType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Where a player whose movement was denied goes back to. Immutable; its text form is what settings files and
 * commands write: {@code last-valid}, {@code trail:2}, {@code allow-after:5}, {@code fixed:world,10,64,-3}.
 */
public final class RevertStrategy {

    public enum Kind {
        /** The center of the last block the player stood in with the move accepted. */
        LAST_VALID,
        /** The {@code n}-th accepted block before that one, along the way the player came. */
        TRAIL,
        /** Deny, but let the {@code n}-th denial in a row through. */
        ALLOW_AFTER,
        /** A fixed point. */
        FIXED
    }

    private static final RevertStrategy LAST_VALID = new RevertStrategy(Kind.LAST_VALID, 0, null);

    /** Reads and writes the text form, for settings and commands. */
    public static final FlagType<RevertStrategy> TYPE = new StrategyType();

    private final Kind kind;
    private final int count;
    private final @Nullable WorldLocPos point;

    private RevertStrategy(Kind kind, int count, @Nullable WorldLocPos point) {
        this.kind = kind;
        this.count = count;
        this.point = point;
    }

    /** The default: the center of the last accepted block. */
    public static RevertStrategy lastValid() {
        return LAST_VALID;
    }

    /** @throws IllegalArgumentException when {@code stepsBack} is below 1 */
    public static RevertStrategy trail(int stepsBack) {
        if (stepsBack < 1) {
            throw new IllegalArgumentException("trail(" + stepsBack + ") must go back at least 1 block - trail(0) is lastValid().");
        }
        return new RevertStrategy(Kind.TRAIL, stepsBack, null);
    }

    /** @throws IllegalArgumentException when {@code denials} is below 1 */
    public static RevertStrategy allowAfter(int denials) {
        if (denials < 1) {
            throw new IllegalArgumentException("allowAfter(" + denials + ") needs at least 1 denial - allowAfter(1) lets the first one through.");
        }
        return new RevertStrategy(Kind.ALLOW_AFTER, denials, null);
    }

    public static RevertStrategy fixed(WorldLocPos at) {
        return new RevertStrategy(Kind.FIXED, 0, Objects.requireNonNull(at, "at"));
    }

    public Kind getKind() {
        return kind;
    }

    /** The steps back of {@link Kind#TRAIL}, the denials of {@link Kind#ALLOW_AFTER}; 0 for the others. */
    public int getCount() {
        return count;
    }

    /** The point of {@link Kind#FIXED}; null for the others. */
    public @Nullable WorldLocPos getPoint() {
        return point;
    }

    @JsonValue
    public String toText() {
        switch (kind) {
            case TRAIL:
                return "trail:" + count;
            case ALLOW_AFTER:
                return "allow-after:" + count;
            case FIXED:
                return "fixed:" + point.getWorldName() + "," + number(point.getX()) + "," + number(point.getY()) + "," + number(point.getZ());
            default:
                return "last-valid";
        }
    }

    /**
     * The inverse of {@link #toText()}.
     *
     * @throws IllegalArgumentException naming the text and the four accepted forms
     */
    @JsonCreator
    public static RevertStrategy parse(String text) {
        String trimmed = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        try {
            if (trimmed.equals("last-valid")) {
                return lastValid();
            }
            if (trimmed.startsWith("trail:")) {
                return trail(Integer.parseInt(trimmed.substring("trail:".length())));
            }
            if (trimmed.startsWith("allow-after:")) {
                return allowAfter(Integer.parseInt(trimmed.substring("allow-after:".length())));
            }
            if (trimmed.startsWith("fixed:")) {
                String[] parts = text.trim().substring("fixed:".length()).split(",", -1);
                if (parts.length == 4 && !parts[0].isEmpty()) {
                    return fixed(WorldLocPos.of(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), parts[0]));
                }
            }
        } catch (NumberFormatException notANumber) {
            //falls through to the message below
        } catch (IllegalArgumentException outOfRange) {
            throw new IllegalArgumentException("Invalid revert strategy '" + text + "': " + outOfRange.getMessage(), outOfRange);
        }
        throw new IllegalArgumentException("Invalid revert strategy '" + text + "': write last-valid, trail:<blocks back>, allow-after:<denials>"
                + " or fixed:<world>,<x>,<y>,<z>, e.g. trail:2 or fixed:world,10,64,-3.");
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof RevertStrategy)) {
            return false;
        }
        RevertStrategy that = (RevertStrategy) other;
        return kind == that.kind && count == that.count && Objects.equals(toText(), that.toText());
    }

    @Override
    public int hashCode() {
        return toText().hashCode();
    }

    @Override
    public String toString() {
        return toText();
    }

    private static final class StrategyType implements FlagType<RevertStrategy> {

        @Override
        public String getId() {
            return "revert-strategy";
        }

        @Override
        public Class<RevertStrategy> getValueType() {
            return RevertStrategy.class;
        }

        @Override
        public RevertStrategy parse(String text) throws FlagParseException {
            try {
                return RevertStrategy.parse(text);
            } catch (IllegalArgumentException invalid) {
                throw new FlagParseException(invalid.getMessage(), invalid);
            }
        }

        @Override
        public String format(RevertStrategy value) {
            return value.toText();
        }

        @Override
        public JsonNode toJson(RevertStrategy value) {
            return TextNode.valueOf(value.toText());
        }

        @Override
        public RevertStrategy fromJson(JsonNode node) throws FlagParseException {
            return parse(node == null ? null : node.asText());
        }

        @Override
        public List<String> tabComplete(String partial) {
            List<String> forms = new ArrayList<>(Arrays.asList("last-valid", "trail:", "allow-after:", "fixed:"));
            String lower = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
            forms.removeIf(form -> !form.startsWith(lower));
            return forms;
        }
    }
}
