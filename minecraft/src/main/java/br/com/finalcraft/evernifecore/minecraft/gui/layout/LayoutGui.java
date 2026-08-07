package br.com.finalcraft.evernifecore.minecraft.gui.layout;

import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.ontime.OntimeManager;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.time.ECTimeFormat;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
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
 * <pre>{@code
 * public class ProfileGui extends LayoutGui<MyPlayerData, ProfileLayout> {
 *     public ProfileGui(MyPlayerData subject) {
 *         super(Layouts.of(ProfileLayout.class), subject);
 *     }
 * }
 * }</pre>
 */
public class LayoutGui<P extends IPlayerData, L extends LayoutBase> extends Gui<L> {

    /** What {@code %playerdata_*%} answers with, over the subject of the screen. */
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

    private final P playerData;

    public LayoutGui(@Nonnull L layout, @Nonnull P playerData) {
        super(required(layout).getType(), layout.getRows(), layout);
        if (playerData == null) {
            throw new IllegalArgumentException("A LayoutGui is a screen about a player, so it needs the "
                    + "player data it is about. A screen that speaks of nobody is a plain Gui.of(layout).");
        }
        this.playerData = playerData;
    }

    /** Whose data this screen shows. Not the viewer, and not necessarily online. */
    @Nonnull
    public P getPlayerData() {
        return playerData;
    }

    @Override
    @Nonnull
    public CompoundReplacer getReplacer() {
        return super.getReplacer().appendReplacer(PLAYER_DATA, playerData);
    }

    private static <L extends LayoutBase> L required(L layout) {
        if (layout == null) {
            throw new IllegalArgumentException("A LayoutGui is sized and decorated by its layout, so it "
                    + "cannot be built without one. Read it with Layouts.of(MyLayout.class).");
        }
        return layout;
    }

}
