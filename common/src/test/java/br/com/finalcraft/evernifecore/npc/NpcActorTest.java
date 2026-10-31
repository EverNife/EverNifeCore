package br.com.finalcraft.evernifecore.npc;

import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.DamageContext;
import br.com.finalcraft.evernifecore.action.ECEntityType;
import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.action.event.InteractNpcActionEvent;
import br.com.finalcraft.evernifecore.action.event.KillActionEvent;
import br.com.finalcraft.evernifecore.api.platoverride.entity.NativeEntity;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.testing.ActionWorld;
import br.com.finalcraft.evernifecore.testing.Actors;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.FakeNpcProvider;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The three NPC triggers - talked to, handed an item, killed - are the action events with the NPC on their actor. */
@ECoreTest
class NpcActorTest {

    private static final NpcRef GUARD = NpcRef.of("citizens", "7");

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private ActionWorld actions;
    private FakeNpcProvider citizens;
    private TestFPlayerSender alice;

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        actions = ActionWorld.open("FakeQuests", tempDir);
        citizens = new FakeNpcProvider("citizens").at(GUARD, WorldLocPos.of(10, 64, 10, "world"));
        Npcs.register(actions.getOwner(), citizens);
        alice = Senders.player("alice");
    }

    @AfterEach
    void close() {
        Npcs.unregisterAll(actions.getOwner());
        actions.close();
        world.close();
    }

    private List<EntityContext> hearTalks(List<ECEventSubscription<?>> subscriptions) {
        List<EntityContext> heard = new ArrayList<>();
        subscriptions.add(ECEventBus.global().subscribe(InteractNpcActionEvent.Post.class, event -> heard.add(event.getContext())));
        return heard;
    }

    @Test
    void aClickPostsInteractNpcWithTheNpcOnTheEntityActor() {
        List<ECEventSubscription<?>> subscriptions = new ArrayList<>();
        try {
            List<EntityContext> heard = hearTalks(subscriptions);
            assertTrue(citizens.isStarted(), "a listener of the event needs the source, and the provider follows it");

            assertFalse(citizens.click(alice, GUARD));

            assertEquals(1, heard.size());
            assertEquals(EventSource.INTERACT_NPC, heard.get(0).getSource());
            assertEquals(GUARD, heard.get(0).getEntity().getNpc());
            assertSame(alice, heard.get(0).getPlayer());
            assertNull(heard.get(0).getItemInHandKey());
        } finally {
            subscriptions.forEach(ECEventSubscription::unsubscribe);
        }
    }

    @Test
    void handingAnItemIsTheSameClickWithTheItemInTheContext() {
        List<ECEventSubscription<?>> subscriptions = new ArrayList<>();
        try {
            List<EntityContext> heard = hearTalks(subscriptions);

            citizens.click(alice, GUARD, "minecraft:emerald");

            assertEquals(GUARD, heard.get(0).getEntity().getNpc());
            assertEquals(TypeKey.parse("minecraft:emerald"), heard.get(0).getItemInHandKey());
        } finally {
            subscriptions.forEach(ECEventSubscription::unsubscribe);
        }
    }

    @Test
    void aCancelledPreDeniesTheClickAndPostsNothing() {
        List<ECEventSubscription<?>> subscriptions = new ArrayList<>();
        try {
            List<EntityContext> heard = hearTalks(subscriptions);
            subscriptions.add(ECEventBus.global().subscribe(InteractNpcActionEvent.Pre.class, event -> event.setCancelled(true)));

            assertTrue(citizens.click(alice, GUARD));
            assertTrue(heard.isEmpty());
        } finally {
            subscriptions.forEach(ECEventSubscription::unsubscribe);
        }
    }

    @Test
    void aKilledNpcIsTheVictimTheProviderRecognised() {
        NativeEntity body = new NativeEntity(UUID.randomUUID());
        citizens.knows(body, GUARD);
        List<Actor> victims = new ArrayList<>();
        ECEventSubscription<KillActionEvent.Post> killer = ECEventBus.global().subscribe(KillActionEvent.Post.class,
                event -> victims.add(event.getContext().getVictim()));
        try {
            actions.post(kill(body));
            actions.post(kill(new NativeEntity(UUID.randomUUID())));

            assertEquals(GUARD, victims.get(0).getNpc());
            assertNull(victims.get(1).getNpc(), "an entity no provider knows carries no npc");
            assertSame(ECEntityType.ANIMAL, victims.get(1).getEntityType());
        } finally {
            killer.unsubscribe();
        }
    }

    /** What a platform's kill listener builds: the victim's actor carries what the providers resolve for its entity. */
    private DamageContext kill(NativeEntity victim) {
        NpcRef npc = Npcs.resolve(victim).orElse(null);
        Actor actor = Actor.entity(TypeKey.parse(npc == null ? "minecraft:cow" : "minecraft:player"), npc == null ? ECEntityType.ANIMAL : ECEntityType.NPC,
                null, () -> victim, npc);
        return new DamageContext(EventSource.KILL, WorldLocPos.of(0, 64, 0, "world"), Actors.player(alice), null, actor);
    }
}
