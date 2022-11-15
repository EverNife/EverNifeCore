package br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data;

import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import br.com.finalcraft.evernifecore.util.FCArrayUtil;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.stream.Collectors;

@Getter
@Accessors(fluent = true)
public class CMDData<T extends CMDData<T>> {

    private String[] labels = new String[0]; //This means both command ALIASES or SubCommands names
    private String usage = "";
    private String desc = "";
    private String permission;
    private String context = "";
    private CMDAccessValidation cmdAccessValidation = new CMDAccessValidation.Allowed();
    private FCLocaleData[] locales = new FCLocaleData[0];

    public CMDData() {

    }

    public CMDData(String[] labels, String usage, String desc, String permission, String context, CMDAccessValidation cmdAccessValidation, FCLocaleData[] locales) {
        this();
        this.labels = labels;
        this.usage = usage;
        this.desc = desc;
        this.permission = permission;
        this.context = context;
        this.cmdAccessValidation = cmdAccessValidation;
        this.locales = locales;
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

    public CMDData<T> context(String context) {
        this.context = context;
        return this;
    }

    public CMDData<T> setCmdAccessValidation(CMDAccessValidation cmdAccessValidation) {
        this.cmdAccessValidation = cmdAccessValidation;
        return this;
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
