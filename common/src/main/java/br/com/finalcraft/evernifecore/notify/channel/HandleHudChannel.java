package br.com.finalcraft.evernifecore.notify.channel;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.notify.IHudAccess;
import br.com.finalcraft.evernifecore.notify.Notification;
import br.com.finalcraft.evernifecore.notify.NotifyChannel;
import br.com.finalcraft.evernifecore.time.ECScheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A channel whose notifications stay on screen under their id until cleared (a boss bar, the tracker, the sidebar):
 * the same id shows again in place. It remembers what it shows each player, and on which {@link IHudAccess}, so a
 * clear, a quit or a timed hide takes it off the platform that showed it.
 */
public abstract class HandleHudChannel implements NotifyChannel {

    private final IHudAccess.Capability capability;
    private final Map<UUID, Map<String, Handle>> shown = new ConcurrentHashMap<>();

    protected HandleHudChannel(IHudAccess.Capability capability) {
        this.capability = capability;
    }

    @Override
    public boolean isSupported() {
        return EverNifeCore.getPlatform().getHud().supports(capability);
    }

    protected abstract void show(IHudAccess hud, FPlayer player, String id, Notification notification);

    protected abstract void hide(IHudAccess hud, FPlayer player, String id);

    /** Whether the notification's duration hides it; false keeps it until cleared whatever the duration. */
    protected boolean hidesAfterDuration() {
        return false;
    }

    /** Whether the player has one slot for this channel, so a new id replaces what another id shows. */
    protected boolean singleSlot() {
        return false;
    }

    @Override
    public void deliver(FPlayer player, Notification notification) {
        IHudAccess hud = EverNifeCore.getPlatform().getHud();
        String id = notification.getIdOrAnonymous();
        Handle handle = new Handle(player, hud);
        Map<String, Handle> handles = shown.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        if (singleSlot()) {
            handles.clear(); //the new one takes the slot on screen
        }
        handles.put(id, handle);
        show(hud, player, id, notification);

        Duration duration = notification.getDuration();
        if (hidesAfterDuration() && !duration.isZero()) {
            ECScheduler.scheduleAsync(() -> {
                //a later delivery of the same id has its own timer, and a clear already hid it
                if (handles.remove(id, handle)) {
                    hide(handle.hud, handle.player, id);
                }
            }, duration.toMillis());
        }
    }

    @Override
    public void clear(FPlayer player, String id) {
        Map<String, Handle> handles = shown.get(player.getUniqueId());
        Handle handle = handles == null ? null : handles.remove(id);
        if (handle != null) {
            hide(handle.hud, handle.player, id);
        }
    }

    @Override
    public void onQuit(UUID player) {
        Map<String, Handle> handles = shown.remove(player);
        if (handles == null) {
            return;
        }
        for (Map.Entry<String, Handle> entry : handles.entrySet()) {
            hide(entry.getValue().hud, entry.getValue().player, entry.getKey());
        }
    }

    @Override
    public void clearAll() {
        for (UUID player : new ArrayList<>(shown.keySet())) {
            onQuit(player);
        }
    }

    /** Compared by identity: two deliveries of one id are two handles, so a timer only hides its own. */
    private static final class Handle {

        private final FPlayer player;
        private final IHudAccess hud;

        private Handle(FPlayer player, IHudAccess hud) {
            this.player = player;
            this.hud = hud;
        }
    }
}
