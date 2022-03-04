package br.com.finalcraft.evernifecore.commands.finalcmd.help;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HelpLine {

    private final LocaleMessageImp localeMessage;
    private final String permission;
    private transient String label = null;
    private transient String subCMDLabel = null;

    public HelpLine(LocaleMessageImp localeMessage, String permission) {
        this.localeMessage = localeMessage;
        this.permission = permission;
    }

    @Deprecated
    public FancyText getFancyText() {
        return this.localeMessage.getDefaultFancyText();
    }

    public LocaleMessage getLocaleMessage() {
        return this.localeMessage;
    }

    public String getPermission(){
        return permission;
    }

    public void sendTo(CommandSender sender){
        this.localeMessage
                .addPlaceholder("%label%", label)
                .addPlaceholder("%subcmd%", subCMDLabel)
                .send(sender);
    }

    public HelpLine setLabelsUsed(@NotNull String label, @Nullable String subCMDLabel){
        this.label = label;
        this.subCMDLabel = subCMDLabel;
        return this;
    }
}
