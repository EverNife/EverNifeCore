package br.com.finalcraft.evernifecore.gui.custom;

import br.com.finalcraft.evernifecore.util.numberwrapper.NumberWrapper;
import dev.triumphteam.gui.components.GuiType;
import dev.triumphteam.gui.components.InteractionModifier;
import dev.triumphteam.gui.guis.GuiItem;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;

import java.util.*;

public class PaginatedGuiComplex extends GuiComplex {

    private final List<GuiItem> paginatedItems = new ArrayList<>();
    private final Set<Integer> pageSlots = new HashSet<>();
    private final Map<Integer, GuiItem> currentPage = new HashMap<>();
    private int pageNum = 0;

    public PaginatedGuiComplex(int rows, String title, Set<InteractionModifier> interactionModifiers) {
        super(rows, title, interactionModifiers);
    }

    public PaginatedGuiComplex(GuiType guiType, String title, Set<InteractionModifier> interactionModifiers) {
        super(guiType, title, interactionModifiers);
    }

    public List<GuiItem> getPaginatedItems() {
        return paginatedItems;
    }

    public void addPaginatedItem(final GuiItem... items) {
        for (GuiItem item : items) {
            paginatedItems.add(item);
        }
    }

    public void removePaginatedItem(final GuiItem... items) {
        for (GuiItem item : items) {
            paginatedItems.remove(item);
        }
    }

    public Set<Integer> getPageSlots() {
        return pageSlots;
    }

    public void addPageSlot(int... slot) {
        for (int i : slot) {
            this.validateSlot(i);
            pageSlots.add(i);
        }
    }

    public void removePageSlot(int... slot) {
        for (int i : slot) {
            this.validateSlot(i);
            pageSlots.remove(i);
        }
    }

    @Override
    public void open(final HumanEntity player) {
        if (!player.isSleeping()) {
            this.getInventory().clear();
            this.populateGui();
            this.populatePaginatedItemsOnGui();
            this.openInventoryOnMainThread(player, this.getInventory());
        }
    }

    /**
     * Gets the current page number starting from 0
     *
     * @return The current page number
     */
    public int getCurrentPageNum() {
        return pageNum;
    }

    /**
     * Gets the next page number
     *
     * @return The next page number or {@link #pageNum} if no next is present
     */
    public int getNextPageNum() {
        if (pageNum + 1 >= getTotalNumberOfPages()) return pageNum;
        return pageNum + 1;
    }

    /**
     * Gets the previous page number
     *
     * @return The previous page number or {@link #pageNum} if no previous is present
     */
    public int getPrevPageNum() {
        if (pageNum == 0) return pageNum;
        return pageNum - 1;
    }

    /**
     * Goes to the next page
     *
     * @return False if there is no next page.
     */
    public boolean next() {
        if (pageNum + 1 >= getTotalNumberOfPages()) return false;

        pageNum++;
        update();
        return true;
    }

    /**
     * Goes to the previous page if possible
     *
     * @return False if there is no previous page.
     */
    public boolean previous() {
        if (pageNum == 0) return false;

        pageNum--;
        update();
        return true;
    }

    /**
     * Gets the number of pages the GUI has
     *
     * @return The pages number
     */
    public int getTotalNumberOfPages() {
        return (int) Math.ceil((double) paginatedItems.size() / calculateMaxContentPerPage());
    }

    public int calculateMaxContentPerPage() {
        int count = 0;

        for (Integer slot : pageSlots) {
            if (currentPage.containsKey(slot) == false && (getGuiItem(slot) != null || getInventory().getItem(slot) != null)) {
                continue;
            }
            count++;
        }

        return count;
    }

    /**
     * Gets the page number
     *
     * @return The current page number
     */
    protected int getPageNum() {
        return pageNum;
    }

    /**
     * Sets the page number. This will not update the GUI
     *
     * @param pageNum Sets the current page to be the specified number
     */
    protected void setPageNum(final int pageNum) {
        this.pageNum = NumberWrapper.of(pageNum)
                .bound(0, getTotalNumberOfPages())
                .get();
    }

    public void clearPaginatedItems() {
        paginatedItems.clear();
        currentPage.clear();
        pageNum = 0;
        update();
    }

    @Override
    public void update() {
        this.getInventory().clear();
        this.populateGui();
        this.populatePaginatedItemsOnGui();

        for(HumanEntity viewer : new ArrayList<>(this.getInventory().getViewers())) {
            ((Player)viewer).updateInventory();
        }
    }

    protected void populatePaginatedItemsOnGui() {
        // Adds the paginated items to the page's empty slots and non-blank slots
        for (final GuiItem guiItem : getPaginatedItemsAtPageNumber(pageNum)) {
            for (int slot = 0; slot < getRows() * 9; slot++) {
                if (getGuiItem(slot) != null || getInventory().getItem(slot) != null){
                    continue;
                }
                currentPage.put(slot, guiItem);
                this.getInventory().setItem(slot, guiItem.getItemStack());
                break;
            }
        }
    }

    public List<GuiItem> getPaginatedItemsAtPageNumber(final int givenPage) {
        if (givenPage == getPageNum()){
            return new ArrayList<>(currentPage.values());
        }

        final List<GuiItem> guiPage = new ArrayList<>();

        int pageSize = calculateMaxContentPerPage();

        int max = ((givenPage * pageSize) + pageSize);
        if (max > paginatedItems.size()) max = paginatedItems.size();

        for (int i = givenPage * pageSize; i < max; i++) {
            guiPage.add(paginatedItems.get(i));
        }

        return guiPage;
    }

}
