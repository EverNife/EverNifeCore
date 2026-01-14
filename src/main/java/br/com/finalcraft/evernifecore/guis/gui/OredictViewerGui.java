package br.com.finalcraft.evernifecore.guis.gui;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.cooldown.PlayerCooldown;
import br.com.finalcraft.evernifecore.gui.PlayerGui;
import br.com.finalcraft.evernifecore.gui.custom.PaginatedGuiComplex;
import br.com.finalcraft.evernifecore.gui.layout.IHasLayout;
import br.com.finalcraft.evernifecore.guis.LayoutManager;
import br.com.finalcraft.evernifecore.guis.loyalt.OredictViewerLayout;
import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.nms.data.oredict.OreDictEntry;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
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

        for (ItemStack oreDictItem : this.oreDictItems) {
            getGui().addPaginatedItem(
                    FCItemFactory.from(oreDictItem).asGuiItem()
                            .setAction(event -> {
                                PlayerCooldown cooldown = getPlayerData().getCooldown("OREDICT_MENU_CLICK");
                                if (cooldown.isInCooldown()){
                                    return;
                                }
                                cooldown.startWith(200, TimeUnit.MILLISECONDS);

                                if (getPlayer().getGameMode() != GameMode.CREATIVE || !FCBukkitUtil.hasThePermission(getPlayer(), PermissionNodes.EVERNIFECORE_COMMAND_OREINFO_CREATIVE)){
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
