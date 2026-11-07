package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.ranking.RankedEntry;
import br.com.finalcraft.evernifecore.ranking.RankedList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Objects;
import java.util.function.Function;

/** What a {@link RankingScreen} shows: one {@link RankedList}, under the title it is asked for (the layout's by default). */
public final class RankingRequest {

    private final RankedList<?> list;
    private String title;
    private Function<RankedEntry<?>, ItemSpec> icon;

    private RankingRequest(RankedList<?> list) {
        this.list = Objects.requireNonNull(list, "A ranking screen shows one RankedList; build it with RankedList.of(...).");
    }

    @Nonnull
    public static RankingRequest of(@Nonnull RankedList<?> list) {
        return new RankingRequest(list);
    }

    /** The window title, in place of the layout's. */
    @Nonnull
    public RankingRequest title(@Nullable String title) {
        this.title = title;
        return this;
    }

    /**
     * The item each entry is drawn as - a player's head, the ranked thing's own item - in place of the layout's; the
     * layout's text stays over it. A null answer keeps the layout's item.
     */
    @Nonnull
    public RankingRequest icon(@Nullable Function<RankedEntry<?>, ItemSpec> perEntry) {
        this.icon = perEntry;
        return this;
    }

    @Nullable
    public Function<RankedEntry<?>, ItemSpec> getIcon() {
        return icon;
    }

    @Nonnull
    public RankedList<?> getList() {
        return list;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Override
    public String toString() {
        return "RankingRequest{" + list.getId() + "}";
    }

}
