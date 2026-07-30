package br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.AbstractArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ResolutionPhase;
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
    /**
     * Widened to the shared root so the contextual family shares this class: the narrow bound stays on
     * each annotation, which is where a developer picking the wrong family gets told about it.
     */
    private Class<? extends AbstractArgParser> parser;
    private FCLocaleData[] locales = new FCLocaleData[0];
    private String def = "";
    private String[] aliases = new String[0];
    private String usageName = "";
    private String permission = "";
    private boolean showOnUsage = true;
    private boolean fromSender = false;
    /** Contextual only: when the parameter resolves, or {@code PARSER_DEFAULT} to let its parser say. */
    private ResolutionPhase phase = ResolutionPhase.PARSER_DEFAULT;

    public ArgData(Arg arg) {
        this.name = arg.value();
        this.context = arg.context();
        this.parser = arg.parser();
        this.locales = Arrays.stream(arg.locales())
                .map(FCLocaleData::new)
                .collect(Collectors.toList())
                .toArray(new FCLocaleData[0]);
        this.def = arg.def();
        this.fromSender = arg.fromSender();
    }

    public ArgData(Arg.Flag arg) {
        this.name = arg.value();
        this.context = arg.context();
        this.parser = arg.parser();
        this.locales = Arrays.stream(arg.locales())
                .map(FCLocaleData::new)
                .collect(Collectors.toList())
                .toArray(new FCLocaleData[0]);
        this.def = arg.def();
        this.aliases = arg.aliases();
        this.usageName = arg.usageName();
        this.permission = arg.permission();
        this.showOnUsage = arg.showOnUsage();
    }

    public ArgData(Arg.Contextual arg) {
        this.name = arg.value();
        this.context = arg.context();
        this.parser = arg.parser();
        this.phase = arg.phase();
    }

    public ArgData() {
        name = "";
        context = "";
        parser = ArgParser.class;
    }

    /**
     * The metadata of a parameter that declares no annotation at all. Its parser is the contextual
     * sentinel, because "nothing was declared" is exactly what sends the lookup to the manager.
     */
    public static ArgData ofUnannotatedParameter() {
        return new ArgData().setParser(ArgParserContextual.class);
    }

    public ArgData replace(String placeholder, String value){
        this.name = this.name.replace(placeholder, value);
        this.context = this.context.replace(placeholder, value);
        this.def = this.def.replace(placeholder, value);
        this.usageName = this.usageName.replace(placeholder, value);
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
        this.usageName = replacer.apply(this.usageName);
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
