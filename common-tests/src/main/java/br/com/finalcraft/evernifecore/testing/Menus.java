package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.economy.Currencies;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.menu.Goods;
import br.com.finalcraft.evernifecore.menu.Offer;
import br.com.finalcraft.evernifecore.menu.OfferCatalog;
import br.com.finalcraft.evernifecore.menu.Price;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.screen.ClickGesture;
import br.com.finalcraft.evernifecore.screen.ClickKind;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Shops for a test, on the worlds that already exist: a {@link ScreenWorld} (screens, clicks, prompts - opened with
 * {@code Platforms::withInventory} for item goods) and a {@link LogicWorld} (the shop plugin, its audit log, the pending
 * deliveries, the clock). Two currencies of the shop, {@code <shop>:coins} and {@code <shop>:gold}, are registered
 * until {@link #close()}.
 *
 * <pre>{@code
 * screens = ScreenWorld.open(dir, Menus::inventorySlots);
 * shop = LogicWorld.open("FakeShop", dir.resolve("shop"));
 * menus = Menus.in(screens, shop);
 * TestFPlayerSender alex = menus.player("Alex", 500);
 * Transactions.buy(menus.context(alex), Offer.buy(goods, Price.of(menus.coins(), 100)).build(), 1);
 * }</pre>
 */
public final class Menus implements AutoCloseable {

    /** Slots per player of the inventory {@link #inventorySlots} asks for. */
    public static final int INVENTORY_SLOTS = 36;

    private final ScreenWorld screens;
    private final LogicWorld shop;
    private final TestCurrency coins;
    private final TestCurrency gold;

    private Menus(ScreenWorld screens, LogicWorld shop) {
        this.screens = screens;
        this.shop = shop;
        String owner = shop.getOwner().getMetaInfo().getName().toLowerCase(Locale.ROOT);
        this.coins = Economies.currency(owner + ":coins");
        this.gold = Economies.currency(owner + ":gold");
        Currencies.register(shop.getOwner(), coins);
        Currencies.register(shop.getOwner(), gold);
        shop.deliveries(); //boots the player data the limits and the pending deliveries live in
        shop.auditLog();
    }

    public static Menus in(ScreenWorld screens, LogicWorld shop) {
        return new Menus(screens, shop);
    }

    /** What {@code ScreenWorld.open(dir, Menus::inventorySlots)} passes: a platform with an inventory of {@link #INVENTORY_SLOTS}. */
    public static Platforms inventorySlots(Platforms platform) {
        return platform.withInventory(INVENTORY_SLOTS);
    }

    public ScreenWorld getScreens() {
        return screens;
    }

    public LogicWorld getShop() {
        return shop;
    }

    /** {@code <shop>:coins}, wallets for every owner kind. */
    public TestCurrency coins() {
        return coins;
    }

    /** {@code <shop>:gold}, a second currency for composite prices. */
    public TestCurrency gold() {
        return gold;
    }

    /** The in-memory inventory of the platform; fails when the screen world was opened without {@link #inventorySlots}. */
    public TestInventory inventory() {
        TestInventory inventory = ((TestPlatform) EverNifeCore.getPlatform()).getTestInventory();
        if (inventory == null) {
            throw new IllegalStateException("The screen world has no inventory: open it with ScreenWorld.open(dir, Menus::inventorySlots).");
        }
        return inventory;
    }

    /** An online, logged-in viewer with {@code coins} of {@link #coins()}. */
    public TestFPlayerSender player(String name, double coins) {
        TestFPlayerSender player = screens.viewer(name);
        PlayerController.handleLogin(player.getUniqueId(), name).join();
        this.coins.balance(CurrencyOwner.player(player.getUniqueId()), coins);
        return player;
    }

    public BigDecimal balance(TestCurrency currency, FPlayer player) {
        return currency.getBalance(CurrencyOwner.player(player.getUniqueId()));
    }

    /** A context of the shop plugin for {@code player}. */
    public ExecutionContext context(FPlayer player) {
        return shop.context(player).build();
    }

    /** {@code goods} bought for {@code coins} coins each. */
    public Offer.Builder buy(Goods goods, double coins) {
        return Offer.buy(goods, Price.of(this.coins, coins));
    }

    /** {@code goods} sold for {@code coins} coins each. */
    public Offer.Builder sell(Goods goods, double coins) {
        return Offer.sell(goods, Price.of(this.coins, coins));
    }

    public static CountedGoods counted(String name, int held) {
        return new CountedGoods(name, held);
    }

    public static RecordingGoods recording(Goods goods) {
        return new RecordingGoods(goods);
    }

    public static OfferCatalog catalog(Offer... offers) {
        return OfferCatalog.of(offers);
    }

    /** A click on the offer button {@code offerId}, then the ticks that let the screen settle. */
    public boolean clickOffer(ScreenView view, String offerId, ClickGesture gesture) {
        boolean handled = screens.click(view, offerId, gesture);
        screens.advanceTicks(2);
        return handled;
    }

    /** Answers the quantity prompt as if the viewer typed {@code text}, then lets the screen come back. */
    public void answerPrompt(String text) {
        screens.answerPrompt(text);
        screens.advanceTicks(2);
    }

    /** Answers the confirmation open for {@code viewer} - its confirm button, or its deny one. */
    public void confirm(FPlayer viewer, boolean yes) {
        ScreenView view = Screens.getOpenView(viewer);
        if (view == null) {
            throw new IllegalStateException(viewer.getName() + " has no screen open: nothing asked for a confirmation.");
        }
        screens.click(view, yes ? 11 : 15, ClickGesture.PRIMARY, ClickKind.NOTHING);
        screens.advanceTicks(2);
    }

    /** Every {@code type} posted on the global bus from now on. */
    public <T extends IECEvent> List<T> recorded(Class<T> type) {
        return screens.recorded(type);
    }

    /** Takes the two currencies out. Close it before the shop and the screens. */
    @Override
    public void close() {
        Currencies.unregisterAll(shop.getOwner());
    }
}
