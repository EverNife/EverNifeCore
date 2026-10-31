package br.com.finalcraft.evernifecore.flag.core;

import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Arrays;
import br.com.finalcraft.evernifecore.flag.FlagInteraction;
import br.com.finalcraft.evernifecore.flag.FlagScope;
import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.ECEntityType;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import jakarta.annotation.Nullable;

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

    /** It follows the player: a holder that follows them may carry it. */
    @Override
    public FlagScope getScope() {
        return FlagScope.ACTOR;
    }

    /** It protects the victim: the victim's groups and following holders speak, not the attacker's. */
    @Override
    public @Nullable FPlayer getSubject(ActionContext context) {
        return victim(context);
    }

    @Override
    public Set<String> getChannels() {
        return new HashSet<>(Arrays.asList("damage:player-victim"));
    }

    @Override
    public Set<FlagInteraction> getInteractions() {
        return new LinkedHashSet<>(Arrays.asList(
                FlagInteraction.with(FlagKey.of(CoreFlags.OWNER, "damage-over-time"), "invincible = TRUE: no damage over time"),
                FlagInteraction.with(FlagKey.of(CoreFlags.OWNER, "damage-multiplier"), "invincible = TRUE: nothing to multiply")));
    }
}
