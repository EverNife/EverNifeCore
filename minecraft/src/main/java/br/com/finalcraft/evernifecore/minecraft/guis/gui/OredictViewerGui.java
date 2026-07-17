package br.com.finalcraft.evernifecore.minecraft.guis.gui;

import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.cooldown.PlayerCooldown;
import br.com.finalcraft.evernifecore.minecraft.McPermissionNodes;
import br.com.finalcraft.evernifecore.minecraft.gui.PlayerGui;
import br.com.finalcraft.evernifecore.minecraft.gui.custom.PaginatedGuiComplex;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.IHasLayout;
import br.com.finalcraft.evernifecore.minecraft.guis.LayoutManager;
import br.com.finalcraft.evernifecore.minecraft.guis.loyalt.OredictViewerLayout;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.minecraft.nms.data.oredict.OreDictEntry;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import jakarta.annotation.Nonnull;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class OredictViewerGui extends PlayerGui<PlayerData, PaginatedGuiComplex> implements IHasLayout<OredictViewerLayout> {

    private static RegexReplacer<OredictViewerGui> REPLACER = new RegexReplacer<OredictViewerGui>()
            .addParser("oredict_name", thisGui -> thisGui.oreDictEntry.getOreName())
            .addParser("oredict_amount", thisGui -> thisGui.oreDictItems.size());

    private final OreDictEntry oreDictEntry;
    private final List<ItemStack> oreDictItems;

    public OredictViewerGui(OreDictEntry oreDictEntry, PlayerData playerData) {
        super(playerData);

        this.oreDictEntry = oreDictEntry;
        this.oreDictItems = oreDictEntry.getItemStacks();

        setupLayout(this);

        layout().INFO_SLOT.applyTo(this);

        layout().PREVIOUS_PAGE.applyTo(this)
                .setAction(inventoryClickEvent -> getGui().previous());

        layout().NEXT_PAGE.applyTo(this)
                .setAction(inventoryClickEvent -> getGui().next());

        getGui().addPageSlotAll();

        //a 200ms click throttle so a double-click in creative does not hand out two stacks. Resolved
        //once for this online viewer - its cooldown row is hot-loaded, so the join is a cache hit - and
        //shared by every paginated item's action; memory-only by design, it need not outlive the menu.
        PlayerCooldown clickThrottle = playerData.getCooldown("OREDICT_MENU_CLICK").join();

        for (ItemStack oreDictItem : this.oreDictItems) {
            getGui().addPaginatedItem(
                    FCItemFactory.from(oreDictItem).asGuiItem()
                            .setAction(event -> {
                                if (clickThrottle.isInCooldown()){
                                    return;
                                }
                                clickThrottle.startWith(200, TimeUnit.MILLISECONDS);

                                if (getPlayer().getGameMode() != GameMode.CREATIVE || !FCBukkitUtil.hasThePermission(getPlayer(), McPermissionNodes.EVERNIFECORE_COMMAND_OREINFO_CREATIVE)){
                                    return;
                                }

                                FCBukkitUtil.giveItemsTo(getPlayer(), oreDictItem.clone());
                            })
            );
        }


    }

    @Override
    public @Nonnull CompoundReplacer getReplacer() {
        return super.getReplacer().appendReplacer(REPLACER.compound(this));
    }

    @Override
    public OredictViewerLayout layout() {
        return LayoutManager.OREDICT_VIEWER_LAYOUT;
    }
}
