package br.com.finalcraft.evernifecore.logic.delivery.command;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.CoreCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.delivery.Deliveries;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryOrigin;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryPolicy;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Locales;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the deliveries commands say comes from the lang file: an edited text is what the sender reads, in every outcome. */
class DeliveriesCommandTextsTest {

    private static final String GIFT = "fakeshop:gift";

    @FinalCMD(aliases = "shop")
    public static class Shop_Cmd {
        @FinalCMD.Node(subcmd = "deliveries", permission = "shop.deliveries")
        final DeliveriesCommandNode deliveries = new DeliveriesCommandNode();
    }

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;
    private LogicWorld shop;
    private FinalCMDPluginCommand node;
    private FinalCMDPluginCommand ecore;
    private TestFPlayerSender alex;
    private TestCommandSender admin;

    @BeforeEach
    void open() {
        harness = Commands.harness("DeliveriesCmd", tempDir.resolve("cmd"));
        shop = LogicWorld.open("FakeShop", tempDir.resolve("shop"));
        FCLocaleManager.loadLocale(harness.ecPluginData, DeliveriesCommandNode.class, CoreDeliveriesCommands.class);
        node = harness.register(new Shop_Cmd());
        ecore = harness.register(CoreCommand.class);
        alex = shop.player("Alex");
        alex.grant("shop.deliveries");
        admin = Senders.console("admin").grant(PermissionNodes.EVERNIFECORE_COMMAND_DELIVERIES);
        Effects.registry().register(shop.getOwner(), TypeDescriptor.<Effect>of(GIFT)
                .describe("Hands the player a named gift.")
                .param(ParamSpec.required("name", ParamSpec.Kind.STRING, "The gift."))
                .factory(params -> ctx -> { })
                .build());
    }

    @AfterEach
    void close() {
        Effects.registry().unregister(shop.getOwner(), GIFT);
        shop.close();
        harness.close();
    }

    private void enqueue(String reason) throws IOException {
        shop.deliveries().enqueue(alex.getUniqueId(), DeliveryOrigin.of("FakeShop", "test", reason), DeliveryPolicy.KEEP_SELF_CONTAINED, null, null,
                Collections.singletonList(new ObjectMapper().readTree("{\"type\": \"" + GIFT + "\", \"name\": \"sword\"}"))).join();
    }

    /** The messages arrive when the data does, on whatever thread answered it. */
    private static void awaitMessage(String expected, Supplier<List<String>> messages) {
        long until = System.currentTimeMillis() + 5000;
        while (!messages.get().contains(expected) && System.currentTimeMillis() < until) {
            Thread.yield();
        }
        assertTrue(messages.get().contains(expected), "expected <" + expected + "> in " + messages.get());
    }

    /** Runs {@code line} as {@code sender} with the message {@code key} edited to {@code text} and waits for {@code expected}. */
    private void assertReads(String key, String text, FinalCMDPluginCommand command, FCommandSender sender, Supplier<List<String>> messages,
                             String line, String expected) {
        try (Locales.Retext edited = Locales.retext(harness.ecPluginData, key, harness.ecPluginData.getPluginLanguage(), text)) {
            harness.dispatch(command, sender, line);
            awaitMessage(expected, messages);
        }
    }

    private void assertAlexReads(String key, String text, String line, String expected) {
        assertReads(key, text, node, alex, alex::getMessages, line, expected);
    }

    private void assertAdminReads(String key, String text, String line, String expected) {
        assertReads(key, text, ecore, admin, admin::getMessages, line, expected);
    }

    @Test
    void theListOfThePlayerReadsItsEditedHeaderAndLine() throws IOException {
        enqueue("refund");

        assertAlexReads("DeliveriesCommandNode.LIST_HEADER", "§bWaiting: ${count}", "deliveries list", "§bWaiting: 1");
        alex.clearMessages();
        assertAlexReads("DeliveriesCommandNode.LINE_WITH_REASON_MAY_WAIT", "§b${plugin} for ${reason}", "deliveries list", "§bfakeshop for refund");
    }

    @Test
    void anEmptyListReadsItsEditedNotice() {
        shop.deliveries();

        assertAlexReads("DeliveriesCommandNode.NONE_PENDING", "§bNothing waits.", "deliveries list", "§bNothing waits.");
    }

    @Test
    void aFailedReadReadsItsEditedErrorWithTheReason() {
        //no PlayerData layer booted: the read fails, and the command has to say so
        assertAlexReads("DeliveriesCommandNode.READ_FAILED", "§bBroken", "deliveries list", "§bBroken");
    }

    @Test
    void anIdNobodyHasReadsItsEditedError() throws IOException {
        enqueue("refund");

        assertAlexReads("DeliveriesCommandNode.NO_MATCH", "§bNone like ${id}", "deliveries claim zzz", "§bNone like zzz");
    }

    @Test
    void aDeliveryIsClaimedByTheFirstCharactersOfItsId() throws IOException {
        enqueue("refund");
        String id = Deliveries.get().listOf(alex.getUniqueId()).join().get(0).getId().toString().substring(0, 8);

        assertAlexReads("DeliveriesCommandNode.CLAIMED", "§bGot ${delivered}", "deliveries claim " + id, "§bGot 1");
    }

    @Test
    void aClaimReadsItsEditedResult() throws IOException {
        enqueue("refund");

        assertAlexReads("DeliveriesCommandNode.CLAIMED", "§bGot ${delivered}, ${kept} left", "deliveries claim all", "§bGot 1, 0 left");
    }

    @Test
    void theAdminListReadsItsEditedHeaderAndLine() throws IOException {
        enqueue("refund");

        assertAdminReads("CoreDeliveriesCommands.LIST_HEADER", "§b${plugin}: ${players}", "deliveries list fakeshop", "§bfakeshop: 1");
        admin.clearMessages();
        assertAdminReads("CoreDeliveriesCommands.LIST_LINE", "§b${count} here", "deliveries list fakeshop", "§b1 here");
    }

    @Test
    void anAdminListOfAPluginWithNothingReadsItsEditedRefusal() {
        shop.deliveries();

        assertAdminReads("CoreDeliveriesCommands.NONE_FROM", "§bNothing of ${plugin}", "deliveries list ghost", "§bNothing of ghost");
    }

    @Test
    void anAdminListWhoseReadFailedReadsItsEditedErrorAndNotAnEmptyList() {
        //no PlayerData layer booted: the read fails, and "nobody has any" would be a lie
        assertAdminReads("CoreDeliveriesCommands.READ_FAILED", "§bBroken", "deliveries list fakeshop", "§bBroken");
    }

    @Test
    void aPurgeWhoseReadFailedReadsItsEditedErrorAndNotAPurgeOfNothing() {
        assertAdminReads("CoreDeliveriesCommands.PURGE_FAILED", "§bHalted", "deliveries purge fakeshop", "§bHalted");
    }

    @Test
    void aPurgeReadsItsEditedResult() throws IOException {
        enqueue("refund");

        assertAdminReads("CoreDeliveriesCommands.PURGED", "§bDropped ${count} of ${plugin}", "deliveries purge fakeshop", "§bDropped 1 of fakeshop");
        assertTrue(Deliveries.get().listOf(alex.getUniqueId()).join().isEmpty());
    }
}
