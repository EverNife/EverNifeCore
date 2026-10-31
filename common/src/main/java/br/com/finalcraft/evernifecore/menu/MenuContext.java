package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.screen.ClickContext;
import br.com.finalcraft.evernifecore.screen.ClickGesture;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a menu adds to an {@link ExecutionContext}: no subclass, only attributes under the keys below, written by the
 * menu glue and read back here. The opener's arguments are also the variables {@code {0}}, {@code {1}}... of the
 * context's texts.
 */
public final class MenuContext {

    /** The {@link Menu}. */
    public static final String MENU = "menu";
    /** The {@link Offer} a click trades. */
    public static final String OFFER = "menu.offer";
    /** The {@link ClickGesture}; absent outside a click. */
    public static final String CLICK = "menu.click";
    /** The opener's arguments, a {@code List<String>}. */
    public static final String ARGUMENTS = "menu.args";
    /** The {@link ScreenView} the click came from - the key the screen itself writes; absent from a command or the API. */
    public static final String VIEW = ScreenView.class.getName();

    private MenuContext() {
    }

    /** A context outside any screen - a command, the API - for {@code viewer} (null for none), run as {@code caller}. */
    public static ExecutionContext of(@Nullable FPlayer viewer, Caller caller, List<String> arguments) {
        return withArguments(ExecutionContext.builder(caller).player(viewer), arguments).build();
    }

    /** The glue's form: a click on {@code menu}'s button of {@code offer} (null for a plain button), run as the menu's owner. */
    public static ExecutionContext of(ClickContext click, Menu menu, @Nullable String offerId, @Nullable Offer offer) {
        List<String> arguments = new ArrayList<>();
        for (Object argument : click.getView().getArguments()) {
            arguments.add(String.valueOf(argument));
        }
        Caller caller = Caller.of(menu.getOwner(), menu.getId() + (offerId == null ? "" : "#" + offerId));
        return withArguments(ExecutionContext.builder(caller).player(click.getViewer()), arguments)
                .attribute(VIEW, click.getView())
                .attribute(ClickContext.class.getName(), click)
                .attribute(MENU, menu)
                .attribute(OFFER, offer)
                .attribute(CLICK, click.getGesture())
                .build();
    }

    private static ExecutionContext.Builder withArguments(ExecutionContext.Builder builder, List<String> arguments) {
        List<String> copy = Collections.unmodifiableList(new ArrayList<>(arguments));
        for (int i = 0; i < copy.size(); i++) {
            builder.variable(String.valueOf(i), copy.get(i));
        }
        return builder.attribute(ARGUMENTS, copy);
    }

    public static @Nullable Menu getMenu(ExecutionContext ctx) {
        return ctx.getAttribute(MENU, Menu.class);
    }

    public static @Nullable Offer getOffer(ExecutionContext ctx) {
        return ctx.getAttribute(OFFER, Offer.class);
    }

    public static @Nullable ClickGesture getClick(ExecutionContext ctx) {
        return ctx.getAttribute(CLICK, ClickGesture.class);
    }

    /** Empty, never null. */
    @SuppressWarnings("unchecked")
    public static List<String> getArguments(ExecutionContext ctx) {
        List<String> arguments = ctx.getAttribute(ARGUMENTS, List.class);
        return arguments != null ? arguments : Collections.<String>emptyList();
    }

    public static @Nullable ScreenView getView(ExecutionContext ctx) {
        return ctx.getAttribute(VIEW, ScreenView.class);
    }
}
