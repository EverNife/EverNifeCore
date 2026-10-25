package br.com.finalcraft.evernifecore.ranking;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.integration.placeholders.PAPIIntegration;
import br.com.finalcraft.evernifecore.placeholder.manipulation.ManipulationContext;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.time.ECClock;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The ranked-list placeholders, served on any {@link RegexReplacer} a plugin hands over:
 * <pre>{@code
 * RankedPlaceholders.register(replacer, plugin);   // %finaljobs_top_[miner-level]_1_name%
 * }</pre>
 * {@code top_[list]_{n}_name} (a player's name, otherwise the entry's text), {@code top_[list]_{n}_value} (what the
 * list is ordered by), {@code top_[list]_{n}_[key]} (a value the list declared with {@code addValue}),
 * {@code top_[list]_size} / {@code _total} (entries kept / returned by the source), {@code top_[list]_position} (the
 * asking player's place) and {@code top_[list]_age} (seconds since the read).
 *
 * <p>What does not exist - list, entry, key, player on the list - answers {@code ""}. Placeholders read the shared
 * snapshot and never wait: a list whose read is still running answers {@code ""}, and asking is what starts the read.
 * Any thread.</p>
 */
public final class RankedPlaceholders {

    /** The prefix the core serves every list under, by full id: {@code %evernifecore_top_[owner:name]_...%}. */
    public static final String CORE_PREFIX = "evernifecore";

    private RankedPlaceholders() {
    }

    /** Adds the placeholders to {@code replacer}; {@code [list]} names one of {@code owner}'s lists by its name. */
    public static <P extends IPlayerData> void register(RegexReplacer<P> replacer, ECPluginData owner) {
        String ownerPrefix = owner.getMetaInfo().getName().toLowerCase(Locale.ROOT) + ":";
        register(replacer, name -> RankedLists.find(ownerPrefix + name));
    }

    /**
     * Serves {@value #CORE_PREFIX}, where {@code [list]} is a full id - only when PlaceholderAPI is there to ask.
     *
     * @return the replacer that answers it; null without PlaceholderAPI
     */
    public static @Nullable RegexReplacer<IPlayerData> registerCore(ECPluginData core) {
        if (!PAPIIntegration.isPresent()) {
            return null;
        }
        RegexReplacer<IPlayerData> replacer = PAPIIntegration.createPlaceholderIntegration(core, CORE_PREFIX, IPlayerData.class);
        register(replacer, RankedLists::find);
        return replacer;
    }

    private static <P extends IPlayerData> void register(RegexReplacer<P> replacer, Function<String, RankedList<?>> lists) {
        replacer.addManipulator("top_[list]_{op}", (data, ctx) -> {
            RankedList<?> list = lists.apply(ctx.getArgs("[list]").getValue());
            return list == null ? "" : answer(list, data, ctx);
        });
    }

    private static <T> String answer(RankedList<T> list, @Nullable IPlayerData asker, ManipulationContext ctx) {
        RankedSnapshot<T> snapshot = current(list);
        if (snapshot == null) {
            return "";
        }
        String op = ctx.getString("{op}");
        switch (op.toLowerCase(Locale.ROOT)) {
            case "size":
                return String.valueOf(snapshot.getShownCount());
            case "total":
                return String.valueOf(snapshot.getTotalCount());
            case "position":
                Integer position = asker == null ? null : snapshot.positionOf(asker.getUniqueId());
                return position == null ? "" : String.valueOf(position);
            case "age":
                return String.valueOf((ECClock.now().toEpochMilli() - snapshot.getBuiltAt()) / 1000);
            default:
                return entryValue(list, snapshot, op);
        }
    }

    // {n}_name, {n}_value or {n}_[key]
    private static <T> String entryValue(RankedList<T> list, RankedSnapshot<T> snapshot, String op) {
        int cut = op.indexOf('_');
        if (cut <= 0) {
            return "";
        }
        int n;
        try {
            n = Integer.parseInt(op.substring(0, cut));
        } catch (NumberFormatException notANumber) {
            return "";
        }
        List<RankedEntry<T>> entries = snapshot.getEntries();
        if (n < 1 || n > entries.size()) {
            return "";
        }
        RankedEntry<T> entry = entries.get(n - 1);
        String what = op.substring(cut + 1);

        Object value;
        if (what.startsWith("[") && what.endsWith("]") && what.length() > 2) {
            value = list.valueOf(entry, what.substring(1, what.length() - 1));
        } else if (what.equalsIgnoreCase("name")) {
            value = list.isAboutPlayers() ? list.valueOf(entry, RankedList.PLAYER_KEY) : entry.getObject();
        } else if (what.equalsIgnoreCase("value")) {
            value = list.valueOf(entry, RankedList.VALUE_KEY);
        } else {
            return "";
        }
        return value == null ? "" : String.valueOf(value);
    }

    /** What the list can answer right now; asking is what starts a read the policy calls for. */
    private static <T> @Nullable RankedSnapshot<T> current(RankedList<T> list) {
        CompletableFuture<RankedSnapshot<T>> read = list.snapshot();
        if (read.isDone() && !read.isCompletedExceptionally()) {
            return read.join();
        }
        return list.snapshotIfPresent();
    }
}
