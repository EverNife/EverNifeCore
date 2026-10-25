package br.com.finalcraft.evernifecore.pageviewer.theme;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.CommandMessageContext;
import br.com.finalcraft.evernifecore.pageviewer.PageViewer;
import br.com.finalcraft.evernifecore.ranking.RankedSnapshot;
import jakarta.annotation.Nullable;

import java.util.Optional;

/**
 * What the chrome of a page is allowed to know: the page and the read it shows, not the objects on it. Built once per
 * recipient, because that is the granularity everything on it varies at - the language it is written in and where
 * this particular reader stands on the list.
 */
public final class PageContext {

    private final PageViewer<?> page;
    private final RankedSnapshot<?> snapshot;
    private final int currentPage;
    private final int lastPage;
    private final boolean aboutPlayers;
    private final @Nullable FCommandSender reader;
    private final CommandMessageContext origin;

    // positionOf walks the whole snapshot, so it happens on the first mention and never again - and never at all on a
    // page whose text does not cite it.
    private Optional<Integer> resolvedRank = null;

    public PageContext(PageViewer<?> page,
                       RankedSnapshot<?> snapshot,
                       int currentPage,
                       int lastPage,
                       boolean aboutPlayers,
                       @Nullable FCommandSender reader,
                       CommandMessageContext origin) {
        this.page = page;
        this.snapshot = snapshot;
        this.currentPage = currentPage;
        this.lastPage = lastPage;
        this.aboutPlayers = aboutPlayers;
        this.reader = reader;
        this.origin = origin;
    }

    /**
     * The page being rendered. A navigation strategy needs it to name the page or to open the reader's session on it;
     * the chrome has no business calling anything on it.
     */
    public PageViewer<?> getPage() {
        return page;
    }

    /** The read this page shows - shared by every reader of the list. */
    public RankedSnapshot<?> getSnapshot() {
        return snapshot;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getLastPage() {
        return lastPage;
    }

    /** How many entries the page holds after its ceiling - what the reader can actually reach. */
    public int getShownRows() {
        return snapshot.getShownCount();
    }

    /** How many the source returned, which is the number worth telling the reader. */
    public int getTotalRows() {
        return snapshot.getTotalCount();
    }

    public boolean isTruncated() {
        return snapshot.isTruncated();
    }

    /** Whether the entries are players, which is the only thing the wording of a count depends on. */
    public boolean isAboutPlayers() {
        return aboutPlayers;
    }

    public @Nullable FCommandSender getReader() {
        return reader;
    }

    /**
     * The command scope the page was FIRST sent in. Navigation runs inside {@code /ecpage}, and a line citing
     * {@code ${label}} must still name the command that produced the page.
     */
    public CommandMessageContext getOrigin() {
        return origin;
    }

    /**
     * Where this reader stands on the ordered list, 1-based, or {@code null} when they are not on it at all - the
     * console, or a player the page is not about.
     */
    public @Nullable Integer getReaderRank() {
        if (resolvedRank == null) {
            resolvedRank = Optional.ofNullable(aboutPlayers && reader != null ? snapshot.positionOf(reader.getUniqueId()) : null);
        }
        return resolvedRank.orElse(null);
    }
}
