package br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data;

import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;

/**
 * The {@code @FinalCMD.Capture} method of a node. It is never reachable by name (the node's label is
 * what routes to it) and never permission-gated on its own - the node it belongs to already is - so
 * it only exists to give the capture method the same argument machinery every other command method
 * has.
 */
public class CaptureData extends CMDData<CaptureData> {

    public CaptureData(CMDData<?> nodeData) {
        super(nodeData.getLabels(), "", "", nodeData.getContext(), new CMDAccessValidation[0], new FCLocaleData[0]);
    }

    public CaptureData() {
        super();
    }
}
