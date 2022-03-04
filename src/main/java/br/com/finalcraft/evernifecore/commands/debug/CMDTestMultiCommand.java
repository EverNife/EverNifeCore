package br.com.finalcraft.evernifecore.commands.debug;


import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@FinalCMD(
        aliases = {"testcmd","testcmdalternative"},
        permission = PermissionNodes.EVERNIFECORE_COMMAND_ITEMINFO
)
public class CMDTestMultiCommand {

    @FinalCMD.SubCMD(
            subcmd = {"sub1"},
            usage = "%name% <add|remove> <OnlinePlayer>",
            permission = PermissionNodes.EVERNIFECORE_COMMAND_ITEMINFO,
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Go USA!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Vai Brasil!")
            }
    )
    public void sub1(CommandSender sender) {
        sender.sendMessage("Hola Manito 1");
    }

    @FinalCMD.SubCMD(
            subcmd = {"sub2"},
            usage = "%name% <add|remove|xabulengs> <Player>",
            desc = "Vai Cabrau",
            permission = PermissionNodes.EVERNIFECORE_COMMAND_ITEMINFO
    )
    public void sub2(CommandSender sender) {
        sender.sendMessage("Hola Manito 2");
    }

    @FinalCMD.SubCMD(
            subcmd = {"eco"},
            permission = PermissionNodes.EVERNIFECORE_COMMAND_ITEMINFO
    )
    public void eco(CommandSender sender,
                           @Arg(name = "<add|ReMoVe>") String operation,
                           @Arg(name = "<Player>") PlayerData target,
                           @Arg(name = "<Amount>", context = "[0:100]") Integer amount) {

        sender.sendMessage("Operation [" + operation + "] applied to " + target.getUniqueId() + " --> ValueOf " + amount);
    }

    @FinalCMD.SubCMD(
            subcmd = {"ecoonline"},
            permission = PermissionNodes.EVERNIFECORE_COMMAND_ITEMINFO
    )
    public void ecoonline(CommandSender sender,
                    @Arg(name = "<add|ReMoVe>") String operation,
                    @Arg(name = "<Player>", context = "online") PlayerData target,
                    @Arg(name = "<Amount>", context = "[0:1000]") Integer amount) {

        sender.sendMessage("Operation [" + operation + "] applied to " + target.getUniqueId() + " --> ValueOf " + amount);
    }

    @FinalCMD.SubCMD(
            subcmd = {"ban"},
            permission = PermissionNodes.EVERNIFECORE_COMMAND_ITEMINFO
    )
    public void ban(CommandSender sender,
                           @Arg(name = "<a|b|c|d|e|f|g>") String operation1,
                           @Arg(name = "<--a|--b|--c|--d|--e|--f|--g>") String operation2,
                           @Arg(name = "<Player>") Player target,
                           @Arg(name = "<Amount>", context = "[-1:1]") Double amount) {
        target.sendMessage("Operation [" + operation1 + " and " + operation2 + "] applied to " + target.getUniqueId() + " --> ValueOf " + amount);
    }

}
