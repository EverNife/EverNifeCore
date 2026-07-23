package br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data;

import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;

import java.util.Arrays;
import java.util.stream.Collectors;

public class SubCMDData extends CMDData<SubCMDData> {

    public SubCMDData(FinalCMD.SubCMD subCMD) {
        super(subCMD.subcmd(),
                subCMD.usage(),
                subCMD.permission(),
                subCMD.context(),
                Arrays.stream(subCMD.validation())
                        .map(aClass -> FCReflectionUtil.getConstructors().getConstructor(aClass).newInstance())
                        .collect(Collectors.toList())
                        .toArray(new CMDAccessValidation[0]),
                Arrays.stream(subCMD.locales())
                        .map(FCLocaleData::new)
                        .collect(Collectors.toList())
                        .toArray(new FCLocaleData[0])
        );
    }

    public SubCMDData() {
        super();
    }
}
