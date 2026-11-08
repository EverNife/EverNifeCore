package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.flag.Decision;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagSettings;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.TristateFlag;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * {@code owner-only-pickup}: TRUE makes an item dropped in the region its dropper's. A drop marks the owner
 * ({@link ItemContext#setItemOwner}) for the platform listener to keep; a pickup or a hopper is then denied, where the item
 * lies, to anyone but that owner. An item that left the region falls under wherever it lies now. The
 * {@link TypeFilterSettings} narrow it to some items or actors.
 */
public final class OwnerOnlyPickupFlag extends TristateFlag {

    public OwnerOnlyPickupFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "owner-only-pickup"),
                new LinkedHashSet<>(Arrays.asList(EventSource.ITEM_DROP, EventSource.ITEM_PICKUP, EventSource.ITEM_HOPPER_PICKUP)),
                Tristate.FALSE, Tristate.TRUE);
    }

    /** All of them: miss one and the item reaches someone other than its owner. */
    @Override
    public Set<EventSource> getRequiredSources() {
        return getNeeds();
    }

    @Override
    public boolean applies(ActionContext context) {
        return context instanceof ItemContext && getNeeds().contains(context.getSource());
    }

    @Override
    public Decision decide(ActionContext context, Tristate value, FlagSettings settings) {
        if (value != Tristate.TRUE || !applies(context) || !covers((ItemContext) context, settings)) {
            return Decision.PASS;
        }
        ItemContext item = (ItemContext) context;
        FPlayer player = item.getPlayer();
        if (item.getSource() == EventSource.ITEM_DROP) {
            if (player != null) {
                item.setItemOwner(player.getUniqueId());
            }
            return Decision.PASS;
        }
        UUID owner = item.getItemOwner();
        return owner == null || (player != null && owner.equals(player.getUniqueId())) ? Decision.PASS : Decision.DENY;
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
        return "TRUE: an item dropped in the region can be picked up there only by whoever dropped it, and no hopper pulls it."
                + " only/except in the settings narrow it to some items.";
    }
}
