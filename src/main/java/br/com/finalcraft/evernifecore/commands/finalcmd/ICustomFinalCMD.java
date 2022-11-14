package br.com.finalcraft.evernifecore.commands.finalcmd;

import br.com.finalcraft.evernifecore.commands.finalcmd.executor.contexts.CustomizeContext;
import org.jetbrains.annotations.NotNull;

public interface ICustomFinalCMD {

    void customize(@NotNull CustomizeContext context);

}
