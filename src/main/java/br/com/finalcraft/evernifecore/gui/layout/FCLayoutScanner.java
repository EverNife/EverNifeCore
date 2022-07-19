package br.com.finalcraft.evernifecore.gui.layout;

import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.itemstack.itembuilder.FCItemBuilder;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.ReflectionUtil;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.stream.Collectors;

public class FCLayoutScanner {

    public static <T extends LayoutBase> T loadLayout(Class<T> layoutClass){
        Plugin plugin = JavaPlugin.getProvidingPlugin(layoutClass);
        return loadLayout(plugin, new Config(plugin, "guis/" + layoutClass.getSimpleName() + ".yml"), layoutClass);
    }

    public static <T extends LayoutBase> T loadLayout(Plugin plugin, Config config, Class<T> layoutClass){

        T layoutInstance = ReflectionUtil.getConstructor(layoutClass).invoke();
        layoutInstance.config = config;
        layoutInstance.title = layoutInstance.defaultTitle().replace("%layout_name%", layoutClass.getSimpleName());

        layoutInstance.title = config.getOrSetDefaultValue("Settings.title", layoutInstance.title);

        boolean hasAnyBackgroundAtStart = config.contains("Background");

        for (Field declaredField : layoutInstance.getClass().getDeclaredFields()) {
            if (declaredField.getType() == LayoutIcon.class){

                LayoutIcon layoutIcon = null;
                try {
                    layoutIcon = (LayoutIcon) declaredField.get(layoutInstance);
                } catch (IllegalAccessException ignored) {
                    ;
                }

                if (layoutIcon == null){
                    plugin.getLogger().warning("[FCLayoutScanner] No LayoutIcon found on ["+ layoutClass.getClass().getName() + "] " + declaredField.toString());
                    continue; //This is a dev error
                }

                String ICON_NAME = declaredField.getName();
                String permission = "";
                int[] slot = {};
                boolean background = false;
                LocaleMessageImp localeMessage = new LocaleMessageImp(plugin,
                        ICON_NAME,
                        false
                );;

                // =========== Apply Configurations from @LayoutIconData ===========
                LayoutIconData layoutIconData = declaredField.getAnnotation(LayoutIconData.class);
                if (layoutIconData != null){
                    permission = layoutIconData.permission();
                    slot = layoutIconData.slot();
                    background = layoutIconData.background();

                    //In case we have @FCLocale
                    if (layoutIconData.locale().length > 0){
                        FCLocaleData[] localeData = Arrays.stream(layoutIconData.locale())
                                .map(FCLocaleData::new)
                                .collect(Collectors.toList())
                                .toArray(new FCLocaleData[0]);

                        for (FCLocaleData fcLocale : localeData) {
                            localeMessage.addLocale(fcLocale.lang(),
                                    new FancyText(
                                            fcLocale.text(),
                                            fcLocale.hover()
                                    )
                            );
                        }
                    }
                }

                if (localeMessage.getFancyTextMap().size() == 0){
                    //This means there was no locale on this LayoutIcon
                    //Lets just extract it from the ItemStak itself
                    //TODO Add this in the future for Multiple Localization
                }

                // =========== Save Defaults on the Config ===========
                String SECTION_KEY = background ? "Background" : "Layout";
                ConfigSection itemSection = config.getConfigSection(SECTION_KEY + "." + ICON_NAME);

                if (background == false || !hasAnyBackgroundAtStart){
                    //When a background, it will only save Defaults if there is already no existing Background LayoutIcon on the config

                    itemSection.setDefaultValue("Slot", slot);

                    if (!permission.isEmpty()){
                        itemSection.setDefaultValue("Permission", permission);
                    }

                    FCItemBuilder itemBuilder = FCItemFactory.from(layoutIcon.getItemStack());
                    if (localeMessage != null && localeMessage.getFancyTextMap().size() > 0){
                        String displayName = FCColorUtil.colorfy(localeMessage.getDefaultFancyText().getText());
                        String hoverText = FCColorUtil.colorfy(localeMessage.getDefaultFancyText().getHoverText());

                        if (!displayName.isEmpty()){
                            itemBuilder.name(displayName);
                        }

                        if (!hoverText.isEmpty()){
                            itemBuilder.lore(hoverText);
                        }
                    }

                    //The Default Values name and lore are based on the Default plugin locale.
                    itemSection.setDefaultValue("DisplayItem", itemBuilder.toDataPart());
                }

                // ===========  Load Values From the Config ===========
                try {
                    slot = itemSection.getStringList("Slot")
                            .stream()
                            .mapToInt(value -> Integer.valueOf(value))
                            .toArray();
                    permission = itemSection.getString("Permission","");
                    ItemStack itemStack = FCItemFactory.from(
                            itemSection.getStringList("DisplayItem")
                    ).build();

                    LayoutIcon newLayout = new LayoutIcon(itemStack, slot, false, permission, null);
                    declaredField.set(layoutInstance, newLayout);

                    if (background){
                        layoutInstance.getBackgroundIcons().add(newLayout);
                    }else {
                        layoutInstance.getLayoutIcons().add(newLayout);
                    }
                }catch (Exception e){
                    plugin.getLogger().warning("[FCLayoutScanner] Failed to load LayoutIcon {" + ICON_NAME + "} from " + config.getAbsolutePath());
                    e.printStackTrace();
                }
            }
        }

        layoutInstance.onLayoutLoad();

        config.saveIfNewDefaults();
        return layoutInstance;
    }

}
