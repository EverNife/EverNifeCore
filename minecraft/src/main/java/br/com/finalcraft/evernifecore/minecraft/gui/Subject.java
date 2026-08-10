package br.com.finalcraft.evernifecore.minecraft.gui;

import br.com.finalcraft.evernifecore.ontime.OntimeManager;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.time.ECTimeFormat;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import jakarta.annotation.Nonnull;

/**
 * Whose data a screen is showing, and how that answers {@code %playerdata_*%}. See
 * {@link SubjectHolder} for the screens that have one.
 *
 * @param <P> the concrete player data the screen speaks of
 */
public final class Subject<P extends IPlayerData> {

    /** What {@code %playerdata_*%} answers with, over the subject of a screen. */
    public static final RegexReplacer<IPlayerData> PLAYER_DATA = new RegexReplacer<IPlayerData>()
            .addParser("playerdata", IPlayerData::getName)
            .addParser("playerdata_name", IPlayerData::getName)
            .addParser("playerdata_uuid", IPlayerData::getUniqueId)
            .addParser("playerdata_is_online", IPlayerData::isPlayerOnline)
            .addParser("playerdata_ontime", data -> FCTimeFrame.of(OntimeManager.getProvider()
                    .getOntime(data)).getFormattedDiscursive())
            .addParser("playerdata_last_seen", data -> ECTimeFormat.getFormatted(data.getLastSeen()))
            .addParser("playerdata_last_seen_millis", IPlayerData::getLastSeen)
            .addParser("playerdata_first_seen", data -> ECTimeFormat.getFormatted(data.getFirstSeen()))
            .addParser("playerdata_first_seen_millis", IPlayerData::getFirstSeen);

    private P data;

    public Subject(@Nonnull P data) {
        set(data);
    }

    @Nonnull
    public P get() {
        return data;
    }

    public void set(@Nonnull P data) {
        if (data == null) {
            throw new IllegalArgumentException("A screen about a player needs the player data it is "
                    + "about. A screen that speaks of nobody is a plain Gui.of(layout).");
        }
        this.data = data;
    }

    /** {@code base} with this subject's placeholders after it, which is where the order puts them. */
    @Nonnull
    public CompoundReplacer appendTo(@Nonnull CompoundReplacer base) {
        return base.appendReplacer(PLAYER_DATA, data);
    }

}
