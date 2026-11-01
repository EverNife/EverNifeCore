package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.screen.ClickRule;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.PagedList;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.SlotSet;
import br.com.finalcraft.evernifecore.screen.Slots;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A menu a plugin registers by name: a {@link Screen} with offer buttons, opened by id with arguments through
 * {@link MenuRegistry}. The core never reads one from a file - the plugin builds it,
 * {@code Menu.of(plugin, "shop").title("Shop - {0}").offer("diamond", 13, buy, sell).build()}. Immutable once built.
 */
public final class Menu {

    private final MenuId id;
    private final ECPluginData owner;
    private final String title;
    private final Screen<?> screen;
    private final @Nullable Gate openGate;
    private final Map<String, List<Offer>> offers;
    private final ClickMap clicks;
    private final @Nullable String successText;
    private final @Nullable String denyText;
    private @Nullable PagedList<Offer, ?> offerList; //set once by build(), right after the menu its buttons read exists

    private Menu(Builder builder, Screen<?> screen, Map<String, List<Offer>> offers) {
        this.id = builder.id;
        this.owner = builder.owner;
        this.title = builder.title;
        this.screen = screen;
        this.openGate = builder.openGate;
        this.offers = Collections.unmodifiableMap(offers);
        this.clicks = builder.clicks;
        this.successText = builder.successText;
        this.denyText = builder.denyText;
    }

    /** @throws IllegalArgumentException when {@code name} is not {@code [a-z0-9_-]{1,64}} */
    public static Builder of(ECPluginData owner, String name) {
        Objects.requireNonNull(owner, "A menu belongs to the plugin that registers it - 'owner' cannot be null.");
        return new Builder(owner, MenuId.of(owner.getMetaInfo().getName(), name));
    }

    public MenuId getId() {
        return id;
    }

    public ECPluginData getOwner() {
        return owner;
    }

    /** As written: {@code {0}}, {@code {player}} and {@code %placeholders%} are resolved per viewer by the screen. */
    public String getTitle() {
        return title;
    }

    public Screen<?> getScreen() {
        return screen;
    }

    /** Null = anyone opens it. */
    public @Nullable Gate getOpenGate() {
        return openGate;
    }

    /** Offer button id -> its offers (one BUY and/or one SELL); the paged offers of {@link Builder#offers} are not here. */
    public Map<String, List<Offer>> getOffers() {
        return offers;
    }

    /**
     * The paged list {@link Builder#offers} poured the offers into (element id {@code offers}, arrows {@code offers#previous}
     * and {@code offers#next}); null when the menu has none. It is the screen's own list: {@code arrows}, {@code pagedBy},
     * {@code spillIntoPageArrows} or {@code pager} on it change what the menu draws.
     */
    public @Nullable PagedList<Offer, ?> getOfferList() {
        return offerList;
    }

    public ClickMap getClicks() {
        return clicks;
    }

    /** What a trade that went through tells the player - {@code {result}} reads the result; null tells nothing. */
    public @Nullable String getSuccessText() {
        return successText;
    }

    /** What a refused trade tells the player - {@code {missing}} reads why; null tells nothing. */
    public @Nullable String getDenyText() {
        return denyText;
    }

    /** Its SELL offers, first match wins - what {@link Transactions#sellHand}/{@link Transactions#sellAll} read. */
    public OfferCatalog asCatalog() {
        List<Offer> all = new ArrayList<>();
        for (List<Offer> each : offers.values()) {
            all.addAll(each);
        }
        return OfferCatalog.of(all);
    }

    @Override
    public String toString() {
        return "Menu{" + id + ", " + offers.size() + " offer button(s)}";
    }

    public static final class Builder {
        private final ECPluginData owner;
        private final MenuId id;
        private String title = "";
        private Screen<?> screen;
        private Gate openGate;
        private ClickMap clicks = ClickMap.market();
        private String successText = "§a{result}";
        private String denyText = "§c{missing}";
        private final Map<String, OfferSlot> offers = new LinkedHashMap<>();
        private @Nullable PagedOffers paged;

        private Builder(ECPluginData owner, MenuId id) {
            this.owner = owner;
            this.id = id;
        }

        public Builder title(String title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        /** The layout and the plain elements; the offer buttons are added to it by {@link #build()}. A 3-row screen by default. */
        public Builder screen(Screen<?> screen) {
            this.screen = Objects.requireNonNull(screen, "screen");
            return this;
        }

        /** Conditions (and costs) to open it; a denial tells the viewer why and opens nothing. */
        public Builder openGate(@Nullable Gate gate) {
            this.openGate = gate;
            return this;
        }

        /** Which gesture trades what on every offer button; {@link ClickMap#market()} by default. */
        public Builder clicks(ClickMap clicks) {
            this.clicks = Objects.requireNonNull(clicks, "clicks");
            return this;
        }

        public Builder successText(@Nullable String text) {
            this.successText = text;
            return this;
        }

        public Builder denyText(@Nullable String text) {
            this.denyText = text;
            return this;
        }

        /** One button at {@code slot} trading {@code offers} - at most one BUY and one SELL - drawn as the first one's goods. */
        public Builder offer(String offerId, int slot, Offer... offers) {
            return offer(offerId, Slots.of(slot), null, offers);
        }

        /** {@link #offer(String, int, Offer...)} on {@code slots}, drawn as {@code icon} (null: the goods' own). */
        public Builder offer(String offerId, SlotSet slots, @Nullable Icon icon, Offer... offers) {
            Objects.requireNonNull(offerId, "offerId");
            if (this.offers.containsKey(offerId)) {
                throw new IllegalArgumentException("The menu " + id + " already has an offer button '" + offerId + "': give each button its own id.");
            }
            this.offers.put(offerId, new OfferSlot(slots, icon, Arrays.asList(offers)));
            return this;
        }

        /**
         * A paged list of offers poured into {@code region}, one button per offer, read again from {@code source} on every
         * page; {@link Menu#getOfferList()} hands it back to place its arrows. One list per menu.
         *
         * @throws IllegalStateException when the menu already has one
         */
        public Builder offers(SlotSet region, Supplier<List<Offer>> source) {
            return offers(region, null, null, source);
        }

        /**
         * {@link #offers(SlotSet, Supplier)} with the framework's page arrows on {@code previous} and {@code next} (a null
         * side draws none); {@code getOfferList().arrows(...)} changes their look.
         *
         * @throws IllegalStateException when the menu already has one
         */
        public Builder offers(SlotSet region, @Nullable SlotSet previous, @Nullable SlotSet next, Supplier<List<Offer>> source) {
            if (paged != null) {
                throw new IllegalStateException("The menu " + id + " already pours a paged offer list: a menu has one. Put every offer in"
                        + " one source, or give the others their own offer(id, slot, ...) buttons.");
            }
            this.paged = new PagedOffers(Objects.requireNonNull(region, "region"), previous, next, Objects.requireNonNull(source, "source"));
            return this;
        }

        /**
         * @throws IllegalArgumentException that says what to change, on a button without an offer or with two of one type,
         * an effect offer without an icon, or two buttons on one slot
         */
        public Menu build() {
            Screen<?> target = screen != null ? screen : Screen.of(3);
            target.owner(owner);
            if (!title.isEmpty()) {
                target.title(title);
            }
            Map<String, List<Offer>> built = new LinkedHashMap<>();
            Set<Integer> taken = new HashSet<>();
            for (Map.Entry<String, OfferSlot> entry : offers.entrySet()) {
                OfferSlot slot = entry.getValue();
                validate(entry.getKey(), slot);
                for (int raw : slot.slots.resolve(target.getGrid()).toArray()) {
                    if (!taken.add(raw)) {
                        throw new IllegalArgumentException("The offer button '" + entry.getKey() + "' of " + id + " uses slot " + raw
                                + ", which another offer button already uses: one button per slot.");
                    }
                }
                built.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(slot.offers)));
            }
            Menu menu = new Menu(this, target, built);
            for (Map.Entry<String, OfferSlot> entry : offers.entrySet()) {
                OfferSlot slot = entry.getValue();
                List<Offer> offerList = built.get(entry.getKey());
                Icon icon = OfferButtons.icon(menu, entry.getKey(), offerList, slot.icon);
                target.icon(slot.slots, icon).id(entry.getKey())
                        .rule(OfferButtons.rules(menu, entry.getKey(), offerList).toArray(new ClickRule[0]));
            }
            if (paged != null) {
                menu.offerList = OfferButtons.list(menu, target, paged.region, paged.source);
                if (paged.previous != null || paged.next != null) {
                    menu.offerList.pagedBy(paged.previous, paged.next);
                }
            }
            return menu;
        }

        private void validate(String offerId, OfferSlot slot) {
            if (slot.offers.isEmpty() || slot.offers.size() > 2) {
                throw new IllegalArgumentException("The offer button '" + offerId + "' of " + id + " has " + slot.offers.size() + " offers:"
                        + " give it one BUY, one SELL, or one of each.");
            }
            if (slot.offers.size() == 2 && slot.offers.get(0).getType() == slot.offers.get(1).getType()) {
                throw new IllegalArgumentException("The offer button '" + offerId + "' of " + id + " has two " + slot.offers.get(0).getType()
                        + " offers: a button holds at most one of each type - give the second its own button.");
            }
            if (slot.icon == null && slot.offers.get(0).getGoods().getIcon() == null) {
                throw new IllegalArgumentException("The offer button '" + offerId + "' of " + id + " sells " + slot.offers.get(0).getGoods()
                        + ", which has no item to draw: pass an Icon to offer(id, slots, icon, offers...).");
            }
        }
    }

    private static final class OfferSlot {
        private final SlotSet slots;
        private final @Nullable Icon icon;
        private final List<Offer> offers;

        private OfferSlot(SlotSet slots, @Nullable Icon icon, List<Offer> offers) {
            this.slots = slots;
            this.icon = icon;
            this.offers = offers;
        }
    }

    private static final class PagedOffers {
        private final SlotSet region;
        private final @Nullable SlotSet previous;
        private final @Nullable SlotSet next;
        private final Supplier<List<Offer>> source;

        private PagedOffers(SlotSet region, @Nullable SlotSet previous, @Nullable SlotSet next, Supplier<List<Offer>> source) {
            this.region = region;
            this.previous = previous;
            this.next = next;
            this.source = source;
        }
    }
}
