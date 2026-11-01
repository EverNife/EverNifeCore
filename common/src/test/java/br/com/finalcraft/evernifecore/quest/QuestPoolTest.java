package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.quest.event.QuestExpireEvent;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.QuestWorld;
import br.com.finalcraft.evernifecore.testing.TestCurrency;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.time.Window;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static br.com.finalcraft.evernifecore.testing.QuestWorld.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** QuestPool: the draw per window, the history kept out of the next draws, the paid reroll. */
class QuestPoolTest {

    private static final String GOLD = "fakequests:gold";

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private QuestWorld quests;
    private QuestEngine engine;
    private TestFPlayerSender alex;
    private TestCurrency gold;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        quests = QuestWorld.open("FakeQuests", tempDir.resolve("quests"));
        engine = quests.getEngine();
        gold = Economies.currency(GOLD, CurrencyOwner.Kind.PLAYER);
        world.withCurrency(quests.getOwner(), gold);
        alex = quests.player("Alex");
        for (int i = 1; i <= 5; i++) {
            engine.register(quests.parse("{id: daily-" + i + ", meta: {tags: [daily]}, repeat: {window: 'daily at 04:00 zone UTC'},"
                    + " stages: [{id: s, objectives: [{id: z, type: kill, goal: 1}]}]}"));
        }
    }

    @AfterEach
    void close() {
        quests.close();
        world.close();
    }

    @Test
    void aPoolNeverRepeatsTheLastDrawnInConsecutiveWindows() {
        QuestPool pool = engine.pool("daily-mission", p -> p.fromTag("daily").draw(2).window(Window.daily(LocalTime.of(4, 0), ZoneOffset.UTC))
                .noRepeatLast(3));
        ProgressOwner owner = quests.owner(alex);
        List<QuestId> previous = null;
        for (int day = 0; day < 3; day++) {
            List<QuestId> today = pool.current(owner);
            assertEquals(2, new HashSet<>(today).size(), "day " + day + ": " + today);
            assertEquals(today, pool.current(owner), "the same window keeps its draw");
            if (previous != null) {
                Set<QuestId> repeated = new HashSet<>(today);
                repeated.retainAll(previous);
                assertTrue(repeated.isEmpty(), "day " + day + " repeated " + repeated + " of the day before");
            }
            assertTrue(pool.getDraw(owner).getHistory().size() <= 3, pool.getDraw(owner).toString());
            previous = today;
            quests.advance(Duration.ofDays(1));
            quests.runDue();
        }
    }

    @Test
    void rerollChargesTheGateAndRedrawsOnlyWhatWasNotTaken() throws Exception {
        Gate gate = Gate.parse(quests.getOwner(), new YAMLMapper().readTree("{cost: [{type: money, amount: 100, currency: '" + GOLD + "'}]}"),
                ZoneOffset.UTC);
        QuestPool pool = engine.pool("daily-mission", p -> p.fromTag("daily").draw(2).window(Window.daily(LocalTime.of(4, 0), ZoneOffset.UTC))
                .reroll(gate, null));
        gold.balance(CurrencyOwner.player(alex.getUniqueId()), 150);
        ProgressOwner owner = quests.owner(alex);
        List<QuestId> drawn = pool.current(owner);
        QuestId taken = drawn.get(0);
        QuestId dropped = drawn.get(1);
        assertTrue(await(engine.start(owner, taken, QuestParams.NONE)).isSuccess());

        RerollResult rerolled = pool.reroll(alex);

        assertTrue(rerolled.isSuccess(), rerolled.toString());
        assertEquals(2, rerolled.getDrawn().size());
        assertTrue(rerolled.getDrawn().contains(taken), "the active quest stays");
        assertFalse(rerolled.getDrawn().contains(dropped), "the dropped quest is not drawn back");
        assertEquals(0, new BigDecimal("50").compareTo(gold.getBalance(CurrencyOwner.player(alex.getUniqueId()))));

        RerollResult broke = pool.reroll(alex);

        assertFalse(broke.isSuccess());
        assertFalse(broke.explain().isEmpty(), "the gate says what is missing");
        assertEquals(new ArrayList<>(rerolled.getDrawn()), pool.current(owner), "a refused reroll changes nothing");
        assertEquals(0, new BigDecimal("50").compareTo(gold.getBalance(CurrencyOwner.player(alex.getUniqueId()))));
    }

    @Test
    void aPoolAllowsOnlyItsRerollsPerWindow() {
        QuestPool pool = engine.pool("daily-mission", p -> p.fromTag("daily").draw(1).window(Window.daily(LocalTime.of(4, 0), ZoneOffset.UTC))
                .rerollsPerWindow(1));
        ProgressOwner owner = quests.owner(alex);
        pool.current(owner);

        assertTrue(pool.reroll(alex).isSuccess());
        RerollResult again = pool.reroll(alex);
        assertFalse(again.isSuccess());
        assertTrue(again.explain().contains("rerolled 1 of 1 this window; the next window opens at"), again.explain());

        quests.advance(Duration.ofDays(1));
        pool.current(owner);
        assertTrue(pool.reroll(alex).isSuccess(), "a new window gives the reroll back");
    }

    @Test
    void anActiveQuestOfTheLastWindowExpiresWhenTheWindowTurns() {
        QuestPool pool = engine.pool("daily-mission", p -> p.fromTag("daily").draw(1).window(Window.daily(LocalTime.of(4, 0), ZoneOffset.UTC))
                .noRepeatLast(1).expireActiveOnTurn(true));
        ProgressOwner owner = quests.owner(alex);
        QuestId yesterday = pool.current(owner).get(0);
        assertTrue(await(engine.start(owner, yesterday, QuestParams.NONE)).isSuccess());
        List<QuestExpireEvent> expired = quests.recorded(QuestExpireEvent.class);

        quests.advance(Duration.ofDays(1));
        List<QuestId> today = pool.current(owner);

        assertEquals(QuestStatus.EXPIRED, engine.getState(owner, yesterday).get().getStatus());
        assertEquals(1, expired.size(), expired.toString());
        assertFalse(today.contains(yesterday), "the new draw is drawn as before");
    }

    @Test
    void poolsReadFromYaml() throws Exception {
        List<QuestPool> pools = QuestDefinitions.parsePools(engine, new YAMLMapper().readTree("{daily-mission: {from-tag: daily, draw: 1,"
                + " window: 'daily at 04:00 zone UTC', no-repeat-last: 3, reroll: {cooldown: 24h, per-window: 2}, expire-active-on-turn: true}}"),
                ZoneOffset.UTC);

        assertEquals(1, pools.size());
        assertEquals(1, engine.getPool("daily-mission").current(quests.owner(alex)).size());
        assertEquals(3, engine.getPool("daily-mission").getNoRepeatLast());
        assertEquals(2, engine.getPool("daily-mission").getRerollsPerWindow());
        assertTrue(engine.getPool("daily-mission").isExpireActiveOnTurn());
    }
}
