package br.com.finalcraft.evernifecore.hytale.loader.imp;

import br.com.finalcraft.evernifecore.config.yaml.helper.CfgLoadableSalvable;
import br.com.finalcraft.evernifecore.hytale.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.hytale.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import com.hypixel.hytale.math.vector.*;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.List;
import java.util.regex.Pattern;

public class HyCfgLoadableSalvable {

    public static void initialize(){
        CfgLoadableSalvable.addLoadableSalvable(Vector3d.class)
                .setOnConfigSave((configSection, vector3d) -> {
                    configSection.setValue("x", vector3d.x());
                    configSection.setValue("y", vector3d.y());
                    configSection.setValue("z", vector3d.z());
                })
                .setOnConfigLoad(configSection -> new Vector3d(
                        configSection.getDouble("x"),
                        configSection.getDouble("y"),
                        configSection.getDouble("z"))
                );

        CfgLoadableSalvable.addLoadableSalvable(Vector3i.class)
                .setOnConfigSave((configSection, vector3d) -> {
                    configSection.setValue("x", vector3d.x());
                    configSection.setValue("y", vector3d.y());
                    configSection.setValue("z", vector3d.z());
                })
                .setOnConfigLoad(configSection -> new Vector3i(
                        configSection.getInt("x"),
                        configSection.getInt("y"),
                        configSection.getInt("z"))
                );

        CfgLoadableSalvable.addLoadableSalvable(Vector3f.class)
                .setOnConfigSave((configSection, vector3f) -> {
                    configSection.setValue("x", vector3f.x());
                    configSection.setValue("y", vector3f.y());
                    configSection.setValue("z", vector3f.z());
                })
                .setOnConfigLoad(configSection -> new Vector3f(
                        (float) configSection.getDouble("x"),
                        (float) configSection.getDouble("y"),
                        (float) configSection.getDouble("z"))
                );

        CfgLoadableSalvable.addLoadableSalvable(Rotation3f.class)
            .setOnConfigSave((configSection, vector3f) -> {
                configSection.setValue("x", vector3f.x());
                configSection.setValue("y", vector3f.y());
                configSection.setValue("z", vector3f.z());
            })
            .setOnConfigLoad(configSection -> new Rotation3f(
                (float) configSection.getDouble("x"),
                (float) configSection.getDouble("y"),
                (float) configSection.getDouble("z"))
            );

        CfgLoadableSalvable.addLoadableSalvable(Vector2d.class)
                .setOnConfigSave((configSection, chunkPos) -> {
                    configSection.setValue("x", chunkPos.x());
                    configSection.setValue("y", chunkPos.y());
                })
                .setOnConfigLoad(configSection -> new Vector2d(
                        configSection.getInt("x"),
                        configSection.getInt("y"))
                );

        CfgLoadableSalvable.addLoadableSalvable(Location.class)
                .setOnConfigSave((section, location) -> {
                    section.setValue("worldName", location.getWorld());
                    section.setValue("position", location.getPosition());
                    section.setValue("rotation", location.getRotation());
                })
                .setOnConfigLoad(section -> {

                    Vector3d position = section.getLoadable("position", Vector3d.class);
                    Rotation3f rotation = section.getLoadable("rotation", Rotation3f.class);

                    return new Location(
                            section.getString("worldName"),
                            position,
                            rotation
                    );
                })
                .setOnStringSerialize(location -> { // WORLD | x y z yaw pitch

                    Vector3d position = location.getPosition();
                    Rotation3f rotation = location.getRotation();

                    return location.getWorld() + " | "  + position.x() + " " + position.y() + " " + position.z() + " " + rotation.x() + " " + rotation.y()  + " " + rotation.z();
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
                    Rotation3f rotation = new Rotation3f(xRotation.floatValue(), yRotation.floatValue(), zRotation.floatValue());

                    return new Location(
                            world,
                            position,
                            rotation
                    );
                });
        ;

        CfgLoadableSalvable.addLoadableSalvable(ItemStack.class)
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

}
