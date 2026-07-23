package br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data;

import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.util.FCArrayUtil;
import lombok.Getter;

import java.util.Arrays;
import java.util.stream.Collectors;

@Getter
public class CMDData<T extends CMDData<T>> {

    private String[] labels = new String[0]; //This means both command ALIASES or SubCommands names
    private String usage = "";
    /**
     * Runtime-only description override used as the help hover when the command declares no
     * {@code locales()}. Holds a fully-formed, per-instance {@link LocaleMessageImp} - typically a
     * derived copy of a class-level {@code @FCLocale} field (see
     * {@link LocaleMessageImp#derivePlaceholderResolved}) - so a dynamic command built per-instance
     * (e.g. a command alias) can carry its own, multi-language hover without sharing state with any
     * other instance of the same class. Annotation-driven commands should use {@code locales()}
     * instead - it is the only declarative way to describe a command.
     */
    private LocaleMessageImp descriptionOverride;
    private String permission = "";
    private String context = "";
    private CMDAccessValidation[] cmdAccessValidations = new CMDAccessValidation[0];
    private FCLocaleData[] locales = new FCLocaleData[0];

    public CMDData() {

    }

    public CMDData(String[] labels, String usage, String permission, String context, CMDAccessValidation[] cmdAccessValidations, FCLocaleData[] locales) {
        this();
        this.labels = labels;
        this.usage = usage;
        this.permission = permission;
        this.context = context;
        this.cmdAccessValidations = cmdAccessValidations;
        this.locales = locales;
    }

    //Override this CMDData with data from other CMDData
    public T override(T override){
        if (override.getLabels().length > 0) this.labels = override.getLabels();
        if (!override.getUsage().isEmpty()) this.usage = override.getUsage();
        if (override.getDescriptionOverride() != null) this.descriptionOverride = override.getDescriptionOverride();
        if (!override.getPermission().isEmpty()) this.permission = override.getPermission();
        if (override.getLocales().length > 0) this.locales = override.getLocales();
        if (override.getCmdAccessValidations().length > 0) this.cmdAccessValidations = override.getCmdAccessValidations();
        if (override.getLocales().length > 0) this.locales = override.getLocales();
        return (T) this;
    }

    public T setLabels(String[] labels){
        this.labels = labels;
        return (T) this;
    }

    public T setLabels(String label, String... otherLabels){
        this.labels = FCArrayUtil.merge(label, otherLabels);
        return (T) this;
    }

    public T setUsage(String usage) {
        this.usage = usage;
        return (T) this;
    }

    /**
     * Sets the runtime-only description override used as the help hover when the command declares
     * no {@code locales()}. Meant for dynamic commands built per-instance (e.g. {@code CMDAlias}),
     * set during {@code customize()} - annotation-driven commands should use {@code locales()}
     * instead.
     */
    public T setDescriptionOverride(LocaleMessageImp descriptionOverride) {
        this.descriptionOverride = descriptionOverride;
        return (T) this;
    }

    public CMDData<T> setContext(String context) {
        this.context = context;
        return this;
    }

    public CMDData<T> setCmdAccessValidations(CMDAccessValidation... cmdAccessValidation) {
        this.cmdAccessValidations = cmdAccessValidation;
        return this;
    }

    public T setPermission(String permission) {
        this.permission = permission;
        return (T) this;
    }

    public T setLocales(FCLocaleData[] locales){
        this.locales = locales;
        return (T) this;
    }

    public T setLocales(FCLocaleData locale, FCLocaleData... otherLocales){
        this.locales = FCArrayUtil.merge(locale, otherLocales);
        return (T) this;
    }

    public T replace(String placeholder, String value){
        this.labels = Arrays.stream(this.labels).map(s -> s.replace(placeholder, value)).collect(Collectors.toList()).toArray(new String[0]);
        this.usage = this.usage.replace(placeholder, value);
        this.permission = this.permission.replace(placeholder, value);
        for (FCLocaleData locale : this.locales) {
            locale.replace(placeholder, value);
        }
        return (T) this;
    }

    public T replace(CompoundReplacer replacer){
        this.labels = Arrays.stream(this.labels).map(s -> replacer.apply(s)).collect(Collectors.toList()).toArray(new String[0]);
        this.usage = replacer.apply(this.usage);
        this.permission = replacer.apply(this.permission);
        for (FCLocaleData locale : this.locales) {
            locale.replace(replacer);
        }
        return (T) this;
    }

}
