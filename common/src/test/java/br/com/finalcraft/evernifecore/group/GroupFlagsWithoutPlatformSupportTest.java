package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.action.ActionSupport;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.GroupWorld;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A platform that cannot glow and raises no experience: both flags are silent, unsupported and out of the listings. */
class GroupFlagsWithoutPlatformSupportTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private ECPluginData core;
    private GroupWorld parties;
    private Group alpha;

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        CoreFlags.registerAll(core);
        parties = GroupWorld.open("FakeParty", tempDir.resolve("party"));
        alpha = parties.group("alpha", parties.kind("party", GroupKind.Cardinality.MANY));
        List<EventSource> raised = new ArrayList<>(EventSource.all());
        raised.remove(EventSource.XP_GAIN);
        ActionSupport.declare("A platform without experience", raised, null, false, null);
    }

    @AfterEach
    void close() {
        ActionSupport.declare("an undeclared platform", null, null, true, null);
        parties.close();
        FlagRegistry.get().unregisterAll(core);
        Plugins.forget(Plugins.CORE);
        world.close();
    }

    @Test
    void settingThemLogsNothing() {
        List<String> logged = Logs.capture(() -> {
            alpha.setFlag(CoreFlags.GLOW_MEMBERS, Tristate.TRUE);
            alpha.setFlag(CoreFlags.SHARE_XP, 0.5);
        });

        assertTrue(logged.stream().noneMatch(line -> line.contains("glow-members") || line.contains("share-xp")), logged.toString());
    }

    @Test
    void theyLeaveTheListingsAndTheOthersStay() {
        alpha.setFlag(CoreFlags.GLOW_MEMBERS, Tristate.TRUE);
        alpha.setFlag(CoreFlags.SHARE_XP, 0.5);
        alpha.setFlag(CoreFlags.SHARE_MONEY, 0.5);

        assertFalse(FlagRegistry.get().isSupported(CoreFlags.GLOW_MEMBERS));
        assertFalse(FlagRegistry.get().isSupported(CoreFlags.SHARE_XP));
        assertTrue(FlagRegistry.get().isSupported(CoreFlags.SHARE_MONEY));
        assertFalse(FlagRegistry.get().findSupported("glow-members").isPresent());
        assertFalse(FlagRegistry.get().findSupported("share-xp").isPresent());
        Set<FlagKey> listed = FlagRegistry.get().supportedOf(alpha.getFlags()).keySet();
        assertFalse(listed.contains(CoreFlags.GLOW_MEMBERS.getKey()), listed.toString());
        assertFalse(listed.contains(CoreFlags.SHARE_XP.getKey()), listed.toString());
        assertTrue(listed.contains(CoreFlags.SHARE_MONEY.getKey()), listed.toString());
    }
}
