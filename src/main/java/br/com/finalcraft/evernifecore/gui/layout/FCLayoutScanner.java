package br.com.finalcraft.evernifecore.gui.layout;

import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.itemstack.itembuilder.FCItemBuilder;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FCLayoutScanner {

    public static <T extends LayoutBase> T loadLayout(Class<T> layoutClass){
        Plugin plugin = JavaPlugin.getProvidingPlugin(layoutClass);
        return loadLayout(plugin, new Config(plugin, "guis/" + layoutClass.getSimpleName() + ".yml"), layoutClass);
    }

    public static <T extends LayoutBase> T loadLayout(Plugin plugin, Config config, Class<T> layoutClass){
        try {
            T layoutInstance = layoutClass.newInstance();
            ConfigSection LAYOUT_SECTION = config.getConfigSection("Layout");

            for (Field declaredField : layoutInstance.getClass().getDeclaredFields()) {
                if (declaredField.getType() == LayoutIcon.class){

                    LayoutIcon layoutIcon = (LayoutIcon) declaredField.get(layoutInstance);

                    if (layoutIcon == null){
                        plugin.getLogger().warning("[FCLayoutScanner] No LayoutIcon found on ["+ layoutClass.getClass().getName() + "] " + declaredField.toString());
                        continue; //This is a dev error
                    }

                    String ICON_NAME = declaredField.getName();
                    String permission = "";
                    int[] slot = {-1};
                    LocaleMessageImp localeMessage = null;

                    LayoutIconData layoutIconData = declaredField.getAnnotation(LayoutIconData.class);
                    if (layoutIconData != null){
                        permission = layoutIconData.permission();
                        slot = layoutIconData.slot();

                        //In case we have @FCLocale
                        if (layoutIconData.locale().length > 0){
                            FCLocaleData[] localeData = Arrays.stream(layoutIconData.locale())
                                    .map(FCLocaleData::new)
                                    .collect(Collectors.toList())
                                    .toArray(new FCLocaleData[0]);

                            localeMessage = new LocaleMessageImp(plugin,
                                    layoutClass.getSimpleName() + "." + ICON_NAME,
                                    false
                            );

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


                    ConfigSection itemSection = LAYOUT_SECTION.getConfigSection(ICON_NAME);

                    // =========== Save Defaults ===========
                    itemSection.setDefaultValue("Slot", slot.length == 1 ? slot[0] : slot); //Save Slot as INTEGER or INT_ARRAY based on it's size
                    if (!permission.isEmpty()){
                        itemSection.setDefaultValue("Permission", permission);
                    }
                    FCItemBuilder itemBuilder = FCItemFactory.from(layoutIcon.getItemStack());
                    if (localeMessage != null){
                        String displayName = localeMessage.getDefaultFancyText().getText();
                        String hoverText = localeMessage.getDefaultFancyText().getHoverText();

                        if (!displayName.isEmpty()){
                            itemBuilder.name(displayName);
                        }

                        if (!hoverText.isEmpty()){
                            itemBuilder.lore(hoverText);
                        }
                    }

                    //The Default Values name and lore are based on the Default plugin locale.
                    itemSection.setDefaultValue("DisplayItem",itemBuilder.toDataPart());

                    // ===========  Load Values ===========
                    if (itemSection.getValue("Slot") instanceof List){
                        slot = itemSection.getList("Slot").stream().mapToInt(value -> (int) value).toArray();
                    }else {
                        slot = new int[]{itemSection.getInt("Slot")};
                    }
                    permission = itemSection.getString("Permission","");
                    ItemStack itemStack = itemSection.getLoadable("DisplayItem", ItemStack.class);

                    LayoutIcon newLayout = new LayoutIcon(itemStack, slot, permission);
                    declaredField.set(layoutInstance, newLayout);
                    layoutInstance.getLayoutIcons().add(newLayout);
                }
            }

            return layoutInstance;
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
