package br.com.finalcraft.evernifecore.pageviewer.theme;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * The chrome around a page: what comes before the entries, what comes after them, and what a page
 * with no entries says instead. Rendered per recipient, so every line of it speaks the reader's
 * language.
 *
 * <p>What the page itself declares through {@code setFormatHeader}/{@code setFormatFooter} is written
 * around this: the theme's header first, then the page's own header lines; the page's own footer
 * lines first, then the theme's footer.</p>
 */
public interface PageTheme {

    List<FancyText> header(PageContext context);

    List<FancyText> footer(PageContext context);

    /** What a page with no entries says. {@code null} for a theme that would rather say nothing. */
    @Nullable FancyText empty(PageContext context);

    /** The look this core has always shipped: a rule above the entries, optionally a date and a count. */
    static ClassicPageTheme classic() {
        return ClassicPageTheme.LITERAL;
    }

    /**
     * The same, with the rule measured against the platform's chat width instead of written out.
     * The result depends on the CLIENT - chat width, GUI scale, forced unicode font and resource
     * packs are all its options - so this fits the server that knows its players use one setup.
     */
    static ClassicPageTheme autoFit() {
        return ClassicPageTheme.AUTO_FIT;
    }

    /** No chrome at all: the entries and nothing else. */
    static PageTheme none() {
        return NoChrome.INSTANCE;
    }

    final class NoChrome implements PageTheme {

        static final NoChrome INSTANCE = new NoChrome();

        private NoChrome() {
        }

        @Override
        public List<FancyText> header(PageContext context) {
            return Collections.emptyList();
        }

        @Override
        public List<FancyText> footer(PageContext context) {
            return Collections.emptyList();
        }

        @Override
        public @Nullable FancyText empty(PageContext context) {
            return null;
        }
    }
}
