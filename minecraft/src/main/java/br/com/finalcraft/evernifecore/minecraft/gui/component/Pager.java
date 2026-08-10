package br.com.finalcraft.evernifecore.minecraft.gui.component;

import br.com.finalcraft.evernifecore.minecraft.gui.state.AbstractState;
import br.com.finalcraft.evernifecore.minecraft.gui.state.State;

/**
 * Which page of a list is on screen, as a {@link State} rather than a flag: a component that
 * remembers it redraws when the page turns, and a title that reads it renames itself.
 *
 * <p>The page size is never configured. It is the size of the region the list was poured into, which
 * is what makes an admin who widens that region in the yml widen the page along with it.</p>
 *
 * <p>Pages are 1-based, because that is what a player reads. Turning past either end does nothing:
 * there is no wrap-around to make a list look infinite.</p>
 */
public final class Pager extends AbstractState<Integer> {

    private int page = 1;
    private int pageSize = 0;
    private int totalEntries = 0;

    @Override
    public Integer get() {
        return page;
    }

    /** The page on screen, counting from 1. */
    public int getPage() {
        return page;
    }

    /** How many entries fit on a page - the size of the region, once the window has been measured. */
    public int getPageSize() {
        return pageSize;
    }

    public int getTotalEntries() {
        return totalEntries;
    }

    /** At least 1: an empty list still has a page, and it is the one showing nothing. */
    public int getTotalPages() {
        if (pageSize <= 0) {
            return 1;
        }
        return Math.max(1, (totalEntries + pageSize - 1) / pageSize);
    }

    public boolean hasNext() {
        return page < getTotalPages();
    }

    public boolean hasPrevious() {
        return page > 1;
    }

    /** Turns to {@code page}, clamped to what exists. Staying put invalidates nothing. */
    public void setPage(int page) {
        int wanted = Math.min(Math.max(1, page), getTotalPages());
        if (wanted == this.page) {
            return;
        }
        this.page = wanted;
        invalidate();
    }

    public void next() {
        setPage(page + 1);
    }

    public void previous() {
        setPage(page - 1);
    }

    public void first() {
        setPage(1);
    }

    public void last() {
        setPage(getTotalPages());
    }

    /**
     * What the render just measured: how many slots the region has and how many entries the source
     * answered. Measuring does not invalidate - a render that redrew itself for having rendered would
     * not stop - unless the measurement MOVED the page, which is a change like any other and is what
     * whoever reads the page number is waiting for.
     */
    void measure(int pageSize, int totalEntries) {
        this.pageSize = Math.max(0, pageSize);
        this.totalEntries = Math.max(0, totalEntries);
        int within = Math.min(Math.max(1, page), getTotalPages());
        if (within != this.page) {
            this.page = within;
            invalidate();
        }
    }

    @Override
    public String toString() {
        return "Pager{" + page + "/" + getTotalPages() + ", " + totalEntries + " entries}";
    }

}
