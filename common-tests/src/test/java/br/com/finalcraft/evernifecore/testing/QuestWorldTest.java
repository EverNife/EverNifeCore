package br.com.finalcraft.evernifecore.testing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A quest world logs players in the way a platform does: the section reaches its online player. */
class QuestWorldTest {

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private QuestWorld quests;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        quests = QuestWorld.open("FakeQuests", tempDir.resolve("quests"));
    }

    @AfterEach
    void close() {
        quests.close();
        world.close();
    }

    @Test
    void loginAttachesThePlayer() {
        TestFPlayerSender alex = quests.player("Alex");

        assertSame(alex, quests.section(alex).getPlayer(), "a logged-in player's section answers with that player");

        quests.quit(alex);
        assertNull(quests.section(alex).getPlayer(), "after the quit the section is detached");
    }
}
