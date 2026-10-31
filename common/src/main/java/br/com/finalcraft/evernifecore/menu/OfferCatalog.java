package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** What a shop buys back: the SELL offer an item is sold under - what {@link Transactions#sellHand}/{@link Transactions#sellAll} read. */
public interface OfferCatalog {

    /** The SELL offer {@code item} is sold under, or null when the catalog does not buy it. */
    @Nullable Offer sellOfferFor(ExecutionContext ctx, ItemSpec item);

    /** The first SELL offer of {@code offers}, in order, whose goods {@link Goods#matches match} the item. */
    static OfferCatalog of(Offer... offers) {
        return of(Arrays.asList(offers));
    }

    /** {@link #of(Offer...)} over a collection. */
    static OfferCatalog of(Collection<Offer> offers) {
        List<Offer> sells = new ArrayList<>();
        for (Offer offer : offers) {
            if (offer.getType() == Offer.Type.SELL) {
                sells.add(offer);
            }
        }
        List<Offer> frozen = Collections.unmodifiableList(sells);
        return (ctx, item) -> {
            for (Offer offer : frozen) {
                if (offer.getGoods().matches(item)) {
                    return offer;
                }
            }
            return null;
        };
    }
}
