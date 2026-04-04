package br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.ContextualArg;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@AllArgsConstructor
public class ArgContextualData {

    private String name;
    private String context;
    private Class<? extends ArgParserContextual> parser;

    public ArgContextualData(ContextualArg arg) {
        this.name = arg.name();
        this.context = arg.context();
        this.parser = arg.parser();
    }

    public ArgContextualData() {
        this.name = "";
        this.context = "";
        this.parser = ArgParserContextual.class;
    }

    public ArgContextualData replace(String placeholder, String value){
        this.name = this.name.replace(placeholder, value);
        this.context = this.context.replace(placeholder, value);
        return this;
    }

    public ArgContextualData replace(CompoundReplacer replacer){
        this.name = replacer.apply(this.name);
        this.context = replacer.apply(this.context);
        return this;
    }
}
