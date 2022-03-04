package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import lombok.Data;

@Data
public class ArgInfo {

    private final Class argumentType;
    private final ArgData argData;
    private final int index;
    private final boolean required;

    public ArgInfo(Class argumentType, ArgData argData, int index) {
        this.argumentType = argumentType;
        this.argData = argData;
        this.index = index;

        ArgRequirementType type = ArgRequirementType.getArgumentType(argData.name());
        if (type == null){
            throw new ArgMountException("Failed to load ArgRequirementType from ArgData [" + argData.name() + "]");
        }

        this.required = type == ArgRequirementType.REQUIRED;
    }

}
