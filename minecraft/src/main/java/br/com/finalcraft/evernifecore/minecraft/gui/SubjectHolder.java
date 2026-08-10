package br.com.finalcraft.evernifecore.minecraft.gui;

import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import jakarta.annotation.Nonnull;

/**
 * A screen that is ABOUT a player: whoever is looking at it and whoever it speaks of are two different
 * people, and {@code %playerdata_*%} answers for the second.
 *
 * @param <P> the concrete player data the screen speaks of
 */
public interface SubjectHolder<P extends IPlayerData> {

    /** Whose data this screen shows, as the holder the placeholders read through. */
    @Nonnull
    Subject<P> getSubject();

    /** Whose data this screen shows. Not the viewer, and not necessarily online. */
    @Nonnull
    default P getPlayerData() {
        return getSubject().get();
    }

    /**
     * Points this screen at another player. The next {@code refresh()} draws it: the replacer is built
     * fresh on every render, so nothing else has to be rebuilt - and the page, the filter and whatever
     * the components remembered stay where they are, because the screen itself never left.
     */
    default void setPlayerData(@Nonnull P playerData) {
        getSubject().set(playerData);
    }

    /** {@code base} with this subject's placeholders after it. */
    @Nonnull
    default CompoundReplacer withSubject(@Nonnull CompoundReplacer base) {
        return getSubject().appendTo(base);
    }

}
