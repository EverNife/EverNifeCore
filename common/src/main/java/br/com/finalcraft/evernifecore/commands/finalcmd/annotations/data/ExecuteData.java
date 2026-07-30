package br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data;

import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The executable of a node ({@code @FinalCMD.Execute}). It has no labels of its own - the node IS
 * the label - so it inherits the mount point's, and falls back to the node's permission when it
 * declares none.
 */
public class ExecuteData extends CMDData<ExecuteData> {

    public ExecuteData(FinalCMD.Execute execute, CMDData<?> nodeData) {
        super(nodeData.getLabels(),
                "",
                execute.permission().isEmpty() ? nodeData.getPermission() : execute.permission(),
                execute.context(),
                Arrays.stream(execute.validation())
                        .map(aClass -> FCReflectionUtil.getConstructors().getConstructor(aClass).newInstance())
                        .collect(Collectors.toList())
                        .toArray(new CMDAccessValidation[0]),
                Arrays.stream(execute.locales())
                        .map(FCLocaleData::new)
                        .collect(Collectors.toList())
                        .toArray(new FCLocaleData[0])
        );
    }

    public ExecuteData() {
        super();
    }
}
