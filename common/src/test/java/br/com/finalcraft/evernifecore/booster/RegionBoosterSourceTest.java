package br.com.finalcraft.evernifecore.booster;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.core.BoosterEntry;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code boosters} flag where the player stands becomes boosters of the player's gain, and only of the gain it names. */
@ECoreTest
class RegionBoosterSourceTest {

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
    void eachEntryOfTheFlagWhereThePlayerStandsIsABoosterOfThatGain() {
        Region farm = regions.cuboid("farm", WORLD, 0, 0, 0, 10, 10, 10);
        farm.setFlag(CoreFlags.BOOSTERS, Arrays.asList(BoosterEntry.factor("money", 1.5), BoosterEntry.bonus("exp-points", 0.5)));
        RegionBoosterSource source = new RegionBoosterSource(core);
        ExecutionContext ctx = ExecutionContext.builder(Caller.of(core, "test")).player(alice).build();

        Collection<Booster> money = source.boostersFor(ctx, "evernifecore:money");
        assertEquals(1, money.size());
        assertEquals(0, new BigDecimal("1.5").compareTo(money.iterator().next().getMultiplier()));
        assertEquals(1, source.boostersFor(ctx, "evernifecore:exp-points").size());

        alice.at(WORLD, 50, 5, 50);
        assertTrue(source.boostersFor(ctx, "evernifecore:money").isEmpty(), "outside the region the flag no longer counts");
    }
}
