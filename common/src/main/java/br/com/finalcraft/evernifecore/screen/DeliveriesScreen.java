package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.economy.Currencies;
import br.com.finalcraft.evernifecore.economy.Currency;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logic.delivery.ClaimResult;
import br.com.finalcraft.evernifecore.logic.delivery.Deliveries;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryIcon;
import br.com.finalcraft.evernifecore.placeholder.replacer.Closures;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.time.Window;
import br.com.finalcraft.evernifecore.types.TypeRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The viewer's pending {@link Deliveries}, one button each - the core's default for {@link ScreenKey#DELIVERIES}. A click
 * claims that delivery, the claim-all button claims every one; what still does not fit stays listed. The entries come
 * from storage, so the list fills a moment after the window opens.
 *
 * <p>The entry template reads {@code {plugin}}, {@code {reason}}, {@code {created}}, {@code {expires}} ({@code -} for
 * never) and {@code {effects}} (how many) of its delivery; a lore line citing {@code {effect}} repeats once per effect it
 * still owes ({@code 36x minecraft:stone}, {@code $25.00}, {@code /spawn}). An entry draws the item of its delivery's
 * {@link Delivery#getDrawnIcon()}: a theme's is the {@code THEME_<NAME>} icon of the layout (each one a Minecraft item with
 * a Hytale candidate), an item is drawn as it is; either way under the entry's text. A delivery without one draws the
 * entry's own item. The claim's answer goes to the chat with the screen open, and the page redraws from the store.</p>
 */
public final class DeliveriesScreen extends Screen<DeliveriesScreen.DeliveriesLayout> {

    @FCLocale(lang = LocaleType.EN_US, text = "§aClaimed ${delivered} delivery(ies); ${kept} still wait for room.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§aResgatou ${delivered} entrega(s); ${kept} ainda esperam espaço.")
    private static LocaleMessage CLAIMED;

    private static final RegexReplacer<Delivery> ENTRY = new RegexReplacer<Delivery>(Closures.BRACKET)
            .addParser("plugin", delivery -> delivery.getOrigin().getPlugin())
            .addParser("reason", delivery -> delivery.getOrigin().getReason())
            .addParser("effects", delivery -> delivery.getEffects().size())
            .addParser("created", delivery -> date(delivery.getCreatedAt()))
            .addParser("expires", delivery -> delivery.getExpiresAt() == null ? "-" : date(delivery.getExpiresAt()));

    private static String date(Instant when) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(Window.getServerZone()).format(when); //the zone is the config's, read now
    }

    private final MutableState<List<Delivery>> pending = new MutableState<>(Collections.<Delivery>emptyList());

    private DeliveriesScreen(DeliveriesLayout layout) {
        super(layout.getRows(), layout);
        key(ScreenKey.DELIVERIES);
        list(pending::get)
                .id("deliveries")
                .into(l -> l.ENTRY)
                .pagedBy(l -> l.PREVIOUS, l -> l.NEXT)
                .dependsOn(pending)
                .render((delivery, icon) -> drawnAs(listEffects(icon, delivery).addScope(ENTRY, delivery), delivery.getDrawnIcon(), layout))
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

    /** {@code entry} as the item {@code chosen} names, the entry's text kept; untouched without one. */
    static Icon drawnAs(Icon entry, @Nullable DeliveryIcon chosen, DeliveriesLayout layout) {
        if (chosen == null) {
            return entry;
        }
        ItemSpec template = entry.getItem();
        ItemSpec own = chosen.getItem();
        if (own != null) {
            entry.setItem(own.withText(template.getDisplayName(), template.getLore()));
            return entry;
        }
        Icon themed = layout.themeOf(chosen.getTheme());
        if (themed != null) {
            entry.setItem(themed.getItem().withText(template.getDisplayName(), template.getLore()));
            entry.setFallbacks(themed.getFallbacks());
        }
        return entry;
    }

    /** {@code entry} with each lore line citing {@code {effect}} repeated once per effect of {@code delivery}, in every language. */
    static Icon listEffects(Icon entry, Delivery delivery) {
        List<String> effects = new ArrayList<>();
        for (JsonNode effect : delivery.getEffects()) {
            effects.add(describe(effect));
        }
        ItemSpec item = entry.getItem();
        entry.setItem(item.withText(item.getDisplayName(), perEffect(item.getLore(), effects)));
        IconLocale locale = entry.getLocale();
        if (locale != null) {
            IconLocale listed = locale.copy(); //the entry's copy shares the layout's blocks
            for (String lang : locale.getLanguages()) {
                listed.put(lang, perEffect(locale.get(lang), effects));
            }
            entry.setLocale(listed);
        }
        return entry;
    }

    private static List<String> perEffect(List<String> lines, List<String> effects) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (!line.contains("{effect}")) {
                out.add(line);
                continue;
            }
            for (String effect : effects) {
                out.add(line.replace("{effect}", effect));
            }
        }
        return out;
    }

    /** One effect as the player reads it: the core's by what they hand over, any other by its type. */
    static String describe(JsonNode effect) {
        String type = effect.path(TypeRegistry.TYPE_FIELD).asText(effect.size() == 1 ? effect.fieldNames().next() : "?");
        switch (type.startsWith("evernifecore:") ? type.substring("evernifecore:".length()) : type) {
            case "give-item":
                return effect.path("amount").asText("1") + "x " + effect.path("item").asText("?");
            case "money":
                String amount = effect.path("amount").asText("?");
                Currency currency = Currencies.find(effect.path("currency").asText("")).orElse(null);
                try {
                    return currency == null ? amount + " " + effect.path("currency").asText("") : currency.format(new BigDecimal(amount)).getText();
                } catch (NumberFormatException template) {
                    return amount + " " + currency.getId();
                }
            case "command":
                return "/" + effect.path("command").asText("");
            default:
                return type;
        }
    }

    private void claim(ScreenView view, CompletableFuture<ClaimResult> claim) {
        claim.whenComplete((result, failed) -> {
            if (result != null && CLAIMED != null) {
                CLAIMED.custom().addPlaceholder("delivered", result.getDelivered().size())
                        .addPlaceholder("kept", result.getKept().size())
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
                .addLocale(LocaleType.EN_US, "§e{plugin}", "§7{reason}", "§f• {effect}", "§8Since {created}", "§8Expires {expires}", "",
                        "§a ✎ Click to claim")
                .addLocale(LocaleType.PT_BR, "§e{plugin}", "§7{reason}", "§f• {effect}", "§8Desde {created}", "§8Expira {expires}", "",
                        "§a ✎ Clique para resgatar");

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

        //the item of each DeliveryIcon.Theme: placed nowhere, only lent to the entries (a Hytale renderer draws the hytale: candidate)
        @IconData(slot = {})
        public Icon THEME_QUEST = Icon.of(ItemSpec.of("minecraft:book", 1), ItemSpec.of("hytale:Furniture_Crude_Chest_Small", 1));

        @IconData(slot = {})
        public Icon THEME_MONEY = Icon.of(ItemSpec.of("minecraft:gold_ingot", 1), ItemSpec.of("hytale:Ingredient_Bar_Iron", 1));

        @IconData(slot = {})
        public Icon THEME_ITEM = Icon.of(ItemSpec.of("minecraft:ender_chest", 1), ItemSpec.of("hytale:Furniture_Crude_Chest_Small", 1));

        @IconData(slot = {})
        public Icon THEME_COMMAND = Icon.of(ItemSpec.of("minecraft:command_block", 1), ItemSpec.of("hytale:Furniture_Crude_Chest_Small", 1));

        @IconData(slot = {})
        public Icon THEME_AUCTION = Icon.of(ItemSpec.of("minecraft:emerald", 1), ItemSpec.of("hytale:Ingredient_Bar_Iron", 1));

        @IconData(slot = {})
        public Icon THEME_REWARD = Icon.of(ItemSpec.of("minecraft:nether_star", 1), ItemSpec.of("hytale:Rock_Gem_Diamond", 1));

        /** The icon, as the file configured it, whose item {@code theme} draws with; null for no theme. */
        public @Nullable Icon themeOf(@Nullable DeliveryIcon.Theme theme) {
            return theme == null ? null : getIcon("THEME_" + theme.name());
        }

    }

}
