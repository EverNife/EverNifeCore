package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A button bound to raw slots by {@code screen.icon(slots, icon)}, outside any layout. */
class RawSlotButtonTest {

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

    private String materialShownAndClose(ScreenView view, int slot) {
        RenderedIcon shown = world.getRenderer().surfaceOf(view).getSlot(slot);
        view.close();
        world.advanceTicks(2);
        return shown.getItem().getMaterialId();
    }

    /** A background goes under the content whatever the declaration order - declared last, it must not cover it. */
    @Test
    void aBackgroundButtonDeclaredAfterTheContentStaysUnderIt() {
        Screen<?> screen = Screen.of(3);
        screen.addComponent(c -> c.render(slots -> slots.icon(13, Icon.of(ItemSpec.of("minecraft:paper", 1)))));
        screen.icon(Slots.all(), Icon.of(ItemSpec.of("minecraft:gray_stained_glass_pane", 1)).background());

        assertEquals("minecraft:paper", materialShownAndClose(world.open(screen, world.viewer("Steve")), 13));
    }

    /** A shape only becomes slots against the grid; the button's id is taken from the slots it resolves to. */
    @Test
    void aRawSlotButtonAcceptsAShape() {
        Screen<?> screen = Screen.of(3);
        Button frame = screen.icon(Slots.all(), Icon.of(ItemSpec.of("minecraft:gray_stained_glass_pane", 1)));

        assertEquals("slot#0", frame.getId());
        assertEquals("minecraft:gray_stained_glass_pane", materialShownAndClose(world.open(screen, world.viewer("Steve")), 26));
    }

    /** A list into a shape, with no id of its own, is named after the slots the shape resolves to. */
    @Test
    void aListIntoAShapeIsNamedAfterItsResolvedSlots() {
        Screen<?> screen = Screen.of(3);
        PagedList<Integer, ?> list = screen.<Integer>list(() -> Arrays.asList(1, 2))
                .into(Slots.all());

        assertEquals("list#" + Slots.all().resolve(screen.getGrid()).serialize(), list.getId());
    }

}
