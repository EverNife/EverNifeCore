package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.commands.CoreCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventPriority;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.eventbus.ECListenerWatch;
import br.com.finalcraft.evernifecore.eventbus.ECSubscribeOptions;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.RecordingAudience;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /ecore events [type]}: the operator's answer to "who is listening to what" - the bus's own
 * subscriptions and watches, and what each native audience says about the type.
 */
class CoreCommandEventsTest {

    private static final String AUDIENCE = "events-command-recording";

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;
    private final List<ECEventSubscription<?>> subscriptions = new ArrayList<>();
    private ECListenerWatch watch;

    @AfterEach
    void teardown() {
        for (ECEventSubscription<?> subscription : subscriptions) {
            subscription.unsubscribe();
        }
        if (watch != null) watch.stop();
        ECEventBus.global().removeNativeAudience(AUDIENCE);
        if (harness != null) harness.close();
    }

    private TestCommandSender operator() {
        return Senders.console("operator").grant(PermissionNodes.EVERNIFECORE_COMMAND_EVENTS);
    }

    /** The messages without their colour codes - what the operator reads, not how it was painted. */
    private static List<String> plain(TestCommandSender sender) {
        List<String> plain = new ArrayList<>();
        for (String message : sender.getMessages()) {
            plain.add(message.replaceAll("§[0-9a-fk-orA-FK-OR]", ""));
        }
        return plain;
    }

    private static boolean anyContains(List<String> lines, String snippet) {
        return lines.stream().anyMatch(line -> line.contains(snippet));
    }

    @Test
    void withoutATypeItListsTheSubscribedTypesWithTheirCountsAndTheWatches() {
        harness = Commands.harness("EventsOverview", tempDir);
        FinalCMDPluginCommand command = harness.register(CoreCommand.class);
        subscriptions.add(ECEventBus.global().subscribe(CommandProbeEvent.class, event -> {
        }));
        subscriptions.add(ECEventBus.global().subscribe(CommandProbeEvent.class, ECSubscribeOptions.defaults().withPriority(ECEventPriority.LATE), event -> {
        }));
        watch = ECEventBus.global().watchListeners(() -> {
        }, null, CommandProbeEvent.class);
        TestCommandSender operator = operator();

        harness.dispatch(command, operator, "events");

        List<String> lines = plain(operator);
        assertTrue(anyContains(lines, "CommandProbeEvent 2 subscription(s)"), lines.toString());
        String hover = operator.hoverTextOfMessageContaining("CommandProbeEvent");
        assertNotNull(hover, "the full name and the subscriptions ride on the hover: " + operator.getMessages());
        assertTrue(hover.contains(CommandProbeEvent.class.getName()), hover);
        assertTrue(hover.contains("[LATE]"), hover);
        assertTrue(anyContains(lines, "watch [CommandProbeEvent] present"), lines.toString());
    }

    @Test
    void withATypeItPrintsTheDeliveryOrderAndWhatEachAudienceSays() {
        harness = Commands.harness("EventsDetail", tempDir);
        FinalCMDPluginCommand command = harness.register(CoreCommand.class);
        RecordingAudience audience = new RecordingAudience(AUDIENCE);
        audience.setHasListeners(true);
        ECEventBus.global().addNativeAudience(audience);
        ECEventSubscription<PlatformProbeEvent> late = ECEventBus.global().subscribe(PlatformProbeEvent.class,
                ECSubscribeOptions.defaults().withPriority(ECEventPriority.LATE), event -> {
                });
        ECEventSubscription<PlatformProbeEvent> first = ECEventBus.global().subscribe(PlatformProbeEvent.class,
                ECSubscribeOptions.defaults().withPriority(ECEventPriority.FIRST), event -> {
                });
        subscriptions.add(late);
        subscriptions.add(first);
        TestCommandSender operator = operator();

        //the simple name is enough while one subscribed type answers to it
        harness.dispatch(command, operator, "events PlatformProbeEvent");

        List<String> lines = plain(operator);
        assertTrue(anyContains(lines, PlatformProbeEvent.class.getName()), lines.toString());
        int firstAt = indexOfMessageContaining(lines, first.toString());
        int lateAt = indexOfMessageContaining(lines, late.toString());
        assertTrue(firstAt >= 0 && lateAt >= 0, "both subscriptions are printed by their view: " + lines);
        assertTrue(firstAt < lateAt, "in delivery order, FIRST before LATE: " + lines);
        assertTrue(anyContains(lines, AUDIENCE + ": listening"), lines.toString());
    }

    @Test
    void aTypeNoPlatformCanSeeIsNotAskedOfTheAudiences() {
        harness = Commands.harness("EventsBusOnly", tempDir);
        FinalCMDPluginCommand command = harness.register(CoreCommand.class);
        RecordingAudience audience = new RecordingAudience(AUDIENCE);
        ECEventBus.global().addNativeAudience(audience);
        subscriptions.add(ECEventBus.global().subscribe(CommandProbeEvent.class, event -> {
        }));
        TestCommandSender operator = operator();

        harness.dispatch(command, operator, "events " + CommandProbeEvent.class.getName());

        List<String> lines = plain(operator);
        assertTrue(anyContains(lines, "implements IECEvent only"), lines.toString());
        assertFalse(anyContains(lines, AUDIENCE + ":"), "no audience line for a type that never reaches one: " + lines);
        assertEquals(0, audience.getGateChecks(), "and none was asked");
    }

    @Test
    void anUnknownTypeIsRefusedWithTheNamesItCouldHaveBeen() {
        harness = Commands.harness("EventsUnknown", tempDir);
        FinalCMDPluginCommand command = harness.register(CoreCommand.class);
        subscriptions.add(ECEventBus.global().subscribe(CommandProbeEvent.class, event -> {
        }));
        TestCommandSender operator = operator();

        harness.dispatch(command, operator, "events NoSuchEvent");

        List<String> lines = plain(operator);
        assertTrue(anyContains(lines, "No event type called NoSuchEvent"), lines.toString());
        assertTrue(anyContains(lines, "CommandProbeEvent"), "the known types are offered: " + lines);
    }

    private static int indexOfMessageContaining(List<String> messages, String snippet) {
        for (int index = 0; index < messages.size(); index++) {
            if (messages.get(index).contains(snippet)) {
                return index;
            }
        }
        return -1;
    }

    /** Bus-only: implements IECEvent and nothing else, so no audience ever sees it. */
    static class CommandProbeEvent implements IECEvent {
    }

    /** Platform-visible, so the audiences are asked about it. */
    static class PlatformProbeEvent extends ECEvent implements IECEvent {
    }

}
