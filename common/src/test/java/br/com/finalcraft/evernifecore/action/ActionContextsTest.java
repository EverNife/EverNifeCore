package br.com.finalcraft.evernifecore.action;

import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.testing.Actors;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A context shared by several sources says which ones it serves, and refuses another one with the way out; the contexts of
 * a single source carry what that source is about.
 */
class ActionContextsTest {

    private static final WorldLocPos AT = WorldLocPos.of(5, 5, 5, "world");
    private final Actor steve = Actors.player(new TestFPlayerSender("Steve"));

    private static void assertTeaches(IllegalArgumentException refusal, EventSource refused) {
        String message = refusal.getMessage();
        assertTrue(message.contains(refused.toString()), message);
        assertTrue(message.contains("pass the source"), message);
    }

    @Test
    void anItemContextServesTheItemSourcesAndRefusesAnother() {
        for (EventSource source : ItemContext.SOURCES) {
            assertEquals(source, new ItemContext(source, AT, steve, null, TypeKey.parse("minecraft:bread"), 1, null, null).getSource());
        }
        assertTeaches(assertThrows(IllegalArgumentException.class,
                () -> new ItemContext(EventSource.BREAK, AT, steve, null, TypeKey.parse("minecraft:bread"), 1, null, null)), EventSource.BREAK);
    }

    @Test
    void anItemContextCarriesTheAmountAndTheItemEntityWhenThereIsOne() {
        UUID dropped = UUID.randomUUID();
        ItemContext crafted = new ItemContext(EventSource.CRAFT, AT, steve, null, TypeKey.parse("minecraft:stick"), 64, null, null);
        ItemContext drop = new ItemContext(EventSource.ITEM_DROP, AT, steve, null, TypeKey.parse("minecraft:stick"), 3, dropped, null);

        assertEquals(64, crafted.getAmount());
        assertNull(crafted.getItemEntityId(), "a craft has no item lying in the world");
        assertEquals(dropped, drop.getItemEntityId());
    }

    @Test
    void anEntityContextServesTheEntitySourcesAndRefusesAnother() {
        Actor cow = Actors.entity("minecraft:cow", ECEntityType.ANIMAL);
        for (EventSource source : Arrays.asList(EventSource.INTERACT, EventSource.TAME, EventSource.BREED, EventSource.SHEAR)) {
            assertEquals(source, new EntityContext(source, AT, steve, null, cow, null).getSource());
        }
        assertTeaches(assertThrows(IllegalArgumentException.class, () -> new EntityContext(EventSource.KILL, AT, steve, null, cow, null)),
                EventSource.KILL);
    }

    @Test
    void aDamageContextIsAHitOrAKillAndNothingElse() {
        Actor cow = Actors.entity("minecraft:cow", ECEntityType.ANIMAL);
        assertEquals(EventSource.KILL, new DamageContext(EventSource.KILL, AT, steve, null, cow).getSource());
        assertTeaches(assertThrows(IllegalArgumentException.class, () -> new DamageContext(EventSource.TAME, AT, steve, null, cow)),
                EventSource.TAME);
    }

    @Test
    void theContextsOfOneSourceCarryWhatItIsAbout() {
        assertEquals(EventSource.XP_GAIN, new XpContext(AT, steve, null, 7).getSource());
        assertEquals(7, new XpContext(AT, steve, null, 7).getAmount());

        ExploreContext explore = new ExploreContext(AT, steve, null, ChunkPos.of(0, 0), ChunkPos.of(1, 0));
        assertEquals(EventSource.EXPLORE, explore.getSource());
        assertEquals(ChunkPos.of(1, 0), explore.getTo());

        CommandContext command = new CommandContext(AT, steve, null, "/Essentials:Home bed", Arrays.asList("home", "h"));
        assertEquals(EventSource.COMMAND, command.getSource());
        assertEquals(new LinkedHashSet<>(Arrays.asList("home", "h")), command.getNames(), "label without / or prefix, plus every alias");
        assertEquals(Collections.singleton("spawn"), new CommandContext(AT, steve, null, "spawn", Collections.emptyList()).getNames());
    }
}
