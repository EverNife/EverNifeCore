package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.placeholder.replacer.Closures;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.screen.ClickContext;
import br.com.finalcraft.evernifecore.screen.ClickGesture;
import br.com.finalcraft.evernifecore.screen.ClickRule;
import br.com.finalcraft.evernifecore.screen.ConfirmRequest;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenKey;
import br.com.finalcraft.evernifecore.screen.ScreenRequest;
import br.com.finalcraft.evernifecore.screen.SlotSet;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The link between an offer and its button. Every trade of a click goes the one path the screen has: a
 * {@link ClickRule} per gesture of the menu's {@link ClickMap}, whose success effect asks the confirmation when the
 * offer wants one, resolves the quantity (the prompt for {@link Quantity#ASK}) and runs {@link Transactions#trade},
 * then tells the player the menu's success or deny text. The button's text reads {@code {price}}, {@code {sell-price}}
 * and {@code {quantity}}.
 */
final class OfferButtons {

    private OfferButtons() {
    }

    /** One rule per gesture whose binding has an offer of its type on this button; none for a read-only offer. */
    static List<ClickRule> rules(Menu menu, String offerId, List<Offer> offers) {
        List<ClickRule> rules = new ArrayList<>();
        for (Map.Entry<ClickGesture, ClickMap.Binding> binding : menu.getClicks().getBindings().entrySet()) {
            Offer offer = offerOf(offers, binding.getValue().getType());
            if (offer != null && !offer.isReadOnly()) {
                rules.add(ClickRule.on(binding.getKey()).onSuccess(new Trade(menu, offerId, offer, binding.getValue().getQuantity())));
            }
        }
        return rules;
    }

    /** {@code given}, or the first offer's goods, with the price placeholders of {@code offers} scoped on it. */
    static Icon icon(Menu menu, String offerId, List<Offer> offers, @Nullable Icon given) {
        Icon icon = given != null ? given : Icon.of(offers.get(0).getGoods().getIcon());
        return scoped(icon, menu, offerId, offers);
    }

    /** A paged list of offers on {@code screen}: each entry drawn as its goods, each click through the same rules. */
    static void list(Menu menu, Screen<?> screen, SlotSet region, Supplier<List<Offer>> source) {
        screen.list(source).into(region).id("offers")
                .render((offer, icon) -> {
                    if (offer.getGoods().getIcon() != null) {
                        icon.from(offer.getGoods().getIcon());
                    }
                    scoped(icon, menu, "offers", Collections.singletonList(offer));
                })
                .onClick((click, offer) -> ClickRule.dispatch(rules(menu, "offers", Collections.singletonList(offer)), click));
    }

    private static Icon scoped(Icon icon, Menu menu, String offerId, List<Offer> offers) {
        Offer buy = offerOf(offers, Offer.Type.BUY);
        Offer sell = offerOf(offers, Offer.Type.SELL);
        Offer first = offers.get(0);
        return icon.addScope(new RegexReplacer<Menu>(Closures.BRACKET)
                .addParser("price", any -> priceOf(menu, offerId, buy))
                .addParser("sell-price", any -> priceOf(menu, offerId, sell))
                .addParser("quantity", any -> first.getDefaultQuantity().toString()), menu);
    }

    /** One unit of {@code offer}'s price as the viewer reads it, from a preview without a player; "-" when there is no such offer. */
    private static String priceOf(Menu menu, String offerId, @Nullable Offer offer) {
        if (offer == null) {
            return "-";
        }
        ExecutionContext preview = ExecutionContext.builder(Caller.of(menu.getOwner(), menu.getId() + "#" + offerId)).build();
        try {
            return offer.getPrice().quote(preview, 1).describe(preview).toLegacyString();
        } catch (RuntimeException unreadable) {
            return "?"; //a price that reads the player cannot be previewed for nobody
        }
    }

    private static @Nullable Offer offerOf(List<Offer> offers, Offer.Type type) {
        for (Offer offer : offers) {
            if (offer.getType() == type) {
                return offer;
            }
        }
        return null;
    }

    /** The success effect of an offer rule: confirm, ask, trade, tell. */
    static final class Trade implements Effect {
        private final Menu menu;
        private final String offerId;
        private final Offer offer;
        private final Quantity quantity;

        Trade(Menu menu, String offerId, Offer offer, Quantity quantity) {
            this.menu = menu;
            this.offerId = offerId;
            this.offer = offer;
            this.quantity = quantity;
        }

        @Override
        public void apply(ExecutionContext ctx) {
            ClickContext click = ctx.getAttribute(ClickContext.class.getName(), ClickContext.class);
            if (click == null) {
                ctx.getAudit().add(ctx, AuditKind.EFFECT_FAILED, this + ": an offer is traded from a click on its button, and " + ctx.getCaller()
                        + " ran it without one");
                return;
            }
            ExecutionContext trading = MenuContext.of(click, menu, offerId, offer);
            if (!offer.isConfirmed()) {
                trade(click, trading);
                return;
            }
            int shown = quantity.isAsk() || quantity == Quantity.ALL_THAT_FITS ? 1 : quantity.resolve(trading, offer);
            String question = (offer.getType() == Offer.Type.BUY ? "Buy " : "Sell ") + offer.getGoods().describe(trading, shown).toLegacyString()
                    + " for " + offer.getPrice().quote(trading, shown).describe(trading).toLegacyString() + "?";
            ConfirmRequest request = ConfirmRequest.of(question).owner(menu.getOwner()).display(offer.getGoods().getIcon());
            click.open(ScreenKey.CONFIRM, ScreenRequest.of(menu.getOwner()).payload(request)).whenComplete((answer, failed) -> {
                if (failed == null && Boolean.TRUE.equals(answer)) {
                    trade(click, trading);
                }
            });
        }

        private void trade(ClickContext click, ExecutionContext trading) {
            Transactions.trade(trading, offer, quantity).thenAccept(result -> {
                tell(trading, result);
                click.refresh();
            });
        }

        private void tell(ExecutionContext trading, TransactionResult result) {
            FPlayer player = trading.getPlayer();
            String template = result.isSuccess() ? menu.getSuccessText() : menu.getDenyText();
            if (player == null || template == null) {
                return;
            }
            String described = result.describe(trading).toLegacyString();
            player.sendMessage(trading.derive().variable("result", described).variable("missing", described).build().resolve(template));
        }

        @Override
        public String toString() {
            return "trade(" + menu.getId() + "#" + offerId + ", " + offer.getType() + " " + quantity + ")";
        }
    }
}
