package br.com.finalcraft.evernifecore.logic.command;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logic.audit.LogicTrace;
import jakarta.annotation.Nullable;

import java.util.Optional;

/** {@code /ecore logic trace}: what an admin turns on to watch a player's gates, costs and effects as they run. */
public class CoreLogicCommands {

    @FinalCMD.SubCMD(subcmd = "trace", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Toggle a trace of every gate, cost and effect run for a player, optionally of one plugin"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Liga/desliga o trace de todo gate, custo e efeito de um jogador, opcionalmente de um plugin")
    })
    public void trace(FCommandSender sender, @Arg("<player>") FPlayer target, @Arg("[plugin]") String plugin) {
        String watcher = watcherId(sender);
        Optional<LogicTrace.Watch> current = LogicTrace.get(watcher);
        if (current.isPresent() && current.get().getPlayer().equals(target.getUniqueId()) && samePlugin(current.get().getPlugin(), plugin)) {
            LogicTrace.stop(watcher);
            sender.sendMessage("§eLogic trace of §a" + target.getName() + "§e: §coff");
            return;
        }
        LogicTrace.start(watcher, target.getUniqueId(), plugin, sender::sendMessage);
        sender.sendMessage("§eLogic trace of §a" + target.getName() + "§e: §aon§7"
                + (plugin == null ? " (every plugin)" : " (only " + plugin + ")")
                + " - run the same command again to stop it" + (current.isPresent() ? "; it replaces the trace you had." : "."));
    }

    /** A player watches by uuid, so its trace ends when it quits; the console by its name. */
    public static String watcherId(FCommandSender sender) {
        return sender.isPlayer() ? sender.getUniqueId().toString() : "console:" + sender.getName();
    }

    private static boolean samePlugin(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }
}
