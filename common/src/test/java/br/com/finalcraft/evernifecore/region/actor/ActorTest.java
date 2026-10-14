package br.com.finalcraft.evernifecore.region.actor;

import br.com.finalcraft.evernifecore.api.platoverride.entity.NativeEntity;
import br.com.finalcraft.evernifecore.testing.Actors;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActorTest {

    @Test
    void anEntityCarriesItsRoleAndThePlayerBehindIt() {
        TestFPlayerSender shooter = Senders.player("archer");
        Actor arrow = Actor.entity(TypeKey.parse("minecraft:arrow"), ECEntityType.OTHER, shooter, null);

        assertEquals(Actor.Type.ENTITY, arrow.getType());
        assertEquals(TypeKey.parse("minecraft:arrow"), arrow.getKey());
        assertEquals(ECEntityType.OTHER, arrow.getEntityType());
        assertSame(shooter, arrow.getRootPlayer());
        assertSame(shooter, Actors.player(shooter).getRootPlayer());
        assertEquals(ECEntityType.FAKE_PLAYER, Actors.fakePlayer(shooter).getEntityType());
    }

    @Test
    void blocksAndTheEnvironmentHaveNoEntityTypeNorPlayer() {
        Actor block = Actors.block("minecraft:dispenser");
        Actor fire = Actors.environment("fire");

        assertEquals(Actor.Type.BLOCK, block.getType());
        assertEquals(Actor.Type.ENVIRONMENT, fire.getType());
        assertEquals(TypeKey.parse("environment:fire"), fire.getKey());
        for (Actor actor : new Actor[]{block, fire}) {
            assertNull(actor.getEntityType());
            assertNull(actor.getRootPlayer());
            assertNull(actor.getNativeEntity());
            assertNull(actor.getNativeBlock());
        }
    }

    @Test
    void theHandleIsMadeOnTheFirstAskAndKept() {
        Actors.Handles handles = new Actors.Handles();
        Actor creeper = handles.entity("minecraft:creeper", ECEntityType.MONSTER, null);
        assertEquals(0, handles.getEntitiesMade().size(), "nobody asked yet");

        NativeEntity first = creeper.getNativeEntity();
        assertSame(first, creeper.getNativeEntity());
        assertEquals(1, handles.getEntitiesMade().size());
    }

    @Test
    void aClosedActorMakesNoNewHandle() {
        Actors.Handles handles = new Actors.Handles();
        Actor block = handles.block("minecraft:chest");
        block.close();

        IllegalStateException late = assertThrows(IllegalStateException.class, block::getNativeBlock);
        assertTrue(late.getMessage().contains("not the handle"), late.getMessage());
        assertEquals(0, handles.getBlocksMade().size());
    }

    @Test
    void wordsAreLowercaseWithHyphens() {
        assertEquals("fake-player", ECEntityType.FAKE_PLAYER.getWord());
        assertEquals("environment", Actor.Type.ENVIRONMENT.getWord());
    }
}
