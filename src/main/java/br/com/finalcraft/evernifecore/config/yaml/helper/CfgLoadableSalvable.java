package br.com.finalcraft.evernifecore.config.yaml.helper;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.yaml.anntation.Loadable;
import br.com.finalcraft.evernifecore.config.yaml.anntation.Salvable;
import br.com.finalcraft.evernifecore.config.yaml.exeption.LoadableMethodException;
import br.com.finalcraft.evernifecore.config.yaml.helper.smartloadable.SmartLoadSave;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.fancytext.ClickActionType;
import br.com.finalcraft.evernifecore.fancytext.FancyFormatter;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.inventory.data.ItemInSlot;
import br.com.finalcraft.evernifecore.itemstack.invitem.InvItem;
import br.com.finalcraft.evernifecore.itemstack.invitem.InvItemManager;
import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.ChunkPos;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import br.com.finalcraft.evernifecore.util.FCNBTUtil;
import br.com.finalcraft.evernifecore.util.numberwrapper.NumberWrapper;
import br.com.finalcraft.evernifecore.version.MCVersion;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import de.tr7zw.changeme.nbtapi.NBTContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

public class CfgLoadableSalvable {

    public static final List<SmartLoadSave> SMART_LOADABLES = new ArrayList<>(); //Holds all possible Loadables
    public static final Map<Class, Optional<SmartLoadSave>> CACHE_MAP = new ConcurrentHashMap<>(); //Holds a cache for each class request

    public static <O> SmartLoadSave<O> addLoadableSalvable(Class<O> clazz){
        SmartLoadSave smartLoadSave = new SmartLoadSave(clazz);
        SMART_LOADABLES.add(smartLoadSave);
        return smartLoadSave;
    }

    public static <O> @Nullable SmartLoadSave getLoadableStatus(Class<O> clazz){

        return CACHE_MAP.computeIfAbsent(clazz, aClass -> {

            //First of all, lets check if there is not already a loadable capable of loading this class:
            SmartLoadSave<O> smartLoadSave = SMART_LOADABLES.stream()
                    .filter(smartLoadSave1 -> smartLoadSave1.match(clazz))
                    .findFirst()
                    .orElse(null);

            if (smartLoadSave == null){ //If does not exist, create a new one, we will only register if needed!
                smartLoadSave = new SmartLoadSave(clazz);
            }else if (smartLoadSave.getLoadableSalvableClass() != clazz || smartLoadSave.hasAlreadyBeenScanned()){
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

        final Method method = Arrays.stream(clazz.getDeclaredMethods())
                .filter(theMethod -> theMethod.isAnnotationPresent(Loadable.class))
                .findFirst()
                .orElse(null);

        if (method == null){
            return Optional.empty();
        }

        if (!Modifier.isStatic(method.getModifiers())){
            throw new LoadableMethodException("@Loadable Method [" + clazz.getName() + "#" + method.toString() +  "] is not static!");
        }

        method.setAccessible(true);

        final Function<ConfigSection, O> onConfigLoad;
        if (method.getParameterTypes().length == 0){
            throw new LoadableMethodException("@Loadable Method [" + clazz.getName() + "#" + method.toString() +  "] has no arguments!");
        }
        if (method.getParameterTypes().length > 2){
            throw new LoadableMethodException("@Loadable Method [" + clazz.getName() + "#" + method.toString() +  "] has more than two arguments!");
        }

        if (method.getParameterTypes().length == 2){ //Config and Path
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
                    //se o metodo for statico retorna null
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
        addLoadableSalvable(UUID.class)
                .setOnConfigSave((configSection, uuid) -> configSection.setValue(uuid.toString()))
                .setOnConfigLoad(configSection -> UUID.fromString(configSection.getString("")))
                .setOnStringSerialize(uuid -> uuid.toString())
                .setOnStringDeserialize(serializedUUID -> UUID.fromString(serializedUUID));
        ;

        addLoadableSalvable(NumberWrapper.class)
                .setOnConfigSave((configSection, numberWrapper) -> configSection.setValue(numberWrapper.get()))
                .setOnConfigLoad(section -> {
                    Object obj = section.getValue(null);
                    if (obj instanceof Number){
                        return NumberWrapper.of((Number)obj);
                    }
                    throw new IllegalArgumentException("Tried to load a NumberWrapper that is not a Number [" + obj + "]");
                })
        ;

        addLoadableSalvable(BlockPos.class)
                .setOnConfigSave((configSection, blockPos) -> {
                    configSection.setValue("x", blockPos.getX());
                    configSection.setValue("y", blockPos.getY());
                    configSection.setValue("z", blockPos.getZ());
                })
                .setOnConfigLoad(configSection -> new BlockPos(configSection.getInt("x"),configSection.getInt("y"),configSection.getInt("z")));

        addLoadableSalvable(ChunkPos.class)
                .setOnConfigSave((configSection, chunkPos) -> {
                    configSection.setValue("x", chunkPos.getX());
                    configSection.setValue("z", chunkPos.getZ());
                })
                .setOnConfigLoad(configSection -> new ChunkPos(configSection.getInt("x"),configSection.getInt("z")));

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
                            (float) section.getDouble("yaw"),
                            (float) section.getDouble("pitch")
                    );
                })
                .setOnStringSerialize(location -> { // WORLD | x y z yaw pitch
                    return location.getWorld().getName() + " | "  + location.getX() + " " + location.getY() + " " + location.getZ() + " " + location.getYaw() + " " + location.getPitch();
                })
                .setOnStringDeserialize(serializedLocation -> {
                    String[] split = serializedLocation.split(Pattern.quote("|")); // WORLD | x y z yaw pitch
                    String[] splitCoords = split[1].split(" ");

                    World world = Bukkit.getWorld(split[0]);
                    Double x = FCInputReader.parseDouble(splitCoords[0]);
                    Double y = FCInputReader.parseDouble(splitCoords[1]);
                    Double z = FCInputReader.parseDouble(splitCoords[2]);
                    Double yaw = FCInputReader.parseDouble(splitCoords[3]);
                    Double pitch = FCInputReader.parseDouble(splitCoords[4]);

                    return new Location(
                            world,
                            x,
                            y,
                            z,
                            yaw.floatValue(),
                            pitch.floatValue()
                    );
                });
        ;

        addLoadableSalvable(ItemStack.class)
                .setAllowExtends(true)//Allow CraftItemStack, as it's a son of ItemStack
                .setOnConfigSave((configSection, itemStack) -> {

                    configSection.clear();//Clear any previous value

                    String mcIdentifier = FCItemUtils.getMinecraftIdentifier(itemStack, false);

                    NBTContainer nbtContainer = new NBTContainer(
                            FCNBTUtil.getFrom(itemStack).toString() //On 1.16.5 editing the NBTItem might not work, so, lets copy the NBT first...
                    );

                    //Get the NBT and split it into a StringList of 100 chars lengh
                    final String nbtString;
                    if (MCVersion.isLowerEquals(MCVersion.v1_12)){
                        nbtString = nbtContainer.toString();
                    }else {
                        nbtContainer.removeKey("Damage"); //Don't need to save the damage twice
                        nbtString = nbtContainer.toString();
                    }

                    if (!nbtString.isEmpty() && !nbtString.equals("{}")){ //If the NBT is not empty

                        configSection.setValue("minecraftIdentifier", mcIdentifier);
                        InvItem invItem = InvItemManager.of(itemStack.getType());
                        if (invItem != null){
                            configSection.setValue("invItem.name", invItem.getId());
                            for (ItemInSlot itemInSlot : invItem.getItemsFrom(itemStack)) {
                                configSection.setValue("invItem.content." + itemInSlot.getSlot(), itemInSlot.getItemStack());
                            }
                            return;
                        }

                        //split it into a StringList of 100 chars lengh
                        List<String> nbt = Arrays.asList(
                                Iterables.toArray(
                                        Splitter
                                                .fixedLength(100)
                                                .split(nbtString),
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
                                    InvItem invItem = InvItemManager.of(invItemName);
                                    if (invItem == null){
                                        EverNifeCore.warning("Found an InvItem [" + invItemName  + "] but it is not enabled! The content will be ignored!");
                                        return customChest;
                                    }
                                    List<ItemInSlot> itemInSlots = new ArrayList<>();
                                    for (String slot : configSection.getKeys("invItem.content")) {
                                        ItemStack slotItem = configSection.getLoadable("invItem.content." + slot, ItemStack.class);
                                        itemInSlots.add(new ItemInSlot(Integer.parseInt(slot), slotItem));
                                    }
                                    return invItem.setItemsTo(customChest, itemInSlots);
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
