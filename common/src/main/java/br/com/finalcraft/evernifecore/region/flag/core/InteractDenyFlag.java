package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.actor.TypeKey;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.BlockContext;
import br.com.finalcraft.evernifecore.region.flag.context.EntityContext;
import jakarta.annotation.Nullable;

/** The shape of an interact deny flag: TRUE denies using a block or an entity, narrowed by {@link InteractSettings}. */
public abstract class InteractDenyFlag extends DenyFlag {

    /** @param parent the interact flag this one refines; null for none */
    protected InteractDenyFlag(FlagKey key, @Nullable InteractDenyFlag parent) {
        super(key, EventSource.INTERACT, parent);
    }

    @Override
    public Class<? extends FlagSettings> getSettingsType() {
        return InteractSettings.class;
    }

    /** Covers the used block or entity when the {@link InteractSettings} do. */
    @Override
    protected boolean covers(ActionContext context, FlagSettings settings) {
        TypeKey used = usedKey(context);
        return used == null || !(settings instanceof InteractSettings) || ((InteractSettings) settings).matches(used);
    }

    /** The block or entity type being used, or null for a context that names neither. */
    protected static @Nullable TypeKey usedKey(ActionContext context) {
        if (context instanceof BlockContext) {
            return ((BlockContext) context).getBlockKey();
        }
        if (context instanceof EntityContext) {
            return ((EntityContext) context).getEntity().getKey();
        }
        return null;
    }

    protected static boolean isBlockUse(ActionContext context) {
        return context instanceof BlockContext && context.getSource() == EventSource.INTERACT;
    }
}
