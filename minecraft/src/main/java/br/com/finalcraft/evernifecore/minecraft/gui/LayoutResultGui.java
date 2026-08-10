package br.com.finalcraft.evernifecore.minecraft.gui;

import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import jakarta.annotation.Nonnull;

/**
 * A screen that is about a player AND answers a value - {@link LayoutGui} and {@link ResultGui} at
 * once, which single inheritance will not give.
 *
 * <p>A staff screen that picks a punishment for somebody is the case: the icons read
 * {@code %playerdata_*%} of the accused, and {@code ctx.back(punishment)} hands the choice to whoever
 * opened it.</p>
 *
 * <pre>{@code
 * public class PunishGui extends LayoutResultGui<Punishment, StaffData, PunishLayout> {
 *     public PunishGui(StaffData accused) {
 *         super(Layouts.of(PunishLayout.class), accused);
 *         icon(l -> l.BAN).onClick(ctx -> answer(ctx, Punishment.BAN));
 *     }
 * }
 * }</pre>
 *
 * @param <R> what this screen hands back
 * @param <P> whose data it shows
 * @param <L> the layout it was built from
 */
public class LayoutResultGui<R, P extends IPlayerData, L extends LayoutBase> extends ResultGui<R, L>
        implements SubjectHolder<P> {

    private final Subject<P> subject;

    public LayoutResultGui(@Nonnull L layout, @Nonnull P playerData) {
        super(layout);
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
