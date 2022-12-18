package br.com.finalcraft.evernifecore.inventory.extrainvs.factory.imp;

import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.integration.everforgelib.EverForgeLibIntegration;
import br.com.finalcraft.evernifecore.inventory.GenericInventory;
import br.com.finalcraft.evernifecore.inventory.data.ItemInSlot;
import br.com.finalcraft.evernifecore.inventory.extrainvs.ExtraInv;
import br.com.finalcraft.evernifecore.inventory.extrainvs.factory.IExtraInvFactory;
import br.com.finalcraft.evernifecore.util.commons.SimpleEntry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ArmourersInvFactory implements IExtraInvFactory {

    @Override
    public int getInvMaxSize() {
        return 8;//There are several sub invs on this extra inv, so we don't care about this information, but the size is probably 8!
    }

    @Override
    public String getId() {
        return "armourers_workshop";
    }

    @Override
    public ExtraInv getPlayerExtraInv(Player player) {
        Map<String, GenericInventory> map = new HashMap<>();
        for (Map.Entry<String, ItemStack[]> entry : EverForgeLibIntegration.getArmourersWorkshopInventory(player)) {
            map.put(entry.getKey(), new GenericInventory(ItemInSlot.fromStacks(entry.getValue())));
        }

        return new ArmourersExtraInv(this, map);
    }

    @Override
    public void setPlayerExtraInv(Player player, ExtraInv extraInv) {
        List<Map.Entry<String, ItemStack[]>> inventoryContent = ((ArmourersExtraInv) extraInv).getSubInventories()
                .entrySet()
                .stream()
                .map(entry -> {
                    return SimpleEntry.of(
                            entry.getKey(),
                            ItemInSlot.toArray(entry.getValue().getItems())
                    );
                })
                .collect(Collectors.toList());

        EverForgeLibIntegration.setArmourersWorkshopInventory(player, inventoryContent);
    }

    @Override
    public ExtraInv createEmptyExtraInv() {
        return new ArmourersExtraInv(this, Collections.EMPTY_MAP);
    }

    @Override
    public ExtraInv loadExtraInv(ConfigSection section) {
        HashMap<String, GenericInventory> subInventories = new HashMap<>();

        for (String key : section.getKeys()) {
            GenericInventory subInv = section.getLoadable(key, GenericInventory.class);
            subInventories.put(key, subInv);
        }

        return new ArmourersExtraInv(
                this,
                subInventories
        );
    }

    public static class ArmourersExtraInv extends ExtraInv{

        private final Map<String, GenericInventory> subInventories = new HashMap<>();

        public ArmourersExtraInv(IExtraInvFactory factory, Map<String, GenericInventory> subInventories) {
            super(factory);
            for (Map.Entry<String, GenericInventory> entry : subInventories.entrySet()) {
                this.subInventories.put(entry.getKey(), entry.getValue());
            }
        }

        public Map<String, GenericInventory> getSubInventories() {
            return subInventories;
        }

        @Override
        public void onConfigSave(ConfigSection section) {
            section.setValue("", null); //Clear content before saving it

            for (Map.Entry<String, GenericInventory> entry : subInventories.entrySet()) {
                section.setValue(entry.getKey(), entry.getValue());
            }
        }
    }
}
