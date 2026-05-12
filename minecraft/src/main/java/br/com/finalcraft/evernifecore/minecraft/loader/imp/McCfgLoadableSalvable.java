package br.com.finalcraft.evernifecore.minecraft.loader.imp;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.yaml.helper.CfgLoadableSalvable;
import br.com.finalcraft.evernifecore.minecraft.inventory.invitem.InvItem;
import br.com.finalcraft.evernifecore.minecraft.inventory.invitem.InvItemManager;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.minecraft.util.FCItemUtils;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.regex.Pattern;

public class McCfgLoadableSalvable {

    public static void initialize(){
        createBukkitOnlyLoadableSalvables();
    }

    private static void createBukkitOnlyLoadableSalvables(){
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
