package br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.contextualparsers.ArgParserContextualItemStack.Slot;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which piece of armor a declaration asks for is settled while the command is being mounted, so a
 * context nobody can read costs the boot a refusal instead of costing a player a broken command. Reading
 * the inventory itself needs a running server and is not exercised here.
 */
class ArgParserContextualItemStackTest {

    private static ArgParserContextualItemStack parserWith(String context) {
        ArgData argData = new ArgData().setName("item").setContext(context);
        return new ArgParserContextualItemStack(ArgInfo.contextual(ItemStack.class, argData));
    }

    @Test
    void noContextReadsTheHand() {
        assertNull(parserWith("").getSlot(), "an undeclared context is what asks for the held item");
    }

    @Test
    void aNamedSlotResolvesToThatSlot() {
        assertSame(Slot.HEAD, parserWith("HEAD").getSlot());
        assertSame(Slot.CHEST, parserWith("CHEST").getSlot());
        assertSame(Slot.HANDS, parserWith("HANDS").getSlot());
        assertSame(Slot.LEGS, parserWith("LEGS").getSlot());
    }

    @Test
    void aSlotNameIsReadWithoutCaringAboutCaseOrPadding() {
        assertSame(Slot.HEAD, parserWith("head").getSlot());
        assertSame(Slot.HEAD, parserWith("  Head  ").getSlot());
    }

    /** The vocabulary is this platform's own: what a Bukkit server calls a helmet is not a slot here. */
    @Test
    void anUnknownSlotIsRefusedAndTheMessageListsTheOnesThatExist() {
        ArgMountException error = assertThrows(ArgMountException.class, () -> parserWith("HELMET"));

        assertTrue(error.getMessage().contains("HELMET"), error.getMessage());
        for (Slot slot : Slot.values()) {
            assertTrue(error.getMessage().contains(slot.name()), "should offer " + slot + ": " + error.getMessage());
        }
    }

    @Test
    void aListOfSlotsIsRefusedSayingOnlyOneFits() {
        ArgMountException error = assertThrows(ArgMountException.class, () -> parserWith("HEAD|LEGS"));

        assertTrue(error.getMessage().contains("exactly one"), error.getMessage());
    }
}
