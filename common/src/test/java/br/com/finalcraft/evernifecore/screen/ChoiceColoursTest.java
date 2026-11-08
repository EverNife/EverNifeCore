package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.testing.Locales;
import br.com.finalcraft.evernifecore.testing.RecordingSurface;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The colours a choice dresses its item in live in the core's lang file: an admin changes them without a build. */
class ChoiceColoursTest {

    private static final Cost GOLD = new Cost() {
        @Override
        public ConditionResult test(ExecutionContext ctx) {
            return ConditionResult.pass();
        }

        @Override
        public boolean take(ExecutionContext ctx) {
            return true;
        }

        @Override
        public void refund(ExecutionContext ctx) {
        }

        @Override
        public FancyText describe(ExecutionContext ctx) {
            return FancyText.of("50 gold");
        }
    };

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private TestFPlayerSender alex;

    @BeforeEach
    void open() {
        world = ScreenWorld.open(tempDir);
        alex = world.viewer("Alex");
    }

    @AfterEach
    void close() {
        world.close();
    }

    private ScreenView draw() {
        Screen<Layout> screen = Screen.of(1);
        screen.choice(Slots.of(0), Icon.of("minecraft:emerald").displayName("Pay"), FancyText.of("Pay"),
                Gate.builder().cost(GOLD).build(), Collections.emptyList());
        screen.choice(Slots.of(1), Icon.of("minecraft:emerald").displayName("Wait"), FancyText.of("Wait"),
                Gate.builder().require(ctx -> ConditionResult.fail(FancyText.of("Not yet"))).build(), Collections.emptyList());
        return world.open(screen, alex);
    }

    private void close(ScreenView view) {
        view.close();
        world.advanceTicks(2);
    }

    @Test
    void theDefaultsStayGreenGreyAndGrey() {
        ScreenView view = draw();
        RecordingSurface surface = world.getRenderer().surfaceOf(view);

        assertEquals("§aPay", surface.getSlot(0).getItem().getDisplayName());
        assertEquals("§7Wait", surface.getSlot(1).getItem().getDisplayName());
        assertTrue(surface.getSlot(0).getItem().getLore().contains("§750 gold"), surface.getSlot(0).getItem().getLore().toString());
        close(view);
    }

    @Test
    void editingTheThreeTextsChangesTheItemDrawn() {
        ECPluginData core = world.getCore();
        String lang = core.getPluginLanguage();
        try (Locales.Retext enabled = Locales.retext(core, "Choice.ENABLED_NAME_COLOUR", lang, "§b");
             Locales.Retext disabled = Locales.retext(core, "Choice.DISABLED_NAME_COLOUR", lang, "§d");
             Locales.Retext cost = Locales.retext(core, "Choice.COST_LINE", lang, "§ePrice: ${costs}")) {
            ScreenView view = draw();
            RecordingSurface surface = world.getRenderer().surfaceOf(view);

            assertEquals("§bPay", surface.getSlot(0).getItem().getDisplayName());
            assertEquals("§dWait", surface.getSlot(1).getItem().getDisplayName());
            assertTrue(surface.getSlot(0).getItem().getLore().contains("§ePrice: 50 gold"), surface.getSlot(0).getItem().getLore().toString());
            close(view);
        }
    }
}
