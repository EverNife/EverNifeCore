package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.region.RegionId;

import java.util.Locale;

/** A menu's name: {@code owner:name}, the owner being the plugin that registers it; compared ignoring case. Immutable. */
public final class MenuId {

    private final String owner;
    private final String name;

    private MenuId(String owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    /** @throws IllegalArgumentException when either side is not {@code [a-z0-9_-]{1,64}} */
    public static MenuId of(String owner, String name) {
        return new MenuId(RegionId.requireOwner(owner.toLowerCase(Locale.ROOT)), RegionId.requireName("menu", name.toLowerCase(Locale.ROOT)));
    }

    /**
     * Reads {@code owner:name}; a bare {@code name} belongs to {@code defaultOwner}.
     *
     * @throws IllegalArgumentException when the text has more than one {@code :} or a side breaks the rule
     */
    public static MenuId parse(String text, String defaultOwner) {
        String trimmed = text == null ? "" : text.trim();
        int colon = trimmed.indexOf(':');
        if (colon < 0) {
            return of(defaultOwner, trimmed);
        }
        if (trimmed.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("Invalid menu id '" + text + "': write it as owner:name with exactly one ':', e.g."
                    + " finalmenus:shop-vip.");
        }
        return of(trimmed.substring(0, colon), trimmed.substring(colon + 1));
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MenuId && ((MenuId) other).owner.equals(owner) && ((MenuId) other).name.equals(name);
    }

    @Override
    public int hashCode() {
        return 31 * owner.hashCode() + name.hashCode();
    }

    @Override
    public String toString() {
        return owner + ":" + name;
    }
}
