package br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.CMDHelpType;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.util.FCTextUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;


@Getter
@Setter
@Accessors(fluent = true, chain = true)
public class FinalCMDData extends CMDData {

    private String helpHeader;
    private CMDHelpType useDefaultHelp;

    public FinalCMDData(FinalCMD finalCMD) {
        super(finalCMD.aliases(),
                finalCMD.usage(),
                finalCMD.desc(),
                finalCMD.permission(),
                finalCMD.locales()
        );
        this.helpHeader = finalCMD.helpHeader();
        this.useDefaultHelp = finalCMD.useDefaultHelp();

        if (!this.helpHeader.isEmpty()){
            this.helpHeader = FCTextUtil.alignCenter(this.helpHeader, "§2§m-§r");
        }
    }
}
