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
public class LayoutGui<P extends IPlayerData, L extends LayoutBase> extends Gui<L>
        implements SubjectHolder<P> {

    private final Subject<P> subject;

    public LayoutGui(@Nonnull L layout, @Nonnull P playerData) {
        super(requiredLayout(layout).getType(), layout.getRows(), layout);
        this.subject = new Subject<>(playerData);
    }

    @Override
    @Nonnull
    public Subject<P> getSubject() {
        return subject;
    }

    @Override
    @Nonnull
    public CompoundReplacer getReplacer() {
        return withSubject(super.getReplacer());
    }

}
