package br.com.finalcraft.evernifecore.hytale.notify;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import br.com.finalcraft.evernifecore.hytale.util.FCHytaleAdventureUtil;
import br.com.finalcraft.evernifecore.notify.HudSounds;
import br.com.finalcraft.evernifecore.notify.IHudAccess;
import br.com.finalcraft.evernifecore.notify.ToastStyle;
import br.com.finalcraft.evernifecore.notify.TrackerLine;
import com.hypixel.hytale.protocol.Objective;
import com.hypixel.hytale.protocol.ObjectiveTask;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.assets.TrackOrUpdateObjective;
import com.hypixel.hytale.protocol.packets.assets.UntrackObjective;
import com.hypixel.hytale.protocol.packets.interface_.EventTitleStyle;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Hytale screen: title ({@code EventTitleUtil}, in seconds), toast (a notification), quest tracker (an objective on
 * the objective panel, sent as a packet) and a 2D sound. Hytale has no action bar, no sidebar, and its boss bar is
 * tied to an entity with no progress, so those three answer false; the compass marker is left to a plugin that owns
 * map markers. Every call is a packet write, safe from any thread.
 */
public final class HyHudAccess implements IHudAccess {

    private static final Set<Capability> SUPPORTED = EnumSet.of(Capability.TITLE, Capability.TOAST, Capability.TRACKER, Capability.SOUND);

    /**
     * Sound event ids for the {@link HudSounds} aliases. A null id is an alias with no sound measured on a server yet:
     * it plays nothing and is logged once.
     */
    private static final Map<String, String> ALIASES = Map.of();

    private final Set<String> unknownSounds = ConcurrentHashMap.newKeySet();

    @Override
    public boolean supports(Capability capability) {
        return SUPPORTED.contains(capability);
    }

    private void require(Capability capability) {
        if (!supports(capability)) {
            throw IHudAccess.unsupported(capability);
        }
    }

    private static PlayerRef ref(FPlayer player) {
        return ((HytaleFPlayer<?>) player).getPlayerRef();
    }

    private static Message message(FancyText text) {
        return FCHytaleAdventureUtil.toHytaleMessage(text.toComponent());
    }

    /** Ticks in, seconds out: Hytale times its titles in seconds. */
    static float seconds(int ticks) {
        return Math.max(0, ticks) / 20f;
    }

    @Override
    public void sendTitle(FPlayer player, FancyText title, FancyText subtitle, int fadeIn, int stay, int fadeOut) {
        require(Capability.TITLE);
        EventTitleUtil.showEventTitleToPlayer(ref(player), message(title), message(subtitle), EventTitleStyle.Default, null,
                seconds(stay), seconds(fadeIn), seconds(fadeOut));
    }

    @Override
    public void clearTitle(FPlayer player) {
        require(Capability.TITLE);
        EventTitleUtil.hideEventTitleFromPlayer(ref(player), 0f);
    }

    @Override
    public void showToast(FPlayer player, FancyText title, FancyText description, ToastStyle style) {
        require(Capability.TOAST);
        NotificationUtil.sendNotification(ref(player).getPacketHandler(), message(title), message(description), styleOf(style));
    }

    static NotificationStyle styleOf(ToastStyle style) {
        return switch (style) {
            case SUCCESS -> NotificationStyle.Success;
            case WARNING -> NotificationStyle.Warning;
            case DANGER -> NotificationStyle.Danger;
            default -> NotificationStyle.Default;
        };
    }

    @Override
    public void showTracker(FPlayer player, String id, FancyText title, List<TrackerLine> lines) {
        require(Capability.TRACKER);
        ObjectiveTask[] tasks = new ObjectiveTask[lines.size()];
        for (int i = 0; i < tasks.length; i++) {
            TrackerLine line = lines.get(i);
            tasks[i] = new ObjectiveTask(message(line.getText()).getFormattedMessage(), line.getCurrent(), Math.max(0, line.getNeeded()));
        }
        Objective objective = new Objective(objectiveId(player.getUniqueId(), id), message(title).getFormattedMessage(),
                Message.empty().getFormattedMessage(), null, tasks);
        ref(player).getPacketHandler().write(new TrackOrUpdateObjective(objective));
    }

    @Override
    public void hideTracker(FPlayer player, String id) {
        require(Capability.TRACKER);
        ref(player).getPacketHandler().write(new UntrackObjective(objectiveId(player.getUniqueId(), id)));
    }

    /** The same objective for the same player and id, so a second show updates it and a hide finds it. */
    static UUID objectiveId(UUID player, String id) {
        return UUID.nameUUIDFromBytes(("evernifecore:tracker:" + player + ":" + id).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void playSound(FPlayer player, String soundId, float volume, float pitch) {
        require(Capability.SOUND);
        String id = HudSounds.isAlias(soundId) ? ALIASES.get(soundId) : soundId;
        int index = id == null ? Integer.MIN_VALUE : SoundEvent.getAssetMap().getIndexOrDefault(id, Integer.MIN_VALUE);
        if (index == Integer.MIN_VALUE) {
            if (unknownSounds.add(soundId)) {
                EverNifeCore.getLog().warning("No Hytale sound event for '" + soundId + "'" + (HudSounds.isAlias(soundId)
                        ? " - this alias has no sound mapped on Hytale yet" : " - name a SoundEvent asset id") + "; it plays nothing.");
            }
            return;
        }
        SoundUtil.playSoundEvent2dToPlayer(ref(player), index, SoundCategory.UI, volume, pitch);
    }
}
