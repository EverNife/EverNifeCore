package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.booster.BoosterManager;
import br.com.finalcraft.evernifecore.booster.Boosters;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.ArgParserString;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.TristateFlag;
import br.com.finalcraft.evernifecore.logic.audit.AuditLog;
import br.com.finalcraft.evernifecore.notify.Notification;
import br.com.finalcraft.evernifecore.notify.Notifier;
import br.com.finalcraft.evernifecore.notify.NotifyChannel;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.ScriptedStorage;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Both ways out of the server - the shutdown hook and the plugin-disable path - take back the same registrations. */
@ECoreTest
class ECPluginManagerReleaseTest {

    @TempDir
    Path tempDir;

    private ECStorage storage;

    @AfterEach
    void tearDown() throws Exception {
        Plugins.forget("FakeVip");
        Plugins.forget("BrokenVip");
        if (storage != null) {
            storage.close().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void removePluginDataTakesDownWhatTheShutdownHookWould() throws Exception {
        ECPluginData vip = Plugins.fakePluginData("FakeVip", tempDir.resolve("FakeVip").toFile());
        BoosterManager boosters = BoosterManager.of(vip);
        AuditLog.of(vip, openStorage());
        TristateFlag flag = probeFlag(vip);
        FlagRegistry.get().register(vip, flag);
        ArgParserManager.addGlobalParser(vip, Probe.class, ProbeParser.class);

        ECPluginManager.removePluginData("FakeVip");

        assertFalse(BoosterManager.ofIfPresent(vip).isPresent(), "the booster manager is still open");
        assertFalse(Boosters.getSources().contains(boosters), "the booster manager is still a source");
        assertFalse(AuditLog.ofIfPresent(vip).isPresent(), "the audit log is still open");
        assertTrue(FlagRegistry.get().getByOwner(vip).isEmpty(), "the flags are still registered");
        assertFalse(flag.isRegistered());
        assertNull(ArgParserManager.getParser(vip, Probe.class), "the parser is still registered");
    }

    @Test
    void theShutdownHookReleasesTheRestWhenOneStepThrows() {
        ECPluginData vip = Plugins.fakePluginData("BrokenVip", tempDir.resolve("BrokenVip").toFile());
        Notifier.get().register(vip, new BrokenChannel(), "Throws on clearAll.");
        TristateFlag flag = probeFlag(vip);
        FlagRegistry.get().register(vip, flag);
        BootstrapOf plugin = new BootstrapOf(vip);

        List<String> logged = Logs.capture(plugin::runECPluginShutdown);

        assertTrue(FlagRegistry.get().getByOwner(vip).isEmpty(), "a step after the failing one did not run");
        assertTrue(logged.stream().anyMatch(line -> line.contains("notify channels") && line.contains("probe channel broke")),
                "the failing step is not on the plugin's log: " + logged);
    }

    @Test
    void theCoreLeavingKeepsTheBuiltinParsers(ECoreTestWorld world) {
        ECPluginData core = Plugins.fakePluginData(Plugins.CORE, tempDir.resolve(Plugins.CORE).toFile());
        world.withCorePluginData(core);
        FinalCMDManager.registerBuiltinParsers();

        ECPluginManager.removePluginData(Plugins.CORE);

        assertSame(ArgParserString.class, ArgParserManager.getParser(core, String.class),
                "the builtins are registered once per class load, so the core's data leaving must not take them");
    }

    private ECStorage openStorage() throws Exception {
        ScriptedStorage backend = ScriptedStorage.inMemory();
        backend.init().get(5, TimeUnit.SECONDS);
        storage = backend.asECStorage();
        return storage;
    }

    private static TristateFlag probeFlag(ECPluginData owner) {
        return new TristateFlag(FlagKey.of(owner, "no-probe"), Collections.singleton(EventSource.NONE), Tristate.FALSE, Tristate.TRUE);
    }

    /** A type only this test parses. */
    public static final class Probe {
    }

    public static final class ProbeParser extends ArgParser<Probe> {
        public ProbeParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public ParseResult<Probe> parse(@Nonnull ParseCall call) {
            return ParseResult.empty();
        }
    }

    /** A channel of the plugin whose first shutdown breaks; later ones (the test world's own close) pass. */
    private static final class BrokenChannel implements NotifyChannel {
        private boolean broke;

        @Override
        public String getKey() {
            return "brokenvip:probe";
        }

        @Override
        public boolean isSupported() {
            return true;
        }

        @Override
        public void deliver(FPlayer player, Notification notification) {
        }

        @Override
        public void clearAll() {
            if (broke) {
                return;
            }
            broke = true;
            throw new IllegalStateException("probe channel broke");
        }
    }

    /** A plugin that keeps the default shutdown hook. */
    private static final class BootstrapOf implements IECPluginBootstrap {
        private final ECPluginData data;

        BootstrapOf(ECPluginData data) {
            this.data = data;
        }

        @Override
        public ECPluginData getPluginData() {
            return data;
        }

        @Override
        public void onECPluginEnable() {
        }

        @Override
        public void onECPluginShutdown() {
        }

        @Override
        public void onECPluginReload() {
        }
    }
}
