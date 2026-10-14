package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.actor.ECEntityType;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;

/** TRUE: players cannot hurt each other in the region. Holds for everyone - the bypass permission does not lift it. */
public final class DenyPvpFlag extends DenyFlag {

    public DenyPvpFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-pvp"), EventSource.DAMAGE);
    }

    @Override
    public boolean isBypassable() {
        return false;
    }

    @Override
    public boolean applies(ActionContext context) {
        return hurts(context, ECEntityType.PLAYER, ECEntityType.FAKE_PLAYER) && context.getPlayer() != null;
    }

    @Override
    public String getDescription() {
        return "TRUE: players cannot hurt each other in the region. Holds for everyone - the bypass permission does not lift it.";
    }
}
