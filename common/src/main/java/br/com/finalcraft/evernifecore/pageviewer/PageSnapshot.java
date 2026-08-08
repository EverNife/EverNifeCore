package br.com.finalcraft.evernifecore.pageviewer;

import java.util.Collections;
import java.util.List;

/**
 * One ordered read of the source, numbered and already cut to the page's ceiling. Immutable and
 * shared by every reader of the page: what varies per reader is the text, and the text is not here.
 *
 * <p>This is what the cache holds. The expensive half of a page - consulting the source and ordering
 * it - is the half that does not depend on who is reading, so it is the only half worth keeping.</p>
 */
public final class PageSnapshot<OBJ> {

    private final List<Row<OBJ>> rows;
    private final int totalBeforeCap;
    private final long builtAt;

    PageSnapshot(List<Row<OBJ>> rows, int totalBeforeCap, long builtAt) {
        this.rows = Collections.unmodifiableList(rows);
        this.totalBeforeCap = totalBeforeCap;
        this.builtAt = builtAt;
    }

    public List<Row<OBJ>> getRows() {
        return rows;
    }

    /** How many entries this snapshot actually carries. */
    public int getShownCount() {
        return rows.size();
    }

    /** How many the source returned, before {@code maxEntries} cut it. */
    public int getTotalCount() {
        return totalBeforeCap;
    }

    public long getBuiltAt() {
        return builtAt;
    }

    /** Whether the ceiling dropped something the source had returned. */
    public boolean isTruncated() {
        return totalBeforeCap > rows.size();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}
