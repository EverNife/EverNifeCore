package br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true, chain = true)
@AllArgsConstructor
public class ArgData {

    private String name;
    private String context;
    private Class<? extends ArgParser> parser;

    public ArgData(Arg arg) {
        this.name = arg.name();
        this.context = arg.context();
        this.parser = arg.parser();
    }
}