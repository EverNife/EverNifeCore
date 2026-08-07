package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * How the registry answers "which parser reads this type": the type ITSELF first, the types it can be
 * assigned to afterwards. Each test registers into the global registry and the harness puts it back
 * on close, so what one of them registers cannot answer for another's lookup.
 */
class ArgParserRegistrySystemTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDir(cleanup = CleanupMode.NEVER)
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
        ArgParserManager.addGlobalParser(Vehicle.class, VehicleParser.class);
        ArgParserManager.addGlobalParser(Truck.class, TruckParser.class);

        assertSame(TruckParser.class, ArgParserManager.getParser(harness.ecPluginData, Truck.class));
    }

    @Test
    void aTypeWithNoParserOfItsOwnStillFindsTheAssignableOne() {
        newHarness();
        ArgParserManager.addGlobalParser(Vehicle.class, VehicleParser.class);

        assertSame(VehicleParser.class, ArgParserManager.getParser(harness.ecPluginData, Vehicle.class));
        assertSame(VehicleParser.class, ArgParserManager.getParser(harness.ecPluginData, Truck.class),
                "narrowing the lookup must not turn it into 'exact or nothing'");
    }

    /** Registering the same exact type twice is a platform replacing a builtin: the last one wins. */
    @Test
    void registeringTheSameTypeTwiceKeepsTheLastParser() {
        newHarness();
        ArgParserManager.addGlobalParser(Truck.class, TruckParser.class);
        ArgParserManager.addGlobalParser(Truck.class, BetterTruckParser.class);

        assertSame(BetterTruckParser.class, ArgParserManager.getParser(harness.ecPluginData, Truck.class));
    }
}
