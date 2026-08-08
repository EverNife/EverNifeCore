package br.com.finalcraft.evernifecore.pageviewer;

import jakarta.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The pages reachable by name. A named page is built once, so a link to it is a plain readable
 * string: no handle to keep alive, nothing to expire, and the id means the same thing tomorrow.
 *
 * <p>Nobody registers by hand - declaring {@code .id(...)} on the builder is the registration.</p>
 */
public final class PageRegistry {

    private static final Map<String, PageViewer<?>> PAGES = new ConcurrentHashMap<>();

    private PageRegistry() {
    }

    /**
     * @param pageId {@code plugin:name}, e.g. {@code finaljobs:top}
     */
    static void register(String pageId, PageViewer<?> viewer) {
        //Overwriting is what a plugin reload looks like from here: the new page is the one that is
        //going to be sent, so it has to be the one the old links resolve to.
        PAGES.put(pageId, viewer);
    }

    public static @Nullable PageViewer<?> find(String pageId) {
        return PAGES.get(pageId);
    }

    /** Drops a page's registration - for a plugin unloading, and for a test that starts from nothing. */
    public static void unregister(String pageId) {
        PAGES.remove(pageId);
    }
}
