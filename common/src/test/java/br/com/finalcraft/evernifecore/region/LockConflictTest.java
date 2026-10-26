package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.AmountEvery;
import br.com.finalcraft.evernifecore.flag.FlagMode;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.FlagValue;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.command.CoreRegionCommands;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Logs;
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

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Two overlapping regions that lock a flag to different values: the owner hears it once, and {@code info} shows it. */
@ECoreTest
class LockConflictTest {

    private static final String WORLD = "world";

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private ECPluginData core;
    private RegionWorld regions;

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        CoreFlags.registerAll(core);
        regions = RegionWorld.open("Tombs", tempDir.resolve("tombs"));
    }

    @AfterEach
    void close() {
        FlagRegistry.get().unregisterAll(core);
        Plugins.forget(Plugins.CORE);
        regions.close();
        world.close();
    }

    private static FlagValue<List<AmountEvery>> locked(int amount) {
        return FlagValue.permanent(Collections.singletonList(AmountEvery.of(amount, Duration.ofSeconds(1)))).withMode(FlagMode.SET).withLock(true);
    }

    @Test
    void twoOverlappingLocksThatDisagreeWarnTheOwnerNamingBothRegionsAndValues() {
        Region cathedral = regions.cuboid("cathedral", WORLD, 0, 0, 0, 20, 10, 20);
        Region crypt = regions.cuboid("crypt", WORLD, 0, 0, 0, 10, 10, 10);
        crypt.setPriority(10);
        cathedral.setFlag(CoreFlags.HEAL, locked(5));

        List<String> logged = Logs.capture(() -> crypt.setFlag(CoreFlags.HEAL, locked(0)));

        assertTrue(logged.stream().anyMatch(line -> line.contains("tombs:crypt") && line.contains("tombs:cathedral")
                && line.contains("'0 every 1s'") && line.contains("'5 every 1s'") && line.contains("wins where they meet (tombs:crypt)")),
                String.valueOf(logged));

        TestFPlayerSender admin = Senders.player("admin");
        CoreRegionCommands.sendInfo(admin, crypt);
        admin.assertAnyMessageContains("lock conflict with tombs:cathedral");
    }

    @Test
    void locksThatAgreeOrRegionsApartSayNothing() {
        Region cathedral = regions.cuboid("cathedral", WORLD, 0, 0, 0, 20, 10, 20);
        Region crypt = regions.cuboid("crypt", WORLD, 0, 0, 0, 10, 10, 10);
        Region chapel = regions.cuboid("chapel", WORLD, 100, 0, 100, 110, 10, 110);
        cathedral.setFlag(CoreFlags.HEAL, locked(5));
        chapel.setFlag(CoreFlags.HEAL, locked(9));

        List<String> logged = Logs.capture(() -> crypt.setFlag(CoreFlags.HEAL, locked(5)));

        assertFalse(logged.stream().anyMatch(line -> line.contains("is locked to")), String.valueOf(logged));
        TestFPlayerSender admin = Senders.player("admin");
        CoreRegionCommands.sendInfo(admin, chapel);
        assertFalse(admin.anyMessageContains("lock conflict"), admin.getMessages().toString());
    }
}
