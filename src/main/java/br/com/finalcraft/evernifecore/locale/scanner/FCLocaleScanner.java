package br.com.finalcraft.evernifecore.locale.scanner;

import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.ecplugin.ECPlugin;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.*;
import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

public class FCLocaleScanner {

    public static List<LocaleMessageImp> scanForLocale(Plugin plugin, Class localeClass){

        List<LocaleMessageImp> localeMessageList = new ArrayList<>();
        List<String> allKeys = new ArrayList<>();

        boolean atLeastOneLocaleField = false;

        for (Field declaredField : localeClass.getDeclaredFields()) {
            if (declaredField.getType() == LocaleMessage.class || declaredField.getType() == LocaleMessageImp.class){
                atLeastOneLocaleField = true;

                if (!Modifier.isStatic(declaredField.getModifiers())){
                    plugin.getLogger().severe("[FCLocale] Found a LocaleMessage field that is not static! "  + getFieldAndClassName(declaredField));
                    continue;
                }

                boolean multiLocale  = declaredField.isAnnotationPresent(FCMultiLocales.class);
                boolean simpleLocale = declaredField.isAnnotationPresent(FCLocale.class);
                if (!multiLocale && !simpleLocale){
                    plugin.getLogger().severe("[FCLocale] Found a LocaleMessage field that has no Annotations! " + getFieldAndClassName(declaredField));
                    continue;
                }

                try {
                    declaredField.setAccessible(true);
                } catch (SecurityException e) {
                    plugin.getLogger().severe("[FCLocale] Failed to allow access to field! " + getFieldAndClassName(declaredField));
                    e.printStackTrace();
                    continue;
                }

                FCLocale[] fcLocales = multiLocale ? declaredField.getAnnotation(FCMultiLocales.class).value() : declaredField.getAnnotationsByType(FCLocale.class);

                String key = declaredField.getDeclaringClass().getSimpleName() + "." + declaredField.getName().toUpperCase().replace("__",".").toUpperCase();

                LocaleMessageImp localeMessage = scanForLocale(plugin, key, true, Arrays.stream(fcLocales)
                        .map(FCLocaleData::new)
                        .collect(Collectors.toList())
                        .toArray(new FCLocaleData[0]));

                try {
                    declaredField.set(null, localeMessage);
                } catch (IllegalAccessException e) {
                    plugin.getLogger().severe("[FCLocale] Failed to create an instance of LocaleMessage.class at field! " + getFieldAndClassName(declaredField));
                    e.printStackTrace();
                    continue;
                }

                if (allKeys.contains(localeMessage.getKey().toLowerCase())){
                    plugin.getLogger().warning("[FCLocale] Found an already added {key==" + key + "} at field! Overriding last one! This is an Error! " + getFieldAndClassName(declaredField));
                }

                allKeys.add(localeMessage.getKey().toLowerCase());

                localeMessageList.add(localeMessage);
            }
        }

        if (!atLeastOneLocaleField){
            plugin.getLogger().warning("[FCLocale] The class [" + localeClass.getName() + "] has no fields of LocaleMessage.class to be scanned!");
        }

        localeMessageList.sort(Comparator.comparing(LocaleMessageImp::getKey));

        return localeMessageList;
    }

    public static LocaleMessageImp scanForLocale(Plugin plugin, String key, boolean saveOnFile, FCLocaleData... locales){

        ECPlugin ecPlugin = ECPluginManager.getOrCreateECorePlugin(plugin);

        LocaleMessageImp localeMessage = new LocaleMessageImp(plugin, key);
        ecPlugin.addLocale(localeMessage);

        for (FCLocaleData fcLocale : locales) {
            String text = fcLocale.text();
            String hover = fcLocale.hover().isEmpty() ? null : fcLocale.hover();
            String runCommand = fcLocale.runCommand().isEmpty() ? null : fcLocale.runCommand();
            String lang = fcLocale.lang();
            FancyText fancyText = new FancyText(text, hover, runCommand);

            if (localeMessage.getFancyText(lang) != null){
                plugin.getLogger().severe("[FCLocale] Found a LocaleMessage with repeated {localeTypes} at key [" + key + "]! Ignoring new one!");
                continue;
            }

            localeMessage.addLocale(lang, fancyText);
        }

        if (ecPlugin.getCustomLangConfig() != null){ //We are using a custom lang, need to add the new fancyTexts to the locale message

            FancyText englishFancyText = localeMessage.getFancyText(LocaleType.EN_US.name());
            //Set a DefaultValue for this Custom LocaleMessage based on the ENGLISH hardcoded LocaleMessage
            if (saveOnFile){
                ecPlugin.getCustomLangConfig().setDefaultValue(localeMessage.getKey(), englishFancyText);
            }

            //Get the Custom FancyText of this LocaleMessage on the custom ConfigFile
            FancyText fancyText = ecPlugin.getCustomLangConfig().getFancyText(localeMessage.getKey());
            if (fancyText == null){
                fancyText = englishFancyText;
            }

            localeMessage.addLocale(ecPlugin.getPluginLanguage(), fancyText);
        }

        if (!saveOnFile){
            return localeMessage;
        }

        if (ecPlugin.getCustomLangConfig() != null){
            ecPlugin.getCustomLangConfig().saveIfNewDefaults();
        }

        for (Map.Entry<LocaleType, Config> entry : ecPlugin.getHardcodedLocalizations().entrySet()) {
            Config lang = entry.getValue();

            boolean anyChange = false;
            FancyText originalFancyText = localeMessage.getFancyText(entry.getKey().name());

            if (originalFancyText == null){
                originalFancyText = new FancyText("[LOCALE_NOT_FOUND]");
            }

            FancyText onFileFancyText = lang.getFancyText(localeMessage.getKey());

            if (onFileFancyText == null || !onFileFancyText.equals(originalFancyText)){
                anyChange = true;
                lang.setValue(localeMessage.getKey(), originalFancyText);
            }

            if (anyChange){
                lang.save();
            }
        }

        return localeMessage;
    }

    private static String getFieldAndClassName(Field field){
        return "[" + field.getName()  +"] in [" + field.getDeclaringClass().getName() + "]";
    }
}
