package br.com.finalcraft.evernifecore.commands.finalcmd;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.FinalCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.SubCMDData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ICustomFinalCMD {

    void customize(@NotNull FinalCMDData finalCMDData, @NotNull List<SubCMDData> subCMDData);

}
