package br.com.finalcraft.evernifecore.region.placeholder;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.cache.CacheableSupplier;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.integration.placeholders.PAPIIntegration;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.placeholder.manipulation.ManipulationContext;
import br.com.finalcraft.evernifecore.placeholder.manipulation.PlaceholderArgs;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionClock;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.command.FlagValues;
import br.com.finalcraft.evernifecore.region.event.RegionRemovedEvent;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import br.com.finalcraft.evernifecore.region.session.RegionPlayerSession;
import br.com.finalcraft.evernifecore.region.shape.BoundingBox;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * The region placeholders, served on any {@link RegexReplacer} a plugin hands over - one line per prefix:
 * <pre>{@code
 * RegionPlaceholders.register(PAPIIntegration.createPlaceholderIntegration(pluginData, "theduel", IPlayerData.class),
 *         RegionPlaceholderScope.ownedBy(pluginData));
 * }</pre>
 * About the player: {@code in_region}, {@code in_region_[name]}, {@code region_count}. About one region, chosen as the
 * player's ({@code region_<op>}) or by name ({@code region_[name]_<op>}): {@code name}, {@code name_[fallback=...]},
 * {@code has_display_name}, {@code description}, {@code id}, {@code full_id}, {@code plugin_owner}, {@code world},
 * {@code priority}, {@code parent}, {@code expires_in}, {@code min_x}..{@code max_z}, {@code center_x}..{@code center_z},
 * {@code volume}, {@code players_count}, {@code players_<n>}, {@code players_[index=n|sort=name|entry]},
 * {@code players_list}, {@code time_inside}, {@code has_player_[name]}, {@code flag_[flag]}, {@code has_flag_[flag]},
 * {@code flags_count}, {@code groups_count}, {@code group_[group]_members_count}.
 *
 * <p>What does not exist - no region, no player, no value - answers {@code ""}. The players inside a region and the
 * value of a flag in it are read at most once a second per region (per region and flag), whoever asks: a scoreboard
 * asks for every player every few ticks. Placeholders only read, so any thread may resolve them.</p>
 */
public final class RegionPlaceholders {

    /** The prefix the core serves every region under. */
    public static final String CORE_PREFIX = "evernifecore";

    private static final long CACHE_MILLIS = 1000;

    private static final Map<Region, CacheableSupplier<List<FPlayer>>> PLAYERS = new ConcurrentHashMap<>();
    private static final Map<Region, Map<FlagKey, CacheableSupplier<String>>> FLAG_VALUES = new ConcurrentHashMap<>();
    private static volatile boolean followingRemovals;

    private RegionPlaceholders() {
    }

    /**
     * Serves the core's prefix, {@value #CORE_PREFIX}, over every region - only when PlaceholderAPI is there to ask.
     *
     * @return the replacer that answers it; null without PlaceholderAPI
     */
    public static @Nullable RegexReplacer<IPlayerData> registerCore(ECPluginData core) {
        if (!PAPIIntegration.isPresent()) {
            return null;
        }
        RegexReplacer<IPlayerData> replacer = PAPIIntegration.createPlaceholderIntegration(core, CORE_PREFIX, IPlayerData.class);
        register(replacer, RegionPlaceholderScope.everyRegion());
        return replacer;
    }

    /** Adds every region placeholder to {@code replacer}, seeing the regions of {@code scope}. */
    public static <P extends IPlayerData> void register(RegexReplacer<P> replacer, RegionPlaceholderScope scope) {
        followRemovals();

        replacer.addParser("in_region", "Whether you are in a region", data -> {
            FPlayer viewer = playerOf(data);
            return viewer == null ? "" : !scope.at(viewer).isEmpty();
        });
        replacer.addManipulator("in_region_[region]", (data, ctx) -> {
            FPlayer viewer = playerOf(data);
            return viewer == null ? "" : scope.at(viewer).contains(scope.find(ctx.getArgs("[region]").getValue()));
        });
        replacer.addParser("region_count", "How many regions you are in", data -> {
            FPlayer viewer = playerOf(data);
            return viewer == null ? "" : scope.at(viewer).size();
        });

        for (Operation operation : OPERATIONS) {
            String own = "region_" + operation.suffix;
            if (operation.hasGaps()) {
                replacer.addManipulator(own, (data, ctx) -> operation.apply(regionOf(playerOf(data), scope), playerOf(data), ctx, scope));
            } else {
                replacer.addParser(own, data -> operation.apply(regionOf(playerOf(data), scope), playerOf(data), null, scope));
            }
            replacer.addManipulator("region_[region]_" + operation.suffix, (data, ctx) ->
                    operation.apply(scope.find(ctx.getArgs("[region]").getValue()), playerOf(data), ctx, scope));
        }
    }

    private static @Nullable FPlayer playerOf(@Nullable IPlayerData data) {
        return data == null ? null : data.getPlayer();
    }

    private static @Nullable Region regionOf(@Nullable FPlayer player, RegionPlaceholderScope scope) {
        if (player == null) {
            return null;
        }
        List<Region> at = scope.at(player);
        return at.isEmpty() ? null : at.get(0);
    }

    // ------------------------------------------------------------------
    //  The operations on one region
    // ------------------------------------------------------------------

    private interface Answer {
        Object answer(Region region, @Nullable FPlayer viewer, @Nullable ManipulationContext ctx, RegionPlaceholderScope scope);
    }

    private interface Absent {
        @Nullable Object answer(@Nullable ManipulationContext ctx);
    }

    private static final class Operation {
        final String suffix;
        final Answer answer;
        final Absent absent;

        Operation(String suffix, Answer answer, Absent absent) {
            this.suffix = suffix;
            this.answer = answer;
            this.absent = absent;
        }

        boolean hasGaps() {
            return suffix.indexOf('{') >= 0 || suffix.indexOf('[') >= 0;
        }

        @Nullable Object apply(@Nullable Region region, @Nullable FPlayer viewer, @Nullable ManipulationContext ctx, RegionPlaceholderScope scope) {
            return region == null ? absent.answer(ctx) : answer.answer(region, viewer, ctx, scope);
        }
    }

    private enum PlayerOrder { NAME, ENTRY }

    private static final List<Operation> OPERATIONS = new ArrayList<>();

    private static void op(String suffix, Answer answer) {
        OPERATIONS.add(new Operation(suffix, answer, ctx -> ""));
    }

    static {
        OPERATIONS.add(new Operation("name_[options?]",
                (region, viewer, ctx, scope) -> {
                    ctx.getArgs("[options]").allowOnly("fallback");
                    return region.getDisplayName() != null ? region.getDisplayName() : scope.idOf(region);
                },
                ctx -> ctx.getArgs("[options]").allowOnly("fallback").getValue("fallback", "")));
        op("has_display_name", (region, viewer, ctx, scope) -> region.getDisplayName() != null);
        op("description", (region, viewer, ctx, scope) -> region.getDescription() == null ? "" : region.getDescription());
        op("id", (region, viewer, ctx, scope) -> scope.idOf(region));
        op("full_id", (region, viewer, ctx, scope) -> region.getId().toString());
        op("plugin_owner", (region, viewer, ctx, scope) -> region.getId().getOwner());
        op("world", (region, viewer, ctx, scope) -> region.getWorld());
        op("priority", (region, viewer, ctx, scope) -> region.getPriority());
        op("parent", (region, viewer, ctx, scope) -> region.getParent() == null ? "" : scope.idOf(region.getParent()));
        op("expires_in", (region, viewer, ctx, scope) -> {
            Instant expiresAt = region.getExpiresAt();
            return expiresAt == null ? "" : Math.max(0, Duration.between(RegionClock.now(), expiresAt).getSeconds());
        });
        bound("min_x", BoundingBox::getMinX);
        bound("min_y", BoundingBox::getMinY);
        bound("min_z", BoundingBox::getMinZ);
        bound("max_x", BoundingBox::getMaxX);
        bound("max_y", BoundingBox::getMaxY);
        bound("max_z", BoundingBox::getMaxZ);
        center("center_x", BoundingBox::getMinX, BoundingBox::getMaxX);
        center("center_y", BoundingBox::getMinY, BoundingBox::getMaxY);
        center("center_z", BoundingBox::getMinZ, BoundingBox::getMaxZ);
        op("volume", (region, viewer, ctx, scope) -> region.getShape().getVolume() == Shape.UNBOUNDED_VOLUME ? "" : region.getShape().getVolume());
        op("players_count", (region, viewer, ctx, scope) -> playersInside(region).size());
        OPERATIONS.add(new Operation("players_{n}",
                (region, viewer, ctx, scope) -> {
                    Integer n = position(ctx.getString("{n}"));
                    return n == null ? null : nth(playersInside(region), n);
                },
                ctx -> position(ctx.getString("{n}")) == null ? null : ""));   //not a number: maybe another players_ placeholder
        op("players_[options]", (region, viewer, ctx, scope) -> {
            PlaceholderArgs args = ctx.getArgs("[options]").allowOnly("index", "sort");
            int index = args.getInt("index", 1);
            List<FPlayer> players = args.getEnum("sort", PlayerOrder.class, PlayerOrder.NAME) == PlayerOrder.ENTRY
                    ? byEntry(region, playersInside(region))
                    : playersInside(region);
            return nth(players, index);
        });
        op("players_list", (region, viewer, ctx, scope) -> playersInside(region).stream().map(FPlayer::getName).collect(Collectors.joining(", ")));
        op("time_inside", (region, viewer, ctx, scope) -> {
            Instant entered = viewer == null ? null : enteredAt(viewer, region);
            return entered == null ? "" : Duration.between(entered, RegionClock.now()).getSeconds();
        });
        op("has_player_[player]", (region, viewer, ctx, scope) -> {
            String name = ctx.getArgs("[player]").getValue();
            return playersInside(region).stream().anyMatch(player -> player.getName().equalsIgnoreCase(name));
        });
        op("flag_[flag]", (region, viewer, ctx, scope) -> flagValue(region, flag(ctx.getArgs("[flag]"))));
        op("has_flag_[flag]", (region, viewer, ctx, scope) -> region.getFlagValue(flag(ctx.getArgs("[flag]"))) != null);
        op("flags_count", (region, viewer, ctx, scope) -> region.getFlags().size());
        op("groups_count", (region, viewer, ctx, scope) -> region.getGroups().size());
        op("group_[group]_members_count", (region, viewer, ctx, scope) -> {
            AccessGroup group = region.getGroupIfPresent(ctx.getArgs("[group]").getValue());
            return group == null ? "" : group.getMembers().size();
        });
    }

    private static void bound(String suffix, ToIntFunction<BoundingBox> side) {
        op(suffix, (region, viewer, ctx, scope) -> {
            int value = side.applyAsInt(region.getShape().getBounds());
            return isUnbounded(value) ? "" : value;
        });
    }

    private static void center(String suffix, ToIntFunction<BoundingBox> min, ToIntFunction<BoundingBox> max) {
        op(suffix, (region, viewer, ctx, scope) -> {
            BoundingBox bounds = region.getShape().getBounds();
            int low = min.applyAsInt(bounds);
            int high = max.applyAsInt(bounds);
            return isUnbounded(low) || isUnbounded(high) ? "" : number((low + (double) high) / 2);
        });
    }

    /** A world-wide region, and a polygon on Y, have no edge there. */
    private static boolean isUnbounded(int value) {
        return value == Integer.MIN_VALUE || value == Integer.MAX_VALUE;
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static @Nullable Integer position(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static String nth(List<FPlayer> players, int position) {
        return position >= 1 && position <= players.size() ? players.get(position - 1).getName() : "";
    }

    private static @Nullable Instant enteredAt(FPlayer player, Region region) {
        RegionPlayerSession session = RegionLookup.get().getSessionIfPresent(player.getUniqueId());
        return session == null ? null : session.getEnteredAt(region);
    }

    /** First in, first; a player the tracker has not placed goes last, by name. */
    private static List<FPlayer> byEntry(Region region, List<FPlayer> byName) {
        List<FPlayer> sorted = new ArrayList<>(byName);
        sorted.sort(Comparator.comparing(player -> enteredAt(player, region), Comparator.nullsLast(Comparator.naturalOrder())));
        return sorted;
    }

    private static RegionFlag<?> flag(PlaceholderArgs args) {
        String name = args.getValue();
        return FlagRegistry.get().find(name).orElseThrow(() ->
                new PlaceholderArgs.InvalidArgumentException(args.getBlock(), "no registered flag is called '" + name + "'"));
    }

    // ------------------------------------------------------------------
    //  The one-second cache
    // ------------------------------------------------------------------

    /** The online players inside, alphabetically - computed at most once a second per region. */
    private static List<FPlayer> playersInside(Region region) {
        return PLAYERS.computeIfAbsent(region, key -> CacheableSupplier.of(() -> {
            List<FPlayer> inside = new ArrayList<>(key.getPlayersInside());
            inside.sort(Comparator.comparing(FPlayer::getName, String.CASE_INSENSITIVE_ORDER));
            return Collections.unmodifiableList(inside);
        }).withInterval(CACHE_MILLIS)).getValue();
    }

    /** The region's own effective value - parents and the flag's default, no player - at most once a second. */
    private static String flagValue(Region region, RegionFlag<?> flag) {
        return FLAG_VALUES.computeIfAbsent(region, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(flag.getKey(), key -> CacheableSupplier.of(() -> format(flag, RegionLookup.get().resolve(flag, region, null)))
                        .withInterval(CACHE_MILLIS))
                .getValue();
    }

    /** {@code true}/{@code false} for a yes/no flag ({@code ""} when undefined), {@code world x y z} for a place. */
    static String format(RegionFlag<?> flag, @Nullable Object value) {
        if (value == null || value == Tristate.UNDEFINED) {
            return "";
        }
        if (value instanceof Tristate) {
            return value.toString().toLowerCase(Locale.ROOT);
        }
        if (value instanceof WorldLocPos) {
            WorldLocPos at = (WorldLocPos) value;
            return at.getWorldName() + " " + number(at.getX()) + " " + number(at.getY()) + " " + number(at.getZ());
        }
        return FlagValues.format(flag, value);
    }

    private static void followRemovals() {
        if (followingRemovals) {
            return;
        }
        synchronized (RegionPlaceholders.class) {
            if (!followingRemovals) {
                ECEventBus.global().subscribe(RegionRemovedEvent.class, event -> forget(event.getRegion()));
                followingRemovals = true;
            }
        }
    }

    private static void forget(Region region) {
        PLAYERS.remove(region);
        FLAG_VALUES.remove(region);
    }

    /** How many regions the cache holds anything for. */
    static int getCachedRegions() {
        Map<Region, Boolean> regions = new ConcurrentHashMap<>();
        PLAYERS.keySet().forEach(region -> regions.put(region, true));
        FLAG_VALUES.keySet().forEach(region -> regions.put(region, true));
        return regions.size();
    }
}
