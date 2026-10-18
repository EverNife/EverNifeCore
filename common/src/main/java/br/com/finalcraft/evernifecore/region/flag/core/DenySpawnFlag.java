package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.actor.ECEntityType;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.SpawnContext;
import jakarta.annotation.Nullable;

/**
 * TRUE: creatures do not spawn in the region, narrowed to some by {@link TypeFilterSettings} on the creature's key.
 * {@code deny-spawn} speaks of every creature; a refinement speaks of one {@link ECEntityType} and, where it is not
 * set, reads {@code deny-spawn}.
 */
public final class DenySpawnFlag extends DenyFlag {

    private final @Nullable ECEntityType creature;
    private final String description;

    /** {@code deny-spawn}, about every creature. */
    public DenySpawnFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-spawn"), EventSource.SPAWN);
        this.creature = null;
        this.description = "TRUE: no creature spawns in the region; only/except in the settings narrow it to some.";
    }

    /** A refinement of {@code parent} about {@code creature} only. */
    public DenySpawnFlag(String name, DenySpawnFlag parent, ECEntityType creature, String description) {
        super(FlagKey.of(CoreFlags.OWNER, name), EventSource.SPAWN, parent);
        this.creature = creature;
        this.description = description;
    }

    /** The kind of creature this flag speaks of; null for every one. */
    public @Nullable ECEntityType getCreature() {
        return creature;
    }

    @Override
    public boolean applies(ActionContext context) {
        return context instanceof SpawnContext && (creature == null || ((SpawnContext) context).getEntity().getEntityType() == creature);
    }

    @Override
    public Class<? extends FlagSettings> getSettingsType() {
        return TypeFilterSettings.class;
    }

    @Override
    protected boolean covers(ActionContext context, FlagSettings settings) {
        return !(settings instanceof TypeFilterSettings)
                || ((TypeFilterSettings) settings).matches(((SpawnContext) context).getEntity().getKey());
    }

    @Override
    public String getDescription() {
        return description;
    }
}
