package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.implementation.McFinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.minecraft.loader.imp.McPlatform;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Commands as the server's command map holds them. Registering and unregistering take a different route on
 * Arclight (the command tree is re-synced), from 1.19 (the command has to be told it left the map) and for the
 * {@code minecraft:} aliases a hybrid adds - so the map is read, not the framework's own bookkeeping.
 */
public final class CommandsSuite implements ConformanceSuite {

    private static final String THROWAWAY = "ecctmp";

    @Override
    public String getId() {
        return "commands";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                ConformanceCase.of("registered", () -> {
                    Map<String, Command> known = McPlatform.getCommandMapKnownCommands();
                    Command ecc = known.get("ecc");
                    Command full = known.get("ecoreconformance");
                    return Verdict.of(ecc instanceof McFinalCMDPluginCommand && ecc == full,
                            "ecc=" + describe(ecc) + " ecoreconformance=" + describe(full));
                }),
                ConformanceCase.of("dispatch", () -> {
                    boolean handled = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ecc list");
                    return Verdict.of(handled, "dispatchCommand(console, \"ecc list\")=" + handled);
                }),
                ConformanceCase.of("tab-complete", () -> {
                    Command ecc = McPlatform.getCommandMapKnownCommands().get("ecc");
                    List<String> offered = ecc.tabComplete(Bukkit.getConsoleSender(), "ecc", new String[]{""});
                    return Verdict.of(offered.containsAll(Arrays.asList("list", "run", "write", "read")), "offered=" + offered);
                }),
                ConformanceCase.of("register-twice", CommandsSuite::registerTwice),
                ConformanceCase.of("playerlist-is-the-core", () -> {
                    Command playerlist = McPlatform.getCommandMapKnownCommands().get("playerlist");
                    String owner = playerlist instanceof PluginIdentifiableCommand
                            ? ((PluginIdentifiableCommand) playerlist).getPlugin().getName() : null;
                    return Verdict.of(playerlist instanceof McFinalCMDPluginCommand && "EverNifeCore".equals(owner),
                            "playerlist=" + describe(playerlist) + " owner=" + owner);
                }));
    }

    /** The second registration of the same label has to win, with the first one gone from the map. */
    private static Verdict registerTwice() {
        ECPluginData plugin = ECPluginManager.getOrCreateECorePluginData(Bukkit.getPluginManager().getPlugin("ECoreConformance"));
        try {
            FinalCMDManager.registerCommand(plugin, new Throwaway());
            Command first = McPlatform.getCommandMapKnownCommands().get(THROWAWAY);
            FinalCMDManager.registerCommand(plugin, new Throwaway());
            Command second = McPlatform.getCommandMapKnownCommands().get(THROWAWAY);
            return Verdict.of(first != null && second != null && first != second,
                    "first=" + describe(first) + " second=" + describe(second) + " replaced=" + (first != second));
        } finally {
            FinalCMDManager.unregisterCommand(THROWAWAY, plugin);
        }
    }

    private static String describe(Command command) {
        return command == null ? "absent" : command.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(command));
    }

    @FinalCMD(aliases = THROWAWAY, permission = "evernifecore.conformance")
    public static final class Throwaway {
        @FinalCMD.SubCMD(subcmd = "noop")
        public void noop(FCommandSender sender) {
        }
    }
}
