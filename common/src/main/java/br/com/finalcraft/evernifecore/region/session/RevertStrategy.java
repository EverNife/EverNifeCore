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
 * commands write - one of six:
 * <ul>
 *     <li>{@code last-valid} - the center of the last accepted block (the default);</li>
 *     <li>{@code trail:<n>} - the {@code n}-th accepted block before that one;</li>
 *     <li>{@code allow-after:<n>} - deny, but let the {@code n}-th denial in a row through;</li>
 *     <li>{@code fixed:<x>,<y>,<z>[,<world>]} - a point in the named world, or in whatever world the player is in
 *     when reverted;</li>
 *     <li>{@code spawn} - the server spawn a spawn plugin registered, else the world's ({@code SpawnPoints});</li>
 *     <li>{@code worldspawn} - the spawn point of the world the player is in.</li>
 * </ul>
 * The numbers come first and the world last because the value lives inside {@code key=value} settings, where a
 * space would end it.
 */
public final class RevertStrategy {

    public enum Type {
        /** The center of the last block the player stood in with the move accepted. */
        LAST_VALID,
        /** The {@code n}-th accepted block before that one, along the way the player came. */
        TRAIL,
        /** Deny, but let the {@code n}-th denial in a row through. */
        ALLOW_AFTER,
        /** A fixed point, in a named world or in the player's own. */
        FIXED,
        /** The server spawn, or the world spawn while no spawn plugin registered one. */
        SPAWN,
        /** The spawn point of the player's world. */
        WORLD_SPAWN
    }

    private static final RevertStrategy LAST_VALID = new RevertStrategy(Type.LAST_VALID, 0, 0, 0, 0, null);
    private static final RevertStrategy SPAWN = new RevertStrategy(Type.SPAWN, 0, 0, 0, 0, null);
    private static final RevertStrategy WORLD_SPAWN = new RevertStrategy(Type.WORLD_SPAWN, 0, 0, 0, 0, null);

    private static final String ACCEPTED_FORMS = "last-valid, trail:<blocks back>, allow-after:<denials>, fixed:<x>,<y>,<z>[,<world>],"
            + " spawn or worldspawn - e.g. trail:2, fixed:10,64,-3 or fixed:10,64,-3,world";

    /** Reads and writes the text form, for settings and commands. */
    public static final FlagType<RevertStrategy> TYPE = new StrategyType();

    private final Type type;
    private final int count;
    private final double x;
    private final double y;
    private final double z;
    private final @Nullable String world;

    private RevertStrategy(Type type, int count, double x, double y, double z, @Nullable String world) {
        this.type = type;
        this.count = count;
        this.x = x;
        this.y = y;
        this.z = z;
        this.world = world;
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
        return new RevertStrategy(Type.TRAIL, stepsBack, 0, 0, 0, null);
    }

    /** @throws IllegalArgumentException when {@code denials} is below 1 */
    public static RevertStrategy allowAfter(int denials) {
        if (denials < 1) {
            throw new IllegalArgumentException("allowAfter(" + denials + ") needs at least 1 denial - allowAfter(1) lets the first one through.");
        }
        return new RevertStrategy(Type.ALLOW_AFTER, denials, 0, 0, 0, null);
    }

    /** A point in a named world. */
    public static RevertStrategy fixed(WorldLocPos at) {
        Objects.requireNonNull(at, "at");
        return new RevertStrategy(Type.FIXED, 0, at.getX(), at.getY(), at.getZ(), Objects.requireNonNull(at.getWorldName(), "at.worldName"));
    }

    /** A point in whatever world the player is in when reverted. */
    public static RevertStrategy fixed(double x, double y, double z) {
        return new RevertStrategy(Type.FIXED, 0, x, y, z, null);
    }

    /** The server spawn ({@code ISpawnProvider}), or the world spawn while no spawn plugin registered one. */
    public static RevertStrategy spawn() {
        return SPAWN;
    }

    /** The spawn point of the player's world. */
    public static RevertStrategy worldSpawn() {
        return WORLD_SPAWN;
    }

    public Type getType() {
        return type;
    }

    /** The steps back of {@link Type#TRAIL}, the denials of {@link Type#ALLOW_AFTER}; 0 for the others. */
    public int getCount() {
        return count;
    }

    /** The point of a {@link Type#FIXED} that names its world; null for one in the player's world and for the others. */
    public @Nullable WorldLocPos getPoint() {
        return type == Type.FIXED && world != null ? WorldLocPos.of(x, y, z, world) : null;
    }

    /** The coordinates of {@link Type#FIXED}; 0 for the others. */
    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    /** The world a {@link Type#FIXED} names; null when it is the player's own, and for the other kinds. */
    public @Nullable String getWorld() {
        return world;
    }

    @JsonValue
    public String toText() {
        switch (type) {
            case TRAIL:
                return "trail:" + count;
            case ALLOW_AFTER:
                return "allow-after:" + count;
            case FIXED:
                return "fixed:" + number(x) + "," + number(y) + "," + number(z) + (world == null ? "" : "," + world);
            case SPAWN:
                return "spawn";
            case WORLD_SPAWN:
                return "worldspawn";
            default:
                return "last-valid";
        }
    }

    /**
     * The inverse of {@link #toText()}.
     *
     * @throws IllegalArgumentException naming the text and the six accepted forms
     */
    @JsonCreator
    public static RevertStrategy parse(String text) {
        String trimmed = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        try {
            if (trimmed.equals("last-valid")) {
                return lastValid();
            }
            if (trimmed.equals("spawn")) {
                return spawn();
            }
            if (trimmed.equals("worldspawn")) {
                return worldSpawn();
            }
            if (trimmed.startsWith("trail:")) {
                return trail(Integer.parseInt(trimmed.substring("trail:".length())));
            }
            if (trimmed.startsWith("allow-after:")) {
                return allowAfter(Integer.parseInt(trimmed.substring("allow-after:".length())));
            }
            if (trimmed.startsWith("fixed:")) {
                //the world keeps its case: world names are case sensitive on most platforms
                String[] parts = text.trim().substring("fixed:".length()).split(",", -1);
                if (parts.length == 3 || (parts.length == 4 && !parts[3].trim().isEmpty())) {
                    double x = Double.parseDouble(parts[0]);
                    double y = Double.parseDouble(parts[1]);
                    double z = Double.parseDouble(parts[2]);
                    return parts.length == 3 ? fixed(x, y, z) : fixed(WorldLocPos.of(x, y, z, parts[3].trim()));
                }
            }
        } catch (NumberFormatException notANumber) {
            //falls through to the message below
        } catch (IllegalArgumentException outOfRange) {
            throw new IllegalArgumentException("Invalid revert strategy '" + text + "': " + outOfRange.getMessage(), outOfRange);
        }
        throw new IllegalArgumentException("Invalid revert strategy '" + text + "': write " + ACCEPTED_FORMS + ".");
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
        return type == that.type && count == that.count && Objects.equals(toText(), that.toText());
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
            List<String> forms = new ArrayList<>(Arrays.asList("last-valid", "trail:", "allow-after:", "fixed:", "spawn", "worldspawn"));
            String lower = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
            forms.removeIf(form -> !form.startsWith(lower));
            return forms;
        }
    }
}
