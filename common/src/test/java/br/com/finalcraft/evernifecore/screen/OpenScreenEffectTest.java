package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.audit.AuditEntry;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stock effect {@code open-screen} opens what answers a key, with its arguments, and audits a context without a player. */
class OpenScreenEffectTest {

    private static final ScreenKey JOURNAL = ScreenKey.of("quests", "journal");

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private LogicWorld quests;
    private TestFPlayerSender alex;

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir);
        alex = world.viewer("Alex");
        quests = LogicWorld.open("Quests", tempDir.resolve("quests"));
        Screens.override(quests.getOwner(), JOURNAL, ScreenScope.server(), ctx -> Screen.of(1).title("Journal: {0}"));
    }

    @AfterEach
    void teardown() {
        Screens.closeOwnedBy(quests.getOwner());
        quests.close();
        world.close();
    }

    private Effect effect(String singleQuotedJson) {
        try {
            JsonNode node = new ObjectMapper().readTree(singleQuotedJson.replace('\'', '"'));
            return Effects.parse(quests.getOwner(), node, ZoneOffset.UTC);
        } catch (IOException unreadable) {
            throw new IllegalArgumentException(unreadable);
        }
    }

    @Test
    void itOpensWhatAnswersTheKeyWithItsArguments() {
        Effects.apply(quests.context(alex).build(), effect("{'type': 'open-screen', 'screen': 'quests:journal', 'args': ['chapter one']}"));

        ScreenView view = Screens.getOpenView(alex);
        assertNotNull(view, "the journal opened");
        assertEquals("Journal: chapter one", view.getCurrentTitle());
        assertEquals(JOURNAL, view.getScreen().getKey());
        view.close();
        world.advanceTicks(2);
    }

    @Test
    void withoutAPlayerItAuditsAndOpensNothing() {
        ExecutionContext ctx = quests.context().build();

        Effects.apply(ctx, effect("{'type': 'open-screen', 'screen': 'quests:journal'}"));

        List<AuditEntry> failed = ctx.getAudit().getEntries(AuditKind.EFFECT_FAILED);
        assertEquals(1, failed.size(), ctx.getAudit().getEntries().toString());
        assertTrue(failed.get(0).getText().contains("no player to open a screen for"), failed.get(0).getText());
        assertTrue(ctx.getAudit().getEntries(AuditKind.EFFECT_APPLIED).isEmpty(), "an effect that audited its failure was not applied");
        assertEquals(0, Screens.getOpenCount());
    }

}
