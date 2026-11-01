package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.screen.prompt.PromptSpec;
import br.com.finalcraft.evernifecore.testing.RecordingSurface;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** A label and a bar need no layout: on raw slots they draw, as their own kind, with the bar carrying its ratio. */
    @Test
    void aLabelAndAProgressBarOnRawSlotsDrawAsTheirKind() {
        MutableState<Double> ratio = State.of(0.25D);
        Screen<?> screen = Screen.of(3);
        screen.label(Slots.of(4), Icon.of(ItemSpec.of("minecraft:paper", 1)));
        screen.progress(Slots.of(5), Icon.of(ItemSpec.of("minecraft:experience_bottle", 1)), ratio);

        ScreenView view = world.open(screen, world.viewer("Steve"));
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        assertEquals(ElementKind.LABEL, surface.getLastPatch(4).getKind());
        assertEquals(ElementKind.PROGRESS, surface.getLastPatch(5).getKind());
        assertEquals(0.25D, surface.getLastPatch(5).getProgress(), 1e-9);

        ratio.set(0.75D);
        world.advanceTicks(1);
        assertEquals(0.75D, surface.getLastPatch(5).getProgress(), 1e-9, "the bar follows its state");
        assertEquals("minecraft:paper", materialShownAndClose(view, 4));
    }

    /** An input on a raw slot asks through a prompt, like the layout form, and hands the answer back. */
    @Test
    void anInputOnARawSlotAsksThroughAPrompt() {
        List<String> typed = new ArrayList<>();
        Screen<?> screen = Screen.of(3);
        screen.input(Slots.of(0), Icon.of(ItemSpec.of("minecraft:name_tag", 1)))
                .onInput(PromptSpec.of("Name?"), (view, value) -> typed.add(value));

        ScreenView view = world.open(screen, world.viewer("Steve"));
        world.click(view, 0, ClickGesture.PRIMARY, ClickKind.NOTHING);
        world.advanceTicks(1);
        assertTrue(view.isSuspended(), "the screen is set aside while the prompt asks");

        world.answerPrompt("Notch");
        world.advanceTicks(2);

        assertEquals(Collections.singletonList("Notch"), typed);
        assertFalse(view.isSuspended(), "and comes back with the answer");
        assertEquals("minecraft:name_tag", materialShownAndClose(view, 0));
    }

    /** Arrows on raw slots draw the icon handed to {@code arrows}; the side left {@code null} keeps the framework's. */
    @Test
    void rawSlotPageArrowsDrawTheIconsGivenToArrows() {
        Screen<?> screen = Screen.of(3);
        screen.<Integer>list(() -> Arrays.asList(1, 2, 3))
                .into(Slots.of(0, 1))
                .render((entry, icon) -> icon.setItem(ItemSpec.of("minecraft:paper", entry)))
                .pagedBy(Slots.of(18), Slots.of(26))
                .arrows(null, Icon.of(ItemSpec.of("minecraft:arrow", 1)));

        ScreenView view = world.open(screen, world.viewer("Steve"));
        RenderedIcon previous = world.getRenderer().surfaceOf(view).getSlot(18);
        assertEquals(DefaultIcons.previousPage().getItem().getMaterialId(), previous == null ? null : previous.getItem().getMaterialId(),
                "a null side keeps the framework's arrow");
        assertEquals("minecraft:arrow", materialShownAndClose(view, 26));
    }

}
