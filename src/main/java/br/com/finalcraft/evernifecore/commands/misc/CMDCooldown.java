package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLine;
import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import org.bukkit.command.CommandSender;

@FinalCMD(
        aliases = {"cooldown", "cooldowns"},
        permission = PermissionNodes.EVERNIFECORE_COMMAND_COOLDOWN
)
public class CMDCooldown {

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThe cooldown §7[§2%cooldown%§7]§c is not in cooldown!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cO cooldown §7[§2%cooldown%§7]§c não está em cooldown!")
    private static LocaleMessage COOLDOWN_NOT_IN_COOLDOWN; //:V

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThe cooldown §7[§2%cooldown%§7]§c was successfully removed!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cO cooldown §7[§2%cooldown%§7]§c foi removido com sucesso!")
    private static LocaleMessage COOLDOWN_REMOVED;

    @FinalCMD.SubCMD(
            subcmd = "reset",
            usage = "%name% <CooldownID>",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Reset an specific cooldown!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Reseta um cooldown especifico!")
            }
    )
    public void reset(CommandSender sender, MultiArgumentos argumentos, HelpLine helpLine) {

        if (argumentos.emptyArgs(1)){
            helpLine.sendTo(sender);
            return;
        }

        Cooldown cooldown = Cooldown.of(argumentos.getStringArg(1));
        if (!cooldown.isInCooldown()){
            COOLDOWN_NOT_IN_COOLDOWN.addPlaceholder("%cooldown%", cooldown.getIdentifier()).send(sender);
            return;
        }

        cooldown.stop();
        COOLDOWN_REMOVED.addPlaceholder("%cooldown%", cooldown.getIdentifier()).send(sender);
    }

    @FinalCMD.SubCMD(
            subcmd = "resetplayer",
            usage = "%name% <player> <CooldownID>",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Reset an specific player cooldown!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Reseta um cooldown especifico de um jogador!")
            }
    )
    public void resetPlayerCooldown(CommandSender sender, MultiArgumentos argumentos, HelpLine helpLine) {

        if (argumentos.emptyArgs(1,2)){
            helpLine.sendTo(sender);
            return;
        }

        PlayerData playerData = argumentos.get(1).getPlayerData();

        if (playerData == null){
            FCMessageUtil.playerDataNotFound(sender, argumentos.getStringArg(1));
            return;
        }

        Cooldown cooldown = Cooldown.of(argumentos.getStringArg(2));
        if (!cooldown.isInCooldown()){
            COOLDOWN_NOT_IN_COOLDOWN.addPlaceholder("%cooldown%", cooldown.getIdentifier()).send(sender);
            return;
        }

        cooldown.stop();
        COOLDOWN_REMOVED.addPlaceholder("%cooldown%", cooldown.getIdentifier()).send(sender);
        return;
    }



    @FinalCMD.SubCMD(
            subcmd = "reload",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Reload all generic cooldown (NonPlayer) !"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Recarrega todos os cooldowns genéricos (NonPlayer)!")
            }
    )
    public void reload(CommandSender sender) {
        ConfigManager.reloadCooldownConfig();
        sender.sendMessage("§2§l ▶ §aTodos os Cooldowns foram recarregados com sucesso!");
    }
}
