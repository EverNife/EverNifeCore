package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.QuestWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A definition check belongs to the plugin that added it and leaves with it. */
class QuestDefinitionCheckOwnerTest {

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private QuestWorld quests;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        quests = QuestWorld.open("CheckedQuests", tempDir.resolve("quests"));
    }

    @AfterEach
    void close() {
        Plugins.forget("Refuser");
        quests.close();
        world.close();
    }

    @Test
    void aCheckAddedByAPluginLeavesWithIt() {
        ECPluginData refuser = Plugins.fakePluginData("Refuser", tempDir.resolve("refuser").toFile());
        QuestEngine.addDefinitionCheck(refuser, definition -> {
            throw new IllegalArgumentException("Refuser takes no quest.");
        });
        String quest = "{id: walk, stages: [{id: s, objectives: [{id: o, type: kill}]}]}";

        assertThrows(IllegalArgumentException.class, () -> quests.getEngine().register(quests.parse(quest)));

        ECPluginManager.removePluginData("Refuser");

        assertDoesNotThrow(() -> quests.getEngine().register(quests.parse(quest)), "the check outlived its plugin");
    }
}
