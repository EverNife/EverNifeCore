package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.custom.ICustomFinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.custom.contexts.CustomizeContext;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCServerUtil;
import jakarta.annotation.Nonnull;

public class CMDAlias implements ICustomFinalCMD {

    //Class-level template, loaded once at registration (see FCLocaleManager.loadLocale, called
    //from FinalCMDManager.registerCommand BEFORE customize()); customize() below never sends this
    //field directly, it only derives a per-instance copy with %the_command% baked in.
    @FCLocale(lang = LocaleType.EN_US, text = "Alias for the command: [%the_command%]")
    @FCLocale(lang = LocaleType.PT_BR, text = "Atalho para o comando: [%the_command%]")
    private static LocaleMessageImp DESCRIPTION;

    private final String[] aliases;
    private final String theCommand;

    public CMDAlias(String alias, String theCommand) {
        this.aliases = new String[]{alias};
        this.theCommand = theCommand;
    }

    public CMDAlias(String[] aliases, String theCommand) {
        this.aliases = aliases;
        this.theCommand = theCommand;
    }

    @FinalCMD(
            aliases = ""
    )
    public void onAliasExecution(FCommandSender sender, MultiArgumentos argumentos) {
        FCServerUtil.makePlayerExecuteCommand(sender,
                theCommand + " " + String.join(" ", argumentos.getStringArgs())
        );
    }

    @Override
    public void customize(@Nonnull CustomizeContext context) {
        context.getFinalCMDData().setLabels(this.aliases);
        context.getFinalCMDData().setDescriptionOverride(
                DESCRIPTION.derivePlaceholderResolved("%the_command%", theCommand)
        );
    }

}
