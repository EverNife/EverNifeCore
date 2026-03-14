package br.com.finalcraft.evernifecore.locale.data;

import br.com.finalcraft.evernifecore.fancytext.ClickActionType;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.stream.Collectors;

@Getter
@Setter
@Accessors(fluent = true, chain = true)
@AllArgsConstructor
public class FCLocaleData {

    private String text;
    private String hover;
    private String runCommand;
    private ClickActionType clickActionType;
    private String lang;
    private Child[] children;

    public FCLocaleData() {
        text = "";
        hover = "";
        runCommand = "";
        clickActionType = ClickActionType.RUN_COMMAND;
        lang = LocaleType.EN_US;
        children = new Child[0];
    }

    public FCLocaleData(FCLocale locale) {
        this.text = locale.text();
        this.hover = locale.hover();
        this.runCommand = locale.runCommand();
        this.clickActionType = locale.clickActionType();
        this.lang = LocaleType.normalize(locale.lang()); //If "eN_uS" is passed, it will be normalized to "EN_US"
        this.children = Arrays.stream(locale.children()).map(FCLocaleData.Child::new).collect(Collectors.toList()).toArray(new Child[0]);
    }

    public FCLocaleData replace(String placeholder, String value){
        this.text = this.text.replace(placeholder, value);
        this.hover = this.hover.replace(placeholder, value);
        this.runCommand = this.runCommand.replace(placeholder, value);
        for (Child child : this.children) {
            child.replace(placeholder, value);
        }
        return this;
    }

    public FCLocaleData replace(CompoundReplacer replacer){
        this.text = replacer.apply(this.text);
        this.hover = replacer.apply(this.hover);
        this.runCommand = replacer.apply(this.runCommand);
        for (Child child : this.children) {
            child.replace(replacer);
        }
        return this;
    }

    @Getter
    @Setter
    @Accessors(fluent = true, chain = true)
    public static class Child {

        private String text;
        private String hover;
        private String runCommand;
        private ClickActionType clickActionType;

        public Child() {
            text = "";
            hover = "";
            runCommand = "";
            clickActionType = ClickActionType.RUN_COMMAND;
        }

        public Child(FCLocale.Child child) {
            this.text = child.text();
            this.hover = child.hover();
            this.runCommand = child.runCommand();
            this.clickActionType = child.clickActionType();
        }

        public Child replace(String placeholder, String value){
            this.text = this.text.replace(placeholder, value);
            this.hover = this.hover.replace(placeholder, value);
            this.runCommand = this.runCommand.replace(placeholder, value);
            return this;
        }

        public Child replace(CompoundReplacer replacer){
            this.text = replacer.apply(this.text);
            this.hover = replacer.apply(this.hover);
            this.runCommand = replacer.apply(this.runCommand);
            return this;
        }
    }
}
