package br.com.finalcraft.evernifecore.gui.layout;

import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.config.cfg.SettingsScanner;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.itemstack.itembuilder.FCItemBuilder;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FCLayoutScanner {

    public static <T extends LayoutBase> T loadLayout(Class<T> layoutClass){
        Plugin plugin = JavaPlugin.getProvidingPlugin(layoutClass);
        return loadLayout(plugin, new Config(plugin, "guis/" + layoutClass.getSimpleName() + ".yml"), layoutClass);
    }

    public static <T extends LayoutBase> T loadLayout(Plugin plugin, Config config, Class<T> layoutClass){

        T layoutInstance = FCReflectionUtil.getConstructor(layoutClass).invoke();
        layoutInstance.config = config;

        @Nullable LayoutBaseData layoutBaseData = layoutClass.getAnnotation(LayoutBaseData.class);

        if (layoutBaseData != null){
            layoutInstance.title = layoutBaseData.title();
            layoutInstance.rows = layoutBaseData.rows();
            layoutInstance.integrateToPAPI = layoutBaseData.integrateToPAPI();
        }else {
            layoutInstance.title = "➲  §0§l%layout_name%";
            layoutInstance.rows = 6;
            layoutInstance.integrateToPAPI = false;
        }

        //Title
        layoutInstance.title = layoutInstance.title.replace("%layout_name%", layoutClass.getSimpleName());
        layoutInstance.title = config.getOrSetDefaultValue("Settings.title", layoutInstance.title.replace("§","&"));
        layoutInstance.title = FCColorUtil.colorfy(layoutInstance.title);
        //Rows
        layoutInstance.rows = config.getOrSetDefaultValue("Settings.rows", layoutInstance.rows);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")){
            layoutInstance.integrateToPAPI = config.getOrSetDefaultValue("Settings.integrateToPAPI", layoutInstance.integrateToPAPI, "If the items and Title of this GUI should be PARSED by PlaceholderAPI");
        }else {
            layoutInstance.integrateToPAPI = false;
        }

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

                    List<String> slotsAsString = new ArrayList<>();
                    for (int i : slot) {
                        slotsAsString.add(String.valueOf(i));
                    }
                    itemSection.setDefaultValue("Slot", slotsAsString);

                    if (!permission.isEmpty()){
                        itemSection.setDefaultValue("Permission", permission);
                    }

                    FCItemBuilder itemBuilder = FCItemFactory.from(layoutIcon.getItemStack());
                    if (localeMessage != null && localeMessage.getFancyTextMap().size() > 0){
                        String displayName = FCColorUtil.colorfy(localeMessage.getDefaultFancyText().getText());
                        String hoverText = FCColorUtil.colorfy(localeMessage.getDefaultFancyText().getHoverText());

                        if (!displayName.isEmpty()){
                            itemBuilder.displayName(displayName);
                        }

                        if (!hoverText.isEmpty()){
                            itemBuilder.lore(hoverText);
                        }
                    }

                    //The Default Values name and lore are based on the Default plugin locale.
                    itemSection.setDefaultValue("DisplayItem", itemBuilder.toDataPart());
                }

                // ===========  Load Values From the Config ===========
                if (background && !itemSection.contains()){
                    //If is a background and is not present on file, ignore it
                    try {
                        declaredField.set(layoutInstance, null);
                    } catch (IllegalAccessException e) {
                        plugin.getLogger().warning("[FCLayoutScanner] Failed to load LayoutIconBackground {" + ICON_NAME + "} from " + layoutClass.getSimpleName());
                        e.printStackTrace();
                        continue;
                    }
                }

                try {
                    if (itemSection.contains("Slot")){
                        slot = itemSection.getStringList("Slot")
                                .stream()
                                .mapToInt(value -> Integer.valueOf(value))
                                .toArray();
                    }

                    permission = itemSection.getString("Permission", permission);

                    ItemStack itemStack = layoutIcon.getItemStack();
                    if (itemSection.contains("DisplayItem")){
                        itemStack = FCItemFactory.from(
                                itemSection.getStringList("DisplayItem")
                        ).build();
                    }

                    LayoutIcon newLayout = new LayoutIcon(itemStack, slot, false, permission, null);
                    declaredField.set(layoutInstance, newLayout);

                    if (!background){
                        layoutInstance.getLayoutIcons().add(newLayout);
                    }
                }catch (Exception e){
                    plugin.getLogger().warning("[FCLayoutScanner] Failed to load LayoutIcon {" + ICON_NAME + "} from " + config.getAbsolutePath());
                    e.printStackTrace();
                }
            }
        }

        for (String backgroundKey : config.getKeys("Background")) {
            LayoutIcon backgroundIcon = config.getLoadable("Background." + backgroundKey, LayoutIcon.class);
            layoutInstance.getBackgroundIcons().add(
                    backgroundIcon
                            .asBuilder()
                            .setBackground(true)
                            .build()
            );
        }

        //Load Settings from this Layout as well
        SettingsScanner.loadSettings(plugin, config, layoutInstance);

        layoutInstance.onLayoutLoad();

        config.saveIfNewDefaults();
        return layoutInstance;
    }

}
