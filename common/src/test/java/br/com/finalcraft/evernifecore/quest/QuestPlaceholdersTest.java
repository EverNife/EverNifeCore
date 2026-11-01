package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.quest.placeholder.QuestPlaceholderScope;
import br.com.finalcraft.evernifecore.quest.placeholder.QuestPlaceholders;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.QuestWorld;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static br.com.finalcraft.evernifecore.testing.QuestWorld.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The quest placeholders in the two scopes: one plugin's (bare names) and every engine's (full ids). */
class QuestPlaceholdersTest {

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private QuestWorld quests;
    private QuestEngine engine;
    private TestFPlayerSender alex;
    private RegexReplacer<IPlayerData> own;
    private RegexReplacer<IPlayerData> every;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        quests = QuestWorld.open("FakeQuests", tempDir.resolve("quests"));
        engine = quests.getEngine();
        alex = quests.player("Alex");
        engine.register(quests.parse("{id: hunt, name: Hunter, description: Kill things, repeat: {cooldown: 1h},"
                + " params: {mob: {values: [zombie]}}, stages: [{id: kills, objectives: [{id: z, type: kill, goal: 4}]}]}"));
        engine.register(quests.parse("{id: d1, meta: {tags: [daily]}, stages: [{id: s, objectives: [{id: o, type: kill}]}]}"));
        engine.register(quests.parse("{id: d2, meta: {tags: [daily]}, stages: [{id: s, objectives: [{id: o, type: kill}]}]}"));
        own = new RegexReplacer<>();
        QuestPlaceholders.register(own, QuestPlaceholderScope.ownedBy(quests.getOwner()));
        every = new RegexReplacer<>();
        QuestPlaceholders.registerCore(every);
    }

    @AfterEach
    void close() {
        quests.close();
        world.close();
    }

    private String ask(RegexReplacer<IPlayerData> replacer, String placeholder) {
        String asked = "%" + placeholder + "%";
        String answer = replacer.apply(asked, PlayerController.getLoaded(alex.getUniqueId()));
        return asked.equals(answer) ? "<unanswered>" : answer;
    }

    @Test
    void aQuestAnswersStatusProgressPercentAndParams() {
        assertEquals("AVAILABLE", ask(own, "quest_[hunt]_status"));
        assertTrue(await(engine.start(quests.owner(alex), quests.id("hunt"), QuestParams.NONE)).isSuccess());
        quests.post(quests.kill(alex, "minecraft:zombie"));

        assertEquals("ACTIVE", ask(own, "quest_[hunt]_status"));
        assertEquals("kills", ask(own, "quest_[hunt]_stage"));
        assertEquals("1", ask(own, "quest_[hunt]_progress_[z]"));
        assertEquals("4", ask(own, "quest_[hunt]_goal_[z]"));
        assertEquals("25", ask(own, "quest_[hunt]_percent"));
        assertEquals("zombie", ask(own, "quest_[hunt]_param_[mob]"));
        assertEquals("Hunter", ask(own, "quest_[hunt]_name"));
        assertEquals("1", ask(own, "quests_active_count"));
    }

    @Test
    void timesAndCooldownFollowTheCompletion() {
        assertTrue(await(engine.start(quests.owner(alex), quests.id("hunt"), QuestParams.NONE)).isSuccess());
        await(engine.complete(quests.owner(alex), quests.id("hunt")));
        quests.advance(Duration.ofMinutes(10));

        assertEquals("1", ask(own, "quest_[hunt]_times"));
        assertEquals("COOLDOWN", ask(own, "quest_[hunt]_status"));
        assertEquals("3000", ask(own, "quest_[hunt]_cooldown"), "50 min left of the 1 h");
        assertEquals("1", ask(own, "quests_completed_count"));
    }

    @Test
    void whatDoesNotExistAnswersEmpty() {
        assertEquals("", ask(own, "quest_[nope]_status"));
        assertEquals("", ask(own, "quest_[hunt]_progress_[nope]"));
        assertEquals("", ask(own, "quest_[hunt]_bogus"));
        assertEquals("", ask(own, "pool_[nope]_1"));
        assertEquals("", ask(own, "tracked_name"));
    }

    @Test
    void theOwnScopeTakesBareNamesAndEveryEngineFullIds() {
        assertEquals("AVAILABLE", ask(every, "quest_[fakequests:hunt]_status"));
        assertEquals("", ask(every, "quest_[hunt]_status"), "every engine names a quest by its full id");
        assertEquals("", ask(own, "quest_[fakequests:hunt]_status"), "a plugin's own scope names its quests bare");
    }

    @Test
    void aPoolAnswersItsDrawAndTheTrackedQuestItsOps() {
        QuestPool pool = engine.pool("daily", p -> p.fromTag("daily").draw(2));
        List<QuestId> drawn = pool.current(quests.owner(alex));
        await(engine.track(alex, quests.id("hunt")));

        assertEquals(drawn.get(0).getName(), ask(own, "pool_[daily]_1"));
        assertEquals(drawn.get(0).toString(), ask(every, "pool_[daily]_1"));
        assertEquals("2", ask(own, "pool_[daily]_count"));
        assertEquals("hunt", ask(own, "tracked_id"));
        assertEquals("Hunter", ask(own, "tracked_name"));
    }
}
