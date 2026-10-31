package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.ranking.RankedList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Objects;

/** What a {@link RankingScreen} shows: one {@link RankedList}, under the title it is asked for (the layout's by default). */
public final class RankingRequest {

    private final RankedList<?> list;
    private String title;

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
