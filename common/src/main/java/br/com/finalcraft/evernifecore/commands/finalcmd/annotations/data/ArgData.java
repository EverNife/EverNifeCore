package br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FlagArg;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.stream.Collectors;

@Getter
@Setter
@Accessors(chain = true)
@AllArgsConstructor
public class ArgData {

    private String name;
    private String context;
    private Class<? extends ArgParser> parser;
    private FCLocaleData[] locales = new FCLocaleData[0];
    private boolean flag = false;

    public ArgData(Arg arg) {
        this.name = arg.name();
        this.context = arg.context();
        this.parser = arg.parser();
        this.locales = Arrays.stream(arg.locales())
                .map(FCLocaleData::new)
                .collect(Collectors.toList())
                .toArray(new FCLocaleData[0]);
    }

    public ArgData(FlagArg arg) {
        this.name = arg.name();
        this.context = arg.context();
        this.parser = arg.parser();
        this.locales = Arrays.stream(arg.locales())
                .map(FCLocaleData::new)
                .collect(Collectors.toList())
                .toArray(new FCLocaleData[0]);
    }

    public ArgData() {
        name = "";
        context = "";
        parser = ArgParser.class;
    }

    public ArgData replace(String placeholder, String value){
        this.name = this.name.replace(placeholder, value);
        this.context = this.context.replace(placeholder, value);
        for (FCLocaleData locale : this.locales) {
            locale.replace(placeholder, value);
        }
        return this;
    }

    public ArgData replace(CompoundReplacer replacer){
        this.name = replacer.apply(this.name);
        this.context = replacer.apply(this.context);
        for (FCLocaleData locale : this.locales) {
            locale.replace(replacer);
        }
        return this;
    }
}
