package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.region.RegionId;
import jakarta.annotation.Nonnull;

/**
 * The name of a screen others may replace: {@code owner:name}, the owner being the plugin whose content it shows.
 * An ad-hoc screen has none.
 */
public final class ScreenKey {

    /** The core's yes/no question, answering {@code Boolean} - {@code Screens.confirm}. */
    public static final ScreenKey CONFIRM = of("evernifecore", "confirm");
    /** The viewer's pending deliveries, each claimable by a click. */
    public static final ScreenKey DELIVERIES = of("evernifecore", "deliveries");
    /** The entries of a {@code RankedList}, built from a {@code RankingRequest}. */
    public static final ScreenKey RANKING = of("evernifecore", "ranking");
    /** The viewer's quests by status, built from a {@code QuestJournalScreen.Request}. */
    public static final ScreenKey QUEST_JOURNAL = of("evernifecore", "quest-journal");
    /** One quest offered, accept or decline, built from a {@code QuestOfferScreen.Request}. */
    public static final ScreenKey QUEST_OFFER = of("evernifecore", "quest-offer");

    private final String owner;
    private final String name;

    private ScreenKey(String owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    /** @throws IllegalArgumentException when either side is not {@code [a-z0-9_-]{1,64}} */
    @Nonnull
    public static ScreenKey of(@Nonnull String owner, @Nonnull String name) {
        return new ScreenKey(RegionId.requireOwner(owner), RegionId.requireName("screen", name));
    }

    /** {@code owner:name} in one string. */
    @Nonnull
    public static ScreenKey parse(@Nonnull String key) {
        int colon = key == null ? -1 : key.indexOf(':');
        if (colon < 0 || key.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("Invalid screen key '" + key + "': write it as owner:name with exactly one ':',"
                    + " the owner being the plugin whose screen it is, e.g. 'evernifecore:confirm'.");
        }
        return of(key.substring(0, colon), key.substring(colon + 1));
    }

    @Nonnull
    public String getOwner() {
        return owner;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ScreenKey && ((ScreenKey) o).owner.equals(owner) && ((ScreenKey) o).name.equals(name);
    }

    @Override
    public int hashCode() {
        return 31 * owner.hashCode() + name.hashCode();
    }

    @Override
    public String toString() {
        return owner + ":" + name;
    }

}
