package br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data;

import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The segment a {@code @FinalCMD.Node} mount point declares. It carries no {@code usage}: a node's
 * line is always built from its own labels and its capture's arguments, never from free text.
 */
public class NodeData extends CMDData<NodeData> {

    public NodeData(FinalCMD.Node node) {
        super(node.subcmd(),
                "",
                node.permission(),
                node.context(),
                Arrays.stream(node.validation())
                        .map(aClass -> FCReflectionUtil.getConstructors().getConstructor(aClass).newInstance())
                        .collect(Collectors.toList())
                        .toArray(new CMDAccessValidation[0]),
                Arrays.stream(node.locales())
                        .map(FCLocaleData::new)
                        .collect(Collectors.toList())
                        .toArray(new FCLocaleData[0])
        );
    }

    public NodeData() {
        super();
    }
}
