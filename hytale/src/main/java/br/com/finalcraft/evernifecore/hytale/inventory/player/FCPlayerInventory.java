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
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import jakarta.annotation.Nullable;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.IntFunction;

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
        snapshotSectionsFrom(((HytaleFPlayer) player)::getInventorySection);

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

    // ==================== the six built-in sections ====================
    // These two are mirrors and must stay one below the other: each section reads from, and writes back to,
    // the container that names it. Pointing two sections at one container is silent data loss rather than a
    // misplaced item, because restoring also blanks every slot the snapshot does not fill - so the last write
    // wins the whole container and every section before it is gone.

    void snapshotSectionsFrom(IntFunction<ItemContainer> playerSections) {
        this.storage = new GenericInventory(playerSections.apply(InventoryComponent.STORAGE_SECTION_ID));
        this.armor = new GenericInventory(playerSections.apply(InventoryComponent.ARMOR_SECTION_ID));
        this.hotbar = new GenericInventory(playerSections.apply(InventoryComponent.HOTBAR_SECTION_ID));
        this.utility = new GenericInventory(playerSections.apply(InventoryComponent.UTILITY_SECTION_ID));
        this.tools = new GenericInventory(playerSections.apply(InventoryComponent.TOOLS_SECTION_ID));
        this.backpack = new GenericInventory(playerSections.apply(InventoryComponent.BACKPACK_SECTION_ID));
    }

    void restoreSectionsTo(IntFunction<ItemContainer> playerSections) {
        storage.restoreTo(playerSections.apply(InventoryComponent.STORAGE_SECTION_ID));
        armor.restoreTo(playerSections.apply(InventoryComponent.ARMOR_SECTION_ID));
        hotbar.restoreTo(playerSections.apply(InventoryComponent.HOTBAR_SECTION_ID));
        utility.restoreTo(playerSections.apply(InventoryComponent.UTILITY_SECTION_ID));
        tools.restoreTo(playerSections.apply(InventoryComponent.TOOLS_SECTION_ID));
        backpack.restoreTo(playerSections.apply(InventoryComponent.BACKPACK_SECTION_ID));
    }

    public void restoreTo(FPlayer player){
        restoreTo(player, ExtraInvManager.getAllFactories());
    }

    public void restoreTo(FPlayer player, @Nullable Collection<IExtraInvFactory<?>> inventoryFactories) {
        restoreSectionsTo(((HytaleFPlayer) player)::getInventorySection);

        if (inventoryFactories != null) {
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
