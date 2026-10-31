package br.com.finalcraft.evernifecore.dialogue;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.RegionId;

import java.util.Objects;

/** Which dialogue, as {@code owner:name}: the plugin that registered it and its name there, both lowercased. */
public final class DialogueId {

    private final String owner;
    private final String name;

    private DialogueId(String owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    /** @throws IllegalArgumentException when {@code name} is not {@code [a-z0-9_-]{1,64}} */
    public static DialogueId of(ECPluginData owner, String name) {
        return of(owner.getMetaInfo().getName(), name);
    }

    /** @throws IllegalArgumentException when either half breaks the rule of a region id */
    public static DialogueId of(String owner, String name) {
        return new DialogueId(RegionId.requireOwner(owner), RegionId.requireName("dialogue", name));
    }

    /**
     * {@code owner:name} read back, case-insensitively.
     *
     * @throws IllegalArgumentException when the text is not one owner and one name around a single {@code :}
     */
    public static DialogueId parse(String text) {
        int colon = text == null ? -1 : text.indexOf(':');
        if (colon <= 0 || text.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("Expected owner:name, got '" + text + "': the owner is the plugin that registered the"
                    + " dialogue and the name is its name there, e.g. 'finalcolonies:tomas'.");
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
    public boolean equals(Object other) {
        return other instanceof DialogueId && ((DialogueId) other).owner.equals(owner) && ((DialogueId) other).name.equals(name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, name);
    }

    @Override
    public String toString() {
        return owner + ":" + name;
    }
}
