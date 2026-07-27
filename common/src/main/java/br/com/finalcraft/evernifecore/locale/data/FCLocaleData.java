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

/**
 * The mutable form of one {@link FCLocale} declaration. The accessors are fluent on purpose: this
 * type mirrors the annotation element for element, so {@code data.hover()} reads exactly like the
 * {@code hover()} of the annotation it was built from, and a call site can be moved between the two
 * without rewording. That is also why these names cannot be "fixed" independently - the annotation's
 * element names are public contract of every {@code @FCLocale} ever written.
 */
@Getter
@Setter
@Accessors(fluent = true, chain = true)
@AllArgsConstructor
public class FCLocaleData {

    private String text;
    private String hover;
    private String click;
    private ClickActionType clickType;
    private String lang;
    private Child[] children;

    public FCLocaleData() {
        text = "";
        hover = "";
        click = "";
        clickType = ClickActionType.RUN_COMMAND;
        lang = LocaleType.EN_US;
        children = new Child[0];
    }

    public FCLocaleData(FCLocale locale) {
        this.text = locale.text();
        this.hover = locale.hover();
        this.click = locale.click();
        this.clickType = locale.clickType();
        this.lang = LocaleType.normalize(locale.lang()); //If "eN_uS" is passed, it will be normalized to "EN_US"
        this.children = Arrays.stream(locale.children()).map(Child::new).collect(Collectors.toList()).toArray(new Child[0]);
    }

    public FCLocaleData replace(String placeholder, String value){
        this.text = this.text.replace(placeholder, value);
        this.hover = this.hover.replace(placeholder, value);
        this.click = this.click.replace(placeholder, value);
        for (Child child : this.children) {
            child.replace(placeholder, value);
        }
        return this;
    }

    public FCLocaleData replace(CompoundReplacer replacer){
        this.text = replacer.apply(this.text);
        this.hover = replacer.apply(this.hover);
        this.click = replacer.apply(this.click);
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
        private String click;
        private ClickActionType clickType;

        public Child() {
            text = "";
            hover = "";
            click = "";
            clickType = ClickActionType.RUN_COMMAND;
        }

        public Child(FCLocale.Child child) {
            this.text = child.text();
            this.hover = child.hover();
            this.click = child.click();
            this.clickType = child.clickType();
        }

        public Child replace(String placeholder, String value){
            this.text = this.text.replace(placeholder, value);
            this.hover = this.hover.replace(placeholder, value);
            this.click = this.click.replace(placeholder, value);
            return this;
        }

        public Child replace(CompoundReplacer replacer){
            this.text = replacer.apply(this.text);
            this.hover = replacer.apply(this.hover);
            this.click = replacer.apply(this.click);
            return this;
        }
    }
}
