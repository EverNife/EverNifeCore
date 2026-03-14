package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import lombok.Data;

@Data
public class ArgInfo {

    private final Class argumentType;
    private final ArgData argData;
    private final int index;
    private final ArgRequirementType requirementType;

    public ArgInfo(Class argumentType, ArgData argData, int index, ArgRequirementType requirementType) {
        this.argumentType = argumentType;
        this.argData = argData;
        this.index = index;
        this.requirementType = requirementType;
    }

    public boolean isRequired(){
        return requirementType.isRequired();
    }

    public boolean isProvidedByContext(){
        return requirementType.isProvidedByContext();
    }
}
