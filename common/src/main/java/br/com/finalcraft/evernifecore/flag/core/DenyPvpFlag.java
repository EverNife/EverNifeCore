package br.com.finalcraft.evernifecore.flag.core;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import br.com.finalcraft.evernifecore.flag.FlagScope;
import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.ECEntityType;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.flag.FlagKey;

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

    /** It follows the player: a holder that follows them may carry it. */
    @Override
    public FlagScope getScope() {
        return FlagScope.ACTOR;
    }

    @Override
    public Set<String> getChannels() {
        return new HashSet<>(Arrays.asList("damage:pvp"));
    }
}
