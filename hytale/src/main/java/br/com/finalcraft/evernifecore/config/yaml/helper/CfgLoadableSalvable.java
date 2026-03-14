package br.com.finalcraft.evernifecore.config.yaml.helper;

import br.com.finalcraft.evernifecore.api.hytale.math.vector.BlockPos;
import br.com.finalcraft.evernifecore.api.hytale.math.vector.ChunkPos;
import br.com.finalcraft.evernifecore.api.hytale.math.vector.RegionPos;
import br.com.finalcraft.evernifecore.config.fcconfiguration.FCConfigurationManager;
import br.com.finalcraft.evernifecore.config.fcconfiguration.annotation.FConfig;
import br.com.finalcraft.evernifecore.config.yaml.anntation.Loadable;
import br.com.finalcraft.evernifecore.config.yaml.anntation.Salvable;
import br.com.finalcraft.evernifecore.config.yaml.exeption.LoadableMethodException;
import br.com.finalcraft.evernifecore.config.yaml.helper.smartloadable.SmartLoadSave;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.fancytext.ClickActionType;
import br.com.finalcraft.evernifecore.fancytext.FancyFormatter;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.time.DayOfToday;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import br.com.finalcraft.evernifecore.util.FCTimeUtil;
import br.com.finalcraft.evernifecore.util.numberwrapper.NumberWrapper;
import com.hypixel.hytale.math.vector.*;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
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

            FConfig configuration = clazz.getAnnotation(FConfig.class);
            if (configuration != null){
                FCConfigurationManager.attachLoadableSalvableFunctions(configuration, clazz, smartLoadSave);
                shouldRegister = true;
            }else {
                if (Salvable.class.isAssignableFrom(clazz)){ //This class is a Salvable, STORE it!
                    smartLoadSave.setOnConfigSave(
                            (configSection, o) -> ((Salvable) o).onConfigSave(configSection)
                    );
                    shouldRegister = true;
                }

                //Let's attempt to extract LoadableData from it as well
                Optional<Function<ConfigSection, O>> onConfigLoadOptional = extractLoadableMethod(aClass);
                if (onConfigLoadOptional.isPresent()){
                    smartLoadSave.setOnConfigLoad(onConfigLoadOptional.get());
                    shouldRegister = true;
                }

                if (shouldRegister == false){
                    if (clazz.isEnum()){
                        smartLoadSave.setOnConfigSave((configSection, o) -> configSection.setValue(o.toString()));
                        smartLoadSave.setOnConfigLoad(configSection -> (O) Enum.valueOf((Class<Enum>) clazz, configSection.getString("")));
                        shouldRegister = true;
                    }
                }
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

    private static <O> @Nonnull Optional<Function<ConfigSection, O>> extractLoadableMethod(@Nonnull Class<O> clazz){

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

        addLoadableSalvable(FCReflectionUtil.getClass("java.util.LinkedHashMap$LinkedValues"))
                .setOnConfigSave((section, linkedHashMap) -> {
                    section.setValue("", new ArrayList<>((Collection) linkedHashMap));
                });

        addLoadableSalvable(Set.class)
                .setOnConfigSave((section, set) -> {
                    section.setValue("", new ArrayList<>(set));
                })
                .setAllowExtends(true);

        addLoadableSalvable(ZonedDateTime.class)
                .setOnConfigSave((section, zonedDateTime) -> {
                    section.setValue("", FCTimeUtil.FORMATTER_DEFAULT.format(zonedDateTime));
                })
                .setOnConfigLoad(configSection -> {
                    String formatedTime = configSection.getString("");
                    return FCTimeUtil.universalDateConverter(formatedTime).atZone(DayOfToday.getInstance().getZoneId());
                });

        addLoadableSalvable(LocalDateTime.class)
                .setOnConfigSave((section, localDateTime) -> {
                    section.setValue("", FCTimeUtil.FORMATTER_DEFAULT.format(localDateTime));
                })
                .setOnConfigLoad(configSection -> {
                    String formatedTime = configSection.getString("");
                    return FCTimeUtil.universalDateConverter(formatedTime);
                });

        addLoadableSalvable(FCTimeFrame.class)
                .setOnConfigSave((section, fcTimeFrame) -> {
                    section.setValue("", FCTimeUtil.fromMillis(fcTimeFrame.getMillis()));
                })
                .setOnConfigLoad(configSection -> {
                    String timeFrame = configSection.getString("");
                    return FCTimeFrame.of(FCTimeUtil.toMillis(timeFrame));
                });

        createHytaleOnlyLoadableSalvables();
        createBlocPosLoadableSalvables();
    }

    private static void createHytaleOnlyLoadableSalvables(){
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
                    Object saveText = text.contains("\n") ? Arrays.asList(text.split("\n",-1)) : text;

                    if (hasHover == false && hasAction == false) {
                        //If there is no hover or action, just save the text
                        configSection.setValue(saveText);
                        return;
                    }

                    configSection.setValue("text", saveText);

                    if (hasHover) {
                        String hoverText = fancyText.getHoverText().replace("§","&");
                        Object saveHover = hoverText.contains("\n") ? Arrays.asList(hoverText.split("\n",-1)) : hoverText;
                        configSection.setValue("hoverText", saveHover);
                    }
                    if (hasAction) {
                        String clickActionText = fancyText.getClickActionText().replace("§","&");
                        Object saveAction = clickActionText.contains("\n") ? Arrays.asList(clickActionText.split("\n",-1)) : clickActionText;
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

                                    if (stringList != null && stringList.size() > 0){
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


        addLoadableSalvable(Vector3d.class)
                .setOnConfigSave((configSection, vector3d) -> {
                    configSection.setValue("x", vector3d.getX());
                    configSection.setValue("y", vector3d.getY());
                    configSection.setValue("z", vector3d.getZ());
                })
                .setOnConfigLoad(configSection -> new Vector3d(
                        configSection.getDouble("x"),
                        configSection.getDouble("y"),
                        configSection.getDouble("z"))
                );

        addLoadableSalvable(Vector3i.class)
                .setOnConfigSave((configSection, vector3d) -> {
                    configSection.setValue("x", vector3d.getX());
                    configSection.setValue("y", vector3d.getY());
                    configSection.setValue("z", vector3d.getZ());
                })
                .setOnConfigLoad(configSection -> new Vector3i(
                        configSection.getInt("x"),
                        configSection.getInt("y"),
                        configSection.getInt("z"))
                );

        addLoadableSalvable(Vector3f.class)
                .setOnConfigSave((configSection, vector3f) -> {
                    configSection.setValue("x", vector3f.getX());
                    configSection.setValue("y", vector3f.getY());
                    configSection.setValue("z", vector3f.getZ());
                })
                .setOnConfigLoad(configSection -> new Vector3f(
                        (float) configSection.getDouble("x"),
                        (float) configSection.getDouble("y"),
                        (float) configSection.getDouble("z"))
                );

        addLoadableSalvable(Vector2d.class)
                .setOnConfigSave((configSection, chunkPos) -> {
                    configSection.setValue("x", chunkPos.getX());
                    configSection.setValue("y", chunkPos.getY());
                })
                .setOnConfigLoad(configSection -> new Vector2d(
                        configSection.getInt("x"),
                        configSection.getInt("y"))
                );

        addLoadableSalvable(Location.class)
                .setOnConfigSave((section, location) -> {
                    section.setValue("worldName", location.getWorld());
                    section.setValue("position", location.getPosition());
                    section.setValue("rotation", location.getRotation());
                })
                .setOnConfigLoad(section -> {

                    Vector3d position = section.getLoadable("position", Vector3d.class);
                    Vector3f rotation = section.getLoadable("rotation", Vector3f.class);

                    return new Location(
                            section.getString("worldName"),
                            position,
                            rotation
                    );
                })
                .setOnStringSerialize(location -> { // WORLD | x y z yaw pitch

                    Vector3d position = location.getPosition();
                    Vector3f rotation = location.getRotation();

                    return location.getWorld() + " | "  + position.getX() + " " + position.getY() + " " + position.getZ() + " " + rotation.getX() + " " + rotation.getY()  + " " + rotation.getZ();
                })
                .setOnStringDeserialize(serializedLocation -> {
                    String[] split = serializedLocation.split(Pattern.quote("|")); // WORLD | x y z yaw pitch
                    String[] splitCoords = split[1].split(" ");

                    String world = split[0];
                    Double x = FCInputReader.parseDouble(splitCoords[0]);
                    Double y = FCInputReader.parseDouble(splitCoords[1]);
                    Double z = FCInputReader.parseDouble(splitCoords[2]);
                    Double xRotation = FCInputReader.parseDouble(splitCoords[3]);
                    Double yRotation = FCInputReader.parseDouble(splitCoords[4]);
                    Double zRotation = FCInputReader.parseDouble(splitCoords[5]);

                    Vector3d position = new Vector3d(x,y,z);
                    Vector3f rotation = new Vector3f(xRotation.floatValue(), yRotation.floatValue(), zRotation.floatValue());

                    return new Location(
                            world,
                            position,
                            rotation
                    );
                });
        ;

        addLoadableSalvable(ItemStack.class)
                .setAllowExtends(true)
                .setOnConfigSave((configSection, itemStack) -> {
                    configSection.setValue(ItemDataPart.readItem(itemStack));
                })
                .setOnConfigLoad(
                        configSection -> {
                            Object value = configSection.getValue("");
                            return FCItemFactory.from((List<String>) value).build();
                        }
                )
        ;

    }

    private static void createBlocPosLoadableSalvables(){
        addLoadableSalvable(BlockPos.class)
                .setOnConfigSave((configSection, pos) -> {
                    configSection.setValue("x", pos.getX());
                    configSection.setValue("y", pos.getY());
                    configSection.setValue("z", pos.getZ());
                })
                .setOnConfigLoad(configSection -> new BlockPos(
                        configSection.getInt("x"),
                        configSection.getInt("y"),
                        configSection.getInt("z")
                ))
                .setOnStringSerialize(BlockPos::serialize)
                .setOnStringDeserialize(BlockPos::deserialize);

        addLoadableSalvable(ChunkPos.class)
                .setOnConfigSave((configSection, pos) -> {
                    configSection.setValue("x", pos.getX());
                    configSection.setValue("z", pos.getZ());
                })
                .setOnConfigLoad(configSection -> new ChunkPos(
                        configSection.getInt("x"),
                        configSection.getInt("z")
                ))
                .setOnStringSerialize(ChunkPos::serialize)
                .setOnStringDeserialize(ChunkPos::deserialize);

        addLoadableSalvable(RegionPos.class)
                .setOnConfigSave((configSection, pos) -> {
                    configSection.setValue("x", pos.getX());
                    configSection.setValue("z", pos.getZ());
                })
                .setOnConfigLoad(configSection -> new RegionPos(
                        configSection.getInt("x"),
                        configSection.getInt("z")
                ))
                .setOnStringSerialize(RegionPos::serialize)
                .setOnStringDeserialize(RegionPos::deserialize);
    }
}
