package br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.contextualparsers.ArgParserContextualItemStack.Slot;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.bukkit.inventory.ItemStack;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which piece of equipment a declaration asks for is settled while the command is being mounted, so a
 * context nobody can read costs the boot a refusal instead of costing a player a broken command. Reading
 * the inventory itself needs a running server and is not exercised here.
 */
class ArgParserContextualItemStackTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private FinalCmdTestHarness newHarness() {
        harness = new FinalCmdTestHarness("EquipmentContext", tempDir);
        return harness;
    }

    private static ArgParserContextualItemStack parserWith(String context) {
        ArgData argData = new ArgData().setName("item").setContext(context);
        return new ArgParserContextualItemStack(ArgInfo.contextual(ItemStack.class, argData));
    }

    // ------------------------------------------------------------------
    // No context at all: the hand, exactly as before there was any other option
    // ------------------------------------------------------------------

    @Test
    void noContextReadsTheHand() {
        assertNull(parserWith("").getSlot(), "an undeclared context is what asks for the held item");
    }

    // ------------------------------------------------------------------
    // A named slot
    // ------------------------------------------------------------------

    @Test
    void aNamedSlotResolvesToThatSlot() {
        assertSame(Slot.HELMET, parserWith("HELMET").getSlot());
        assertSame(Slot.CHESTPLATE, parserWith("CHESTPLATE").getSlot());
        assertSame(Slot.LEGGINGS, parserWith("LEGGINGS").getSlot());
        assertSame(Slot.BOOTS, parserWith("BOOTS").getSlot());
        assertSame(Slot.OFF_HAND, parserWith("OFF_HAND").getSlot());
    }

    @Test
    void aSlotNameIsReadWithoutCaringAboutCaseOrPadding() {
        assertSame(Slot.HELMET, parserWith("helmet").getSlot());
        assertSame(Slot.HELMET, parserWith("  Helmet  ").getSlot());
    }

    // ------------------------------------------------------------------
    // A context no slot answers to
    // ------------------------------------------------------------------

    @Test
    void anUnknownSlotIsRefusedAndTheMessageListsTheOnesThatExist() {
        ArgMountException error = assertThrows(ArgMountException.class, () -> parserWith("PANTS"));

        assertTrue(error.getMessage().contains("PANTS"), error.getMessage());
        for (Slot slot : Slot.values()) {
            assertTrue(error.getMessage().contains(slot.name()), "should offer " + slot + ": " + error.getMessage());
        }
    }

    @Test
    void aListOfSlotsIsRefusedSayingOnlyOneFits() {
        ArgMountException error = assertThrows(ArgMountException.class, () -> parserWith("HELMET|BOOTS"));

        assertTrue(error.getMessage().contains("exactly one"), error.getMessage());
    }

    // ------------------------------------------------------------------
    // Where the refusal lands: the boot, not a dispatch
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "equipmentslotgoodcmd")
    public static class GoodContextCmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender,
                        @Arg.Contextual(value = "helmet", context = "HELMET", parser = ArgParserContextualItemStack.class) ItemStack helmet) {
        }
    }

    @FinalCMD(aliases = "equipmentslotbadcmd")
    public static class BadContextCmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender,
                        @Arg.Contextual(value = "helmet", context = "PANTS", parser = ArgParserContextualItemStack.class) ItemStack helmet) {
        }
    }

    @Test
    void aSlotThatExistsMountsCleanly() {
        FinalCMDPluginCommand command = newHarness().register(new GoodContextCmd());

        assertNotNull(command, "HELMET is a slot this platform has");
    }

    @Test
    void anUnknownSlotIsRefusedAtRegistration() {
        ArgMountException error = newHarness().registerExpectingError(new BadContextCmd());

        assertTrue(error.getMessage().contains("PANTS"), error.getMessage());
        assertTrue(error.getMessage().contains(Slot.HELMET.name()), error.getMessage());
        assertTrue(error.getMessage().contains(BadContextCmd.class.getName()), "the refusal names the command it is about: " + error.getMessage());
    }
}
