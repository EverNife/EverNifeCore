package br.com.finalcraft.evernifecore.minecraft.notify;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.minecraft.loader.EverNifeCoreBukkitPlugin;
import br.com.finalcraft.evernifecore.minecraft.sound.FCSound;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.minecraft.util.FCMinecraftAdventureUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.notify.BossBarColor;
import br.com.finalcraft.evernifecore.notify.HudSounds;
import br.com.finalcraft.evernifecore.notify.IHudAccess;
import br.com.finalcraft.evernifecore.notify.ToastStyle;
import br.com.finalcraft.evernifecore.notify.TrackerLine;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Bukkit screen. What it supports is decided once, by version: action bar and title above 1.7.10 (or with the
 * NecroTempus mod there), boss bar from 1.9, toast from 1.12, sidebar, tracker, sound and compass everywhere. Action
 * bar and title go through the Adventure audience first and the Bukkit API after; boss bars, the sidebar, the
 * tracker and toasts are Bukkit objects, touched on the main thread (each call hops there by itself). The tracker
 * is drawn on the sidebar slot, which it shares with {@link #setSidebar}: the last one shown is drawn, and hiding it
 * draws the other again, or gives the player back the scoreboard they had.
 */
public final class McHudAccess implements IHudAccess {

    private static final String OBJECTIVE = "ec_hud";
    private static final String SIDEBAR_BOARD = "sidebar";
    private static final String TRACKER_BOARD = "tracker:";

    private final MCDetailedVersion version;
    private final Set<Capability> supported;
    /** Toasts ride on loading an advancement at runtime; a server that refuses it once never gets another one. */
    private volatile boolean toastBroken;
    private final Set<String> unknownSounds = ConcurrentHashMap.newKeySet();

    private final BossBars bossBars;
    private final Map<UUID, Screen> screens = new ConcurrentHashMap<>();

    public McHudAccess() {
        this(MCDetailedVersion.getCurrent(), FCBukkitUtil.isModLoaded("necrotempus"));
    }

    /**
     * @param version     the server version the answers are for
     * @param necroTempus whether the NecroTempus mod is loaded - it brings action bar and title to 1.7.10
     */
    McHudAccess(MCDetailedVersion version, boolean necroTempus) {
        this.version = version;
        this.supported = supportedOn(version, necroTempus);
        this.bossBars = supported.contains(Capability.BOSS_BAR) ? new BossBars() : null;
    }

    static Set<Capability> supportedOn(MCDetailedVersion version, boolean necroTempus) {
        Set<Capability> supported = EnumSet.of(Capability.SIDEBAR, Capability.TRACKER, Capability.SOUND, Capability.COMPASS);
        if (version.isHigherEquals(MCDetailedVersion.v1_8_R1) || necroTempus) {
            supported.add(Capability.ACTION_BAR);
            supported.add(Capability.TITLE);
        }
        if (version.isHigherEquals(MCDetailedVersion.v1_9_R1)) {
            supported.add(Capability.BOSS_BAR);
        }
        if (version.isHigherEquals(MCDetailedVersion.v1_12_R1)) {
            supported.add(Capability.TOAST);
        }
        return supported;
    }

    /** Stable for the process, except {@code TOAST}: it turns false for good the first time the server refuses one. */
    @Override
    public boolean supports(Capability capability) {
        return supported.contains(capability) && !(capability == Capability.TOAST && toastBroken);
    }

    private void require(Capability capability) {
        if (!supports(capability)) {
            throw IHudAccess.unsupported(capability);
        }
    }

    private static Player bukkit(FPlayer player) {
        return player.getDelegate(Player.class);
    }

    // ---- ACTION_BAR ----

    @Override
    public void sendActionBar(FPlayer player, FancyText text) {
        require(Capability.ACTION_BAR);
        FCMinecraftAdventureUtil.sendActionBar(bukkit(player), text.toComponent());
    }

    // ---- TITLE ----

    @Override
    public void sendTitle(FPlayer player, FancyText title, FancyText subtitle, int fadeIn, int stay, int fadeOut) {
        require(Capability.TITLE);
        Player bukkit = bukkit(player);
        if (FCMinecraftAdventureUtil.isAdventureAvailable()) {
            try {
                FCMinecraftAdventureUtil.getAdventure().player(bukkit).showTitle(Title.title(title.toComponent(), subtitle.toComponent(),
                        Title.Times.times(ticks(fadeIn), ticks(stay), ticks(fadeOut))));
                return;
            } catch (Throwable adventureFailure) {
                //the Bukkit API below is the next transport
            }
        }
        if (version.isLower(MCDetailedVersion.v1_11_R1)) {
            //the timed form only arrived in 1.11; before that the server takes the pair alone
            bukkit.sendTitle(title.getText(), subtitle.getText());
        } else {
            TimedTitle.send(bukkit, title.getText(), subtitle.getText(), fadeIn, stay, fadeOut);
        }
    }

    /** The timed title, a 1.11 member: only reached on the {@code isLower(v1_11_R1) == false} branch. */
    private static final class TimedTitle {
        static void send(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }
    }

    private static Duration ticks(int ticks) {
        return Duration.ofMillis(Math.max(0, ticks) * 50L);
    }

    @Override
    public void clearTitle(FPlayer player) {
        require(Capability.TITLE);
        Player bukkit = bukkit(player);
        if (FCMinecraftAdventureUtil.isAdventureAvailable()) {
            try {
                FCMinecraftAdventureUtil.getAdventure().player(bukkit).clearTitle();
                return;
            } catch (Throwable adventureFailure) {
                //the Bukkit API below is the next transport
            }
        }
        bukkit.resetTitle();
    }

    // ---- BOSS_BAR ----

    @Override
    public void showBossBar(FPlayer player, String id, FancyText text, float progress, BossBarColor color) {
        require(Capability.BOSS_BAR);
        Player bukkit = bukkit(player);
        ECScheduler.onMainThread(() -> bossBars.show(bukkit, id, text.getText(), progress, color));
    }

    @Override
    public void hideBossBar(FPlayer player, String id) {
        require(Capability.BOSS_BAR);
        Player bukkit = bukkit(player);
        ECScheduler.onMainThread(() -> bossBars.hide(bukkit, id));
    }

    /**
     * The boss bars shown, one Bukkit bar per player and id. A 1.9 API: created only where {@code BOSS_BAR} is
     * supported, so the class never loads on an older server. Thread: main.
     */
    private static final class BossBars {
        private final Map<String, BossBar> bars = new ConcurrentHashMap<>();

        void show(Player player, String id, String text, float progress, BossBarColor color) {
            BossBar bar = bars.computeIfAbsent(player.getUniqueId() + "|" + id,
                    key -> Bukkit.createBossBar(text, BarColor.valueOf(color.name()), BarStyle.SOLID));
            bar.setTitle(text);
            bar.setColor(BarColor.valueOf(color.name()));
            bar.setProgress(Math.max(0d, Math.min(1d, progress)));
            if (!bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
        }

        void hide(Player player, String id) {
            BossBar bar = bars.remove(player.getUniqueId() + "|" + id);
            if (bar != null) {
                bar.removeAll();
            }
        }

        void forget(Player player) {
            String prefix = player.getUniqueId() + "|";
            for (Iterator<Map.Entry<String, BossBar>> it = bars.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, BossBar> entry = it.next();
                if (entry.getKey().startsWith(prefix)) {
                    entry.getValue().removeAll();
                    it.remove();
                }
            }
        }
    }

    // ---- SIDEBAR / TRACKER ----

    /** Lines longer than the score name the server takes (16 chars on 1.7, 40 from 1.8) are cut; the title at 32. */
    @Override
    public void setSidebar(FPlayer player, FancyText title, List<FancyText> lines) {
        require(Capability.SIDEBAR);
        if (lines.isEmpty()) {
            clearSidebar(player);
            return;
        }
        List<String> texts = new ArrayList<>();
        for (FancyText line : lines) {
            texts.add(line.getText());
        }
        putBoard(bukkit(player), SIDEBAR_BOARD, title.getText(), texts);
    }

    @Override
    public void clearSidebar(FPlayer player) {
        require(Capability.SIDEBAR);
        removeBoard(bukkit(player), SIDEBAR_BOARD);
    }

    /** Drawn on the sidebar slot: one line per {@link TrackerLine}, its count after the text. */
    @Override
    public void showTracker(FPlayer player, String id, FancyText title, List<TrackerLine> lines) {
        require(Capability.TRACKER);
        List<String> texts = new ArrayList<>();
        for (TrackerLine line : lines) {
            texts.add(line.getText().getText() + (line.hasCount() ? " §7" + line.getCurrent() + "/" + line.getNeeded() : ""));
        }
        putBoard(bukkit(player), TRACKER_BOARD + id, title.getText(), texts);
    }

    @Override
    public void hideTracker(FPlayer player, String id) {
        require(Capability.TRACKER);
        removeBoard(bukkit(player), TRACKER_BOARD + id);
    }

    /** One player's sidebar slot: the boards that want it (the last one put is drawn) and the scoreboard to give back. */
    private static final class Screen {
        final LinkedHashMap<String, Board> boards = new LinkedHashMap<>();
        Scoreboard own;
        Scoreboard previous;
    }

    private static final class Board {
        final String title;
        final List<String> lines;

        Board(String title, List<String> lines) {
            this.title = title;
            this.lines = lines;
        }
    }

    private void putBoard(Player player, String name, String title, List<String> lines) {
        ECScheduler.onMainThread(() -> {
            Screen screen = screens.computeIfAbsent(player.getUniqueId(), uuid -> new Screen());
            screen.boards.remove(name);
            screen.boards.put(name, new Board(title, lines));
            draw(player, screen);
        });
    }

    private void removeBoard(Player player, String name) {
        ECScheduler.onMainThread(() -> {
            Screen screen = screens.get(player.getUniqueId());
            if (screen == null || screen.boards.remove(name) == null) {
                return;
            }
            draw(player, screen);
        });
    }

    private void draw(Player player, Screen screen) {
        Board top = null;
        for (Board board : screen.boards.values()) {
            top = board;
        }
        if (top == null) {
            screens.remove(player.getUniqueId());
            if (player.isOnline() && player.getScoreboard() == screen.own) {
                player.setScoreboard(screen.previous != null ? screen.previous : Bukkit.getScoreboardManager().getMainScoreboard());
            }
            return;
        }
        if (screen.own == null) {
            screen.own = Bukkit.getScoreboardManager().getNewScoreboard();
            screen.previous = player.getScoreboard();
        }
        Objective old = screen.own.getObjective(OBJECTIVE);
        if (old != null) {
            old.unregister();
        }
        Objective objective = screen.own.registerNewObjective(OBJECTIVE, "dummy");
        objective.setDisplayName(cut(top.title, 32));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        int limit = version.isLower(MCDetailedVersion.v1_8_R1) ? 16 : 40;
        Set<String> used = new HashSet<>();
        int score = top.lines.size();
        for (String line : top.lines) {
            String entry = cut(line, limit);
            //a scoreboard keys lines by text: a repeated line gets an invisible reset code until it is unique
            while (!used.add(entry)) {
                entry = cut(entry, limit - 2) + "§r";
                if (entry.length() > limit) {
                    break;
                }
            }
            objective.getScore(entry).setScore(score--);
        }
        if (player.getScoreboard() != screen.own) {
            player.setScoreboard(screen.own);
        }
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    // ---- TOAST ----

    @Override
    public void showToast(FPlayer player, FancyText title, FancyText description, ToastStyle style) {
        require(Capability.TOAST);
        Player bukkit = bukkit(player);
        String json = toastJson(title, description, style);
        ECScheduler.onMainThread(() -> {
            if (!Toasts.show(bukkit, json)) {
                toastBroken = true;
            }
        });
    }

    /**
     * A toast is a hidden advancement awarded and revoked on the next tick. One advancement per distinct toast: the server
     * cannot unload one ({@code removeAdvancement} only deletes its datapack file), so a repeat reuses the loaded copy and
     * what stays loaded until the next data reload is bounded by the distinct toasts, not by how many were shown. A 1.12
     * API, only reached behind {@code require(TOAST)}. Thread: main.
     */
    private static final class Toasts {
        /** @return false when the server refused the advancement (logged) */
        static boolean show(Player player, String json) {
            NamespacedKey key = keyOf(json);
            Advancement advancement = Bukkit.getAdvancement(key);
            if (advancement == null) {
                try {
                    advancement = Bukkit.getUnsafe().loadAdvancement(key, json);
                } catch (Throwable failure) {
                    advancement = null;
                    EverNifeCore.getLog().warning("This server refused the advancement a toast is drawn with (" + failure
                            + "); toasts are off until restart and notify chains fall to their next step.");
                }
                if (advancement == null) {
                    return false;
                }
                //the loaded copy is all the toast needs; the file would load it again on every boot
                Bukkit.getUnsafe().removeAdvancement(key);
            }
            Advancement shown = advancement;
            //revoked first: the same toast twice within a tick is still awarded, and so still drawn
            player.getAdvancementProgress(shown).revokeCriteria("trigger");
            player.getAdvancementProgress(shown).awardCriteria("trigger");
            ECScheduler.onMainThreadLater(() -> {
                if (player.isOnline()) {
                    player.getAdvancementProgress(shown).revokeCriteria("trigger");
                }
            });
            return true;
        }

        /** The same title, description and style always name the same advancement. */
        static NamespacedKey keyOf(String json) {
            return new NamespacedKey(EverNifeCoreBukkitPlugin.instance, "toast/" + UUID.nameUUIDFromBytes(json.getBytes(StandardCharsets.UTF_8)));
        }
    }

    String toastJson(FancyText title, FancyText description, ToastStyle style) {
        GsonComponentSerializer gson = GsonComponentSerializer.colorDownsamplingGson();
        //1.20.5 moved the icon from an item name to an item stack
        String icon = version.isHigherEquals(MCDetailedVersion.v1_20_R4) ? "{\"id\":\"minecraft:book\"}" : "{\"item\":\"minecraft:book\"}";
        return "{\"criteria\":{\"trigger\":{\"trigger\":\"minecraft:impossible\"}},\"display\":{\"icon\":" + icon
                + ",\"title\":" + gson.serialize(title.toComponent())
                + ",\"description\":" + gson.serialize(description.toComponent())
                + ",\"frame\":\"" + frameOf(style) + "\",\"show_toast\":true,\"announce_to_chat\":false,\"hidden\":true}}";
    }

    static String frameOf(ToastStyle style) {
        switch (style) {
            case SUCCESS:
                return "goal";
            case DANGER:
                return "challenge";
            default:
                return "task";
        }
    }

    // ---- SOUND ----

    @Override
    public void playSound(FPlayer player, String soundId, float volume, float pitch) {
        require(Capability.SOUND);
        String key = resolveSound(soundId);
        if (key == null) {
            return;
        }
        Player bukkit = bukkit(player);
        bukkit.playSound(bukkit.getLocation(), key, volume, pitch);
    }

    /** The key of this server's era for an alias, the id itself otherwise; null (logged once) for an unknown alias. */
    String resolveSound(String soundId) {
        switch (soundId) {
            case HudSounds.LEVEL_UP:
                return FCSound.LEVEL_UP.getKey();
            case HudSounds.CLICK:
                return FCSound.CLICK.getKey();
            case HudSounds.PLING:
                return FCSound.PLING.getKey();
            case HudSounds.DENIED:
                return FCSound.ANVIL_LAND.getKey();
            default:
                if (soundId.startsWith("evernifecore:")) {
                    if (unknownSounds.add(soundId)) {
                        EverNifeCore.getLog().warning("Unknown sound alias '" + soundId + "' - the aliases are " + HudSounds.ALL
                                + "; it plays nothing.");
                    }
                    return null;
                }
                return soundId;
        }
    }

    // ---- COMPASS ----

    @Override
    public void setCompassTarget(FPlayer player, WorldLocPos target) {
        require(Capability.COMPASS);
        World world = Bukkit.getWorld(target.getWorldName());
        if (world == null) {
            throw new IllegalArgumentException("The compass target is in the world '" + target.getWorldName()
                    + "', which is not loaded - load it first, or point at a loaded world.");
        }
        bukkit(player).setCompassTarget(new Location(world, target.getX(), target.getY(), target.getZ()));
    }

    @Override
    public void resetCompassTarget(FPlayer player) {
        require(Capability.COMPASS);
        Player bukkit = bukkit(player);
        bukkit.setCompassTarget(bukkit.getWorld().getSpawnLocation());
    }

    // ---- quit ----

    /** Drops what this screen keeps for a player who left: their boss bars and their sidebar slot. Thread: main. */
    public void forget(Player player) {
        if (bossBars != null) {
            bossBars.forget(player);
        }
        screens.remove(player.getUniqueId());
    }
}
