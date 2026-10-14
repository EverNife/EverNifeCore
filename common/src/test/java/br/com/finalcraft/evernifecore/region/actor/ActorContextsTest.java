package br.com.finalcraft.evernifecore.region.actor;

import br.com.finalcraft.evernifecore.api.platoverride.entity.NativeEntity;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.TestRegions;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.BlockContext;
import br.com.finalcraft.evernifecore.region.flag.context.DamageContext;
import br.com.finalcraft.evernifecore.region.flag.context.EntityContext;
import br.com.finalcraft.evernifecore.region.flag.context.ExplosionContext;
import br.com.finalcraft.evernifecore.region.flag.context.MoveContext;
import br.com.finalcraft.evernifecore.region.flag.context.SpawnContext;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import br.com.finalcraft.evernifecore.testing.Actors;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** What every context says about who acts and about the platform's event, and how the actors' handles live and close. */
@ECoreTest
class ActorContextsTest {

    private static final String WORLD = "world";
    private static final WorldLocPos AT = WorldLocPos.of(5, 5, 5, WORLD);
    private static final TestFPlayerSender STEVE = new TestFPlayerSender("steve");

    /** A stand-in for a platform's event class. */
    private static final class BreakEvent {
    }

    static List<ActionContext> everyContext() {
        Actor steve = Actors.player(STEVE);
        Actor arrow = Actor.entity(TypeKey.parse("minecraft:arrow"), ECEntityType.OTHER, STEVE, null);
        return Arrays.asList(
                new BlockContext(EventSource.BREAK, AT, steve, null, TypeKey.parse("minecraft:stone"), null),
                new BlockContext(EventSource.BREAK, AT, Actors.environment("fire"), null, TypeKey.parse("minecraft:planks"), null),
                new DamageContext(AT, arrow, null, Actors.entity("minecraft:cow", ECEntityType.ANIMAL)),
                new DamageContext(AT, Actors.entity("minecraft:zombie", ECEntityType.MONSTER), null, Actors.player(STEVE)),
                new EntityContext(AT, steve, null, Actors.entity("minecraft:villager", ECEntityType.NPC)),
                new SpawnContext(AT, Actors.environment("spawn"), null, Actors.entity("minecraft:zombie", ECEntityType.MONSTER), "NATURAL"),
                new ExplosionContext(AT, Actors.entity("minecraft:creeper", ECEntityType.MONSTER), null, 3f));
    }

    @ParameterizedTest
    @MethodSource("everyContext")
    void thePlayerOfEveryContextIsTheRootOfItsActor(ActionContext context) {
        assertSame(context.getActor().getRootPlayer(), context.getPlayer(), context.getClass().getSimpleName());
    }

    @Test
    void theCollectionOfContextsIsNotEmptyAndCoversBothRoots() {
        List<ActionContext> contexts = everyContext();
        assertFalse(contexts.isEmpty());
        assertSame(STEVE, contexts.get(2).getPlayer(), "an arrow answers for its shooter");
        assertNull(contexts.get(1).getPlayer(), "fire has no player behind it");
    }

    @Test
    void aCrossingIsDoneByThePlayerWhoCrosses() {
        MoveContext entering = new MoveContext(STEVE, AT, AT, MoveCause.MOVE, MoveContext.Transition.ENTER,
                TestRegions.create(owner, "arena", WORLD), null);

        assertSame(STEVE, entering.getPlayer());
        assertSame(entering.getActor().getRootPlayer(), entering.getPlayer());
        assertEquals(ECEntityType.PLAYER, entering.getActor().getEntityType());
    }

    @Test
    void theNativeEventIsHandedOutByItsClassOnly() {
        BreakEvent event = new BreakEvent();
        BlockContext withEvent = new BlockContext(EventSource.BREAK, AT, Actors.player(STEVE), event, TypeKey.parse("minecraft:stone"), null);
        BlockContext without = new BlockContext(EventSource.BREAK, AT, Actors.player(STEVE), null, TypeKey.parse("minecraft:stone"), null);

        assertSame(event, withEvent.getNativeEvent());
        assertSame(event, withEvent.getNativeEvent(BreakEvent.class));
        assertSame(event, withEvent.getNativeEvent(Object.class));
        assertNull(withEvent.getNativeEvent(String.class), "another class");
        assertNull(without.getNativeEvent(BreakEvent.class), "no event at all");
    }

    // ------------------------------------------------------------------
    // handles: made on demand, kept, closed with the dispatch
    // ------------------------------------------------------------------

    /** A break flag that asks for the actor's handle as many times as told, and keeps what it got. */
    private static final class HandleReadingFlag extends TristateFlag {
        int asks;
        final List<NativeEntity> seen = new ArrayList<>();

        HandleReadingFlag(ECPluginData owner) {
            super(FlagKey.of(owner, "handle-reading"), Collections.singleton(EventSource.BREAK), Tristate.FALSE, Tristate.TRUE);
        }

        @Override
        public Decision decide(ActionContext context, Tristate value) {
            for (int i = 0; i < asks; i++) {
                seen.add(context.getActor().getNativeEntity());
            }
            return Decision.PASS;
        }
    }

    @TempDir
    Path tempDir;

    private ECPluginData owner;
    private RegionLookup lookup;
    private HandleReadingFlag flag;

    @BeforeEach
    void setUp() {
        owner = Plugins.fakePluginData("ActorContextsOwner", tempDir.toFile());
        lookup = TestRegions.newLookup();
        Region yard = TestRegions.box(owner, "yard", WORLD, 0, 0, 0, 10, 10, 10);
        TestRegions.index(lookup, yard);
        flag = new HandleReadingFlag(owner);
        FlagRegistry.get().register(owner, flag);
        yard.setFlag(flag, Tristate.TRUE);
    }

    @AfterEach
    void tearDown() {
        FlagRegistry.get().unregisterAll(owner);
        Plugins.forget("ActorContextsOwner");
    }

    private static BlockContext breakBy(Actor actor) {
        return new BlockContext(EventSource.BREAK, AT, actor, null, TypeKey.parse("minecraft:stone"), null);
    }

    @Test
    void aHandleNobodyAsksForIsNeverMade() {
        Actors.Handles handles = new Actors.Handles();

        lookup.decide(breakBy(handles.entity("minecraft:creeper", ECEntityType.MONSTER, null)));

        assertEquals(0, handles.getEntitiesMade().size());
    }

    @Test
    void aHandleAskedTwiceIsTheSameOne() {
        Actors.Handles handles = new Actors.Handles();
        flag.asks = 2;

        lookup.decide(breakBy(handles.entity("minecraft:creeper", ECEntityType.MONSTER, null)));

        assertEquals(1, handles.getEntitiesMade().size());
        assertSame(flag.seen.get(0), flag.seen.get(1));
    }

    @Test
    void afterTheDispatchNoHandleIsMade() {
        Actors.Handles handles = new Actors.Handles();
        Actor creeper = handles.entity("minecraft:creeper", ECEntityType.MONSTER, null);

        lookup.decide(breakBy(creeper));

        assertThrows(IllegalStateException.class, creeper::getNativeEntity);
        assertEquals(0, handles.getEntitiesMade().size());
    }
}
