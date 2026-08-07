package br.com.finalcraft.evernifecore.minecraft.gui;

import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import jakarta.annotation.Nonnull;

/**
 * A screen that is ABOUT a player: it shows {@code P}'s data to whoever opened it, and those two are
 * not the same person.
 *
 * <p>{@code getViewer()} - reachable from the click, from a component or from a rendered icon - is the
 * player with the window open, and it always exists. {@link #getPlayerData()} is whose data is on
 * screen, and it may be offline. A staff member inspecting somebody else's profile is the same class
 * with no extra line: the {@code %playerdata_*%} placeholders answer for the subject, while an icon's
 * permission is checked against whoever is looking.</p>
 *
 * <p>A screen that is about a player AND answers a value is {@link LayoutResultGui}.</p>
 *
 * <pre>{@code
 * public class ProfileGui extends LayoutGui<MyPlayerData, ProfileLayout> {
 *     public ProfileGui(MyPlayerData subject) {
 *         super(Layouts.of(ProfileLayout.class), subject);
 *     }
 * }
 * }</pre>
 */
public class LayoutGui<P extends IPlayerData, L extends LayoutBase> extends Gui<L> {

    private final Subject<P> subject;

    public LayoutGui(@Nonnull L layout, @Nonnull P playerData) {
        super(required(layout).getType(), layout.getRows(), layout);
        this.subject = new Subject<>(playerData);
    }

    /** Whose data this screen shows. Not the viewer, and not necessarily online. */
    @Nonnull
    public P getPlayerData() {
        return subject.get();
    }

    /**
     * Points this screen at another player. The next {@code refresh()} draws it: the replacer is built
     * fresh on every render, so nothing else has to be rebuilt - and the page, the filter and whatever
     * the components remembered stay where they are, because the screen itself never left.
     */
    protected void setPlayerData(@Nonnull P playerData) {
        subject.set(playerData);
    }

    @Override
    @Nonnull
    public CompoundReplacer getReplacer() {
        return subject.appendTo(super.getReplacer());
    }

    static <L extends LayoutBase> L required(L layout) {
        if (layout == null) {
            throw new IllegalArgumentException("A screen about a player is sized and decorated by its "
                    + "layout, so it cannot be built without one. Read it with Layouts.of(MyLayout.class).");
        }
        return layout;
    }

}
