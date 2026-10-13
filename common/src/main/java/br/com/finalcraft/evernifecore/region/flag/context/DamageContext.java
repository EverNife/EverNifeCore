package br.com.finalcraft.evernifecore.region.flag.context;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import jakarta.annotation.Nullable;

import java.util.Objects;

/** Something about to be hurt. {@link #getPlayer()} is the attacker traced to its root; null for no player behind it. */
public final class DamageContext extends ActionContext {

    public enum Kind {
        PLAYER, ANIMAL, MONSTER, OTHER
    }

    private final Kind victimKind;
    private final @Nullable FPlayer victimPlayer;
    private final String victimTypeId;

    /**
     * @param position     where the victim stands
     * @param victimPlayer the victim when it is a player
     * @param victimTypeId the platform-neutral id of the victim's type, e.g. {@code minecraft:cow}
     */
    public DamageContext(WorldLocPos position, @Nullable FPlayer attacker, @Nullable Object nativeEvent,
                         Kind victimKind, @Nullable FPlayer victimPlayer, String victimTypeId) {
        super(EventSource.DAMAGE, position, attacker, nativeEvent);
        this.victimKind = Objects.requireNonNull(victimKind, "victimKind");
        this.victimPlayer = victimPlayer;
        this.victimTypeId = Objects.requireNonNull(victimTypeId, "victimTypeId");
    }

    public Kind getVictimKind() {
        return victimKind;
    }

    public @Nullable FPlayer getVictimPlayer() {
        return victimPlayer;
    }

    public String getVictimTypeId() {
        return victimTypeId;
    }
}
