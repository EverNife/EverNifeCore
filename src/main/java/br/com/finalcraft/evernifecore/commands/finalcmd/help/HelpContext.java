package br.com.finalcraft.evernifecore.commands.finalcmd.help;

import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import com.google.common.collect.ImmutableList;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class HelpContext {

    @FCLocale(lang = LocaleType.EN_US, text = "§3§oMove the mouse over the commands to see their description!", hover = "§7Move the mouse over the commands to see their description!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§3§oPasse o mouse em cima dos comandos para ver a descrição!", hover = "§7Passe o mouse em cima dos comandos para ver a descrição!")
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

        List<Runnable> helpLinesToSend = new ArrayList<>();

        boolean isPlayer = sender instanceof Player;

        for (CMDMethodInterpreter subCommand : finalCMDPluginCommand.subCommands) {
            if (!subCommand.getCmdData().getPermission().isEmpty() && !sender.hasPermission(subCommand.getCmdData().getPermission())){
                continue;
            }

            if (!isPlayer && subCommand.isPlayerOnly()){
                continue;
            }

            if (subCommand.getCmdData().getCmdAccessValidation().onPreTabValidation(new CMDAccessValidation.Context(subCommand, sender)) != true){
                continue;
            }

            helpLinesToSend.add(() -> {
                subCommand.getHelpLine().setLabelsUsed(label, subCommand.getCmdData().getLabels()[0]).sendTo(sender);
            });
        }

        if (helpLinesToSend.size() == 0){
            FCMessageUtil.needsThePermission(sender);
            return;
        }

        sender.sendMessage(helpHeader.isEmpty() ? "§2§m-----------------------------------------------------" : helpHeader);
        for (Runnable helpLine : helpLinesToSend) {
            helpLine.run();//send each line
        }
        sender.sendMessage("");
        HOLD_MOUSE_OVER.send(sender);
        sender.sendMessage("§2§m-----------------------------------------------------");
    }
}
