package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.region.RegionId;

/**
 * The identity of a quest: {@code owner:name}, where the owner is the name of the plugin whose {@link QuestEngine} holds
 * it. Both parts follow the {@link RegionId} rules and are lowercased, so equality works on the text form.
 */
public final class QuestId implements Comparable<QuestId> {

    private final String owner;
    private final String name;
    private final String text;

    private QuestId(String owner, String name) {
        this.owner = owner;
        this.name = name;
        this.text = owner + ":" + name;
    }

    /** @throws IllegalArgumentException naming the offending part, the rule and a valid example */
    public static QuestId of(String owner, String name) {
        return new QuestId(RegionId.requireOwner(owner), RegionId.requireName("quest", name));
    }

    /**
     * {@code owner:name}, or a bare {@code name} read under {@code defaultOwner}.
     *
     * @throws IllegalArgumentException when the text has more than one {@code :} or a part breaks the rules
     */
    public static QuestId parse(String text, String defaultOwner) {
        int colon = text == null ? -1 : text.indexOf(':');
        if (colon < 0) {
            return of(defaultOwner, text);
        }
        if (text.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("Invalid quest id '" + text + "': write it as owner:name with one ':', or as a bare name"
                    + " of the engine's own plugin, e.g. 'finalquests:mine-iron' or 'mine-iron'.");
        }
        return of(text.substring(0, colon), text.substring(colon + 1));
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    @Override
    public int compareTo(QuestId other) {
        return text.compareTo(other.text);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof QuestId && text.equals(((QuestId) other).text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /** The {@code owner:name} form, always lowercase. */
    @Override
    public String toString() {
        return text;
    }
}
