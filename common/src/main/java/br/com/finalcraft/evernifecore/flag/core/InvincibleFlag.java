package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.ECEntityType;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.flag.FlagKey;

/** TRUE: players in the region take no damage, whatever hurts them. */
public final class InvincibleFlag extends DenyFlag {

    public InvincibleFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "invincible"), EventSource.DAMAGE);
    }

    @Override
    public boolean applies(ActionContext context) {
        return hurts(context, ECEntityType.PLAYER, ECEntityType.FAKE_PLAYER);
    }

    @Override
    public String getDescription() {
        return "TRUE: players in the region take no damage, whatever hurts them.";
    }
}
