package br.com.finalcraft.evernifecore.booster;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.core.BoosterEntry;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.region.RegionLookup;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * {@code evernifecore:region}: the {@code boosters} flag where the player stands (and on the holders that follow
 * them), each {@link BoosterEntry} a booster of the player's gain while it resolves there.
 */
public final class RegionBoosterSource implements BoosterSource {

    private final Caller origin;

    RegionBoosterSource(ECPluginData core) {
        this.origin = Caller.of(core, "region");
    }

    @Override
    public String getId() {
        return "evernifecore:region";
    }

    @Override
    public Collection<Booster> boostersFor(ExecutionContext ctx, String variableId) {
        FPlayer player = ctx.getPlayer();
        if (player == null) {
            return Collections.emptyList();
        }
        List<BoosterEntry> entries = RegionLookup.get().resolve(CoreFlags.BOOSTERS, player);
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<Booster> boosters = new ArrayList<>();
        for (BoosterEntry entry : entries) {
            if (entry.getOn() == null || !Boosters.normalize(entry.getOn()).equals(variableId)) {
                continue;
            }
            Booster.Builder builder = Booster.on(entry.getOn()).forOwner(ProgressOwner.player(player.getUniqueId())).from(origin);
            boosters.add((entry.getFactor() != null ? builder.factor(BigDecimal.valueOf(entry.getFactor()))
                    : builder.bonus(BigDecimal.valueOf(entry.getBonus()))).build());
        }
        return boosters;
    }

    @Override
    public String toString() {
        return getId();
    }
}
