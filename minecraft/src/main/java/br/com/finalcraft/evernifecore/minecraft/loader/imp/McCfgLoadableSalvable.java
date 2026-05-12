package br.com.finalcraft.evernifecore.minecraft.loader.imp;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.yaml.helper.CfgLoadableSalvable;
import br.com.finalcraft.evernifecore.fancytext.ClickActionType;
import br.com.finalcraft.evernifecore.fancytext.FancyFormatter;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.minecraft.inventory.invitem.InvItem;
import br.com.finalcraft.evernifecore.minecraft.inventory.invitem.InvItemManager;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.minecraft.util.FCItemUtils;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class McCfgLoadableSalvable {

    public static void initialize(){
        boolean isBukkitEnvironment = FCReflectionUtil.isClassLoaded("org.bukkit.Bukkit");

        if (isBukkitEnvironment){
            createBukkitOnlyLoadableSalvables();
        }
    }

    private static void createBukkitOnlyLoadableSalvables(){
        CfgLoadableSalvable.addLoadableSalvable(FancyText.class)
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

        CfgLoadableSalvable.addLoadableSalvable(Location.class)
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

        CfgLoadableSalvable.addLoadableSalvable(ItemStack.class)
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
    }
}
