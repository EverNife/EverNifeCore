package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.actor.ECEntityType;
import br.com.finalcraft.evernifecore.region.actor.TypeKey;
import br.com.finalcraft.evernifecore.region.command.FlagValues;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagParseException;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.context.BlockContext;
import br.com.finalcraft.evernifecore.region.flag.context.ExplosionContext;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.flag.core.DenySettings;
import br.com.finalcraft.evernifecore.region.flag.core.TypeFilterSettings;
import br.com.finalcraft.evernifecore.testing.Actors;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deny flags by actor: the settings filter every deny flag, the per-actor refinements, and an explosion asked in stages. */
@ECoreTest
class ActorDenyTest {

    private static final String WORLD = "world";
    private static final WorldLocPos INSIDE = WorldLocPos.of(5, 5, 5, WORLD);
    private static final WorldLocPos OUTSIDE = WorldLocPos.of(50, 5, 5, WORLD);

    @TempDir
    Path tempDir;

    private ECPluginData core;
    private ECPluginData owner;
    private RegionLookup lookup;
    private Region region;

    @BeforeEach
    void setUp() {
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        owner = Plugins.fakePluginData("ActorDenyOwner", tempDir.resolve("owner").toFile());
        lookup = TestRegions.newLookup();
        region = TestRegions.box(owner, "yard", WORLD, 0, 0, 0, 10, 10, 10);
        TestRegions.index(lookup, region);
    }

    @AfterEach
    void tearDown() {
        FlagRegistry.get().unregisterAll(core);
        Plugins.forget("ActorDenyOwner");
        Plugins.forget(Plugins.CORE);
    }

    private void register(RegionFlag<?>... flags) {
        for (RegionFlag<?> flag : flags) {
            FlagRegistry.get().register(core, flag);
        }
    }

    private Decision breakBy(Actor actor, WorldLocPos at) {
        return lookup.decide(new BlockContext(EventSource.BREAK, at, actor, null, TypeKey.parse("minecraft:stone"), null));
    }

    private static Actor creeper() {
        return Actors.entity("minecraft:creeper", ECEntityType.MONSTER);
    }

    private static Actor player() {
        return Actors.player(new TestFPlayerSender("steve"));
    }

    private static DenySettings settings(String actors, String exceptActors) {
        DenySettings settings = new DenySettings();
        settings.setActors(Collections.singletonList(actors));
        settings.setExceptActors(Collections.singletonList(exceptActors));
        return settings;
    }

    // ------------------------------------------------------------------
    // DenySettings: actors / exceptActors on every deny flag
    // ------------------------------------------------------------------

    @Test
    void actorsNarrowADenyFlagToThem() {
        register(CoreFlags.DENY_BLOCK_BREAK);
        region.setFlag(CoreFlags.DENY_BLOCK_BREAK, Tristate.TRUE);
        region.setFlagSettings(CoreFlags.DENY_BLOCK_BREAK, settings("monster", ""));

        assertEquals(Decision.DENY, breakBy(creeper(), INSIDE));
        assertEquals(Decision.PASS, breakBy(player(), INSIDE), "a player without the bypass is not one of the actors");
    }

    @Test
    void exceptActorsTakeAKeyOut() {
        register(CoreFlags.DENY_BLOCK_BREAK);
        region.setFlag(CoreFlags.DENY_BLOCK_BREAK, Tristate.TRUE);
        region.setFlagSettings(CoreFlags.DENY_BLOCK_BREAK, settings("", "minecraft:creeper"));

        assertEquals(Decision.PASS, breakBy(creeper(), INSIDE));
        assertEquals(Decision.DENY, breakBy(Actors.entity("minecraft:zombie", ECEntityType.MONSTER), INSIDE));
    }

    @Test
    void aKeyGlobMatchesEveryKeyOfItsNamespace() {
        register(CoreFlags.DENY_BLOCK_BREAK);
        region.setFlag(CoreFlags.DENY_BLOCK_BREAK, Tristate.TRUE);
        region.setFlagSettings(CoreFlags.DENY_BLOCK_BREAK, settings("pixelmon:*", ""));

        assertEquals(Decision.DENY, breakBy(Actors.entity("pixelmon:pixelmon", ECEntityType.OTHER), INSIDE));
        assertEquals(Decision.PASS, breakBy(creeper(), INSIDE));
    }

    @Test
    void emptyActorsCoverEveryActorType() {
        register(CoreFlags.DENY_BLOCK_BREAK);
        region.setFlag(CoreFlags.DENY_BLOCK_BREAK, Tristate.TRUE);

        assertEquals(Decision.DENY, breakBy(player(), INSIDE), Actor.Type.ENTITY.getWord());
        assertEquals(Decision.DENY, breakBy(Actors.block("minecraft:dispenser"), INSIDE), Actor.Type.BLOCK.getWord());
        assertEquals(Decision.DENY, breakBy(Actors.environment("fire"), INSIDE), Actor.Type.ENVIRONMENT.getWord());
    }

    @Test
    void theActorFilterLivesInTheBaseSoAnInteractFlagHasItToo() {
        register(CoreFlags.DENY_INTERACT, CoreFlags.DENY_INTERACT_BLOCKS, CoreFlags.DENY_INTERACT_ENTITIES);
        region.setFlag(CoreFlags.DENY_INTERACT, Tristate.TRUE);
        TypeFilterSettings settings = new TypeFilterSettings();
        settings.setActors(Arrays.asList("fake-player"));
        settings.setOnly(Arrays.asList("minecraft:chest"));
        region.setFlagSettings(CoreFlags.DENY_INTERACT, settings);
        TestFPlayerSender machine = new TestFPlayerSender("[Quarry]");

        assertEquals(Decision.DENY, lookup.decide(use(Actors.fakePlayer(machine), "minecraft:chest")));
        assertEquals(Decision.PASS, lookup.decide(use(player(), "minecraft:chest")), "a real player is not in the actors");
        assertEquals(Decision.PASS, lookup.decide(use(Actors.fakePlayer(machine), "minecraft:furnace")), "only still narrows the blocks");
    }

    private static BlockContext use(Actor actor, String block) {
        return new BlockContext(EventSource.INTERACT, INSIDE, actor, null, TypeKey.parse(block), null);
    }

    @Test
    void aListAndCommaTextAreTheSameActors() throws FlagParseException {
        register(CoreFlags.DENY_BLOCK_BREAK);
        JsonNodeFactory json = JsonNodeFactory.instance;
        ObjectNode asText = json.objectNode().put("actors", "monster, environment");
        ObjectNode asList = json.objectNode();
        asList.putArray("actors").add("monster").add("environment");

        DenySettings fromText = (DenySettings) FlagValues.parseSettings(CoreFlags.DENY_BLOCK_BREAK, null, asText);
        DenySettings fromList = (DenySettings) FlagValues.parseSettings(CoreFlags.DENY_BLOCK_BREAK, null, asList);

        assertEquals(Arrays.asList("monster", "environment"), fromText.getActors());
        assertEquals(fromList.getActors(), fromText.getActors());
        for (Actor actor : Arrays.asList(creeper(), player(), Actors.environment("fire"), Actors.block("minecraft:dispenser"))) {
            assertEquals(fromList.covers(actor), fromText.covers(actor), actor.toString());
        }
    }

    @Test
    void aDenialWithNoPlayerBehindItTellsNobodyAndDoesNotThrow() {
        register(CoreFlags.DENY_BLOCK_BREAK, CoreFlags.DENY_MESSAGE);
        region.setFlag(CoreFlags.DENY_BLOCK_BREAK, Tristate.TRUE);
        Region global = TestRegions.loaded(owner, "global-yard", WORLD);
        try {
            global.setFlag(CoreFlags.DENY_MESSAGE, "stay out");
            TestFPlayerSender bystander = new TestFPlayerSender("bystander");

            assertEquals(Decision.DENY, breakBy(Actors.environment("fire"), INSIDE));
            assertEquals(Decision.DENY, breakBy(creeper(), INSIDE));
            assertTrue(bystander.getMessages().isEmpty());
        } finally {
            TestRegions.unloadAll();
        }
    }

    // ------------------------------------------------------------------
    // refinements by actor
    // ------------------------------------------------------------------

    @Test
    void aMobRefinementDecidesForMobsOnly() {
        register(CoreFlags.DENY_BLOCK_BREAK, CoreFlags.DENY_BLOCK_BREAK_MOBS, CoreFlags.DENY_BLOCK_BREAK_ENVIRONMENT,
                CoreFlags.DENY_BLOCK_BREAK_FAKE_PLAYERS);

        region.setFlag(CoreFlags.DENY_BLOCK_BREAK, Tristate.FALSE);
        region.setFlag(CoreFlags.DENY_BLOCK_BREAK_MOBS, Tristate.TRUE);
        assertEquals(Decision.DENY, breakBy(creeper(), INSIDE));
        assertEquals(Decision.PASS, breakBy(player(), INSIDE));

        region.setFlag(CoreFlags.DENY_BLOCK_BREAK, Tristate.TRUE);
        region.setFlag(CoreFlags.DENY_BLOCK_BREAK_MOBS, Tristate.FALSE);
        assertEquals(Decision.PASS, breakBy(creeper(), INSIDE), "the refinement defined wins over its parent, for its actors");
        assertEquals(Decision.DENY, breakBy(player(), INSIDE), "everyone else still reads the parent");
    }

    @Test
    void aRefinementsOwnActorsOnlyNarrowItsSet() {
        register(CoreFlags.DENY_BLOCK_BREAK, CoreFlags.DENY_BLOCK_BREAK_MOBS);
        region.setFlag(CoreFlags.DENY_BLOCK_BREAK_MOBS, Tristate.TRUE);
        region.setFlagSettings(CoreFlags.DENY_BLOCK_BREAK_MOBS, settings("monster, player", ""));

        assertEquals(Decision.DENY, breakBy(creeper(), INSIDE));
        assertEquals(Decision.PASS, breakBy(Actors.entity("minecraft:cow", ECEntityType.ANIMAL), INSIDE), "monster narrowed the mobs");
        assertEquals(Decision.PASS, breakBy(player(), INSIDE), "player is outside the refinement, whatever its settings say");
    }

    // ------------------------------------------------------------------
    // deny-explosion, and the stages of an explosion
    // ------------------------------------------------------------------

    @Test
    void denyExplosionDeniesTheWholeExplosion() {
        register(CoreFlags.DENY_EXPLOSION);
        ExplosionContext blast = new ExplosionContext(INSIDE, creeper(), null, 3f);
        assertEquals(Decision.PASS, lookup.decide(blast), "the flag is not set");

        region.setFlag(CoreFlags.DENY_EXPLOSION, Tristate.TRUE);
        assertEquals(Decision.DENY, lookup.decide(new ExplosionContext(INSIDE, creeper(), null, 3f)));
        assertEquals(Decision.PASS, lookup.decide(new ExplosionContext(OUTSIDE, creeper(), null, 3f)));
    }

    @Test
    void anExplosionsBlocksAreAskedAsBreaksByTheSameActor() {
        register(CoreFlags.DENY_EXPLOSION, CoreFlags.DENY_BLOCK_BREAK);
        region.setFlag(CoreFlags.DENY_BLOCK_BREAK, Tristate.TRUE);
        Actors.Handles handles = new Actors.Handles();
        Actor tnt = handles.entity("minecraft:tnt", ECEntityType.OTHER, null);

        try (RegionDispatch dispatch = lookup.dispatch()) {
            assertEquals(Decision.PASS, dispatch.decide(new ExplosionContext(INSIDE, tnt, null, 4f)), "no deny-explosion: it goes off");
            assertEquals(Decision.DENY, dispatch.decide(new BlockContext(EventSource.BREAK, INSIDE, tnt, null,
                    TypeKey.parse("minecraft:stone"), null)), "a block inside the region stays");
            assertEquals(Decision.PASS, dispatch.decide(new BlockContext(EventSource.BREAK, OUTSIDE, tnt, null,
                    TypeKey.parse("minecraft:stone"), null)), "a block outside breaks");
            tnt.getNativeEntity();   //still open between the stages
        }
        assertEquals(1, handles.getEntitiesMade().size());
    }
}
