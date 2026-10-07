package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.BlockContext;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EntityContext;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import jakarta.annotation.Nullable;

/** The shape of the stock interact flags: TRUE denies using a block or an entity, narrowed by {@link InteractSettings}. */
abstract class StockInteractFlag extends StockDenyFlag {

    StockInteractFlag(ECPluginData owner, String name, @Nullable StockInteractFlag parent) {
        super(owner, name, EventSource.INTERACT, parent);
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
    static @Nullable String usedId(ActionContext context) {
        if (context instanceof BlockContext) {
            return ((BlockContext) context).getBlockId();
        }
        if (context instanceof EntityContext) {
            return ((EntityContext) context).getEntityTypeId();
        }
        return null;
    }

    static boolean isBlockUse(ActionContext context) {
        return context instanceof BlockContext && context.getSource() == EventSource.INTERACT;
    }
}
