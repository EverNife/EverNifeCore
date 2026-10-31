package br.com.finalcraft.evernifecore.hytale.screen;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBinding;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The server's page manager in memory: one page per player, built when opened, torn down (its {@code onDismiss}) when
 * another opens over it or it is closed - in the server's order. Records every command and binding sent, and how many
 * {@code runOnWorldThread} hops the platform had made when each page opened - the one it opened in included.
 */
final class RecordingPageTransport implements HyPageTransport {

    static final String WORLD = "default";

    private final Map<UUID, HyPage> current = new HashMap<>();
    private final Set<UUID> outOfWorld = new HashSet<>();
    private final List<Opened> opened = new ArrayList<>();
    private final List<CustomUICommand[]> updates = new ArrayList<>();
    private boolean refuseOpens;

    static final class Opened {

        final HyPage page;
        final List<CustomUICommand> commands;
        final List<CustomUIEventBinding> bindings;
        final int worldHopsAtOpen;

        private Opened(HyPage page, List<CustomUICommand> commands, List<CustomUIEventBinding> bindings, int worldHopsAtOpen) {
            this.page = page;
            this.commands = commands;
            this.bindings = bindings;
            this.worldHopsAtOpen = worldHopsAtOpen;
        }

    }

    @Override
    public String worldOf(FPlayer viewer) {
        return viewer.isOnline() && !outOfWorld.contains(viewer.getUniqueId()) ? WORLD : null;
    }

    @Override
    public PlayerRef refOf(FPlayer viewer) {
        return null;
    }

    @Override
    public boolean open(FPlayer viewer, HyPage page) {
        if (refuseOpens) {
            return false;
        }
        int hops = ((TestPlatform) EverNifeCore.getPlatform()).getWorldThreadRuns().size();
        HyPage previous = current.put(viewer.getUniqueId(), page);
        if (previous != null) {
            previous.onDismiss(null, null);
        }
        UICommandBuilder ui = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        page.build(null, ui, events, null);
        opened.add(new Opened(page, Arrays.asList(ui.getCommands()), Arrays.asList(events.getEvents()), hops));
        return true;
    }

    @Override
    public void update(HyPage page, UICommandBuilder ui) {
        updates.add(ui.getCommands());
    }

    @Override
    public boolean close(FPlayer viewer, HyPage page) {
        if (current.get(viewer.getUniqueId()) != page) {
            return false;
        }
        current.remove(viewer.getUniqueId());
        page.onDismiss(null, null);
        return true;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What a test does and reads
    // -----------------------------------------------------------------------------------------------------------------

    /** The page {@code viewer} is looking at, or {@code null}. */
    HyPage pageOf(FPlayer viewer) {
        return current.get(viewer.getUniqueId());
    }

    /** The player presses escape. */
    void dismiss(FPlayer viewer) {
        HyPage page = current.remove(viewer.getUniqueId());
        if (page == null) {
            throw new IllegalStateException(viewer.getName() + " has no page open to dismiss.");
        }
        page.onDismiss(null, null);
    }

    /** A click on the cell of {@code slot}, as the client reports a binding of {@code kind}. */
    void click(FPlayer viewer, int slot, String kind) {
        pageOf(viewer).handleDataEvent(null, null, new HyPageEvent(String.valueOf(slot), kind, null));
    }

    /** OK on the open question page, with {@code text} in the field. */
    void answer(FPlayer viewer, String text) {
        pageOf(viewer).handleDataEvent(null, null, new HyPageEvent(null, HyPageLayout.SUBMIT, text));
    }

    /** OK on whichever question page is open - the form a conformance run answers through. */
    void answerAny(String text) {
        for (HyPage page : current.values()) {
            if (page instanceof HyPagePrompt.Page) {
                page.handleDataEvent(null, null, new HyPageEvent(null, HyPageLayout.SUBMIT, text));
                return;
            }
        }
        throw new IllegalStateException("No question page is open to answer.");
    }

    void leaveWorld(FPlayer viewer) {
        outOfWorld.add(viewer.getUniqueId());
    }

    void refuseOpens(boolean refuse) {
        this.refuseOpens = refuse;
    }

    List<Opened> getOpened() {
        return opened;
    }

    Opened lastOpened() {
        return opened.get(opened.size() - 1);
    }

    List<CustomUICommand[]> getUpdates() {
        return updates;
    }

}
