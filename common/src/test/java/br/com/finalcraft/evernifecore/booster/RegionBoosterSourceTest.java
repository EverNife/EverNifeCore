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
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The {@code boosters} flag where the player stands becomes boosters of the domain each entry names, and of no other. */
@ECoreTest
class RegionBoosterSourceTest {

    private static final String WORLD = "world";

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private ECPluginData core;
    private RegionWorld regions;
    private ECPluginData duels;
    private TestFPlayerSender alice;

    @BeforeEach
    void open() {
        alice = Senders.player("alice").at(WORLD, 5.5, 5, 5.5);
        world = Platforms.lenient().onlinePlayers(Collections.<FPlayer>singletonList(alice)).install();
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        CoreFlags.registerAll(core);
        Boosters.addSource(core, new RegionBoosterSource(core));
        regions = RegionWorld.open("Farms", tempDir.resolve("farms"));
        duels = Plugins.fakePluginData("Duels", tempDir.resolve("duels").toFile());
    }

    @AfterEach
    void close() {
        Plugins.forget("Duels");
        FlagRegistry.get().unregisterAll(core);
        Boosters.unregisterAll(core);
        Plugins.forget(Plugins.CORE);
        regions.close();
        world.close();
    }

    @Test
    void eachEntryOfTheFlagWhereThePlayerStandsBoostsTheDomainItNamesAndNoOther() {
        BoosterDomain win = BoosterDomain.of(duels, "win").register();
        BoosterDomain kill = BoosterDomain.of(duels, "kill").register();
        BoosterDomain loss = BoosterDomain.of(duels, "loss").register();
        Region farm = regions.cuboid("farm", WORLD, 0, 0, 0, 10, 10, 10);
        farm.setFlag(CoreFlags.BOOSTERS, Arrays.asList(BoosterEntry.factor("Duels:Win", 1.5), BoosterEntry.bonus("duels:kill", 0.5),
                BoosterEntry.factor("ghost:money", 4)));
        ExecutionContext ctx = ExecutionContext.builder(Caller.of(core, "test")).player(alice).build();

        BoosterResult boosted = win.resolve(ctx);
        assertEquals(0, new BigDecimal("1.5").compareTo(boosted.getMultiplier()), boosted.toString());
        assertEquals("duels:win", boosted.getParcels().get(0).getDomainId());
        assertEquals(0, new BigDecimal("1.5").compareTo(kill.resolve(ctx).getMultiplier()));
        assertEquals(0, BigDecimal.ONE.compareTo(loss.resolve(ctx).getMultiplier()), "no entry names duels:loss");

        alice.at(WORLD, 50, 5, 50);
        assertEquals(0, BigDecimal.ONE.compareTo(win.resolve(ctx).getMultiplier()), "outside the region the flag no longer counts");
    }
}
