package br.com.finalcraft.evernifecore.minecraft.gui;

import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.state.MutableState;
import br.com.finalcraft.evernifecore.minecraft.gui.state.State;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.SurfaceDouble;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Whether a change the player would only read is a change the diff can see.
 *
 * <p>The diff asks {@code ItemStack.isSimilar}, and that question is answered by the server's item
 * factory comparing two pieces of metadata. A rig that hands out no metadata therefore answers "same
 * item" for two stacks that say different words - the counter repaints in production and costs
 * nothing here, which is the shape of a green test over a screen that never updates.</p>
 *
 * <p>Both rigs are measured below, in one place, so the cheap one's blindness is a written result
 * rather than a surprise: {@link GuiTestWorld#installWithItemMetadata(Path)} is what a test about
 * text has to stand on, and this is the assertion that says why.</p>
 */
class RenderedTextDiffTest {

    //NEVER: the locale bootstrap's async saveAsync() can race JUnit's default @TempDir cleanup on Windows
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private GuiTestWorld world;

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    private static Icon labelled(String text) {
        return Icon.of(new ItemStack(Material.PAPER)).displayName(text);
    }

    /**
     * Opens a screen whose one icon says whatever {@code label} says, changes the label once, and
     * answers how many times the slot was written for it.
     */
    private int writesCausedByRenaming(MutableState<String> label) {
        Gui<?> gui = Gui.of(3).addComponent(c -> {
            c.remember(label);
            c.render(slots -> slots.icon(13, labelled(label.get())));
        });

        world.openDetached(gui, world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();
        world.advanceTicks(1);
        surface.forgetWrites();

        label.set("&cBusy");
        world.advanceTicks(1);
        return surface.getWriteCount(13);
    }

    @Test
    void anIconWhoseOnlyChangeIsItsNameIsRepaintedWhereNamesAreRemembered() {
        world = GuiTestWorld.installWithItemMetadata(tempDir);

        assertEquals(1, writesCausedByRenaming(State.of("&aReady")),
                "same material, same amount, different words - the player is looking at the words");
    }

    @Test
    void andIsInvisibleWhereTheServerAnswersNoMetadataAtAll() {
        world = GuiTestWorld.install(tempDir);

        assertEquals(0, writesCausedByRenaming(State.of("&aReady")),
                "no metadata means nothing to tell the two stacks apart, so this rig cannot see text "
                        + "change - which is the whole reason the other one exists");
    }

}
