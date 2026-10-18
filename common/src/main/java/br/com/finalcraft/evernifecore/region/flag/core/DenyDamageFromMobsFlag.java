package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.actor.ECEntityType;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;

/**
 * TRUE: animals and monsters with no player behind them cannot hurt a player in the region; falling, lava and the rest
 * of the world still do. The attacker is what hits: an arrow is no mob, whoever shot it.
 */
public final class DenyDamageFromMobsFlag extends DenyFlag {

    public DenyDamageFromMobsFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-damage-from-mobs"), EventSource.DAMAGE);
    }

    @Override
    public boolean applies(ActionContext context) {
        if (!hurts(context, ECEntityType.PLAYER, ECEntityType.FAKE_PLAYER) || context.getPlayer() != null) {
            return false;
        }
        ECEntityType attacker = context.getActor().getEntityType();
        return attacker == ECEntityType.MONSTER || attacker == ECEntityType.ANIMAL;
    }

    @Override
    public String getDescription() {
        return "TRUE: animals and monsters cannot hurt players in the region. Falling, lava and the rest of the world still do.";
    }
}
