package br.com.finalcraft.evernifecore.hytale.npc;

import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.hytale.action.HyActionContexts;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import br.com.finalcraft.evernifecore.testing.Senders;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A Hytale NPC is {@code hytale:<uuid>} - an entity with an NPC component and a uuid; a player or a plain entity is none. */
class HyNpcProviderTest {

    private static final UUID ID = UUID.fromString("4f2a0000-0000-4000-8000-000000000001");

    @Test
    void anNpcEntityWithAUuidIsHytaleUuidAndItsActorCarriesIt() {
        NpcRef ref = HyNpcProvider.refOf(new NPCEntity(), new UUIDComponent(ID));

        assertEquals(NpcRef.of("hytale", ID.toString()), ref);
        Actor guard = HyActionContexts.actor(null, "Quest_Giver", null, null, ref);
        assertEquals(ref, guard.getNpc());
    }

    @Test
    void aPlayerIsNeverAnNpcEvenHandedAReference() {
        FPlayer miner = Senders.player("miner");
        assertNull(HyActionContexts.actor(miner, null, null, null, NpcRef.of("hytale", ID.toString())).getNpc());
    }

    @Test
    void anEntityWithoutTheNpcComponentOrWithoutAUuidIsNone() {
        assertNull(HyNpcProvider.refOf(null, new UUIDComponent(ID)));
        assertNull(HyNpcProvider.refOf(new NPCEntity(), null));
        assertNull(HyActionContexts.actor(null, null, null, null).getNpc());
    }
}
