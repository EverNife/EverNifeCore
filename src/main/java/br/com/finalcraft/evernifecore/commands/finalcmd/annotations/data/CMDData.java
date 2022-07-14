package br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data;

import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import br.com.finalcraft.evernifecore.util.FCArrayUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.stream.Collectors;

@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public class CMDData<T extends CMDData<T>> {

    private String[] labels; //This means both command ALIASES or SubCommands names
    private String usage;
    private String desc;
    private String permission;
    private FCLocaleData[] locales;

    public CMDData() {
        labels = new String[0];
        usage = "";
        desc = "";
        permission = "";
        locales = new FCLocaleData[0];
    }

    //Override this CMDData with data from other CMDData
    public T override(T override){
        if (override.labels().length > 0) this.labels = override.labels();
        if (!override.usage().isEmpty()) this.usage = override.usage();
        if (!override.desc().isEmpty()) this.desc = override.desc();
        if (!override.permission().isEmpty()) this.permission = override.permission();
        if (override.locales().length > 0) this.locales = override.locales();
        return (T) this;
    }

    public T labels(String[] labels){
        this.labels = labels;
        return (T) this;
    }

    public T labels(String label, String... otherLabels){
        this.labels = FCArrayUtil.merge(label, otherLabels);
        return (T) this;
    }

    public T usage(String usage) {
        this.usage = usage;
        return (T) this;
    }

    public T desc(String desc) {
        this.desc = desc;
        return (T) this;
    }

    public T permission(String permission) {
        this.permission = permission;
        return (T) this;
    }

    public T locales(FCLocaleData[] locales){
        this.locales = locales;
        return (T) this;
    }

    public T locales(FCLocaleData locale, FCLocaleData... otherLocales){
        this.locales = FCArrayUtil.merge(locale, otherLocales);
        return (T) this;
    }

    public T replace(String placeholder, String value){
        this.labels = Arrays.stream(this.labels).map(s -> s.replace(placeholder, value)).collect(Collectors.toList()).toArray(new String[0]);
        this.usage = this.usage.replace(placeholder, value);
        this.desc = this.desc.replace(placeholder, value);
        this.permission = this.permission.replace(placeholder, value);
        for (FCLocaleData locale : this.locales) {
            locale.replace(placeholder, value);
        }
        return (T) this;
    }

}
