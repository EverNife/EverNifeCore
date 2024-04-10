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
import br.com.finalcraft.evernifecore.inventory.invitem.InvItem;
import br.com.finalcraft.evernifecore.inventory.invitem.InvItemManager;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.ChunkPos;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import br.com.finalcraft.evernifecore.util.numberwrapper.NumberWrapper;
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
import java.util.stream.Collectors;

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
                        configSection.setValue("formatter", true);
                        for (int index = 0; index < fancyFormatter.getFancyTextList().size(); index++) {
                            configSection.setValue(String.valueOf(index + 1), fancyFormatter.getFancyTextList().get(index));
                        }
                        return;
                    }

                    boolean hasHover = fancyText.getHoverText() != null && !fancyText.getHoverText().isEmpty();
                    boolean hasAction = fancyText.getClickActionText() != null && !fancyText.getClickActionText().isEmpty();

                    String text = fancyText.getText().replace("§","&");
                    Object saveText = text.contains("\n") ? Arrays.asList(text.split("\n")) : text;

                    if (hasHover == false && hasAction == false) {
                        //If there is no hover or action, just save the text
                        configSection.setValue(saveText);
                        return;
                    }

                    configSection.setValue("text", saveText);

                    if (hasHover) {
                        String hoverText = fancyText.getHoverText().replace("§","&");
                        Object saveHover = hoverText.contains("\n") ? Arrays.asList(hoverText.split("\n")) : hoverText;
                        configSection.setValue("hoverText", saveHover);
                    }
                    if (hasAction) {
                        String clickActionText = fancyText.getClickActionText().replace("§","&");
                        Object saveAction = clickActionText.contains("\n") ? Arrays.asList(clickActionText.split("\n")) : clickActionText;
                        configSection.setValue("clickActionText", saveAction);
                        configSection.setValue("clickActionType", fancyText.getClickActionType().name());
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

                                //Create a helper function to convert [String|List<String>] into a single String
                                Function<String, String> getStringFromStringOrStringList = path -> {
                                    if (!configSection.contains(path)){
                                        return null;
                                    }

                                    List<String> stringList = configSection.getStringList(path);

                                    if (stringList != null){
                                        return stringList.stream().collect(Collectors.joining("\n"));
                                    }else {
                                        return configSection.getString(path);
                                    }
                                };

                                if (configSection.contains(("text"))){
                                    //This means this fancyText has more than just the text
                                    String text = getStringFromStringOrStringList.apply("text");
                                    String hoverText = getStringFromStringOrStringList.apply("hoverText");
                                    String actionText = getStringFromStringOrStringList.apply("clickActionText");
                                    String actionTypeName = getStringFromStringOrStringList.apply("clickActionType");
                                    ClickActionType actionType = actionTypeName != null && !actionTypeName.isEmpty() ? ClickActionType.valueOf(actionTypeName) : ClickActionType.NONE;
                                    return new FancyText(
                                            FCColorUtil.colorfy(text),
                                            FCColorUtil.colorfy(hoverText),
                                            FCColorUtil.colorfy(actionText),
                                            actionType
                                    );
                                }else {
                                    return new FancyText(FCColorUtil.colorfy(getStringFromStringOrStringList.apply(null)));
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

                    InvItem invItem = InvItemManager.of(itemStack.getType());
                    if (invItem != null){
                        configSection.setValue("invItem.name", invItem.getId());
                        invItem.onConfigSave(itemStack, configSection);
                    }else {
                        configSection.setValue("", ItemDataPart.readItem(itemStack));
                    }
                })
                .setOnConfigLoad(
                        configSection -> {
                            if (configSection.contains("invItem.name")){
                                String invItemName = configSection.getString("invItem.name");
                                InvItem invItem = InvItemManager.of(invItemName);
                                if (invItem == null){
                                    EverNifeCore.getLog().warning("Found an InvItem [%s] on the section [%s] that doesn't exists! The content will be ignored!", invItemName, configSection.getPath());
                                    return null;
                                }
                                return invItem.onConfigLoad(configSection);
                            }else if (configSection.contains("minecraftIdentifier")){ //This IF here is for legacy support! To keep compatibility with EverNifeCore 2.0.2 or Prior

                                String minecraftIdentifier = configSection.getString("minecraftIdentifier");
                                if (configSection.contains("nbt")){ //Load the nbt if it is separated from the identifier!
                                    String nbt = " " + String.join("", configSection.getStringList("nbt"));
                                    return FCItemFactory.from(minecraftIdentifier + nbt).build();
                                }else {
                                    return FCItemUtils.fromMinecraftIdentifier(minecraftIdentifier);
                                }

                            }else {
                                //IF the key 'minecraftIdentifier' is not present, then this can be three things:
                                //1. A Bukkit Identifier    (MINECRAFT_STONE)
                                //2. A Minecraft Identifier (minecraft:stone)
                                //3. An ItemDataPart        (List<String>)
                                Object value = configSection.getValue("");

                                if (value instanceof List){
                                    return FCItemFactory.from((List<String>) value).build();
                                }else {
                                    return FCItemFactory.from(String.valueOf(value)).build();
                                }
                            }
                        }
                )
        ;

        addLoadableSalvable(FCReflectionUtil.getClass("java.util.LinkedHashMap$LinkedValues"))
                .setOnConfigSave((section, linkedHashMap) -> {
                    section.setValue("", new ArrayList<>((Collection) linkedHashMap));
                });

        addLoadableSalvable(Set.class)
                .setOnConfigSave((section, set) -> {
                    section.setValue("", new ArrayList<>(set));
                })
                .setAllowExtends(true);
    }
}
