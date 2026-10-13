package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.DamageContext;

/** TRUE: animals in the region cannot be hurt. */
public final class DenyDamageAnimalsFlag extends DenyFlag {

    public DenyDamageAnimalsFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-damage-animals"), EventSource.DAMAGE);
    }

    @Override
    public boolean applies(ActionContext context) {
        return hurts(context, DamageContext.Kind.ANIMAL);
    }

    @Override
    public String getDescription() {
        return "TRUE: animals in the region cannot be hurt.";
    }
}
