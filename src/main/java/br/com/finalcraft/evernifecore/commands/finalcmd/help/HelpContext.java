package br.com.finalcraft.evernifecore.commands.finalcmd.help;

import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDPluginCommand;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class HelpContext {

    private final String helpHeader;
    private final List<HelpLine> helpLines;
    private final FinalCMDPluginCommand finalCMDPluginCommand;

    public HelpContext(FinalCMDPluginCommand finalCMDPluginCommand) {
        this.finalCMDPluginCommand = finalCMDPluginCommand;
        this.helpHeader = "";

        Collections.sort(finalCMDPluginCommand.helpLineList, Comparator.comparing(o -> o.getFancyText().getText()));

        this.helpLines = Collections.unmodifiableList(finalCMDPluginCommand.helpLineList);
    }

    public List<HelpLine> getHelpLines() {
        return helpLines;
    }

    public HelpLine getHelpLine(int index){
        return helpLines.get(index);
    }

    public int size(){
        return helpLines.size();
    }

    public void sendTo(CommandSender sender, String label){
        sender.sendMessage("§2§m-----------------------------------------------------");

        for (HelpLine helpLine : helpLines) {
            if (!helpLine.getPermission().isEmpty() && !sender.hasPermission(helpLine.getPermission())){
                continue;
            }
            helpLine.setLabelUsed(label).getFancyText().send(sender);
        }

        sender.sendMessage("");
        sender.sendMessage("§3§oPasse o mouse em cima dos comandos para ver a descrição!");
        sender.sendMessage("§2§m-----------------------------------------------------");
    }
}
