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
    private String def = "";
    private String[] aliases = new String[0];
    private String permission = "";
    private boolean showOnUsage = true;

    public ArgData(Arg arg) {
        this.name = arg.name();
        this.context = arg.context();
        this.parser = arg.parser();
        this.locales = Arrays.stream(arg.locales())
                .map(FCLocaleData::new)
                .collect(Collectors.toList())
                .toArray(new FCLocaleData[0]);
        this.def = arg.def();
    }

    public ArgData(FlagArg arg) {
        this.name = arg.name();
        this.context = arg.context();
        this.parser = arg.parser();
        this.locales = Arrays.stream(arg.locales())
                .map(FCLocaleData::new)
                .collect(Collectors.toList())
                .toArray(new FCLocaleData[0]);
        this.def = arg.def();
        this.aliases = arg.aliases();
        this.permission = arg.permission();
        this.showOnUsage = arg.showOnUsage();
    }

    public ArgData() {
        name = "";
        context = "";
        parser = ArgParser.class;
    }

    public ArgData replace(String placeholder, String value){
        this.name = this.name.replace(placeholder, value);
        this.context = this.context.replace(placeholder, value);
        this.def = this.def.replace(placeholder, value);
        this.permission = this.permission.replace(placeholder, value);
        for (int i = 0; i < this.aliases.length; i++) {
            this.aliases[i] = this.aliases[i].replace(placeholder, value);
        }
        for (FCLocaleData locale : this.locales) {
            locale.replace(placeholder, value);
        }
        return this;
    }

    public ArgData replace(CompoundReplacer replacer){
        this.name = replacer.apply(this.name);
        this.context = replacer.apply(this.context);
        this.def = replacer.apply(this.def);
        this.permission = replacer.apply(this.permission);
        for (int i = 0; i < this.aliases.length; i++) {
            this.aliases[i] = replacer.apply(this.aliases[i]);
        }
        for (FCLocaleData locale : this.locales) {
            locale.replace(replacer);
        }
        return this;
    }
}
