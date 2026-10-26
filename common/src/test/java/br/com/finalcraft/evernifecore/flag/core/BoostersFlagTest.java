package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.FlagCategory;
import br.com.finalcraft.evernifecore.flag.FlagParseException;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.FlagTypes;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code boosters} only carries entries for the booster engine to read: the region's and the party's join, each entry well formed. */
@ECoreTest
class BoostersFlagTest {

    private static final String WORLD = "world";

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private ECPluginData core;
    private RegionWorld regions;
    private TestFPlayerSender alice;

    @BeforeEach
    void open() {
        alice = Senders.player("alice").at(WORLD, 5.5, 5, 5.5);
        world = Platforms.lenient().onlinePlayers(Collections.<FPlayer>singletonList(alice)).install();
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        CoreFlags.registerAll(core);
        regions = RegionWorld.open("Farms", tempDir.resolve("farms"));
    }

    @AfterEach
    void close() {
        FlagRegistry.get().unregisterAll(core);
        Plugins.forget(Plugins.CORE);
        regions.close();
        world.close();
    }

    @Test
    void theRegionsAndThePartysBoostersJoinByConcat() {
        Region farm = regions.cuboid("farm", WORLD, 0, 0, 0, 10, 10, 10);
        BoosterEntry money = BoosterEntry.factor("finaljobs:money", 1.25);
        BoosterEntry exp = BoosterEntry.bonus("exp-points", 0.5);
        farm.setFlag(CoreFlags.BOOSTERS, Collections.singletonList(money));
        regions.actorHolder("party", alice.getUniqueId()).setFlag(CoreFlags.BOOSTERS, Collections.singletonList(exp));

        List<BoosterEntry> boosters = regions.getLookup().resolve(CoreFlags.BOOSTERS, alice);
        assertEquals(new HashSet<>(Arrays.asList(money, exp)), new HashSet<>(boosters));
        assertEquals(2, boosters.size(), "both layers count, nothing merged");
        assertEquals("concat", regions.explain(CoreFlags.BOOSTERS, alice).getResolver().getId());
        assertEquals(FlagCategory.DATA, CoreFlags.BOOSTERS.getCategory(), "nobody in the flag system applies it");
    }

    @Test
    void anEntryWithBothFactorAndBonusIsRefusedTypedOrStored() {
        FlagParseException typed = assertThrows(FlagParseException.class, () -> FlagTypes.BOOSTERS.parse("on=finaljobs:money factor=1.25 bonus=0.5"));
        assertTrue(typed.getMessage().contains("exactly one"), typed.getMessage());

        com.fasterxml.jackson.databind.node.ArrayNode stored = JsonNodeFactory.instance.arrayNode();
        stored.addObject().put("on", "exp-points");
        FlagParseException neither = assertThrows(FlagParseException.class, () -> FlagTypes.BOOSTERS.fromJson(stored));
        assertTrue(neither.getMessage().contains("neither factor nor bonus"), neither.getMessage());
    }

    @Test
    void theTypedFormReadsBackAsItIsShown() throws Exception {
        List<BoosterEntry> typed = FlagTypes.BOOSTERS.parse("on=finaljobs:money factor=1.25, on=exp-points bonus=0.5");
        assertEquals(Arrays.asList(BoosterEntry.factor("finaljobs:money", 1.25), BoosterEntry.bonus("exp-points", 0.5)), typed);
        assertEquals(typed, FlagTypes.BOOSTERS.parse(FlagTypes.BOOSTERS.format(typed)));
        assertEquals(typed, FlagTypes.BOOSTERS.fromJson(FlagTypes.BOOSTERS.toJson(typed)));
    }
}
