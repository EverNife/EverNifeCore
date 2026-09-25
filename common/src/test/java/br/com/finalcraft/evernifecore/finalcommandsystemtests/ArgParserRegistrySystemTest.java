package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the registry answers "which parser reads this type": the type ITSELF first, the types it can be
 * assigned to afterwards. Each test registers into the global registry and the harness puts it back
 * on close, so what one of them registers cannot answer for another's lookup.
 */
class ArgParserRegistrySystemTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private FinalCmdTestHarness newHarness() {
        harness = new FinalCmdTestHarness("ParserRegistry", tempDir);
        return harness;
    }

    public interface Vehicle {
    }

    public interface Truck extends Vehicle {
    }

    public static class VehicleParser extends ArgParser<Vehicle> {
        public VehicleParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public ParseResult<Vehicle> parse(@Nonnull ParseCall call) {
            return ParseResult.empty();
        }
    }

    public static class TruckParser extends ArgParser<Truck> {
        public TruckParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public ParseResult<Truck> parse(@Nonnull ParseCall call) {
            return ParseResult.empty();
        }
    }

    public static class BetterTruckParser extends ArgParser<Truck> {
        public BetterTruckParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public ParseResult<Truck> parse(@Nonnull ParseCall call) {
            return ParseResult.empty();
        }
    }

    /**
     * The order every platform registration has: the general parser is already in place when the
     * specific one arrives, which used to make the specific one unreachable forever.
     */
    @Test
    void aParserForTheTypeItselfWinsOverOneItCanBeAssignedTo() {
        newHarness();
        ArgParserManager.addGlobalParser(harness.ecPluginData, Vehicle.class, VehicleParser.class);
        ArgParserManager.addGlobalParser(harness.ecPluginData, Truck.class, TruckParser.class);

        assertSame(TruckParser.class, ArgParserManager.getParser(harness.ecPluginData, Truck.class));
    }

    @Test
    void aTypeWithNoParserOfItsOwnStillFindsTheAssignableOne() {
        newHarness();
        ArgParserManager.addGlobalParser(harness.ecPluginData, Vehicle.class, VehicleParser.class);

        assertSame(VehicleParser.class, ArgParserManager.getParser(harness.ecPluginData, Vehicle.class));
        assertSame(VehicleParser.class, ArgParserManager.getParser(harness.ecPluginData, Truck.class),
                "narrowing the lookup must not turn it into 'exact or nothing'");
    }

    /** Registering the same exact type twice is a platform replacing a builtin: the last one wins. */
    @Test
    void registeringTheSameTypeTwiceKeepsTheLastParser() {
        newHarness();
        ArgParserManager.addGlobalParser(harness.ecPluginData, Truck.class, TruckParser.class);
        ArgParserManager.addGlobalParser(harness.ecPluginData, Truck.class, BetterTruckParser.class);

        assertSame(BetterTruckParser.class, ArgParserManager.getParser(harness.ecPluginData, Truck.class));
    }

    private static final String OTHER_OWNER = "ParserRegistryOtherOwner";

    /** A second plugin next to the harness one, so a registration has somebody else to collide with. */
    private ECPluginData otherOwner() {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                Plugins.fake(OTHER_OWNER, tempDir.resolve(OTHER_OWNER).toFile()));
        return ECPluginManager.getOrCreateECorePluginData(new Object());
    }

    @Test
    void anOwnersUnregisterBringsBackTheParserItHadCoveredAndWarnsWhenItCovers() {
        newHarness();
        try {
            ECPluginData other = otherOwner();
            ArgParserManager.addGlobalParser(harness.ecPluginData, Truck.class, TruckParser.class);

            List<String> logged = Logs.capture(() -> ArgParserManager.addGlobalParser(other, Truck.class, BetterTruckParser.class));
            assertTrue(logged.stream().anyMatch(line -> line.contains(OTHER_OWNER) && line.contains(harness.ecPluginData.getMetaInfo().getName())
                    && line.contains("BetterTruckParser") && line.contains("TruckParser")), "the warning names both owners: " + logged);
            assertSame(BetterTruckParser.class, ArgParserManager.getParser(harness.ecPluginData, Truck.class));

            assertFalse(ArgParserManager.unregisterGlobalParser(harness.ecPluginData, Vehicle.class), "nothing of its own to take back");
            ArgParserManager.unregisterAll(other);
            assertSame(TruckParser.class, ArgParserManager.getParser(harness.ecPluginData, Truck.class),
                    "the covered parser answers again once its cover leaves");

            assertTrue(ArgParserManager.unregisterGlobalParser(harness.ecPluginData, Truck.class));
            assertNull(ArgParserManager.getParser(harness.ecPluginData, Truck.class));
        } finally {
            Plugins.forget(OTHER_OWNER);
        }
    }

    @Test
    void unregisterAllTakesThePluginsOwnParsersToo() {
        newHarness();
        ArgParserManager.addPluginParser(harness.ecPluginData, Vehicle.class, VehicleParser.class);

        ArgParserManager.unregisterAll(harness.ecPluginData);

        assertNull(ArgParserManager.getParser(harness.ecPluginData, Vehicle.class));
    }
}
