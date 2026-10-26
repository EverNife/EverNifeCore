package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.flag.FlagScope;
import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagSettings;

/**
 * TRUE: items cannot be picked up ({@link EventSource#ITEM_PICKUP}) or dropped ({@link EventSource#ITEM_DROP}) in the
 * region - one instance per source - narrowed to some items by {@link TypeFilterSettings}.
 */
public final class DenyItemFlag extends DenyFlag {

    private final String description;

    /** @param source {@link EventSource#ITEM_PICKUP} or {@link EventSource#ITEM_DROP} */
    public DenyItemFlag(String name, EventSource source, String description) {
        super(FlagKey.of(CoreFlags.OWNER, name), source);
        this.description = description;
    }

    @Override
    public boolean applies(ActionContext context) {
        return context instanceof ItemContext && getNeeds().contains(context.getSource());
    }

    @Override
    public Class<? extends FlagSettings> getSettingsType() {
        return TypeFilterSettings.class;
    }

    @Override
    protected boolean covers(ActionContext context, FlagSettings settings) {
        return !(settings instanceof TypeFilterSettings) || ((TypeFilterSettings) settings).matches(((ItemContext) context).getItemKey());
    }

    @Override
    public String getDescription() {
        return description;
    }

    /** It follows the player: a holder that follows them may carry it. */
    @Override
    public FlagScope getScope() {
        return FlagScope.ACTOR;
    }
}
