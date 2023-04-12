package br.com.finalcraft.evernifecore.commands.debug;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import org.bukkit.command.CommandSender;

@FinalCMD(
        aliases = {"ecotest"}
)
public class CMDEcoTest {

    // -----------------------------------------------------------------------------------------------------------------------------//
    // GIVE
    // -----------------------------------------------------------------------------------------------------------------------------//

    @FCLocale(lang = LocaleType.EN_US, text = "§2§l ▶ §a$%amount% added to %receiver% account. New balance: §e$%balance%")
    @FCLocale(lang = LocaleType.PT_BR, text = "§2§l ▶ §a$%amount% adicionado ao jogador §e%receiver%§a. Novo Saldo: §e$%balance%")
    public static LocaleMessage GIVE_SUCCESS;

    @FCLocale(lang = LocaleType.EN_US, text = "§2§l ▶ §a$%amount% was added to your account. New balance: §e$%balance%")
    @FCLocale(lang = LocaleType.PT_BR, text = "§2§l ▶ §a$%amount% foi adicionado a sua conta. Novo Saldo: §e$%balance%")
    public static LocaleMessage MONEY_WAS_ADDED_TO_YOUR_ACCOUNT;

    @FinalCMD.SubCMD(
            subcmd = {"give","add"},
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "§bGive a specific amount of money to a player!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "§bDá uma quantidade específica de money para um jogador!")
            }
    )
    public void give(CommandSender sender, @Arg(name = "<Player>", locales = @FCLocale(text = "The Player Name")) PlayerData target, @Arg(name = "<Amount>", context = "[0.01:*]", locales = @FCLocale(text = "Amount to be Given!")) Double amount) {

    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // TAKE
    // -----------------------------------------------------------------------------------------------------------------------------//

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §c$%payer% does not have enought money. His balance: §e$%balance%")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §c$%payer% não possui dinheiro suficiente. Seu saldo é: §e$%balance%")
    public static LocaleMessage NOT_ENOUGH_MONEY;

    @FCLocale(lang = LocaleType.EN_US, text = "§2§l ▶ §a$%amount% taken from %payer%'s account. New balance: §e$%balance%")
    @FCLocale(lang = LocaleType.PT_BR, text = "§2§l ▶ §a$%amount% removido do jogador %payer%. Novo Saldo: §e$%balance%")
    public static LocaleMessage TAKE_SUCCESS;

    @FCLocale(lang = LocaleType.EN_US, text = "§2§l ▶ §e$%amount% was removed from your account. New balance: §e$%balance%")
    @FCLocale(lang = LocaleType.PT_BR, text = "§2§l ▶ §e$%amount% foi removido da sua conta. Novo Saldo: §e$%balance%")
    public static LocaleMessage MONEY_WAS_REMOVED_FROM_YOUR_ACCOUNT;

    @FinalCMD.SubCMD(
            subcmd = {"take","remove"},
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "§bRemove a specific amount of money from a player!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "§bRemove uma quantidade específica de money de um jogador!")
            }
    )
    public void take(CommandSender sender, @Arg(name = "<Player>") PlayerData target, @Arg(name = "<Amount>", context = "[0.01:*]") Double amount) {

    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // SET
    // -----------------------------------------------------------------------------------------------------------------------------//

    @FCLocale(lang = LocaleType.EN_US, text = "§2§l ▶ §a%player% balance's was set to §e$%balance%")
    @FCLocale(lang = LocaleType.PT_BR, text = "§2§l ▶ §aO Saldo do jogador %player% foi definido para §e$%balance%")
    public static LocaleMessage SET_SUCESS;

    @FCLocale(lang = LocaleType.EN_US, text = "§2§l ▶ §eYour balance was changed from $%old_balance% to $%new_balance%.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§2§l ▶ §eSeu saldo foi alterdo de $%old_balance% para $%new_balance%.")
    public static LocaleMessage MONEY_WAS_SET_FOR_YOUR_ACCOUNT;

    @FinalCMD.SubCMD(
            subcmd = {"set"},
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "§bSet a player's balance to a specific amount!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "§bDefina o saldo de um jogador para um valor específico!")
            }
    )
    public void set(CommandSender sender, @Arg(name = "<Player>") PlayerData target, @Arg(name = "<Amount>", context = "[0:*]") Double amount) {

    }

}
