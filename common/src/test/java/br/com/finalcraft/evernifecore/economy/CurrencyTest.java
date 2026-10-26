package br.com.finalcraft.evernifecore.economy;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.Variables;
import br.com.finalcraft.evernifecore.region.RegionId;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestCurrency;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.everylibs.util.numberwrapper.NumericSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules {@link AbstractCurrency} owns, the events it posts, {@code transfer} and {@link Currencies}. */
class CurrencyTest {

    private static final CurrencyOwner ALEX = CurrencyOwner.player(UUID.randomUUID());
    private static final CurrencyOwner BRUNO = CurrencyOwner.player(UUID.randomUUID());

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld shop;
    private Caller caller;
    private TestCurrency gold;

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        shop = LogicWorld.open("FakeShop", tempDir);
        caller = shop.caller("shop/vip#slot12");
        gold = Economies.currency("fakeshop:gold").balance(ALEX, 100);
    }

    @AfterEach
    void close() {
        shop.close();
        world.close();
    }

    @Test
    void everyChangePostsExactlyOneBalanceChangeEventWithBeforeAfterCauseAndCaller() {
        List<BalanceChangeEvent> events = shop.recorded(BalanceChangeEvent.class);

        gold.give(ALEX, new BigDecimal("50"), caller);
        gold.take(ALEX, new BigDecimal("30"), caller);
        gold.set(ALEX, new BigDecimal("7"), caller);

        assertEquals(3, events.size());
        assertEquals(Arrays.asList(BalanceChangeEvent.Cause.GIVE, BalanceChangeEvent.Cause.TAKE, BalanceChangeEvent.Cause.SET),
                events.stream().map(BalanceChangeEvent::getCause).collect(Collectors.toList()));
        assertEquals(0, events.get(0).getBefore().compareTo(new BigDecimal("100")));
        assertEquals(0, events.get(0).getAfter().compareTo(new BigDecimal("150")));
        assertEquals(0, events.get(1).getDelta().compareTo(new BigDecimal("-30")));
        assertEquals(0, events.get(2).getBefore().compareTo(new BigDecimal("120")));
        assertSame(gold, events.get(2).getCurrency());
        assertEquals(ALEX, events.get(2).getOwner());
        assertEquals(caller, events.get(2).getCaller());
    }

    @Test
    void whatMovesNothingPostsNothing() {
        List<BalanceChangeEvent> events = shop.recorded(BalanceChangeEvent.class);

        assertEquals(EcoResponse.Reason.INSUFFICIENT_FUNDS, gold.take(ALEX, new BigDecimal("1000"), caller).getReason());
        assertTrue(gold.give(ALEX, BigDecimal.ZERO, caller).isSuccess());
        assertTrue(gold.set(ALEX, new BigDecimal("100"), caller).isSuccess());
        assertEquals(EcoResponse.Reason.INVALID_AMOUNT, gold.give(ALEX, new BigDecimal("-1"), caller).getReason());

        assertTrue(events.isEmpty(), events.toString());
        assertEquals(0, gold.getTakes());
    }

    @Test
    void aTransferMovesBothLegsAndPostsOneTransferEventAndTwoChanges() {
        List<TransferEvent> transfers = shop.recorded(TransferEvent.class);
        List<BalanceChangeEvent> changes = shop.recorded(BalanceChangeEvent.class);

        EcoResponse response = gold.transfer(ALEX, BRUNO, new BigDecimal("40"), caller);

        assertTrue(response.isSuccess());
        assertEquals(0, response.getBalance().compareTo(new BigDecimal("60")), "a transfer answers with the payer's balance");
        assertEquals(0, gold.getBalance(BRUNO).compareTo(new BigDecimal("40")));
        assertEquals(1, transfers.size());
        assertTrue(transfers.get(0).isSuccess());
        assertNull(transfers.get(0).getRefund());
        assertEquals(Arrays.asList(BalanceChangeEvent.Cause.TRANSFER_OUT, BalanceChangeEvent.Cause.TRANSFER_IN),
                changes.stream().map(BalanceChangeEvent::getCause).collect(Collectors.toList()));
    }

    @Test
    void aFailedSecondLegGivesTheFirstBackAndAnswersWithTheGivesReason() {
        RefusingCurrency vault = new RefusingCurrency();
        vault.set(ALEX, new BigDecimal("100"), caller);
        vault.refuseDepositsTo.add(BRUNO);
        List<TransferEvent> transfers = shop.recorded(TransferEvent.class);
        List<BalanceChangeEvent> changes = shop.recorded(BalanceChangeEvent.class);

        EcoResponse response = vault.transfer(ALEX, BRUNO, new BigDecimal("40"), caller);

        assertEquals(EcoResponse.Reason.PROVIDER_ERROR, response.getReason());
        assertEquals(0, vault.getBalance(ALEX).compareTo(new BigDecimal("100")), "the payer got the first leg back");
        assertEquals(0, vault.getBalance(BRUNO).signum());
        assertEquals(1, transfers.size());
        assertFalse(transfers.get(0).isSuccess());
        assertTrue(transfers.get(0).getRefund().isSuccess());
        assertEquals(Arrays.asList(BalanceChangeEvent.Cause.TRANSFER_OUT, BalanceChangeEvent.Cause.TRANSFER_REFUND),
                changes.stream().map(BalanceChangeEvent::getCause).collect(Collectors.toList()));
    }

    @Test
    void aGiveBackThatAlsoFailsReachesTheTransferEvent() {
        RefusingCurrency vault = new RefusingCurrency();
        vault.set(ALEX, new BigDecimal("100"), caller);
        vault.refuseDepositsTo.add(ALEX);
        vault.refuseDepositsTo.add(BRUNO);
        List<TransferEvent> transfers = shop.recorded(TransferEvent.class);

        EcoResponse response = vault.transfer(ALEX, BRUNO, new BigDecimal("40"), caller);

        assertEquals(EcoResponse.Reason.PROVIDER_ERROR, response.getReason());
        assertEquals(0, vault.getBalance(ALEX).compareTo(new BigDecimal("60")), "the lost leg stays lost - and logged");
        assertEquals(EcoResponse.Reason.PROVIDER_ERROR, transfers.get(0).getRefund().getReason());
    }

    @Test
    void whoHoldsExactlyTheAmountHasIt() {
        assertTrue(gold.has(ALEX, new BigDecimal("100")));
        assertTrue(gold.has(ALEX, new BigDecimal("100.00")));
        assertFalse(gold.has(ALEX, new BigDecimal("100.01")));
    }

    @Test
    void anOwnerKindTheCurrencyHasNoWalletForIsRefusedBeforeItRuns() {
        TestCurrency clanVault = Economies.currency("fakeshop:vault", CurrencyOwner.Kind.GROUP);

        EcoResponse response = clanVault.take(ALEX, BigDecimal.ONE, caller);

        assertEquals(EcoResponse.Reason.UNSUPPORTED_OWNER, response.getReason());
        assertTrue(response.getDetail().contains("Currencies.register"), response.getDetail());
        assertEquals(0, clanVault.getTakes());
        assertEquals(EcoResponse.Reason.UNSUPPORTED_OWNER,
                clanVault.transfer(CurrencyOwner.group("fakeclans:red"), BRUNO, BigDecimal.ONE, caller).getReason());
    }

    @Test
    void moneyHasWalletsForPlayersOnlyAndSaysWhereAGroupBalanceLives() {
        world.withEconomy(Economies.inMemory().build());

        EcoResponse response = Currencies.money().give(CurrencyOwner.group("fakeclans:red"), BigDecimal.TEN, caller);

        assertEquals(EcoResponse.Reason.UNSUPPORTED_OWNER, response.getReason());
        assertTrue(response.getDetail().startsWith("money only has wallets for players"), response.getDetail());
        assertEquals(0, Currencies.money().getBalance(CurrencyOwner.global()).signum());
    }

    @Test
    void everyResponseNamesTheCurrencyThatAnswered() {
        assertSame(gold, gold.give(ALEX, BigDecimal.ONE, caller).getCurrency());
        assertSame(gold, gold.take(ALEX, new BigDecimal("-1"), caller).getCurrency());
        assertSame(gold, gold.take(ALEX, new BigDecimal("999"), caller).getCurrency());
    }

    @Test
    void aMoveWithoutACallerIsRefusedWithTheWayToNameOne() {
        NullPointerException error = assertThrows(NullPointerException.class, () -> gold.give(ALEX, BigDecimal.ONE, null));

        assertTrue(error.getMessage().contains("Caller.of(plugin"), error.getMessage());
    }

    @Test
    void asAVariableACurrencyReadsAndSetsTheBalanceOfTheContextsOwner() {
        TestFPlayerSender alex = shop.player("Alex");
        CurrencyOwner wallet = CurrencyOwner.player(alex.getUniqueId());
        gold.balance(wallet, 25);
        ExecutionContext ctx = shop.context(alex).build();

        assertEquals(0, gold.read(ctx).compareTo(new BigDecimal("25")));
        gold.set(ctx, new BigDecimal("80"));
        assertEquals(0, gold.getBalance(wallet).compareTo(new BigDecimal("80")));

        ExecutionContext region = shop.context().owner(ProgressOwner.region(RegionId.parse("fakeshop:arena"))).build();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> gold.read(region));
        assertTrue(error.getMessage().contains("never for a region or a world"), error.getMessage());
    }

    @Test
    void registeringACurrencyMakesItFindableByIdShortNameAliasAndAsAVariable() {
        world.withCurrency(shop.getOwner(), gold, "coins");

        assertSame(gold, Currencies.require("fakeshop:gold"));
        assertSame(gold, Currencies.find("gold").orElse(null));
        assertSame(gold, Currencies.find("coins").orElse(null));
        assertSame(gold, Variables.find(shop.getOwner(), "fakeshop:gold").orElse(null));

        Currencies.unregisterAll(shop.getOwner());

        assertFalse(Currencies.find("fakeshop:gold").isPresent());
        assertFalse(Variables.find(shop.getOwner(), "fakeshop:gold").isPresent(), "its variable leaves with it");
    }

    @Test
    void anUnknownCurrencyIsRefusedNamingWhatIsRegisteredAndHowToRegister() {
        world.withCurrency(shop.getOwner(), gold);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> Currencies.require("fakeshop:silver"));

        assertTrue(error.getMessage().contains("fakeshop:silver"), error.getMessage());
        assertTrue(error.getMessage().contains("fakeshop:gold"), error.getMessage());
        assertTrue(error.getMessage().contains("Currencies.register(owner, currency)"), error.getMessage());
    }

    @Test
    void aPluginCannotRegisterACurrencyThatAnotherPluginsIdNames() {
        TestCurrency foreign = Economies.currency("finaleconomy:gold");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> Currencies.register(shop.getOwner(), foreign));

        assertTrue(error.getMessage().contains("finaleconomy:gold"), error.getMessage());
    }

    /** A wallet in a map whose deposits to the listed owners the "economy" refuses. */
    private static final class RefusingCurrency extends AbstractCurrency {

        private final Map<CurrencyOwner, BigDecimal> wallets = new ConcurrentHashMap<>();
        private final Set<CurrencyOwner> refuseDepositsTo = ConcurrentHashMap.newKeySet();

        RefusingCurrency() {
            super("fakeshop:vault", FancyText.of("vault"), NumericSpec.MONEY);
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
        protected EcoResponse withdraw(CurrencyOwner owner, BigDecimal amount) {
            BigDecimal current = getBalance(owner);
            if (current.compareTo(amount) < 0) {
                return EcoResponse.insufficientFunds(amount, current);
            }
            wallets.put(owner, current.subtract(amount));
            return EcoResponse.success(amount, current.subtract(amount));
        }

        @Override
        protected EcoResponse deposit(CurrencyOwner owner, BigDecimal amount) {
            if (refuseDepositsTo.contains(owner)) {
                return EcoResponse.providerError(amount, getBalance(owner), "the bank is closed");
            }
            wallets.put(owner, getBalance(owner).add(amount));
            return EcoResponse.success(amount, getBalance(owner));
        }

        @Override
        protected EcoResponse overwrite(CurrencyOwner owner, BigDecimal amount) {
            wallets.put(owner, amount);
            return EcoResponse.success(amount, amount);
        }
    }
}
