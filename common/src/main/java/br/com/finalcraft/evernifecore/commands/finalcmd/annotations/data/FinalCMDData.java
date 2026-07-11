package br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.CMDHelpType;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;

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
                Arrays.stream(finalCMD.validation())
                        .map(aClass -> FCReflectionUtil.getConstructors().getConstructor(aClass).newInstance())
                        .collect(Collectors.toList())
                        .toArray(new CMDAccessValidation[0]),
                Arrays.stream(finalCMD.locales())
                        .map(FCLocaleData::new)
                        .collect(Collectors.toList())
                        .toArray(new FCLocaleData[0])
        );
        this.helpHeader = finalCMD.helpHeader();
        this.helpType = finalCMD.useDefaultHelp();

        if (!this.helpHeader.isEmpty()){
            this.helpHeader = EverNifeCore.getPlatform().getChatAdapter().alignCenter(this.helpHeader, "§2§m-§r");
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
