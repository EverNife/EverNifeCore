package br.com.finalcraft.evernifecore.minecraft.screen;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.BukkitScreenWorld;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.PlayerDouble;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.Slots;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A chest has no "disabled" look of its own, so it writes one into the stack: a grey name and the reason as lore. */
class ChestDisabledElementTest {

    @TempDirNobodyCleans
    Path tempDir;

    private BukkitScreenWorld world;
    private PlayerDouble player;

    @BeforeEach
    void setup() {
        world = BukkitScreenWorld.installWithItemMetadata(tempDir);
        player = world.newPlayer("Steve");
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    private static Gate failing(String... reasons) {
        Condition[] conditions = new Condition[reasons.length];
        for (int i = 0; i < reasons.length; i++) {
            String reason = reasons[i];
            conditions[i] = ctx -> ConditionResult.fail(FancyText.of(reason));
        }
        return Gate.of(Arrays.asList(conditions), Collections.<Cost>emptyList());
    }

    @Test
    void aChoiceWhoseGateFailsIsDrawnGreyWithTheReasonInItsLore() {
        Screen<Layout> screen = Screen.of(3);
        screen.choice(Slots.of(13), Icon.of("minecraft:emerald").displayName("Pay").lore("§7A fair price"), FancyText.of("Pay"),
                failing("Needs 10 coins"), Collections.emptyList());
        screen.choice(Slots.of(14), Icon.of("minecraft:emerald").displayName("Free"), FancyText.of("Free"), Gate.open(),
                Collections.emptyList());

        world.open(screen, player);

        ItemMeta disabled = world.getSurface().getItem(13).getItemMeta();
        assertEquals("§7Pay", disabled.getDisplayName(), "a name with no colour of its own is greyed");
        assertEquals(Arrays.asList("§7A fair price", "§cNeeds 10 coins"), disabled.getLore(),
                "the reason is the last line, after the icon's own lore");
        ItemMeta enabled = world.getSurface().getItem(14).getItemMeta();
        assertTrue(enabled.getLore() == null || enabled.getLore().isEmpty(), "an enabled element carries no reason: " + enabled.getLore());
    }

    @Test
    void aNameWithAColourOfItsOwnKeepsItAndAReasonPerLineGetsALorePerLine() {
        Screen<Layout> screen = Screen.of(3);
        screen.choice(Slots.of(13), Icon.of("minecraft:emerald").displayName("§6Pay"), FancyText.of("Pay"),
                failing("Level 10 needed", "Talk to the guard first"), Collections.emptyList());

        world.open(screen, player);

        ItemMeta disabled = world.getSurface().getItem(13).getItemMeta();
        assertEquals("§6Pay", disabled.getDisplayName());
        assertEquals(Arrays.asList("§cLevel 10 needed", "§cTalk to the guard first"), disabled.getLore());
    }
}
