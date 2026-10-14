package br.com.finalcraft.evernifecore.api.platoverride.entity;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.RegionDispatch;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.actor.ECEntityType;
import br.com.finalcraft.evernifecore.region.actor.TypeKey;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.context.BlockContext;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A Hytale entity handle lives as long as the dispatch of its event: afterwards it throws, naming the UUID to keep. */
class NativeEntityTest {

    private static final UUID TRORK = UUID.fromString("0000002a-0000-0000-0000-00000000002a");

    @Test
    void aHandleKeptPastTheDispatchThrowsNamingTheUuid() {
        NativeEntity[] made = new NativeEntity[1];
        Actor trork = Actor.entity(TypeKey.parse("hytale:Trork_Brute"), ECEntityType.MONSTER, null,
                () -> made[0] = new NativeEntity(new Ref<EntityStore>(null), TRORK));
        BlockContext breaking = new BlockContext(EventSource.BREAK, WorldLocPos.of(5, 5, 5, "default"), trork, null,
                TypeKey.parse("hytale:Rock_Stone"), null);

        try (RegionDispatch dispatch = RegionLookup.get().dispatch()) {
            dispatch.decide(breaking);
            NativeEntity handle = trork.getNativeEntity();
            assertSame(made[0], handle);
        }

        IllegalStateException late = assertThrows(IllegalStateException.class, made[0]::getRef);
        assertTrue(late.getMessage().contains(TRORK.toString()), late.getMessage());
        assertTrue(late.getMessage().contains("not the handle"), late.getMessage());
        assertEquals(TRORK, made[0].getUniqueId(), "the UUID stays readable");
    }
}
