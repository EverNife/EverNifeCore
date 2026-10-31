package br.com.finalcraft.evernifecore.dialogue;

import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.event.InteractNpcActionEvent;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.dialogue.event.DialogueChoiceEvent;
import br.com.finalcraft.evernifecore.dialogue.event.DialogueEndedEvent;
import br.com.finalcraft.evernifecore.dialogue.event.DialogueStartedEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.logic.CustomVariables;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.testing.Actors;
import br.com.finalcraft.evernifecore.testing.DialogueTrees;
import br.com.finalcraft.evernifecore.testing.ECEventConformance;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One session per player: a pick pays and moves, a denied one changes nothing, and it ends once, for the first reason. */
class DialogueSessionTest {

    @TempDirNobodyCleans
    Path tempDir;

    private DialogueFixture world;

    @BeforeEach
    void open() {
        world = DialogueFixture.open(tempDir);
    }

    @AfterEach
    void close() {
        world.close();
    }

    /** start --deliver (pays the purse)--> thanks; start --later--> later --bye--> end. */
    private Dialogue tomas(Gate deliverGate) {
        return world.register(Dialogue.builder(world.owner(), "tomas")
                .node(DialogueNode.builder("start").text("Did you bring the logs?")
                        .option(DialogueOption.builder("deliver").text("Here they are.").gate(deliverGate).goTo("thanks"))
                        .option(DialogueOption.builder("later").text("Not yet.").goTo("later")))
                .node(DialogueNode.builder("thanks").text("Thank you!").option(DialogueOption.builder("bye").text("Bye.").end()))
                .node(DialogueNode.builder("later").text("Come back.").option(DialogueOption.builder("bye").text("Bye.").end()))
                .build());
    }

    private void interactWithNpcAs(FPlayer player) {
        NpcRef npc = NpcRef.of("citizens", "12");
        ECEventBus.global().post(new InteractNpcActionEvent.Post(new EntityContext(EventSource.INTERACT_NPC, WorldLocPos.of(0, 64, 0, "world"),
                Actors.player(player), null, Actors.npc("citizens:npc", npc), null)));
    }

    @Test
    void aPassingGateIsPaidAndThePickMovesToItsGoto() {
        DialogueFixture.Purse purse = new DialogueFixture.Purse(10, 4);
        DialogueSession session = world.start(tomas(Gate.of(Collections.emptyList(), Collections.singletonList(purse))));

        DialogueSession.ChoiceResult picked = session.choose("deliver");

        assertTrue(picked.isChosen(), picked.toString());
        assertEquals(6, purse.coins);
        assertEquals("thanks", session.getNode().getId());
        assertTrue(session.isOpen());
    }

    @Test
    void aFailingGateChargesNothingKeepsTheNodeAndAnswersEveryFailure() {
        DialogueFixture.Purse purse = new DialogueFixture.Purse(1, 4);
        DialogueFixture.Toggle reputation = new DialogueFixture.Toggle(false, "Tomás does not trust you yet");
        DialogueSession session = world.start(tomas(Gate.of(Collections.singletonList(reputation), Collections.singletonList(purse))));

        DialogueSession.ChoiceResult picked = session.choose("deliver");

        assertEquals(DialogueSession.ChoiceResult.Outcome.DENIED, picked.getOutcome());
        assertEquals(2, picked.getFailures().size(), picked.toString());
        assertEquals(1, purse.coins);
        assertEquals(0, purse.takes);
        assertEquals("start", session.getNode().getId());
    }

    @Test
    void rememberWritesThePickedOptionIdIntoTheCustomVariable() {
        CustomVariables.CustomVariable<String> answer = world.colonies.customVariables().define("tomas-answer", String.class, "");
        Dialogue tomas = world.register(Dialogue.builder(world.owner(), "tomas")
                .node(DialogueNode.builder("start").text("Logs?").remember("fakecolonies:tomas-answer")
                        .option(DialogueOption.builder("deliver").text("Yes").end())
                        .option(DialogueOption.builder("later").text("No").end()))
                .build());

        world.start(tomas).choose("later");

        assertEquals("later", answer.read(world.colonies.context(world.alex).build()));
    }

    @Test
    void withNoAnswerTheInjectedClockEndsItWithTimeout() {
        Dialogue quick = world.register(Dialogue.builder(world.owner(), "quick").timeout(Duration.ofSeconds(30))
                .node(DialogueNode.builder("start").text("Quick!").option(DialogueOption.builder("ok").text("Ok").goTo("start2")))
                .node(DialogueNode.builder("start2").text("Again!").option(DialogueOption.builder("ok").text("Ok").end()))
                .build());
        DialogueSession session = world.start(quick);

        world.colonies.advance(Duration.ofSeconds(20));
        world.colonies.runDue();
        session.choose("ok"); //re-arms: 30 s from now, not from the start
        world.colonies.advance(Duration.ofSeconds(20));
        world.colonies.runDue();
        assertTrue(session.isOpen(), "a pick re-arms the timeout");

        world.colonies.advance(Duration.ofSeconds(11));
        world.colonies.runDue();
        assertEquals(DialogueEndCause.TIMEOUT, session.getEndCause());
    }

    @Test
    void failWhenEndsTheSessionOnlyWhenTheConditionsSourceFires() {
        DialogueFixture.Toggle near = new DialogueFixture.Toggle(true, "Walk back to Tomás", EventSource.INTERACT_NPC);
        Dialogue tomas = world.register(Dialogue.builder(world.owner(), "tomas").failWhen(near)
                .node(DialogueNode.builder("start").text("Logs?").option(DialogueOption.builder("bye").text("Bye").end()))
                .build());
        DialogueSession session = world.start(tomas);

        near.passes = false;
        assertTrue(session.isOpen(), "nothing fired: nothing is asked");
        interactWithNpcAs(world.screens.viewer("Steve"));
        assertTrue(session.isOpen(), "another player's action is not this session's source");

        interactWithNpcAs(world.alex);
        assertEquals(DialogueEndCause.FAIL_WHEN, session.getEndCause());
    }

    @Test
    void aSecondStartEndsTheFirstAsReplaced() {
        Dialogue first = world.register(DialogueTrees.linear(world.owner(), "first", "One", "Two"));
        Dialogue second = world.register(DialogueTrees.linear(world.owner(), "second", "Three", "Four"));
        DialogueSession before = world.start(first);

        DialogueSession after = world.start(second);

        assertEquals(DialogueEndCause.REPLACED, before.getEndCause());
        assertTrue(after.isOpen());
        assertSame(after, Dialogues.sessionOf(world.alex));
    }

    @Test
    void aQuitEndsItAsPlayerLeft() {
        DialogueSession session = world.start(world.register(DialogueTrees.linear(world.owner(), "talk", "One", "Two")));

        ECEventBus.global().post(new ECPlayerQuitEvent(new PlayerData(world.alex.getUniqueId(), world.alex.getName())));

        assertEquals(DialogueEndCause.PLAYER_LEFT, session.getEndCause());
        assertNull(Dialogues.sessionOf(world.alex));
    }

    @Test
    void endingTwicePostsOneEndedEventWithTheFirstCause() {
        List<DialogueEndedEvent> ended = world.screens.recorded(DialogueEndedEvent.class);
        DialogueSession session = world.start(world.register(DialogueTrees.linear(world.owner(), "talk", "One", "Two")));

        session.end(DialogueEndCause.API);
        session.end(DialogueEndCause.TIMEOUT);

        assertFalse(ended.isEmpty(), "the end was posted");
        assertEquals(1, ended.size(), ended.toString());
        assertEquals(DialogueEndCause.API, ended.get(0).getCause());
    }

    @Test
    void aCancelledChoiceEventChargesNothingAndLeavesTheNode() {
        DialogueFixture.Purse purse = new DialogueFixture.Purse(10, 4);
        DialogueSession session = world.start(tomas(Gate.of(Collections.emptyList(), Collections.singletonList(purse))));
        ECEventSubscription<DialogueChoiceEvent> veto = ECEventBus.global().subscribe(DialogueChoiceEvent.class, event -> event.setCancelled(true));
        try {
            DialogueSession.ChoiceResult picked = session.choose("deliver");

            assertEquals(DialogueSession.ChoiceResult.Outcome.CANCELLED, picked.getOutcome());
            assertEquals(10, purse.coins);
            assertEquals("start", session.getNode().getId());
        } finally {
            veto.unsubscribe();
        }
    }

    @Test
    void reachingANodeWithoutOptionsEndsItWithEnd() {
        DialogueSession session = world.start(world.register(DialogueTrees.linear(world.owner(), "talk", "One", "Two")));

        session.choose("next");

        assertEquals(DialogueEndCause.END, session.getEndCause());
        assertEquals("n1", session.getNode().getId());
    }

    @Test
    void theDialogueEventsConformToThePlatformRules() {
        assertEquals(Collections.emptyList(), ECEventConformance.checkAll(DialogueStartedEvent.class, DialogueChoiceEvent.class,
                DialogueEndedEvent.class));
    }
}
