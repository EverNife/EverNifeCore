package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.flag.FlagScope;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.SessionContext;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagCategory;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagSettings;
import br.com.finalcraft.evernifecore.flag.FlagType;
import br.com.finalcraft.evernifecore.player.state.HeldPlayerState;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A session flag that changes one slot of the player ({@link HeldPlayerState}) while they are inside and gives back what
 * they had when the value stops applying - on leaving, on quitting, or on the next join after a server stop when
 * {@link PlayerStateSettings#getRestore() restore} is {@code persistent}. On a platform without its
 * {@link #getNeededCapabilities() capability} it does nothing, and the boot log says so.
 */
public abstract class PlayerStateFlag<T> extends Flag<T> {

    private final IPlayerStateAccess.Capability capability;

    protected PlayerStateFlag(String name, FlagType<T> type, Set<EventSource> needs, @Nullable T defaultValue,
                              IPlayerStateAccess.Capability capability) {
        super(FlagKey.of(CoreFlags.OWNER, name), type, needs, defaultValue);
        this.capability = capability;
    }

    /** The slot this flag holds while its value applies. */
    protected abstract String getSlot();

    /** Puts the state {@code held} asks for on the player - this flag's value is among its holders. */
    protected abstract void apply(SessionContext context, IPlayerStateAccess access, HeldPlayerState held);

    /** Whether {@code value} changes anything; one that does not (an undefined tristate) holds nothing. */
    protected boolean acts(IPlayerStateAccess access, T value) {
        return true;
    }

    @Override
    public FlagCategory getCategory() {
        return FlagCategory.SESSION;
    }

    @Override
    public Set<IPlayerStateAccess.Capability> getNeededCapabilities() {
        return Collections.singleton(capability);
    }

    @Override
    public Class<? extends FlagSettings> getSettingsType() {
        return PlayerStateSettings.class;
    }


    @Override
    public void onEnter(SessionContext context, T value) {
        IPlayerStateAccess access = access();
        if (access == null || !acts(access, value)) {
            return;
        }
        PlayerStateSettings settings = context.getSettings(this);
        HeldPlayerState held = context.getSession().getHeldState();
        held.hold(getSlot(), this, value, player -> apply(context, access, held), settings.isPersistent(), context.getPlayer());
        apply(context, access, held);
    }

    @Override
    public void onRevalidate(SessionContext context, @Nullable T oldValue, @Nullable T newValue) {
        if (newValue == null) {
            onLeave(context, oldValue);
        } else {
            IPlayerStateAccess access = access();
            if (access != null && !acts(access, newValue)) {
                onLeave(context, oldValue);
            } else {
                onEnter(context, newValue); //another plugin may have moved the player's state meanwhile: put it back
            }
        }
    }

    @Override
    public void onLeave(SessionContext context, @Nullable T lastValue) {
        IPlayerStateAccess access = access();
        if (access == null) {
            return;
        }
        context.getSession().getHeldState().release(getSlot(), this, context.getPlayer());
    }

    private @Nullable IPlayerStateAccess access() {
        IPlayerStateAccess access = EverNifeCore.getPlatform().getPlayerState();
        return access.supports(capability) ? access : null;
    }

    /** It follows the player: a holder that follows them may carry it. */
    @Override
    public FlagScope getScope() {
        return FlagScope.ACTOR;
    }
}
