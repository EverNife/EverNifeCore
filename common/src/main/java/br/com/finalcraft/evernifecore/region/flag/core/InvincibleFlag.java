package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.DamageContext;

/** TRUE: players in the region take no damage, whatever hurts them. */
public final class InvincibleFlag extends DenyFlag {

    public InvincibleFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "invincible"), EventSource.DAMAGE);
    }

    @Override
    public boolean applies(ActionContext context) {
        return hurts(context, DamageContext.Kind.PLAYER);
    }

    @Override
    public String getDescription() {
        return "TRUE: players in the region take no damage, whatever hurts them.";
    }
}
