package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
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

    /** TRUE denies, when the used block or entity is one the settings cover. */
    @Override
    public Decision decide(ActionContext context, Tristate value, FlagSettings settings) {
        if (decide(context, value) != Decision.DENY) {
            return Decision.PASS;
        }
        String id = usedId(context);
        boolean covered = id == null || !(settings instanceof InteractSettings) || ((InteractSettings) settings).matches(id);
        return covered ? Decision.DENY : Decision.PASS;
    }

    /** The block or entity type being used, or null for a context that names neither. */
    protected static @Nullable String usedId(ActionContext context) {
        if (context instanceof BlockContext) {
            return ((BlockContext) context).getBlockId();
        }
        if (context instanceof EntityContext) {
            return ((EntityContext) context).getEntityTypeId();
        }
        return null;
    }

    protected static boolean isBlockUse(ActionContext context) {
        return context instanceof BlockContext && context.getSource() == EventSource.INTERACT;
    }
}
