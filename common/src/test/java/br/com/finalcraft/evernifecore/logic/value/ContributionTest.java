package br.com.finalcraft.evernifecore.logic.value;

import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.logic.audit.AuditEntry;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.logic.audit.AuditLog;
import br.com.finalcraft.evernifecore.logic.audit.AuditQuery;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.ScriptedStorage;
import br.com.finalcraft.evernifecore.testing.TestCurrency;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Several members fill a contribution up to its target, by member, and its completion happens exactly once. */
class ContributionTest {

    private static final ProgressOwner CLAN = ProgressOwner.of("group", "fakeclans/red");

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld clans;
    private TestFPlayerSender alex;
    private TestFPlayerSender bruno;
    private TestCurrency gold;

    @BeforeEach
    void open() {
        world = Platforms.strict().ignoringPlatformRegistrations().mainThreadInline().loggingToStdout().install();
        clans = LogicWorld.open("FakeClans", tempDir);
        alex = clans.player("Alex");
        bruno = clans.player("Bruno");
        gold = Economies.currency("fakeclans:gold").balance(wallet(alex), 60).balance(wallet(bruno), 100);
    }

    @AfterEach
    void close() {
        clans.close();
        world.close();
    }

    private static CurrencyOwner wallet(TestFPlayerSender player) {
        return CurrencyOwner.player(player.getUniqueId());
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + ", got " + actual);
    }

    @Test
    void twoMembersFillItPartiallyAndTheCompletionAppliesOnce() {
        List<String> completions = new ArrayList<>();
        Contribution<?> vault = Contributions.of(clans.getOwner(), clans.getStorage())
                .define("vault-upgrade", CLAN, CurrencyRequirement.of(gold, new BigDecimal("100")))
                .onComplete(ctx -> completions.add(ctx.getPlayer().getName()));
        List<ContributionDepositEvent> deposits = clans.recorded(ContributionDepositEvent.class);
        List<ContributionCompletedEvent> completed = clans.recorded(ContributionCompletedEvent.class);

        DepositResult first = vault.deposit(clans.context(alex).build());
        assertAmount("60", first.getCounted());
        assertFalse(first.isCompleted());
        DepositResult second = vault.deposit(clans.context(bruno).build());
        assertAmount("40", second.getCounted());
        assertTrue(second.isCompleted());
        assertAmount("0", vault.deposit(clans.context(bruno).build()).getCounted());

        assertAmount("0", gold.getBalance(wallet(alex)));
        assertAmount("60", gold.getBalance(wallet(bruno)));
        Map<UUID, BigDecimal> byMember = vault.getByMember();
        assertAmount("60", byMember.get(alex.getUniqueId()));
        assertAmount("40", byMember.get(bruno.getUniqueId()));
        assertTrue(vault.isComplete());
        assertEquals(2, deposits.size());
        assertEquals(1, completed.size());
        assertEquals(1, completions.size());
        assertEquals("Bruno", completions.get(0));
    }

    @Test
    void aCheckingRequirementCountsWhatIsHeldOnceAndTakesNothing() {
        Contribution<?> pledge = clans.contributions()
                .define("pledge", CLAN, CurrencyRequirement.of(gold, new BigDecimal("500")).check());

        assertAmount("60", pledge.deposit(clans.context(alex).build()).getCounted());
        assertAmount("0", pledge.deposit(clans.context(alex).build()).getCounted());
        gold.balance(wallet(alex), 80);
        assertAmount("20", pledge.deposit(clans.context(alex).build()).getCounted());

        assertAmount("80", gold.getBalance(wallet(alex)));
        assertAmount("80", pledge.getTotal());
    }

    @Test
    void whatWasDepositedOutlivesTheContributionsAndADepositIsAlwaysAPlayers() {
        Contributions.of(clans.getOwner(), clans.getStorage()).define("vault-upgrade", CLAN, CurrencyRequirement.of(gold, new BigDecimal("100")))
                .deposit(clans.context(alex).build());
        Contributions.closeOwnedBy(clans.getOwner());

        Contribution<?> reopened = Contributions.of(clans.getOwner(), clans.getStorage())
                .define("vault-upgrade", CLAN, CurrencyRequirement.of(gold, new BigDecimal("100")));

        assertAmount("60", reopened.getTotal());
        IllegalStateException noPlayer = assertThrows(IllegalStateException.class, () -> reopened.deposit(clans.context().build()));
        assertTrue(noPlayer.getMessage().contains(".player(player)"), noPlayer.getMessage());
    }

    /** A consuming requirement of ten "probe"s that hands {@code take} to {@code onTake} and records every give-back. */
    private abstract static class Probe implements ContributionRequirement<String> {
        final List<String> givenBack = new CopyOnWriteArrayList<>();

        @Override public String getSubject() { return "probe"; }
        @Override public BigDecimal getTarget() { return BigDecimal.TEN; }
        @Override public boolean isConsuming() { return true; }
        @Override public BigDecimal countHeld(ExecutionContext ctx) { return BigDecimal.TEN; }
        @Override public void giveBack(ExecutionContext ctx, BigDecimal amount) {
            givenBack.add(ctx.getPlayer().getName() + ":" + amount.toPlainString());
        }
    }

    @Test
    void aDepositNeverHoldsTheContributionWhileTheRequirementReachesThePlayer() {
        AtomicReference<Contribution<?>> vault = new AtomicReference<>();
        vault.set(clans.contributions().define("vault-upgrade", CLAN, new Probe() {
            @Override
            public BigDecimal take(ExecutionContext ctx, BigDecimal upTo) {
                //what a menu on the player's thread does while the take waits for that thread
                CompletableFuture<BigDecimal> read = CompletableFuture.supplyAsync(() -> vault.get().getTotal());
                try {
                    read.get(2, TimeUnit.SECONDS);
                } catch (TimeoutException blocked) {
                    throw new AssertionError("getTotal() blocked behind the deposit's monitor");
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
                return upTo;
            }
        }));

        DepositResult result = vault.get().deposit(clans.context(alex).build());

        assertAmount("10", result.getCounted());
        assertTrue(result.isCompleted());
    }

    @Test
    void aRacingDepositGivesTheSurplusBack() throws Exception {
        CountDownLatch alexTaking = new CountDownLatch(1);
        CountDownLatch letAlexFinish = new CountDownLatch(1);
        Probe probe = new Probe() {
            @Override
            public BigDecimal take(ExecutionContext ctx, BigDecimal upTo) {
                if (ctx.getPlayer().getUniqueId().equals(alex.getUniqueId())) {
                    alexTaking.countDown();
                    try {
                        letAlexFinish.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        throw new AssertionError(interrupted);
                    }
                }
                return upTo;
            }
        };
        Contribution<?> goal = clans.contributions().define("vault-upgrade", CLAN, probe);

        CompletableFuture<DepositResult> alexDeposit = CompletableFuture.supplyAsync(() -> goal.deposit(clans.context(alex).build()));
        assertTrue(alexTaking.await(2, TimeUnit.SECONDS));
        DepositResult brunoResult;
        try {
            //Alex took ten and has not counted them yet; Bruno fills the goal in the meantime
            brunoResult = CompletableFuture.supplyAsync(() -> goal.deposit(clans.context(bruno).build())).get(2, TimeUnit.SECONDS);
        } finally {
            letAlexFinish.countDown();
        }
        DepositResult alexResult = alexDeposit.get(2, TimeUnit.SECONDS);

        assertAmount("10", brunoResult.getCounted());
        assertTrue(brunoResult.isCompleted());
        assertAmount("0", alexResult.getCounted());
        assertFalse(alexResult.isCompleted());
        assertAmount("10", goal.getTotal());
        assertEquals(Collections.singletonList("Alex:10"), probe.givenBack);
        assertEquals(Collections.singleton(bruno.getUniqueId()), goal.getByMember().keySet());
    }

    @Test
    void theRowHandedToTheStorageIsASnapshot() {
        Contribution<?> vault = clans.contributions().define("vault-upgrade", CLAN, CurrencyRequirement.of(gold, new BigDecimal("100")));
        vault.deposit(clans.context(alex).build());
        Contributions.Row saved = clans.getBackend().lastSaved(Contributions.COLLECTION, Contributions.Row.class).get();

        vault.deposit(clans.context(bruno).build());

        assertAmount("60", new BigDecimal(saved.total));
        assertFalse(saved.members.containsKey(bruno.getUniqueId().toString()));
    }

    @Test
    void reopeningOnAnotherStorageWaitsForTheWritesOfTheFirst() throws Exception {
        Contribution<?> vault = clans.contributions().define("vault-upgrade", CLAN, CurrencyRequirement.of(gold, new BigDecimal("100")));
        clans.getBackend().holdWrites();
        vault.deposit(clans.context(alex).build());
        assertEquals(1, clans.getBackend().pendingSaves());
        ScriptedStorage elsewhere = ScriptedStorage.inMemory();
        elsewhere.init().join();
        Thread slowBackend = new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
                //released now, then
            }
            clans.getBackend().releaseWrites();
        });
        slowBackend.start();
        try {
            Contributions.of(clans.getOwner(), elsewhere.asECStorage());

            assertEquals(0, clans.getBackend().pendingSaves());
        } finally {
            slowBackend.join();
            Contributions.closeOwnedBy(clans.getOwner());
            elsewhere.close().join();
        }
    }

    @Test
    void twoWritesOfOneRowLandingNewestFirstLeaveTheLastStateInTheBackend() {
        Contribution<?> vault = clans.contributions().define("vault-upgrade", CLAN, CurrencyRequirement.of(gold, new BigDecimal("500")));
        ScriptedStorage backend = clans.getBackend();
        backend.holdWrites();
        vault.deposit(clans.context(alex).build());
        vault.deposit(clans.context(bruno).build());

        while (backend.heldWrites() > 0) {
            backend.releaseNewestWrite();
        }
        backend.releaseWrites();
        Contributions.closeOwnedBy(clans.getOwner());

        Contributions.Row landed = backend.lastLanded(Contributions.COLLECTION, Contributions.Row.class).get();
        assertAmount("160", new BigDecimal(landed.total));
        assertTrue(landed.members.containsKey(bruno.getUniqueId().toString()));
    }

    @Test
    void aGiveBackThatThrowsIsAuditedRefundFailedAndLoggedAndWhatFitStillCounts() {
        AuditLog log = clans.auditLog();
        Probe overTaking = new Probe() {
            @Override
            public BigDecimal take(ExecutionContext ctx, BigDecimal upTo) {
                return upTo.add(new BigDecimal("5")); //a whole stack where only part was missing
            }

            @Override
            public void giveBack(ExecutionContext ctx, BigDecimal amount) {
                throw new IllegalStateException("inventory full");
            }
        };
        Contribution<?> goal = clans.contributions().define("vault-upgrade", CLAN, overTaking);
        DepositResult[] result = new DepositResult[1];

        List<String> logged = Logs.capture(() -> result[0] = goal.deposit(clans.context(alex).build()));

        assertAmount("10", result[0].getCounted());
        assertTrue(result[0].isCompleted());
        assertAmount("10", goal.getTotal());
        assertTrue(logged.stream().anyMatch(line -> line.contains("giving it back failed") && line.contains("inventory full")), logged.toString());
        clans.awaitAudit();
        List<AuditEntry> failed = log.query(new AuditQuery().kind(AuditKind.REFUND_FAILED)).join();
        assertEquals(1, failed.size(), failed.toString());
        assertAmount("5", failed.get(0).getAmount());
        assertEquals(alex.getUniqueId(), failed.get(0).getPlayer());
    }
}
