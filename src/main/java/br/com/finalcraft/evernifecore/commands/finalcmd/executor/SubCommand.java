package br.com.finalcraft.evernifecore.commands.finalcmd.executor;

import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLine;

import java.util.ArrayList;
import java.util.Arrays;

public class SubCommand {
    public int indice;
    public String subCmdName;
    public String permission;
    public ArrayList<String> textAvant;
    public ExecutorInterpreter executorInterpreter;
    public br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD.SubCMD finalSubCMD;
    public HelpLine helpLine;

    public SubCommand(ExecutorInterpreter executorInterpreter, int indice, String text, String permission, String... textAvant) {
        this.executorInterpreter = executorInterpreter;
        this.finalSubCMD = executorInterpreter.method.getAnnotation(br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD.SubCMD.class);
        this.indice = indice;
        this.subCmdName = text;
        this.permission = permission;
        if (textAvant == null || textAvant.length < 1) {
            this.textAvant = null;
        } else {
            this.textAvant = Arrays.stream(textAvant).collect(ArrayList::new,
                    ArrayList::add,
                    ArrayList::addAll);
        }
    }

    public String getSubCmdName() {
        return subCmdName;
    }

    public int getIndice() {
        return indice;
    }

    public ExecutorInterpreter getExecutorInterpreter() {
        return executorInterpreter;
    }

    public br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD.SubCMD getFinalSubCMD() {
        return finalSubCMD;
    }
}
