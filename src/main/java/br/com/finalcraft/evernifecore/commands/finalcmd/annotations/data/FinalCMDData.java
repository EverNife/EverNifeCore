package br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.CMDHelpType;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import br.com.finalcraft.evernifecore.util.FCTextUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.stream.Collectors;


@Getter
@Setter
@Accessors(fluent = true, chain = true)
public class FinalCMDData extends CMDData<FinalCMDData> {

    private String helpHeader;
    private CMDHelpType useDefaultHelp;

    public FinalCMDData(FinalCMD finalCMD) {
        super(finalCMD.aliases(),
                finalCMD.usage(),
                finalCMD.desc(),
                finalCMD.permission(),
                finalCMD.context(),
                Arrays.stream(finalCMD.locales())
                        .map(FCLocaleData::new)
                        .collect(Collectors.toList())
                        .toArray(new FCLocaleData[0])
        );
        this.helpHeader = finalCMD.helpHeader();
        this.useDefaultHelp = finalCMD.useDefaultHelp();

        if (!this.helpHeader.isEmpty()){
            this.helpHeader = FCTextUtil.alignCenter(this.helpHeader, "§2§m-§r");
        }
    }

    public FinalCMDData() {
        super();
        this.helpHeader = "";
        this.useDefaultHelp = CMDHelpType.FULL;
    }

    @Override
    public FinalCMDData override(FinalCMDData override) {
        if (!override.helpHeader().isEmpty()) this.helpHeader = override.helpHeader();
        if (override.useDefaultHelp() != CMDHelpType.FULL) this.useDefaultHelp = override.useDefaultHelp();
        return super.override(override);
    }
}
