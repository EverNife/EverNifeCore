package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.flag.Decision;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagSettings;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.TristateFlag;

import java.util.Collections;

/**
 * {@code destroy-dropped-items}: TRUE makes an item a player drops in the region leave their hand and never enter the
 * world. It never denies: it marks the drop ({@link ItemContext#setItemDestroyed}) for the platform listener, which
 * destroys the item only when nothing denied the drop - {@code deny-item-drop} keeps the item with the dropper. The
 * {@link TypeFilterSettings} narrow it to some droppers or items.
 */
public final class DestroyDroppedItemsFlag extends TristateFlag {

    public DestroyDroppedItemsFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "destroy-dropped-items"), Collections.singleton(EventSource.ITEM_DROP), Tristate.FALSE, Tristate.TRUE);
    }

    @Override
    public boolean applies(ActionContext context) {
        return context instanceof ItemContext && context.getSource() == EventSource.ITEM_DROP;
    }

    @Override
    public Decision decide(ActionContext context, Tristate value, FlagSettings settings) {
        if (value == Tristate.TRUE && applies(context) && covers((ItemContext) context, settings)) {
            ((ItemContext) context).setItemDestroyed(true);
        }
        return Decision.PASS;
    }

    private static boolean covers(ItemContext context, FlagSettings settings) {
        if (settings instanceof DenySettings && !((DenySettings) settings).covers(context.getActor())) {
            return false;
        }
        return !(settings instanceof TypeFilterSettings) || ((TypeFilterSettings) settings).matches(context.getItemKey());
    }

    @Override
    public Class<? extends FlagSettings> getSettingsType() {
        return TypeFilterSettings.class;
    }

    @Override
    public String getDescription() {
        return "TRUE: items players drop in the region leave their hand and never lie there (deny-item-drop keeps them in the"
                + " hand instead). only/except in the settings narrow it to some items.";
    }
}
