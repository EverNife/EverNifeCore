package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgContextualData;
import lombok.Data;

@Data
public class ArgContextualInfo {

    private final Class argumentType;
    private final ArgContextualData argData;

    public ArgContextualInfo(Class argumentType, ArgContextualData argData) {
        this.argumentType = argumentType;
        this.argData = argData;
    }

}
