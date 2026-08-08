package br.com.finalcraft.evernifecore.pageviewer.nav;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.pageviewer.theme.PageContext;
import jakarta.annotation.Nullable;

/**
 * How a reader reaches the other pages. The three strategies differ in one thing - what a click
 * runs - and they pay very different prices for it, so the choice belongs to whoever wrote the page.
 */
@FunctionalInterface
public interface PageNavigation {

    /** The bar under the page, or {@code null} for a page that offers no navigation at all. */
    @Nullable FancyText render(PageContext context);

    /** What {@link #command(String)} substitutes for the page number. */
    String PAGE_TOKEN = "%page%";

    /**
     * Re-runs the caller's OWN command, {@code %page%} standing for the page number - this takes
     * whatever line the page's command is reached by, and standardises nothing. Zero memory and
     * survives a restart, but it DOES run that command again, side effects and all. Only for a
     * command that is idempotent and cheap; one that charges the player, consumes a cooldown or
     * writes an audit entry must not use this.
     */
    static PageNavigation command(String commandLine) {
        return context -> PVExtraMessages.navigationBar(context,
                page -> commandLine.replace(PAGE_TOKEN, String.valueOf(page)));
    }

    /**
     * A page carrying an {@code id}, navigated by {@code /ecpage <pageId> <page>}. Zero memory,
     * survives a restart, and never re-runs the command that produced the page: the id resolves the
     * viewer straight out of {@code PageRegistry}. Only for a page whose content does not depend on
     * command arguments. The link stays readable ({@code /ecpage finaljobs:top 3}), which is what
     * makes it worth anything in the client's command history.
     */
    static PageNavigation registered(String pageId) {
        return context -> PVExtraMessages.navigationBar(context, page -> "/ecpage " + pageId + " " + page);
    }

    /**
     * A page built for this invocation, kept in memory for ten minutes and renewed on every click.
     * The fallback for a page that depends on an argument and therefore cannot be named. It cannot
     * survive a relog: nothing exists that could rebuild it.
     */
    static PageNavigation session() {
        return context -> {
            PageSession session = PageSessionManager.openOrRenew(context.getReader(), context.getPage(), context.getOrigin());
            if (session == null) {
                //A reader with no identity of their own - the console - cannot own a session, and a
                //link that answered to anybody would be the very leak this strategy exists to close.
                return PVExtraMessages.navigationBar(context, page -> "");
            }
            return PVExtraMessages.navigationBar(context, page -> "/ecpage " + session.getHandle() + " " + page);
        };
    }

    /** No bar at all - for a page that is printed once and never paged. */
    static PageNavigation none() {
        return context -> null;
    }
}
