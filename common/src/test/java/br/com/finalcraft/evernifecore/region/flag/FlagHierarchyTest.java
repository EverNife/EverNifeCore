package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.TestRegions;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.BlockContext;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.testing.Actors;
import br.com.finalcraft.evernifecore.region.actor.TypeKey;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A flag that refines another: declared in its constructor, checked when it registers, climbed when it decides. */
@ECoreTest
class FlagHierarchyTest {

    private static final String OWNER = "HierarchyOwner";
    private static final String OTHER = "HierarchyOther";
    private static final String WORLD = "world";

    /** Denies BREAK on TRUE. */
    private static final class BreakFlag extends TristateFlag {
        BreakFlag(ECPluginData owner, String name, RegionFlag<Tristate> parent) {
            super(FlagKey.of(owner, name), Collections.singleton(EventSource.BREAK), Tristate.FALSE, Tristate.TRUE, parent);
        }

        @Override
        public Decision decide(ActionContext context, Tristate value) {
            return value == Tristate.TRUE ? Decision.DENY : Decision.PASS;
        }
    }

    private static final class TextFlag extends RegionFlag<String> {
        TextFlag(ECPluginData owner, String name) {
            super(FlagKey.of(owner, name), FlagTypes.TEXT, Collections.singleton(EventSource.BREAK), null);
        }

        @Override
        public String resolveTie(List<String> values) {
            return values.get(0);
        }
    }

    @TempDir
    Path tempDir;

    private ECPluginData owner;
    private ECPluginData other;
    private RegionLookup lookup;
    private Region region;

    @BeforeEach
    void setUp() {
        owner = Plugins.fakePluginData(OWNER, tempDir.resolve("owner").toFile());
        other = Plugins.fakePluginData(OTHER, tempDir.resolve("other").toFile());
        lookup = TestRegions.newLookup();
        region = TestRegions.box(owner, "yard", WORLD, 0, 0, 0, 10, 10, 10);
        TestRegions.index(lookup, region);
    }

    @AfterEach
    void tearDown() {
        FlagRegistry.get().unregisterAll(owner);
        FlagRegistry.get().unregisterAll(other);
        Plugins.forget(OWNER);
        Plugins.forget(OTHER);
    }

    private static BlockContext breakAt(TestFPlayerSender player) {
        return new BlockContext(EventSource.BREAK, WorldLocPos.of(5, 5, 5, WORLD), Actors.player(player), null, TypeKey.parse("minecraft:stone"), null);
    }

    @Test
    void aRefinementRegisteredBeforeItsParentIsRefused() {
        BreakFlag parent = new BreakFlag(owner, "parent", null);
        BreakFlag child = new BreakFlag(owner, "child", parent);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> FlagRegistry.get().register(owner, child));

        assertTrue(refused.getMessage().contains("register the parent first"), refused.getMessage());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aRefinementOfAnotherValueTypeIsRefused() {
        TextFlag text = new TextFlag(owner, "text");
        FlagRegistry.get().register(owner, text);
        BreakFlag child = new BreakFlag(owner, "child", (RegionFlag) text);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> FlagRegistry.get().register(owner, child));

        assertTrue(refused.getMessage().contains("same value type"), refused.getMessage());
    }

    @Test
    void theParentKnowsItsRegisteredRefinements() {
        BreakFlag parent = new BreakFlag(owner, "parent", null);
        BreakFlag child = new BreakFlag(owner, "child", parent);
        FlagRegistry.get().register(owner, parent);
        assertEquals(Collections.emptyList(), FlagRegistry.get().getChildren(parent));

        FlagRegistry.get().register(owner, child);

        assertSame(parent, child.getParent());
        assertEquals(Collections.singletonList(child), FlagRegistry.get().getChildren(parent));
    }

    @Test
    void unregisteringTheParentKeepsARefinementOfAnotherOwnerResolvable() {
        BreakFlag parent = new BreakFlag(owner, "parent", null);
        BreakFlag child = new BreakFlag(other, "child", parent);
        FlagRegistry.get().register(owner, parent);
        FlagRegistry.get().register(other, child);
        region.setFlag(child, Tristate.TRUE);

        FlagRegistry.get().unregisterAll(owner);

        assertSame(child, FlagRegistry.get().get(child.getKey()));
        assertEquals(Collections.emptyList(), FlagRegistry.get().getChildren(parent));
        assertEquals(Decision.DENY, lookup.decide(breakAt(new TestFPlayerSender("breaker"))));
    }

    @Test
    void aGrandchildDefinedNowhereReadsItsGrandparent() {
        BreakFlag grandparent = new BreakFlag(owner, "grandparent", null);
        BreakFlag parent = new BreakFlag(owner, "parent", grandparent);
        BreakFlag child = new BreakFlag(owner, "child", parent);
        FlagRegistry.get().register(owner, grandparent);
        FlagRegistry.get().register(owner, parent);
        FlagRegistry.get().register(owner, child);
        TestFPlayerSender player = new TestFPlayerSender("breaker");

        region.setFlag(grandparent, Tristate.TRUE);
        assertEquals(Decision.DENY, lookup.decide(breakAt(player)), "two levels up, still the value that counts");

        region.setFlag(parent, Tristate.FALSE);
        assertEquals(Decision.PASS, lookup.decide(breakAt(player)), "the nearest defined ancestor wins");
    }

    @Test
    void theCoreRegistersTwentyFiveWithItsRefinementsUnderTheirParents() {
        ECPluginData core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        try {
            CoreFlags.registerAll(core);
            RegionFlag<?> interact = FlagRegistry.get().getByOwner(core).stream()
                    .filter(flag -> flag.getKey().getName().equals("deny-interact")).findFirst().orElseThrow(AssertionError::new);

            assertEquals(25, FlagRegistry.get().getByOwner(core).size());
            assertEquals(2, FlagRegistry.get().getChildren(interact).size());
            assertEquals(3, FlagRegistry.get().getChildren(CoreFlags.DENY_BLOCK_BREAK).size(), "mobs, environment, fake players");
            assertEquals(1, FlagRegistry.get().getChildren(CoreFlags.DENY_BLOCK_PLACE).size());
            assertEquals(1, FlagRegistry.get().getChildren(CoreFlags.DENY_EXPLOSION).size());
            for (RegionFlag<?> child : FlagRegistry.get().getChildren(interact)) {
                assertSame(interact, child.getParent(), child.getKey().toString());
            }
        } finally {
            FlagRegistry.get().unregisterAll(core);
            Plugins.forget(Plugins.CORE);
        }
    }
}
