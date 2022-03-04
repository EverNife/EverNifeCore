package br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;


@Getter
@Setter
@Accessors(fluent = true, chain = true)
public class SubCMDData extends CMDData {

    public SubCMDData(FinalCMD.SubCMD subCMD) {
        super(subCMD.subcmd(),
                subCMD.usage(),
                subCMD.desc(),
                subCMD.permission(),
                subCMD.locales()
        );
    }

}
