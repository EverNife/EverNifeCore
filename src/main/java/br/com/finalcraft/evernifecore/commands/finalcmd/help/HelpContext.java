package br.com.finalcraft.evernifecore.commands.finalcmd.help;

import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import com.google.common.collect.ImmutableList;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.stream.Collectors;

public class HelpContext {

    @FCLocale(lang = LocaleType.EN_US, text = "§3§oMove the mouse over the commands to see their description!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§3§oPasse o mouse em cima dos comandos para ver a descrição!")
    public static LocaleMessage HOLD_MOUSE_OVER;

    private final String helpHeader;
    private final FinalCMDPluginCommand finalCMDPluginCommand;
    private final List<HelpLine> helpLines;

    private transient String lastLabel;

    public HelpContext(String helpHeader, FinalCMDPluginCommand finalCMDPluginCommand) {
        this.helpHeader = helpHeader;
        this.finalCMDPluginCommand = finalCMDPluginCommand;
        this.helpLines = ImmutableList.copyOf(finalCMDPluginCommand.subCommands.stream().map(CMDMethodInterpreter::getHelpLine).collect(Collectors.toList()));
        this.lastLabel = finalCMDPluginCommand.getLabel();
    }

    public String getHelpHeader() {
        return helpHeader;
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

    public void setLastLabel(String lastLabel) {
        this.lastLabel = lastLabel;
    }

    public void sendTo(CommandSender sender){
        sendTo(sender, this.lastLabel);
    }

    public void sendTo(CommandSender sender, String label){
        sender.sendMessage(helpHeader.isEmpty() ? "§2§m-----------------------------------------------------" : helpHeader);

        for (CMDMethodInterpreter subCommand : finalCMDPluginCommand.subCommands) {
            if (!subCommand.getCmdData().permission().isEmpty() && !sender.hasPermission(subCommand.getCmdData().permission())){
                continue;
            }

            subCommand.getHelpLine().setLabelsUsed(label, subCommand.getCmdData().labels()[0]).sendTo(sender);
        }

        sender.sendMessage("");
        HOLD_MOUSE_OVER.send(sender);
        sender.sendMessage("§2§m-----------------------------------------------------");
    }
}
