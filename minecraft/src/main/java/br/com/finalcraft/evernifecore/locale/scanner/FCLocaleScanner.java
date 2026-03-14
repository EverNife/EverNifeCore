package br.com.finalcraft.evernifecore.locale.scanner;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.fancytext.ClickActionType;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.FCMultiLocales;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class FCLocaleScanner {

    public static List<LocaleMessageImp> scanForLocale(Plugin plugin, boolean silent, Class localeClass){

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

                FCLocaleData[] fcLocaleDatas = Arrays.stream(fcLocales)
                        .map(FCLocaleData::new)
                        .collect(Collectors.toList())
                        .toArray(new FCLocaleData[0]);

                LocaleMessageImp localeMessage = scanForLocale(plugin, key, true, fcLocaleDatas);

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

        if (!atLeastOneLocaleField && !silent){
            plugin.getLogger().warning("[FCLocale] The class [" + localeClass.getName() + "] has no fields of LocaleMessage.class to be scanned!");
        }

        localeMessageList.sort(Comparator.comparing(LocaleMessageImp::getKey));

        return localeMessageList;
    }

    public static LocaleMessageImp scanForLocale(Plugin plugin, String key, boolean shouldSyncToFile, FCLocaleData... locales){

        ECPluginData ecPluginData = ECPluginManager.getOrCreateECorePluginData(plugin);

        LocaleMessageImp existingLocale = ecPluginData.getLocalizedMessages().get(key);

        if (existingLocale != null){
            //This locale is already here
            return existingLocale;
        }

        LocaleMessageImp newLocale = new LocaleMessageImp(plugin, key, shouldSyncToFile);

        ecPluginData.addLocale(newLocale);

        for (FCLocaleData fcLocale : locales) {
            String text = fcLocale.text();
            String hover = fcLocale.hover().isEmpty() ? null : fcLocale.hover();
            String clickActionText = fcLocale.runCommand().isEmpty() ? null : fcLocale.runCommand();
            ClickActionType clickActionType = fcLocale.clickActionType();
            String lang = fcLocale.lang();
            FancyText fancyText = new FancyText(
                    FCColorUtil.colorfy(text),
                    FCColorUtil.colorfy(hover),
                    clickActionText,
                    clickActionType
            );

            if (newLocale.getFancyText(lang) != null){
                plugin.getLogger().severe("[FCLocale] Found a LocaleMessage with repeated {localeTypes} at key [" + key + "]! Ignoring new one!");
                continue;
            }

            if (fcLocale.children().length > 0){
                //Append children making this FancyText a FancyFormamtter
                for (FCLocaleData.Child child : fcLocale.children()) {
                    text = child.text();
                    hover = child.hover().isEmpty() ? null : child.hover();
                    clickActionText = child.runCommand().isEmpty() ? null : child.runCommand();
                    clickActionType = child.clickActionType();

                    fancyText = fancyText.append(
                            FCColorUtil.colorfy(text),
                            FCColorUtil.colorfy(hover),
                            clickActionText
                    );
                    fancyText.setClickAction(clickActionType);
                }
            }

            newLocale.addLocale(lang, fancyText);
            ecPluginData.addHardcodedLocaleIfNeeded(lang);
        }

        return newLocale;
    }

    private static String getFieldAndClassName(Field field){
        return "[" + field.getName()  +"] in [" + field.getDeclaringClass().getName() + "]";
    }
}
