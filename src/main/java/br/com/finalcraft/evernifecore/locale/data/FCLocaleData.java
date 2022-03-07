package br.com.finalcraft.evernifecore.locale.data;

import br.com.finalcraft.evernifecore.locale.FCLocale;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true, chain = true)
@AllArgsConstructor
public class FCLocaleData {

    private String text;
    private String hover;
    private String runCommand;
    private String lang;

    public FCLocaleData(FCLocale locale) {
        this.text = locale.text();
        this.hover = locale.hover();
        this.runCommand = locale.runCommand();
        this.lang = locale.lang().name();
    }

    public FCLocaleData replace(String placeholder, String value){
        this.text = this.text.replace(placeholder, value);
        this.hover = this.hover.replace(placeholder, value);
        this.runCommand = this.runCommand.replace(placeholder, value);
        return this;
    }
}
