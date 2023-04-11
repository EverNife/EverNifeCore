package br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.CMDHelpType;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import br.com.finalcraft.evernifecore.util.FCTextUtil;

import java.util.Arrays;
import java.util.stream.Collectors;


public class FinalCMDData extends CMDData<FinalCMDData> {

    private String helpHeader;
    private CMDHelpType helpType;

    public FinalCMDData(FinalCMD finalCMD) {
        super(finalCMD.aliases(),
                finalCMD.usage(),
                finalCMD.desc(),
                finalCMD.permission(),
                finalCMD.context(),
                FCReflectionUtil.getConstructor(finalCMD.validation()).invoke(),
                Arrays.stream(finalCMD.locales())
                        .map(FCLocaleData::new)
                        .collect(Collectors.toList())
                        .toArray(new FCLocaleData[0])
        );
        this.helpHeader = finalCMD.helpHeader();
        this.helpType = finalCMD.useDefaultHelp();

        if (!this.helpHeader.isEmpty()){
            this.helpHeader = FCTextUtil.alignCenter(this.helpHeader, "§2§m-§r");
        }
    }

    public FinalCMDData() {
        super();
        this.helpHeader = "";
        this.helpType = CMDHelpType.FULL;
    }

    public String getHelpHeader() {
        return helpHeader;
    }

    public CMDHelpType getHelpType() {
        return helpType;
    }

    @Override
    public FinalCMDData override(FinalCMDData override) {
        if (!override.getHelpHeader().isEmpty()) this.helpHeader = override.getHelpHeader();
        if (override.getHelpType() != CMDHelpType.FULL) this.helpType = override.getHelpType();
        return super.override(override);
    }
}
