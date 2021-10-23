package br.com.finalcraft.evernifecore.commands.debug;


import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.UUID;

public class CMDBlockInfo {

    public static HashSet<UUID> INFO_HASHSET = new HashSet<>();

    @FCLocale(lang = LocaleType.EN_US, text = "§2§l ▶ §aINFO mode Enabled!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§2§l ▶ §aINFO mode Ativado!")
    private static LocaleMessage INFO_MODE_ENABLED;

    @FCLocale(lang = LocaleType.EN_US, text = "§c§l ▶ §eINFO mode Disabled!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§c§l ▶ §eINFO mode Desativado!")
    private static LocaleMessage INFO_MODE_DISABLED;

    @FinalCMD(
            aliases = {"blockinfo"},
            permission = PermissionNodes.EVERNIFECORE_COMMAND_BLOCKINFO
    )
    public void onCommand(Player player, String label, MultiArgumentos argumentos) {
        UUID uuid = player.getUniqueId();
        if (INFO_HASHSET.contains(uuid)){
            INFO_HASHSET.remove(uuid);
            INFO_MODE_DISABLED.send(player);
        }else {
            INFO_HASHSET.add(uuid);
            INFO_MODE_ENABLED.send(player);
        }
    }

    public static boolean isInDebugMode(Player player){
        if (INFO_HASHSET.isEmpty()) return false;
        return INFO_HASHSET.contains(player.getUniqueId());
    }

}
