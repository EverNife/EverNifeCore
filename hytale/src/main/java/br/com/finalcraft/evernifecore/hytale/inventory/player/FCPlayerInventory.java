package br.com.finalcraft.evernifecore.hytale.inventory.player;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import br.com.finalcraft.evernifecore.hytale.inventory.GenericInventory;
import br.com.finalcraft.evernifecore.hytale.inventory.extrainvs.ExtraInv;
import br.com.finalcraft.evernifecore.hytale.inventory.extrainvs.ExtraInvManager;
import br.com.finalcraft.evernifecore.hytale.inventory.extrainvs.factory.IExtraInvFactory;
import br.com.finalcraft.everyconfig.binding.ConfigContext;
import br.com.finalcraft.everyconfig.binding.ConfigLifecycle;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import jakarta.annotation.Nullable;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Persists as {@code {storage, armor, hotbar, utility, tools, backpack, extra.<id>}}. The six sub-inventories
 * bind as ordinary {@code @Data} bean properties; the factory-driven {@code extra.<id>} map is handled in the
 * {@link ConfigLifecycle} hooks, where each factory's own {@link IExtraInvFactory#onConfigLoad} stays polymorphic.
 */
@Data
public class FCPlayerInventory implements ConfigLifecycle {

    protected GenericInventory storage = new GenericInventory();
    protected GenericInventory armor = new GenericInventory();
    protected GenericInventory hotbar = new GenericInventory();
    protected GenericInventory utility = new GenericInventory();
    protected GenericInventory tools = new GenericInventory();
    protected GenericInventory backpack = new GenericInventory();

    @JsonIgnore
    protected List<ExtraInv> extraInvs = new ArrayList<>();

    public FCPlayerInventory() {

    }

    public FCPlayerInventory(GenericInventory storage) {
        this(storage, new GenericInventory(), new GenericInventory(), new GenericInventory(), new GenericInventory(), new GenericInventory(), new ArrayList<>());
    }

    public FCPlayerInventory(GenericInventory storage, GenericInventory armor, GenericInventory hotbar, GenericInventory utility, GenericInventory tools, GenericInventory backpack, List<ExtraInv> extraInvs) {
        this.storage = storage;
        this.armor = armor;
        this.hotbar = hotbar;
        this.utility = utility;
        this.tools = tools;
        this.backpack = backpack;
        this.extraInvs = extraInvs;
    }

    public FCPlayerInventory(FPlayer player) {
        this(player, ExtraInvManager.getAllFactories());
    }

    public FCPlayerInventory(FPlayer player, @Nullable Collection<IExtraInvFactory<?>> inventoryFactories) {
        Inventory playerInventory = ((HytaleFPlayer)player).getPlayer().getInventory();

        this.storage = new GenericInventory(playerInventory.getStorage());
        this.armor = new GenericInventory(playerInventory.getArmor());
        this.hotbar = new GenericInventory(playerInventory.getHotbar());
        this.utility = new GenericInventory(playerInventory.getUtility());
        this.tools = new GenericInventory(playerInventory.getTools());
        this.backpack = new GenericInventory(playerInventory.getBackpack());

        if (inventoryFactories != null){
            for (IExtraInvFactory<?> factory : inventoryFactories) {
                try {
                    ExtraInv extraInv = factory.extractFromPlayer(player);
                    extraInvs.add(extraInv);
                }catch (Exception e){
                    EverNifeCore.getLog().info("Failed to extract ExtraInv(" + factory.getId() + ") from " + player.getName());
                    e.printStackTrace();
                }
            }
        }
    }

    public ItemStack getHead() {
        return armor.getItem(0);
    }

    public ItemStack getChest() {
        return armor.getItem(1);
    }

    public ItemStack getHands() {
        return armor.getItem(2);
    }

    public ItemStack getLegs() {
        return armor.getItem(3);
    }

    public ExtraInv getExtraInv(String extraInvId){
        return extraInvs.stream()
                .filter(extraInv -> extraInv.getFactory().getId().equals(extraInvId))
                .findFirst()
                .orElse(null);
    }

    public void restoreTo(FPlayer player){
        restoreTo(player, ExtraInvManager.getAllFactories());
    }

    public void restoreTo(FPlayer player, @Nullable Collection<IExtraInvFactory<?>> inventoryFactories) {
        Inventory playerInventory = ((HytaleFPlayer)player).getPlayer().getInventory();

        storage.restoreTo(playerInventory.getStorage());
        armor.restoreTo(playerInventory.getStorage());
        hotbar.restoreTo(playerInventory.getStorage());
        utility.restoreTo(playerInventory.getStorage());
        tools.restoreTo(playerInventory.getStorage());
        backpack.restoreTo(playerInventory.getStorage());

        for (IExtraInvFactory factory : inventoryFactories) {
            // We need to ge all factories, rather than use 'this.getExtraInvs()'
            // because if there is a factory that is not present on 'this.extraInvs()',
            // it means that we need to erase that extraInv on the player
            try {
                ExtraInv extraInv = this.getExtraInv(factory.getId());
                if (extraInv == null){
                    extraInv = factory.createEmptyExtraInv();
                }
                factory.applyToPlayer(player, extraInv);
            }catch (Throwable e){
                EverNifeCore.getLog().info("Failed to restore ExtraInv(" + factory.getId() + ") into " + player.getName());
                e.printStackTrace();
            }
        }
    }

    // ==================== config lifecycle ====================

    /** Write each extra inventory under {@code extra.<id>}; its slot map routes through {@link GenericInventory}.
     *  Runs post-save so {@code extra} lands after the bound fields, matching the legacy key order. */
    @Override
    public void postSave(ConfigContext context) {
        for (ExtraInv extraInv : extraInvs) {
            context.section().setValue("extra." + extraInv.getFactory().getId(), extraInv);
        }
    }

    /** Rebuild the extras from {@code extra.<id>} through each factory's own polymorphic
     *  {@link IExtraInvFactory#onConfigLoad}. */
    @Override
    public void postLoad(ConfigContext context) {
        for (String extraInvKey : context.section().getKeys("extra")) {
            ConfigSection extraInvSection = context.section().getConfigSection("extra." + extraInvKey);
            try {
                IExtraInvFactory factory = ExtraInvManager.getFactory(extraInvKey);
                if (factory == null) {
                    continue;
                }
                extraInvs.add(factory.onConfigLoad(extraInvSection));
            } catch (Throwable e) {
                EverNifeCore.getLog().info("Failed to load ExtraInv(" + extraInvKey + ") at " + extraInvSection.getPath());
                e.printStackTrace();
            }
        }
    }
}
