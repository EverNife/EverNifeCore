package br.com.finalcraft.evernifecore.logic.value;

import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
}
