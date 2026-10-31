package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.ECEntityType;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.flag.FlagInteraction;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagScope;
import br.com.finalcraft.evernifecore.group.GroupLookup;

import java.util.Collections;
import java.util.Set;

/**
 * TRUE: two players who share a group ({@link GroupLookup#shareGroup}, any provider, a region's own groups aside)
 * cannot hurt each other. Set on a place it holds for everyone there; set on a group it holds wherever its members
 * are. Independent of {@code deny-pvp}: that one TRUE already denies every hit, and {@code deny-pvp: FALSE} with this
 * TRUE is PvP between different groups only. Holds for everyone - the bypass permission does not lift it.
 */
public final class DenyFriendlyFireFlag extends DenyFlag {

    public DenyFriendlyFireFlag(FlagKey key) {
        super(key, EventSource.DAMAGE);
    }

    @Override
    public boolean isBypassable() {
        return false;
    }

    @Override
    public boolean applies(ActionContext context) {
        if (!hurts(context, ECEntityType.PLAYER, ECEntityType.FAKE_PLAYER)) {
            return false;
        }
        FPlayer attacker = context.getPlayer();
        FPlayer hurt = victim(context);
        return attacker != null && hurt != null && GroupLookup.get().shareGroup(attacker.getUniqueId(), hurt.getUniqueId());
    }

    @Override
    public String getDescription() {
        return "TRUE: members of one group cannot hurt each other. With deny-pvp FALSE, PvP happens between different groups only.";
    }

    /** It follows the player: a group carries it wherever its members are. */
    @Override
    public FlagScope getScope() {
        return FlagScope.ACTOR;
    }

    @Override
    public Set<FlagInteraction> getInteractions() {
        return Collections.singleton(FlagInteraction.with(FlagKey.of(CoreFlags.OWNER, "deny-pvp"),
                "deny-pvp = TRUE already denies every hit; deny-pvp = FALSE + deny-friendly-fire = TRUE: PvP only between different groups"));
    }
}
