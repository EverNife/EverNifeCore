package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.economy.AbstractCurrency;
import br.com.finalcraft.evernifecore.economy.Currencies;
import br.com.finalcraft.evernifecore.economy.Currency;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryDestinations;
import br.com.finalcraft.evernifecore.menu.Goods;
import br.com.finalcraft.evernifecore.menu.Menu;
import br.com.finalcraft.evernifecore.menu.MenuRegistry;
import br.com.finalcraft.evernifecore.menu.Offer;
import br.com.finalcraft.evernifecore.menu.Price;
import br.com.finalcraft.evernifecore.menu.TransactionEvent;
import br.com.finalcraft.evernifecore.screen.ClickGesture;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.everylibs.util.numberwrapper.NumericSpec;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The {@code shop} screen of {@link ScreenSuite}: a menu registered as {@code ecoreconformance:shop} whose one button
 * buys a diamond for 10 tokens (left) and buys it back for 4 (right, plain diamonds only), the tokens being an in-memory
 * currency of the check-up - a test server has no economy plugin behind {@code money}. Opened by hand on a server that has
 * one, it prices in {@code money} instead.
 */
final class ShopSample {

    static final String NAME = "shop";
    private static final ItemSpec DIAMOND = ItemSpec.of("minecraft:diamond", 1);
    private static final ItemMatcher ANY_DIAMOND = ItemMatcher.of("minecraft:diamond");
    private static volatile Tokens tokens;

    private ShopSample() {
    }

    /**
     * Registers the menu again (and the tokens once) and answers its screen. {@code manualViewer} is who opened it through
     * {@code /ecc screen} ({@code null} under the check): they pay in {@code money} when an economy answers for it, and
     * otherwise in tokens, 100 of them given on every open.
     */
    static synchronized Screen<?> build(ECPluginData plugin, @Nullable UUID manualViewer) {
        if (tokens == null) {
            tokens = new Tokens(plugin.getMetaInfo().getName().toLowerCase(Locale.ROOT) + ":tokens");
            Currencies.register(plugin, tokens);
        }
        Currency currency = tokens;
        if (manualViewer != null) {
            if (Currencies.money().isAvailable()) {
                currency = Currencies.money();
            } else {
                tokens.set(CurrencyOwner.player(manualViewer), new BigDecimal("100"));
            }
        }
        MenuRegistry.get().unregisterAll(plugin);
        Menu menu = Menu.of(plugin, NAME)
                .offer("diamond", 13,
                        Offer.buy(Goods.ofItem(DIAMOND, ANY_DIAMOND.plainOnly()), Price.of(currency, 10)).build(),
                        Offer.sell(Goods.ofItem(DIAMOND, ANY_DIAMOND.plainOnly()), Price.of(currency, 4)).build())
                .build();
        MenuRegistry.get().register(plugin, menu);
        return menu.getScreen().debounce(0);
    }

    /** Left buys one (tokens 100 -> 90, a diamond more), right sells it back (+4 times the player's boosters, the diamond gone): two TransactionEvents. */
    static String exercise(ECPluginData plugin, FPlayer viewer, ScreenView view, List<String> problems) {
        CurrencyOwner wallet = CurrencyOwner.player(viewer.getUniqueId());
        tokens.set(wallet, new BigDecimal("100"));
        List<TransactionEvent> seen = new CopyOnWriteArrayList<>();
        ECEventSubscription<TransactionEvent> listening = ECEventBus.global().subscribe(TransactionEvent.class, seen::add);
        try {
            int before = diamonds(viewer);
            view.click("diamond", ClickGesture.PRIMARY);
            BigDecimal afterBuy = tokens.getBalance(wallet);
            int boughtDiamonds = diamonds(viewer);
            view.click("diamond", ClickGesture.SECONDARY);
            BigDecimal afterSell = tokens.getBalance(wallet);
            int soldDiamonds = diamonds(viewer);
            check(problems, afterBuy.compareTo(new BigDecimal("90")) == 0 && boughtDiamonds == before + 1,
                    "the left click left " + afterBuy + " tokens and " + boughtDiamonds + " diamonds (from 100 and " + before + ")");
            //a sale pays what the player's boosters of the currency say: the bot is an operator, so permission boosters apply
            BigDecimal boost = ExecutionContext.builder(Caller.of(plugin, "screen/shop")).player(viewer).build().getMultiplier(tokens.getId());
            BigDecimal expectedAfterSell = afterBuy.add(new BigDecimal("4").multiply(boost));
            check(problems, afterSell.compareTo(expectedAfterSell) == 0 && soldDiamonds == before,
                    "the right click left " + afterSell + " tokens and " + soldDiamonds + " diamonds (expected " + expectedAfterSell
                            + ", the sale boosted x" + boost.toPlainString() + ")");
            check(problems, seen.size() == 2, seen.size() + " TransactionEvent(s), 2 expected");
            return "tokens 100->" + afterBuy.toPlainString() + "->" + afterSell.toPlainString() + " diamonds " + before + "->" + boughtDiamonds
                    + "->" + soldDiamonds + " sale-boost=x" + boost.stripTrailingZeros().toPlainString() + " events=" + seen.size();
        } finally {
            listening.unsubscribe();
        }
    }

    private static int diamonds(FPlayer viewer) {
        return DeliveryDestinations.requireInventory().count(viewer, ANY_DIAMOND);
    }

    private static void check(List<String> problems, boolean holds, String otherwise) {
        if (!holds) {
            problems.add(otherwise);
        }
    }

    /** Wallets in memory, for every owner kind. */
    private static final class Tokens extends AbstractCurrency {
        private final Map<CurrencyOwner, BigDecimal> wallets = new ConcurrentHashMap<>();

        private Tokens(String id) {
            super(id, FancyText.of("tokens"), NumericSpec.POINTS);
        }

        void set(CurrencyOwner owner, BigDecimal amount) {
            wallets.put(owner, amount);
        }

        @Override
        public boolean supports(CurrencyOwner.Kind kind) {
            return true;
        }

        @Override
        public BigDecimal getBalance(CurrencyOwner owner) {
            return wallets.getOrDefault(owner, BigDecimal.ZERO);
        }

        @Override
        protected synchronized EcoResponse withdraw(CurrencyOwner owner, BigDecimal amount) {
            BigDecimal balance = getBalance(owner);
            if (balance.compareTo(amount) < 0) {
                return EcoResponse.insufficientFunds(amount, balance);
            }
            wallets.put(owner, balance.subtract(amount));
            return EcoResponse.success(amount, balance.subtract(amount));
        }

        @Override
        protected synchronized EcoResponse deposit(CurrencyOwner owner, BigDecimal amount) {
            BigDecimal balance = getBalance(owner).add(amount);
            wallets.put(owner, balance);
            return EcoResponse.success(amount, balance);
        }

        @Override
        protected synchronized EcoResponse overwrite(CurrencyOwner owner, BigDecimal amount) {
            wallets.put(owner, amount);
            return EcoResponse.success(amount, amount);
        }
    }
}
