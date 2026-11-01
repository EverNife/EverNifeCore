package br.com.finalcraft.evernifecore.dialogue;

import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.EventSourceUsage;
import br.com.finalcraft.evernifecore.action.event.InteractNpcActionEvent;
import br.com.finalcraft.evernifecore.dialogue.event.DialogueEndedEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.event.RegionCrossedEvent;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import br.com.finalcraft.evernifecore.testing.Actors;
import br.com.finalcraft.evernifecore.testing.DialogueTrees;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What starts a dialogue by itself - the effect, an NPC, a region - and the effect that ends it. */
class DialogueTriggersTest {

    private static final NpcRef TOMAS = NpcRef.of("citizens", "12");

    @TempDirNobodyCleans
    Path tempDir;

    private DialogueFixture world;

    @BeforeEach
    void open() {
        world = DialogueFixture.open(tempDir, platform -> platform.worldEvents().platformProviderId("test"));
    }

    @AfterEach
    void close() {
        world.close();
    }

    private Effect effect(String singleQuotedJson) {
        try {
            return Effects.parse(world.owner(), new ObjectMapper().readTree(singleQuotedJson.replace('\'', '"')), ZoneOffset.UTC);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private void useNpc(NpcRef npc) {
        ECEventBus.global().post(new InteractNpcActionEvent.Post(new EntityContext(EventSource.INTERACT_NPC, WorldLocPos.of(0, 64, 0, "world"),
                Actors.player(world.alex), null, Actors.npc("citizens:npc", npc), null)));
    }

    private Dialogue withTrigger(String name, DialogueTrigger trigger) {
        return world.register(Dialogue.builder(world.owner(), name).trigger(trigger)
                .node(DialogueNode.builder("start").text("Hello!").option(DialogueOption.builder("bye").text("Bye").end()))
                .build());
    }

    @Test
    void theStartDialogueEffectStartsItWithTheContextsCallerAsOrigin() {
        world.register(DialogueTrees.linear(world.owner(), "tomas", "Hello!", "Bye!"));

        Effects.apply(world.colonies.context(world.alex).build(), effect("{'type': 'start-dialogue', 'dialogue': 'fakecolonies:tomas'}"));

        DialogueSession session = Dialogues.sessionOf(world.alex);
        assertNotNull(session, "the effect started the dialogue");
        assertEquals("fakecolonies:tomas", session.getDialogue().getId().toString());
        assertEquals(world.colonies.caller("test"), session.getCaller());
    }

    @Test
    void enteringTheTriggerRegionStartsIt() {
        try (RegionWorld regions = RegionWorld.open("FakeTowns", tempDir.resolve("towns"))) {
            Region tavern = regions.cuboid("tavern", "world", 0, 0, 0, 10, 10, 10);
            Region square = regions.cuboid("square", "world", 20, 0, 20, 30, 10, 30);
            withTrigger("barkeeper", DialogueTrigger.regionEnter(tavern.getId()));

            ECEventBus.global().post(new RegionCrossedEvent(world.alex, null, WorldLocPos.of(25, 5, 25, "world"), MoveCause.MOVE,
                    Collections.<Region>emptyList(), Collections.singletonList(square), null));
            assertNull(Dialogues.sessionOf(world.alex), "another region starts nothing");

            ECEventBus.global().post(new RegionCrossedEvent(world.alex, null, WorldLocPos.of(5, 5, 5, "world"), MoveCause.MOVE,
                    Collections.<Region>emptyList(), Collections.singletonList(tavern), null));
            DialogueSession session = Dialogues.sessionOf(world.alex);
            assertNotNull(session, "entering the tavern starts the barkeeper");
            assertEquals("region-enter:faketowns:tavern", session.getCaller().getOrigin());

            Dialogues.closeOwnedBy(world.owner()); //before the regions close: the trigger held MOVE
            world.screens.advanceTicks(2);
        }
    }

    @Test
    void usingTheTriggerNpcStartsItAndAnotherNpcDoesNot() {
        withTrigger("tomas", DialogueTrigger.npc(TOMAS));

        useNpc(NpcRef.of("citizens", "99"));
        assertNull(Dialogues.sessionOf(world.alex), "another NPC starts nothing");

        useNpc(TOMAS);
        DialogueSession session = Dialogues.sessionOf(world.alex);
        assertNotNull(session);
        assertEquals(TOMAS, session.getContext().getNpc());
        assertEquals(TOMAS, session.newExecutionContext().build().getAttribute(DialogueContext.NPC, NpcRef.class));
    }

    @Test
    void theNpcSourceIsNeededOnlyWhileADialogueHasAnNpcTrigger() {
        assertFalse(EventSourceUsage.get().isNeeded(EventSource.INTERACT_NPC), "nothing needs it before");

        withTrigger("tomas", DialogueTrigger.npc(TOMAS));
        assertTrue(EventSourceUsage.get().isNeeded(EventSource.INTERACT_NPC), "an npc trigger needs it");

        Dialogues.closeOwnedBy(world.owner());
        assertFalse(EventSourceUsage.get().isNeeded(EventSource.INTERACT_NPC), "closing the owner lets it go");
    }

    @Test
    void endDialogueEffectEndsTheSession() {
        List<DialogueEndedEvent> ended = world.screens.recorded(DialogueEndedEvent.class);
        Effect end = effect("{'type': 'end-dialogue'}");

        Effects.apply(world.colonies.context(world.alex).build(), end);
        assertTrue(ended.isEmpty(), "without a session there is nothing to end");

        DialogueSession session = world.start(world.register(DialogueTrees.linear(world.owner(), "talk", "One", "Two")));
        Effects.apply(world.colonies.context(world.alex).build(), end);

        assertEquals(DialogueEndCause.API, session.getEndCause());
        assertEquals(1, ended.size(), ended.toString());
    }
}
