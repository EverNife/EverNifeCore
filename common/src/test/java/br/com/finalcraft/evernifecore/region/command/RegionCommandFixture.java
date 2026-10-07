package br.com.finalcraft.evernifecore.region.command;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.pageviewer.nav.PVExtraMessages;
import br.com.finalcraft.evernifecore.pageviewer.theme.ClassicPageTheme;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.stock.StockFlags;
import br.com.finalcraft.evernifecore.region.selection.SelectionSession;
import br.com.finalcraft.evernifecore.region.selection.SelectionSpec;
import br.com.finalcraft.evernifecore.region.selection.SelectionTool;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.nio.file.Path;

/**
 * Two owners of regions ({@code FakeProt} and {@code TheDuel}), the stock flags registered, the region messages loaded
 * and a command harness - what every region command test starts from.
 */
abstract class RegionCommandFixture {

    static final String WORLD = "world";

    @TempDirNobodyCleans
    Path tempDir;

    FinalCmdTestHarness harness;
    RegionWorld prot;
    RegionWorld duel;
    TestFPlayerSender steve;

    @BeforeEach
    void openRegionCommandWorld() {
        harness = Commands.harness("RegionCmd", tempDir.resolve("cmd"));
        prot = RegionWorld.open("FakeProt", tempDir.resolve("prot"));
        duel = RegionWorld.open("TheDuel", tempDir.resolve("duel"));
        StockFlags.registerAll(harness.ecPluginData);
        FCLocaleManager.loadLocale(harness.ecPluginData, ArgParserRegion.class, ArgParserRegionFlag.class, ArgParserFlagValue.class,
                RegionCommandNode.class, CoreRegionCommands.class, SelectionTool.class, SelectionSession.class,
                ClassicPageTheme.class, PVExtraMessages.class);
        steve = Senders.player("Steve").at(WORLD, 5, 5, 5);
    }

    @AfterEach
    void closeRegionCommandWorld() {
        SelectionTool.cancelAll();
        FlagRegistry.get().unregisterAll(harness.ecPluginData);
        duel.close();
        prot.close();
        harness.close();
    }

    static RegionFlag<?> flag(String name) {
        return FlagRegistry.get().find(name).orElseThrow(() -> new AssertionError("no flag " + name));
    }

    static Object valueOf(Region region, String flagName) {
        return region.getFlag(flag(flagName));
    }

    RegionCommandNode protNode() {
        return new RegionCommandNode(prot.getManager(), SelectionSpec.cuboid());
    }

    /** {@code /fp region ...} over {@code node}, as a plugin that owns regions would mount it. */
    @FinalCMD(aliases = "fp")
    public static class Fp_Cmd {
        @FinalCMD.Node(subcmd = "region", permission = "fp.region")
        final RegionCommandNode regions;

        Fp_Cmd(RegionCommandNode regions) {
            this.regions = regions;
        }
    }
}
