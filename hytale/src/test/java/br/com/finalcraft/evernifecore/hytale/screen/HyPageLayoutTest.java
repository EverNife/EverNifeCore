package br.com.finalcraft.evernifecore.hytale.screen;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.screen.ClickGesture;
import br.com.finalcraft.evernifecore.screen.ElementKind;
import br.com.finalcraft.evernifecore.screen.ElementPatch;
import br.com.finalcraft.evernifecore.screen.Grid;
import br.com.finalcraft.evernifecore.screen.RenderedIcon;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommandType;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBinding;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the page sends for each thing a screen can show, read off the commands and bindings themselves. */
class HyPageLayoutTest {

    // -----------------------------------------------------------------------------------------------------------------
    //  The frame
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void theScreenStartsFromTheCoreLayoutWithItsTitleWithoutColourCodes() {
        Drawn drawn = draw("§aShop §lNow", Grid.rows(1));
        assertEquals(CustomUICommandType.Append, drawn.commands.get(0).type);
        assertEquals(HyPageLayout.SCREEN_LAYOUT, drawn.commands.get(0).text);
        assertTrue(drawn.value("#ScreenTitle.Text").contains("Shop Now"), drawn.value("#ScreenTitle.Text"));
    }

    @Test
    void oneCellIsAppendedToTheGridPerSlot() {
        Drawn drawn = draw("T", Grid.rows(3));
        long cells = drawn.commands.stream()
                .filter(c -> c.type == CustomUICommandType.Append && "#Grid".equals(c.selector) && HyPageLayout.CELL_LAYOUT.equals(c.text))
                .count();
        assertEquals(27, cells);
    }

    @Test
    void theGridIsAsWideAsItsColumnsSoCellsWrapAtTheGridWidth() {
        Drawn chest = draw("T", Grid.rows(2));
        Drawn dispenser = draw("T", Grid.of(3, 9));
        assertTrue(chest.value("#Grid.Anchor").contains(String.valueOf(9 * HyPageLayout.CELL_SIZE)), chest.value("#Grid.Anchor"));
        assertTrue(dispenser.value("#Grid.Anchor").contains(String.valueOf(3 * HyPageLayout.CELL_SIZE)), dispenser.value("#Grid.Anchor"));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  A cell
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aHytaleItemIsDrawnByItsAssetId() {
        Drawn drawn = cell(patch(icon("hytale:Ingredient_Bar_Iron", 1)));
        assertTrue(drawn.value("#Grid[0] #Icon.ItemId").contains("Ingredient_Bar_Iron"));
        assertTrue(drawn.value("#Grid[0] #Icon.Visible").contains("true"));
    }

    @Test
    void anItemOfAnotherGameFallsBackToTheFirstHytaleFallback() {
        RenderedIcon icon = new RenderedIcon(ItemSpec.of("minecraft:diamond", 1),
                Arrays.asList(ItemSpec.of("minecraft:emerald", 1), ItemSpec.of("hytale:Rock_Gem_Diamond", 1)), Collections.emptyList());
        assertTrue(cell(patch(icon)).value("#Grid[0] #Icon.ItemId").contains("Rock_Gem_Diamond"));
    }

    @Test
    void anItemNoHytaleAssetMatchesIsTheNeutralBlockNeverTheSelectionTool() {
        String drawn = cell(patch(icon("minecraft:diamond_sword", 1))).value("#Grid[0] #Icon.ItemId");
        assertTrue(drawn.contains(HyItemIcons.NEUTRAL_ITEM), drawn);
        assertFalse(drawn.contains("Tool_Hoe"), drawn);
    }

    @Test
    void theAmountShowsOnlyAboveOne() {
        assertTrue(cell(patch(icon("hytale:Rock_Stone", 12))).value("#Grid[0] #Amount.Text").contains("12"));
        assertFalse(cell(patch(icon("hytale:Rock_Stone", 1))).value("#Grid[0] #Amount.Text").contains("1"));
    }

    @Test
    void nameAndLoreAreTheTooltipOneLineEachWithoutColourCodes() {
        ItemSpec item = ItemSpec.of("hytale:Rock_Stone", 1).withText("§6Gold Star", Arrays.asList("§7first line", "second line"));
        String tooltip = cell(patch(new RenderedIcon(item, Collections.emptyList(), Collections.emptyList())))
                .value("#Grid[0] #Button.TooltipText");
        assertTrue(tooltip.contains("Gold Star"), tooltip);
        assertTrue(tooltip.contains("first line"), tooltip);
        assertTrue(tooltip.contains("second line"), tooltip);
        assertFalse(tooltip.contains("§"), tooltip);
    }

    @Test
    void aClosedChoiceIsADisabledButtonWhoseTooltipSaysWhy() {
        ElementPatch closed = new ElementPatch("buy", ElementKind.CHOICE, 0, icon("hytale:Rock_Stone", 1), -1, false, "§cNot enough coins");
        Drawn drawn = cell(closed);
        assertTrue(drawn.value("#Grid[0] #Button.Disabled").contains("true"));
        assertTrue(drawn.value("#Grid[0] #Button.TooltipText").contains("Not enough coins"));
    }

    @Test
    void anEmptySlotIsAnInertCellWithNoIcon() {
        Drawn drawn = cell(null);
        assertTrue(drawn.value("#Grid[0] #Icon.Visible").contains("false"));
        assertTrue(drawn.value("#Grid[0] #Button.Disabled").contains("true"));
        assertNull(drawn.find("#Grid[0] #Icon.ItemId"), "an empty cell names no item");
    }

    @Test
    void aPatchTouchesOnlyTheCellItNames() {
        UICommandBuilder ui = new UICommandBuilder();
        HyPageLayout.cell(ui, 5, patch(icon("hytale:Rock_Stone", 1)));
        for (CustomUICommand command : ui.getCommands()) {
            assertTrue(command.selector.startsWith("#Grid[5] "), command.selector);
            assertEquals(CustomUICommandType.Set, command.type, "a patch only sets, never appends or clears");
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Clicks
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void everyCellButtonHasTheThreeClickBindingsCarryingItsSlotAndKind() {
        Drawn drawn = draw("T", Grid.rows(1));
        List<CustomUIEventBinding> onCell4 = new ArrayList<>();
        for (CustomUIEventBinding binding : drawn.bindings) {
            if ("#Grid[4] #Button".equals(binding.selector)) {
                onCell4.add(binding);
            }
        }
        assertEquals(3, onCell4.size(), "primary, secondary and double");
        List<CustomUIEventBindingType> types = new ArrayList<>();
        for (CustomUIEventBinding binding : onCell4) {
            types.add(binding.type);
            assertTrue(binding.data.contains("\"Slot\"") && binding.data.contains("\"4\""), binding.data);
            assertTrue(binding.data.contains(binding.type.name()), binding.data);
        }
        assertEquals(Arrays.asList(CustomUIEventBindingType.Activating, CustomUIEventBindingType.RightClicking,
                CustomUIEventBindingType.DoubleClicking), types);
        assertEquals(9 * 3, drawn.bindings.size());
    }

    @Test
    void theThreeBindingsAreThePrimarySecondaryAndDoubleGesturesAndNothingElseIsAGesture() {
        assertEquals(ClickGesture.PRIMARY, HyPageLayout.gestureOf("Activating"));
        assertEquals(ClickGesture.SECONDARY, HyPageLayout.gestureOf("RightClicking"));
        assertEquals(ClickGesture.DOUBLE, HyPageLayout.gestureOf("DoubleClicking"));
        assertEquals(ClickGesture.UNKNOWN, HyPageLayout.gestureOf("KeyDown"));
        assertEquals(ClickGesture.UNKNOWN, HyPageLayout.gestureOf(null));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The question page
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void okSendsTheFieldTextAsValueAndCancelSendsOnlyItsKind() {
        UICommandBuilder ui = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        HyPageLayout.drawPrompt(ui, events, "§eHow many?");
        CustomUIEventBinding ok = null;
        CustomUIEventBinding cancel = null;
        for (CustomUIEventBinding binding : events.getEvents()) {
            if ("#Ok".equals(binding.selector)) {
                ok = binding;
            } else if ("#Cancel".equals(binding.selector)) {
                cancel = binding;
            }
        }
        assertNotNull(ok);
        assertNotNull(cancel);
        assertEquals(CustomUIEventBindingType.Activating, ok.type);
        assertTrue(ok.data.contains("\"@Value\"") && ok.data.contains("#Input.Value"), ok.data);
        assertTrue(ok.data.contains(HyPageLayout.SUBMIT), ok.data);
        assertFalse(cancel.data.contains("@Value"), cancel.data);
        Drawn drawn = new Drawn(ui, events);
        assertEquals(HyPageLayout.PROMPT_LAYOUT, drawn.commands.get(0).text);
        assertTrue(drawn.value("#Question.Text").contains("How many?"));
    }

    @Test
    void aRefusedAnswerEmptiesTheFieldAndShowsWhy() {
        UICommandBuilder ui = new UICommandBuilder();
        HyPageLayout.promptError(ui, "§cThat is not a number");
        Drawn drawn = new Drawn(ui, new UIEventBuilder());
        assertTrue(drawn.value("#Error.Text").contains("That is not a number"));
        assertTrue(drawn.value("#Input.Value").contains("\"\""), drawn.value("#Input.Value"));
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static RenderedIcon icon(String id, int amount) {
        return new RenderedIcon(ItemSpec.of(id, amount), Collections.emptyList(), Collections.emptyList());
    }

    private static ElementPatch patch(RenderedIcon icon) {
        return new ElementPatch("probe", ElementKind.BUTTON, 0, icon, -1, true, null);
    }

    private static Drawn draw(String title, Grid grid) {
        UICommandBuilder ui = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        HyPageLayout.draw(ui, events, title, grid, new ElementPatch[grid.getSize()]);
        return new Drawn(ui, events);
    }

    private static Drawn cell(ElementPatch patch) {
        UICommandBuilder ui = new UICommandBuilder();
        HyPageLayout.cell(ui, 0, patch);
        return new Drawn(ui, new UIEventBuilder());
    }

    private static final class Drawn {

        private final List<CustomUICommand> commands;
        private final List<CustomUIEventBinding> bindings;

        private Drawn(UICommandBuilder ui, UIEventBuilder events) {
            this.commands = Arrays.asList(ui.getCommands());
            this.bindings = Arrays.asList(events.getEvents());
        }

        private CustomUICommand find(String selector) {
            for (CustomUICommand command : commands) {
                if (command.type == CustomUICommandType.Set && selector.equals(command.selector)) {
                    return command;
                }
            }
            return null;
        }

        private String value(String selector) {
            CustomUICommand command = find(selector);
            if (command == null) {
                throw new AssertionError("No set(" + selector + ") among " + commands.size() + " commands");
            }
            return command.data;
        }

    }

}
