package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logic.delivery.ClaimResult;
import br.com.finalcraft.evernifecore.logic.delivery.Deliveries;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.placeholder.replacer.Closures;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The viewer's pending {@link Deliveries}, one button each - the core's default for {@link ScreenKey#DELIVERIES}. A click
 * claims that delivery, the claim-all button claims every one; what still does not fit stays listed. The entries come
 * from storage, so the list fills a moment after the window opens.
 *
 * <p>The entry template reads {@code {plugin}}, {@code {reason}} and {@code {effects}} of its delivery.</p>
 */
public final class DeliveriesScreen extends Screen<DeliveriesScreen.DeliveriesLayout> {

    @FCLocale(lang = LocaleType.EN_US, text = "§aClaimed %delivered% delivery(ies); %kept% still wait for room.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§aResgatou %delivered% entrega(s); %kept% ainda esperam espaço.")
    private static LocaleMessage CLAIMED;

    private static final RegexReplacer<Delivery> ENTRY = new RegexReplacer<Delivery>(Closures.BRACKET)
            .addParser("plugin", delivery -> delivery.getOrigin().getPlugin())
            .addParser("reason", delivery -> delivery.getOrigin().getReason())
            .addParser("effects", delivery -> delivery.getEffects().size());

    private final MutableState<List<Delivery>> pending = new MutableState<>(Collections.<Delivery>emptyList());

    private DeliveriesScreen(DeliveriesLayout layout) {
        super(layout.getRows(), layout);
        key(ScreenKey.DELIVERIES);
        list(pending::get)
                .id("deliveries")
                .into(l -> l.ENTRY)
                .pagedBy(l -> l.PREVIOUS, l -> l.NEXT)
                .dependsOn(pending)
                .render((delivery, icon) -> icon.addScope(ENTRY, delivery))
                .onClick((click, delivery) -> claim(click.getView(), Deliveries.get().claim(click.getViewer(), delivery.getId())));
        icon(l -> l.CLAIM_ALL)
                .visibleWhen(() -> !pending.get().isEmpty())
                .dependsOn(pending)
                .onClick(click -> claim(click.getView(), Deliveries.get().claimAll(click.getViewer())));
        icon(l -> l.EMPTY)
                .visibleWhen(() -> pending.get().isEmpty())
                .dependsOn(pending);
        addComponent(component -> reload(component.getView()));
    }

    /** The deliveries of whoever opens it, on the admin's {@code DeliveriesLayout}. */
    @Nonnull
    public static DeliveriesScreen of() {
        return new DeliveriesScreen(Layouts.of(DeliveriesLayout.class));
    }

    /** The deliveries this screen lists right now - empty until storage answered. */
    @Nonnull
    public List<Delivery> getPending() {
        return pending.get();
    }

    private void claim(ScreenView view, CompletableFuture<ClaimResult> claim) {
        claim.whenComplete((result, failed) -> {
            if (result != null && CLAIMED != null) {
                CLAIMED.custom().addPlaceholder("%delivered%", result.getDelivered().size())
                        .addPlaceholder("%kept%", result.getKept().size())
                        .send(view.getViewer());
            }
            reload(view);
        });
    }

    private void reload(ScreenView view) {
        Deliveries.get().listOf(view.getViewerId()).thenAccept(found -> view.getScheduler().run(() -> pending.set(found)));
    }

    /** What the deliveries screen looks like, and the file the admin restyles it in. */
    @ScreenLayout(title = "§8Deliveries", rows = 4)
    public static class DeliveriesLayout extends Layout {

        @IconData(slot = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26})
        public Icon ENTRY = Icon.of("minecraft:chest")
                .addLocale(LocaleType.EN_US, "§e{plugin}", "§7{reason}", "§7{effects} effect(s)", "", "§a ✎ Click to claim")
                .addLocale(LocaleType.PT_BR, "§e{plugin}", "§7{reason}", "§7{effects} efeito(s)", "", "§a ✎ Clique para resgatar");

        @IconData(slot = {27})
        public Icon PREVIOUS = DefaultIcons.previousPage();

        @IconData(slot = {35})
        public Icon NEXT = DefaultIcons.nextPage();

        @IconData(slot = {32})
        public Icon CLAIM_ALL = Icon.of(DefaultIcons.wool(DefaultIcons.Color.LIME))
                .addLocale(LocaleType.EN_US, "§a§lClaim everything")
                .addLocale(LocaleType.PT_BR, "§a§lResgatar tudo");

        @IconData(slot = {30})
        public Icon EMPTY = Icon.of("minecraft:paper")
                .addLocale(LocaleType.EN_US, "§7Nothing to claim")
                .addLocale(LocaleType.PT_BR, "§7Nada para resgatar");

        @IconData(slot = {28, 29, 31, 33, 34}, background = true)
        public Icon BACKGROUND = Icon.of(DefaultIcons.pane(DefaultIcons.Color.GRAY)).displayName("§7");

    }

}
