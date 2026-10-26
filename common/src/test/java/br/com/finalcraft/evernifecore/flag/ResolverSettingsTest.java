package br.com.finalcraft.evernifecore.flag;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code resolve:} in a flag's settings names another strategy among those the flag accepts; anything else fails loud. */
@ECoreTest
class ResolverSettingsTest {

    private static final String WORLD = "world";
    private static final String ACCEPTED = "sum-by-interval, first-defined";

    /** A heal of the test's own plugin, whose settings file is always read. */
    private static final class Mending extends Flag<List<AmountEvery>> {
        Mending(ECPluginData owner) {
            super(FlagKey.of(owner, "mending"), FlagTypes.AMOUNT_EVERY_LIST, Collections.singleton(EventSource.NONE), null);
        }

        @Override
        public FlagResolver<List<AmountEvery>> getResolver() {
            return FlagResolvers.sumByInterval();
        }

        @Override
        public List<FlagResolver<List<AmountEvery>>> getAcceptedResolvers() {
            return Arrays.asList(getResolver(), FlagResolvers.firstDefined());
        }

        @Override
        public SettingsFilePolicy getSettingsFilePolicy() {
            return SettingsFilePolicy.ALWAYS;
        }
    }

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
        regions = RegionWorld.open("Spas", tempDir.resolve("spas"));
    }

    @AfterEach
    void close() {
        FlagRegistry.get().unregisterAll(regions.getOwner());
        FlagRegistry.get().unregisterAll(core);
        Plugins.forget(Plugins.CORE);
        regions.close();
        world.close();
    }

    private void writeServerSettings(String name, String resolve) throws Exception {
        Path file = tempDir.resolve("spas").resolve("regions").resolve("flags").resolve(name + ".yml");
        Files.createDirectories(file.getParent());
        Files.write(file, ("Settings:\n  resolve: " + resolve + "\n").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void serverSettingsNamingAResolverTheFlagRefusesFailTheRegisterListingTheAccepted() throws Exception {
        writeServerSettings("mending", "max");
        Mending mending = new Mending(regions.getOwner());
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> FlagRegistry.get().register(regions.getOwner(), mending));
        assertTrue(refused.getMessage().contains("resolve: max") && refused.getMessage().contains(ACCEPTED), refused.getMessage());
        assertFalse(mending.isRegistered(), "a refused register leaves the flag unbound");

        writeServerSettings("mending", "first-defined");
        FlagRegistry.get().register(regions.getOwner(), mending);
        assertEquals("first-defined", mending.getResolver(mending.getSettings()).getId());
    }

    @Test
    void anAcceptedResolverInTheRegionsSettingsIsTheOneThatJoins() {
        Region spa = regions.cuboid("spa", WORLD, 0, 0, 0, 10, 10, 10);
        Region pool = regions.cuboid("pool", WORLD, 0, 0, 0, 8, 10, 8);
        spa.setFlag(CoreFlags.HEAL, Collections.singletonList(AmountEvery.of(2, Duration.ofSeconds(1))));
        pool.setFlag(CoreFlags.HEAL, Collections.singletonList(AmountEvery.of(10, Duration.ofSeconds(1))));
        assertEquals("sum-by-interval", regions.getLookup().explain(CoreFlags.HEAL, WORLD, 5, 5, 5, null).getResolver().getId());

        FlagSettings firstOnly = new FlagSettings();
        firstOnly.setResolve("first-defined");
        spa.setFlagSettings(CoreFlags.HEAL, firstOnly);
        pool.setFlagSettings(CoreFlags.HEAL, firstOnly);
        Resolution<List<AmountEvery>> resolution = regions.getLookup().explain(CoreFlags.HEAL, WORLD, 5, 5, 5, null);
        assertEquals("first-defined", resolution.getResolver().getId());
        assertEquals(1, resolution.getCountedLayers().size());
    }

    @Test
    void aRegionOverrideNamingAResolverTheFlagRefusesIsRefusedWithTheSameText() {
        Region spa = regions.cuboid("spa", WORLD, 0, 0, 0, 10, 10, 10);
        FlagSettings wrong = new FlagSettings();
        wrong.setResolve("max");
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> spa.setFlagSettings(CoreFlags.HEAL, wrong));
        assertTrue(refused.getMessage().contains("resolve: max") && refused.getMessage().contains(ACCEPTED), refused.getMessage());
        assertEquals(null, spa.getFlagSettings(CoreFlags.HEAL), "nothing was kept");
    }
}
