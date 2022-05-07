package br.com.finalcraft.evernifecore.config.yaml.helper;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.config.yaml.exeption.LoadableMethodException;
import br.com.finalcraft.evernifecore.config.yaml.helper.smartloadable.SmartLoadSave;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.fancytext.ClickActionType;
import br.com.finalcraft.evernifecore.fancytext.FancyFormatter;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.inventory.data.ItemSlot;
import br.com.finalcraft.evernifecore.itemstack.invitem.InvItem;
import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import br.com.finalcraft.evernifecore.util.numberwrapper.NumberWrapper;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

public class CfgLoadableSalvable {

    public static final List<SmartLoadSave> SMART_LOADABLES = new ArrayList<>(); //Holds all possible Loadables
    public static final Map<Class, Optional<SmartLoadSave>> CACHE_MAP = new HashMap<>(); //Holds a cache for each class request

    public static <O> SmartLoadSave<O> addLoadableSalvable(Class<O> clazz){
        SmartLoadSave smartLoadSave = new SmartLoadSave(clazz);
        SMART_LOADABLES.add(smartLoadSave);
        return smartLoadSave;
    }

    public static <O> @Nullable SmartLoadSave getLoadableStatus(Class<O> clazz){
        return CACHE_MAP.computeIfAbsent(clazz, aClass -> {

            //First of all, lets check if there is not already a loadable capable of loading this class:
            SmartLoadSave<O> smartLoadSave = SMART_LOADABLES.stream().filter(smartLoadSave1 -> smartLoadSave1.match(clazz)).findFirst().orElse(null);

            if (smartLoadSave == null){ //If does not exist, create a new one, we will only register if needed!
                smartLoadSave = new SmartLoadSave(clazz);
            }

            if (smartLoadSave.getClass() != clazz || smartLoadSave.hasAlreadyBeenScanned()){
                //If we have already done the process bellow this check for this SmartLoadable
                //or this is just a son of the smartLodable already registered, no need to look further
                return Optional.of(smartLoadSave);
            }

            boolean shouldRegister = false;

            if (Salvable.class.isAssignableFrom(clazz)){ //This class is a Salvable, STORE it!
                smartLoadSave.setOnConfigSave(
                        (configSection, o) -> ((Salvable) o).onConfigSave(configSection)
                );
                shouldRegister = true;
            }

            //Lets attempt to extract LoadableData from it as well
            Optional<Function<ConfigSection, O>> onConfigLoadOptional = extractLoadableMethod(aClass);
            if (onConfigLoadOptional.isPresent()){
                smartLoadSave.setOnConfigLoad(onConfigLoadOptional.get());
                shouldRegister = true;
            }

            if (shouldRegister){
                smartLoadSave.setHasAlreadyBeenScanned(true);
                SMART_LOADABLES.add(smartLoadSave);
                return Optional.of(smartLoadSave);
            }else {
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static <O> @NotNull Optional<Function<ConfigSection, O>> extractLoadableMethod(@NotNull Class<O> clazz){
        final Method method = Arrays.stream(clazz.getDeclaredMethods()).filter(theMethod -> {
            return theMethod.isAnnotationPresent(Loadable.class)
                    || theMethod.isAnnotationPresent(Config.Loadable.class); //Deprecated Code, keeping it here for legacy support //TODO Remove this support on next major release
        }).findFirst().orElse(null);

        if (method == null){
            return Optional.empty();
        }

        if (!method.isAccessible()){
            method.setAccessible(true);
        }

        final Function<ConfigSection, O> onConfigLoad;
        if (method.getParameterTypes().length > 2){
            throw new LoadableMethodException("@Loadable Method [" + clazz.getName() + "#" + method.toString() +  "] has more than two arguments!");
        }
        if (method.getParameterTypes().length > 1){ //Config and Path
            onConfigLoad = section -> {
                try {
                    return (O) method.invoke(null, section.getConfig(), section.getPath());
                }catch (Exception e){
                    throw new RuntimeException(e);
                }
            };
        }else {
            onConfigLoad = section -> { //Config Section
                try {
                    return (O) method.invoke(null, section);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }
            };
        }

        return Optional.of(onConfigLoad);
    }

    //------------------------------------------------------------------------------------------------------------------
    //    Hardcoded Auto-Registered Loadables
    //------------------------------------------------------------------------------------------------------------------

    static {
        addLoadableSalvable(NumberWrapper.class)
                .setOnConfigSave((configSection, numberWrapper) -> configSection.setValue(numberWrapper.get()))
        ;

        addLoadableSalvable(FancyText.class)
                .setAllowExtends(true)//FancyFormatter should come in this as well
                .setOnConfigSave((configSection, fancyText) -> {

                    configSection.clear();//Clear any previous value

                    if (fancyText instanceof FancyFormatter){
                        FancyFormatter fancyFormatter = (FancyFormatter) fancyText;
                        configSection.setValue(null);//Erase previous value first
                        configSection.setValue("formatter", true);
                        for (int index = 0; index < fancyFormatter.getFancyTextList().size(); index++) {
                            configSection.setValue(String.valueOf(index + 1), fancyFormatter.getFancyTextList().get(index));
                        }
                        return;
                    }

                    boolean hover = fancyText.getHoverText() != null && !fancyText.getHoverText().isEmpty();
                    boolean action = fancyText.getClickActionText() != null && !fancyText.getClickActionText().isEmpty();

                    if (hover || action){
                        configSection.setValue("text", fancyText.getText().replace("§","&"));
                        if (hover) configSection.setValue("hoverText", fancyText.getHoverText().replace("§","&"));
                        if (action) {
                            configSection.setValue("clickActionText", fancyText.getClickActionText().replace("§","&"));
                            configSection.setValue("clickActionType", fancyText.getClickActionType().name());
                        }
                    }else {
                        configSection.setValue(fancyText.getText().replace("§","&"));
                    }
                })
                .setOnConfigLoad(
                        configSection -> {
                            if (configSection.contains("formatter")){//It's a FancyFormmater

                                FancyFormatter fancyFormatter = FancyFormatter.of();
                                for (String key : configSection.getKeys()) {
                                    if (key.equals("formatter")) continue;

                                    FancyText fancyText = configSection.getLoadable(key, FancyText.class);
                                    fancyFormatter.append(fancyText);
                                }

                                return fancyFormatter;
                            }else { //Normal FancyText
                                if (configSection.contains(("text"))){
                                    String text = configSection.getString("text");
                                    String hoverText = configSection.getString("hoverText", null);
                                    String actionText = configSection.getString("clickActionText", null);
                                    String actionTypeName = configSection.getString("clickActionType", null);
                                    ClickActionType actionType = actionTypeName != null && !actionTypeName.isEmpty() ? ClickActionType.valueOf(actionTypeName) : ClickActionType.NONE;
                                    return new FancyText(
                                            FCColorUtil.colorfy(text),
                                            FCColorUtil.colorfy(hoverText),
                                            FCColorUtil.colorfy(actionText),
                                            actionType
                                    );
                                }else {
                                    return new FancyText(FCColorUtil.colorfy(configSection.getString(null)));
                                }
                            }
                        }
                )
        ;

        addLoadableSalvable(Location.class)
                .setOnConfigSave((section, location) -> {
                    section.setValue("worldName", location.getWorld().getName());
                    section.setValue("x", location.getX());
                    section.setValue("y", location.getY());
                    section.setValue("z", location.getZ());
                    section.setValue("yaw", location.getYaw());
                    section.setValue("pitch", location.getPitch());
                })
                .setOnConfigLoad(section -> {
                    return new Location(
                            Bukkit.getWorld(
                                    section.getString("worldName")
                            ),
                            section.getDouble("x"),
                            section.getDouble("y"),
                            section.getDouble("z"),
                            section.getDouble("yaw").floatValue(),
                            section.getDouble("pitch").floatValue()
                    );
                });
        ;

        addLoadableSalvable(ItemStack.class)
                .setAllowExtends(true)//Allow CraftItemStack, as it's a son of ItemStack
                .setOnConfigSave((configSection, itemStack) -> {

                    configSection.clear();//Clear any previous value

                    String mcIdentifier = FCItemUtils.getMinecraftIdentifier(itemStack, false);
                    if (NMSUtils.get().hasNBTTagCompound(itemStack)){
                        configSection.setValue("minecraftIdentifier", mcIdentifier); //Only present when there is NBT
                        InvItem invItem = InvItem.getInvItem(itemStack);
                        if (invItem != null){
                            configSection.setValue("invItem.name", invItem.name());
                            for (ItemSlot itemSlot : invItem.getItemsFrom(itemStack)) {
                                configSection.setValue("invItem.content." + itemSlot.getSlot(), itemSlot.getItemStack());
                            }
                            return;
                        }
                        //Get the NBT and split it into a StringList of 100 chars lengh
                        List<String> nbt = Arrays.asList(
                                Iterables.toArray(
                                        Splitter
                                                .fixedLength(100)
                                                .split(NMSUtils.get().getNBTtoString(itemStack)),
                                        String.class
                                )
                        );
                        configSection.setValue("nbt", nbt);
                    }else {
                        configSection.setValue(null, mcIdentifier);
                    }
                })
                .setOnConfigLoad(
                        configSection -> {
                            if (configSection.contains("minecraftIdentifier")){
                                String minecraftIdentifier = configSection.getString("minecraftIdentifier");
                                if (configSection.contains("nbt")){
                                    String nbt = " " + String.join("", configSection.getStringList("nbt"));
                                    return FCItemUtils.fromMinecraftIdentifier(minecraftIdentifier + nbt);
                                }else if (configSection.contains("invItem.name")){
                                    ItemStack customChest = FCItemUtils.fromMinecraftIdentifier(minecraftIdentifier);
                                    String invItemName = configSection.getString("invItem.name");
                                    InvItem invItem = InvItem.valueOf(invItemName);
                                    if (!invItem.isEnabled()){
                                        EverNifeCore.warning("Found an InvItem [" + invItem.name()  + "] but it is not enabled! The content will be ignored!");
                                        return customChest;
                                    }
                                    List<ItemSlot> itemSlots = new ArrayList<>();
                                    for (String slot : configSection.getKeys("invItem.content")) {
                                        ItemStack slotItem = configSection.getLoadable("invItem.content." + slot, ItemStack.class);
                                        itemSlots.add(new ItemSlot(Integer.parseInt(slot), slotItem));
                                    }
                                    return invItem.createChestWithItems(customChest, itemSlots);
                                }else {
                                    return FCItemUtils.fromMinecraftIdentifier(minecraftIdentifier);
                                }
                            }else {//Attempt to Load from BUKKIT or MC identifier, just in case
                                return FCItemUtils.fromIdentifier(configSection.getString(null));
                            }
                        }
                )
        ;
    }
}
