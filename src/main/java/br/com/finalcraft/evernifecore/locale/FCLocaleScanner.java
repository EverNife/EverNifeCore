package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FCLocaleScanner {

    protected static List<LocaleMessageImp> scanForLocale(Plugin plugin, Class localeClass){

        List<LocaleMessageImp> localeMessageList = new ArrayList<>();
        List<String> allKeys = new ArrayList<>();

        for (Field declaredField : localeClass.getDeclaredFields()) {
            if (declaredField.getType() == LocaleMessage.class || declaredField.getType() == LocaleMessageImp.class){

                if (!Modifier.isStatic(declaredField.getModifiers())){
                    plugin.getLogger().warning("[FCLocale] Found a LocaleMessage field [" + declaredField.getName()  +"] that is not static!");
                    continue;
                }

                boolean multiLocale = declaredField.isAnnotationPresent(FCMultiLocales.class);
                boolean fclocale    = declaredField.isAnnotationPresent(FCLocale.class);
                if (!multiLocale && !fclocale){
                    plugin.getLogger().warning("[FCLocale] Found a LocaleMessage field [" + declaredField.getName()  +"] that has no Annotations!");
                    continue;
                }

                try {
                    declaredField.setAccessible(true);
                } catch (SecurityException e) {
                    plugin.getLogger().warning("[FCLocale] Failed to allow acess at field [" + declaredField.getName()  +"]!");
                    e.printStackTrace();
                    continue;
                }

                String key = declaredField.getName().toUpperCase().replace("__",".");

                LocaleMessageImp localeMessage = new LocaleMessageImp(plugin, key);
                try {
                    declaredField.set(null, localeMessage);
                } catch (IllegalAccessException e) {
                    plugin.getLogger().warning("[FCLocale] Failed to create an instance of LocaleMessage.class at field [" + declaredField.getName()  +"]!");
                    e.printStackTrace();
                    continue;
                }

                FCLocale[] fcLocales = multiLocale ? declaredField.getAnnotation(FCMultiLocales.class) .value() : declaredField.getAnnotationsByType(FCLocale.class);

                for (FCLocale fcLocale : fcLocales) {
                    String text = fcLocale.text();
                    String hover = fcLocale.hover().isEmpty() ? null : fcLocale.hover();
                    String runCommand = fcLocale.runCommand().isEmpty() ? null : fcLocale.runCommand();
                    String lang = fcLocale.lang().name();
                    FancyText fancyText = new FancyText(text, hover, runCommand);

                    localeMessage.addLocale(fancyText, lang);
                }

                if (allKeys.contains(localeMessage.getKey())){
                    plugin.getLogger().warning("[FCLocale] Found an already added {key} at field [" + declaredField.getName()  +"]! Ignoring new one!");
                    continue;
                }

                allKeys.add(localeMessage.getKey().toLowerCase());

                localeMessageList.add(localeMessage);
            }
        }

        if (localeMessageList.size() == 0){
            plugin.getLogger().warning("[FCLocale] The class [" + localeClass.getName() + "] has no fields of LocaleMessage.class to be scanned!");
            return localeMessageList;
        }

        localeMessageList.sort(Comparator.comparing(LocaleMessageImp::getKey));

        String classSimpleName = localeClass.getSimpleName();

        for (String localeName : LocaleType.getAllNames()) {
            Config lang = new Config(plugin, "localization/lang_" + localeName + ".yml");
            boolean anyChange = false;
            for (LocaleMessageImp localeMessage : localeMessageList) {

                FancyText originalFancyText = localeMessage.getFancyText(localeName);

                if (originalFancyText == null){
                    originalFancyText = new FancyText("[LOCALE_NOT_FOUND]");
                }

                FancyText onFileFancyText = lang.getFancyText(classSimpleName + "." + localeMessage.getKey());

                if (onFileFancyText == null || !onFileFancyText.equals(originalFancyText)){
                    anyChange = true;
                    lang.setValue(classSimpleName + "." + localeMessage.getKey(), originalFancyText);
                }

            }

            if (anyChange) lang.save();
        }

        return localeMessageList;
    }

}
