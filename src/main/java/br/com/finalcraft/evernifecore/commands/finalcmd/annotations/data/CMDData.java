package br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data;

import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import br.com.finalcraft.evernifecore.util.FCArrayUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public class CMDData<T extends CMDData<T>> {

    private String[] labels; //This means both command ALIASES or SubCommands names
    private String usage;
    private String desc;
    private String permission;
    private FCLocaleData[] locales;
    private @Nullable BiConsumer<ArgData, Class> argCustomizer;

    public CMDData() {
        labels = new String[0];
        usage = "";
        desc = "";
        permission = "";
        locales = new FCLocaleData[0];
        argCustomizer = null;
    }

    //Override this CMDData with data from other CMDData
    public T override(T override){
        if (override.labels().length > 0) this.labels = override.labels();
        if (!override.usage().isEmpty()) this.usage = override.usage();
        if (!override.desc().isEmpty()) this.desc = override.desc();
        if (!override.permission().isEmpty()) this.permission = override.permission();
        if (override.locales().length > 0) this.locales = override.locales();
        if (override.argCustomizer() != null) this.argCustomizer = override.argCustomizer();
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

    public T argCustomizer(BiConsumer<ArgData, Class> argCustomizer){
        this.argCustomizer = argCustomizer;
        return (T) this;
    }

}
