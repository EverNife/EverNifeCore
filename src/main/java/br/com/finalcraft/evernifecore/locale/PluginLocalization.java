package br.com.finalcraft.evernifecore.locale;

import java.util.Arrays;
import java.util.HashSet;

public class PluginLocalization {

    private final String pluginLang;
    protected HashSet<Class> classList = new HashSet<>();

    public PluginLocalization(String pluginLang) {
        this.pluginLang = pluginLang;
    }

    public void addLocaleClass(Class... classes){
        classList.addAll(Arrays.asList(classes));
    }

    public String getPluginLang() {
        return pluginLang;
    }

    public HashSet<Class> getClassList() {
        return classList;
    }
}
