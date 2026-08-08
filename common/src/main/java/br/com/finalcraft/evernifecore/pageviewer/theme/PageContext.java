package br.com.finalcraft.evernifecore.pageviewer.theme;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.CommandMessageContext;
import br.com.finalcraft.evernifecore.pageviewer.PageViewer;
import jakarta.annotation.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * What the chrome of a page is allowed to know: the page, and not the objects on it. Built once per
 * recipient, because that is the granularity everything on it varies at - the language it is written
 * in and where this particular reader stands on the list.
 */
public final class PageContext {

    private final PageViewer<?> page;
    private final int currentPage;
    private final int lastPage;
    private final int shownRows;
    private final int totalRows;
    private final boolean aboutPlayers;
    private final @Nullable FCommandSender reader;
    private final CommandMessageContext origin;
    private final Supplier<Integer> readerRank;

    // The scan behind readerRank walks the whole snapshot, so it happens on the first mention and
    // never again - and never at all on a page whose text does not cite it.
    private Optional<Integer> resolvedRank = null;

    public PageContext(PageViewer<?> page,
                       int currentPage,
                       int lastPage,
                       int shownRows,
                       int totalRows,
                       boolean aboutPlayers,
                       @Nullable FCommandSender reader,
                       CommandMessageContext origin,
                       Supplier<Integer> readerRank) {
        this.page = page;
        this.currentPage = currentPage;
        this.lastPage = lastPage;
        this.shownRows = shownRows;
        this.totalRows = totalRows;
        this.aboutPlayers = aboutPlayers;
        this.reader = reader;
        this.origin = origin;
        this.readerRank = readerRank;
    }

    /**
     * The page being rendered. A navigation strategy needs it to name the page or to open the
     * reader's session on it; the chrome has no business calling anything on it.
     */
    public PageViewer<?> getPage() {
        return page;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getLastPage() {
        return lastPage;
    }

    /** How many entries the page holds after its ceiling - what the reader can actually reach. */
    public int getShownRows() {
        return shownRows;
    }

    /** How many the source returned, which is the number worth telling the reader. */
    public int getTotalRows() {
        return totalRows;
    }

    public boolean isTruncated() {
        return shownRows < totalRows;
    }

    /** Whether the entries are players, which is the only thing the wording of a count depends on. */
    public boolean isAboutPlayers() {
        return aboutPlayers;
    }

    public @Nullable FCommandSender getReader() {
        return reader;
    }

    /**
     * The command scope the page was FIRST sent in. Navigation runs inside {@code /ecpage}, and a
     * line citing {@code ${label}} must still name the command that produced the page.
     */
    public CommandMessageContext getOrigin() {
        return origin;
    }

    /**
     * Where this reader stands on the ordered list, 1-based, or {@code null} when they are not on it
     * at all - the console, or a player the page is not about.
     */
    public @Nullable Integer getReaderRank() {
        if (resolvedRank == null) {
            resolvedRank = Optional.ofNullable(readerRank.get());
        }
        return resolvedRank.orElse(null);
    }
}
