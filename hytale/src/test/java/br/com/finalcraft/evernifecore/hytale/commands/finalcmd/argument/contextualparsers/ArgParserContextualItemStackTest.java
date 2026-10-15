package br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.contextualparsers.ArgParserContextualItemStack.Slot;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ResolvedArguments;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which piece of armor a declaration asks for is settled while the command is being mounted, so a
 * context nobody can read costs the boot a refusal instead of costing a player a broken command. Reading
 * the inventory itself needs a running server and is not exercised here.
 */
@ECoreTest
class ArgParserContextualItemStackTest {

    @TempDirNobodyCleans
    Path tempDir;

    /** A player whose entity already left every world: the PlayerRef is still there, its reference is not. */
    static HytaleFPlayer departed(String name) {
        return HytaleFPlayer.of(new PlayerRef(null, UUID.randomUUID(), name, "en_us", null, null));
    }

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

    /** The player left between sending the command and its parse: refused as not online, never a NullPointerException. */
    @Test
    void aPlayerWhoLeftIsRefusedAsNotOnline() {
        try (ECoreTestWorld ignored = Platforms.lenient().install()) {
            FCLocaleManager.loadLocale(Plugins.fakePluginData("ItemStackParserOwner", tempDir.toFile()), FCMessageUtil.class);
            try {
                for (String context : new String[]{"", "HEAD"}) {
                    ArgParserContextualItemStack parser = parserWith(context);
                    ContextualParseCall call = new ContextualParseCall(departed("Gone"), parser.getArgInfo(), null, ResolvedArguments.none(),
                            new MultiArgumentos(new String[0]), null, null);

                    ParseResult<ItemStack> result = assertDoesNotThrow(() -> parser.parse(call));

                    assertSame(ParseResult.Kind.DENIED, result.getKind(), "context '" + context + "'");
                }
                assertNull(ArgParserContextualItemStack.itemInHand(departed("Gone")), "a hand nobody is in holds nothing");
            } finally {
                Plugins.forget("ItemStackParserOwner");
            }
        }
    }
}
