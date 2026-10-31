package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.testing.RecordingSurface;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Whether a change the player would only read is a change the diff can see.
 *
 * <p>The diff compares {@link RenderedIcon}s by value, and the text is part of that value. A diff keyed on the material
 * and the amount alone would answer "same item" for two icons that say different words - the counter would repaint in
 * production and cost nothing here, which is the shape of a green test over a screen that never updates.</p>
 */
class RenderedTextDiffTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir);
    }

    @AfterEach
    void teardown() {
        world.close();
    }

    /**
     * Opens a screen whose one icon is whatever {@code look} makes of {@code label}, changes the label once, and answers
     * how many times the slot was written for it.
     */
    private int writesCausedByRelabelling(Function<String, Icon> look) {
        MutableState<String> label = State.of("&aReady");
        Screen<?> screen = Screen.of(3).addComponent(c -> {
            c.remember(label);
            c.render(slots -> slots.icon(13, look.apply(label.get())));
        });

        ScreenView view = world.open(screen, world.viewer("Steve"));
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        world.advanceTicks(1);
        surface.forgetApplies();

        label.set("&cBusy");
        world.advanceTicks(1);
        int writes = 0;
        for (List<ElementPatch> apply : surface.getApplies()) {
            for (ElementPatch patch : apply) {
                if (patch.getSlot() == 13) {
                    writes++;
                }
            }
        }
        view.close();
        world.advanceTicks(2);
        return writes;
    }

    @Test
    void anIconWhoseOnlyChangeIsItsNameIsRepainted() {
        assertEquals(1, writesCausedByRelabelling(text -> Icon.of(ItemSpec.of("minecraft:paper", 1)).displayName(text)),
                "same material, same amount, different words - the player is looking at the words");
    }

    @Test
    void anIconWhoseOnlyChangeIsItsLoreIsRepainted() {
        assertEquals(1, writesCausedByRelabelling(text -> Icon.of(ItemSpec.of("minecraft:paper", 1))
                        .displayName("Status").lore(Collections.singletonList(text))),
                "the name is the same and the lore is not - the lore is text the player reads too");
    }

}
