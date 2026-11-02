package br.com.finalcraft.evernifecore.logic.audit;

import br.com.finalcraft.evernifecore.booster.Booster;
import br.com.finalcraft.evernifecore.economy.Currency;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.value.Contribution;
import br.com.finalcraft.evernifecore.logic.value.CurrencyRequirement;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestCurrency;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A plugin's audit log keeps what that plugin moved, answers who traded with whom, and forgets what is past its retention. */
class AuditLogTest {

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld shop;
    private TestFPlayerSender alex;
    private TestFPlayerSender bia;

    @BeforeEach
    void open() {
        world = Platforms.strict().ignoringPlatformRegistrations().mainThreadInline().loggingToStdout().platformProviderId("test").install();
        shop = LogicWorld.open("FakeShop", tempDir.resolve("shop"));
        alex = shop.player("Alex");
        bia = shop.player("Bia");
    }

    @AfterEach
    void close() {
        shop.close();
        world.close();
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private List<AuditEntry> query(AuditLog log, AuditQuery query) {
        await(log.awaitWrites());
        return await(log.query(query));
    }

    @Test
    void itKeepsOnlyWhatItsOwnPluginMovedAndSinceFiltersByTime() {
        AuditLog log = shop.auditLog();
        try (LogicWorld other = LogicWorld.open("OtherShop", tempDir.resolve("other"))) {
            Gate.open().pay(shop.context(alex).build());
            Gate.open().pay(other.context(alex).build());
            shop.advance(Duration.ofHours(1));
            Gate.open().pay(shop.context(bia).build());

            List<AuditEntry> all = query(log, new AuditQuery().kind(AuditKind.GATE_PAID));
            assertEquals(2, all.size(), all.toString());
            assertTrue(all.stream().allMatch(entry -> entry.getCaller().getPlugin() == shop.getOwner()), all.toString());
            assertEquals(bia.getUniqueId(), all.get(0).getPlayer(), "newest first");

            List<AuditEntry> recent = query(log, new AuditQuery().since(shop.now().minus(Duration.ofMinutes(30))));
            assertEquals(1, recent.size(), recent.toString());
            assertEquals(bia.getUniqueId(), recent.get(0).getPlayer());
        }
    }

    @Test
    void aTransferNamesTheOtherPartyAndBetweenFindsTheTradeInEitherOrder() {
        AuditLog log = shop.auditLog();
        CurrencyOwner alexWallet = CurrencyOwner.player(alex.getUniqueId());
        CurrencyOwner biaWallet = CurrencyOwner.player(bia.getUniqueId());
        UUID carl = UUID.randomUUID();
        TestCurrency gold = Economies.currency("fakeshop:gold", CurrencyOwner.Kind.PLAYER).balance(alexWallet, 100).balance(biaWallet, 100);
        world.withCurrency(shop.getOwner(), gold);

        gold.transfer(alexWallet, biaWallet, new BigDecimal(10), shop.caller("trade"));
        shop.advance(Duration.ofSeconds(1));
        gold.transfer(biaWallet, alexWallet, new BigDecimal(5), shop.caller("trade"));
        gold.transfer(alexWallet, CurrencyOwner.player(carl), new BigDecimal(1), shop.caller("trade"));

        List<AuditEntry> ab = query(log, new AuditQuery().between(alex.getUniqueId(), bia.getUniqueId()));
        List<AuditEntry> ba = query(log, new AuditQuery().between(bia.getUniqueId(), alex.getUniqueId()));

        assertEquals(2, ab.size(), ab.toString());
        assertEquals(2, ba.size(), ba.toString());
        AuditEntry newest = ab.get(0);
        assertEquals(AuditKind.TRANSFER, newest.getKind());
        assertEquals(bia.getUniqueId(), newest.getPlayer(), "the payer");
        assertEquals(alex.getUniqueId(), newest.getCounterparty(), "the one who received");
        assertEquals(0, new BigDecimal(5).compareTo(newest.getAmount()));
        assertEquals("fakeshop:gold", newest.getSubject());
        assertEquals(1, query(log, new AuditQuery().counterparty(carl)).size());
    }

    @Test
    void openingTheLogAgainWithAnEqualRetentionKeepsTheOneAlreadyOpen() {
        AuditLog first = AuditLog.of(shop.getOwner(), shop.getStorage());

        assertSame(first, AuditLog.of(shop.getOwner(), shop.getStorage()));
        assertSame(first, AuditLog.of(shop.getOwner(), shop.getStorage(), AuditRetention.days(30)));
        assertNotSame(first, AuditLog.of(shop.getOwner(), shop.getStorage(), AuditRetention.days(7)));
    }

    @Test
    void theRetentionSweepDeletesWhatIsOlderThanItKeeps() {
        AuditLog log = AuditLog.of(shop.getOwner(), shop.getStorage(), AuditRetention.days(1));
        log.record(AuditEntry.builder(AuditKind.DEPOSIT, shop.caller("old")).text("old").build());
        shop.advance(Duration.ofDays(2));
        log.record(AuditEntry.builder(AuditKind.DEPOSIT, shop.caller("new")).text("new").build());
        await(log.awaitWrites());

        assertEquals(1, (int) await(log.sweepRetention()));

        List<AuditEntry> left = query(log, new AuditQuery());
        assertEquals(1, left.size(), left.toString());
        assertEquals("new", left.get(0).getCaller().getOrigin());
    }

    @Test
    void aBoosterGrantAndADepositAreRecordedByTheirManagers() {
        AuditLog log = shop.auditLog();
        CurrencyOwner alexWallet = CurrencyOwner.player(alex.getUniqueId());
        Currency gold = Economies.currency("fakeshop:gold", CurrencyOwner.Kind.PLAYER).balance(alexWallet, 50);
        world.withCurrency(shop.getOwner(), gold);
        Contribution<Currency> vault = shop.contributions().define("vault", ProgressOwner.global(), CurrencyRequirement.of(gold, new BigDecimal(30)));

        await(shop.boosters().grant(Booster.on("fakeshop:gold").factor(2).forOwner(ProgressOwner.player(alex.getUniqueId())).build()));
        vault.deposit(shop.context(alex).build());

        List<AuditEntry> granted = query(log, new AuditQuery().kind(AuditKind.BOOSTER_GRANTED));
        assertEquals(1, granted.size(), granted.toString());
        assertEquals("fakeshop:gold", granted.get(0).getSubject());
        assertEquals(alex.getUniqueId(), granted.get(0).getPlayer());
        List<AuditEntry> deposits = query(log, new AuditQuery().kind(AuditKind.DEPOSIT).player(alex.getUniqueId()));
        assertEquals(1, deposits.size(), deposits.toString());
        assertEquals(0, new BigDecimal(30).compareTo(deposits.get(0).getAmount()));
        assertEquals("vault", deposits.get(0).getSubject());
    }
}
