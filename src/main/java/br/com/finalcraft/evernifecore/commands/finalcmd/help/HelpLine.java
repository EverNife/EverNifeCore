package br.com.finalcraft.evernifecore.commands.finalcmd.help;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import org.bukkit.command.CommandSender;

public class HelpLine {

    private final FancyText fancyTextOriginal;
    private final String permission;

    private FancyText fancyText;

    private String lastLabel = "";

    public HelpLine(FancyText fancyText, String permission) {
        this.fancyTextOriginal = fancyText;
        this.permission = permission;
        this.fancyText = fancyText.clone();
    }

    public FancyText getFancyText() {
        return fancyText;
    }

    public String getPermission(){
        return permission;
    }

    public void sendTo(CommandSender sender){
        this.fancyText.send(sender);
    }

    public HelpLine setLabelUsed(String label){
        if (!label.equals(lastLabel)){
            this.fancyText = fancyTextOriginal.clone().replace("%label%", label);
            this.lastLabel = label;
        }
        return this;
    }
}
